package com.pandalfinder.data

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.pandalfinder.BuildConfig
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Parsed semantic version representation (major.minor.patch).
 * Provides strictly numeric comparisons (prevents lexicographical sorting bugs such as "2.10.0" < "2.9.0").
 */
data class SemanticVersion(
    val major: Int,
    val minor: Int,
    val patch: Int
) : Comparable<SemanticVersion> {

    override fun compareTo(other: SemanticVersion): Int {
        if (this.major != other.major) return this.major.compareTo(other.major)
        if (this.minor != other.minor) return this.minor.compareTo(other.minor)
        return this.patch.compareTo(other.patch)
    }

    override fun toString(): String = "$major.$minor.$patch"

    companion object {
        fun parse(versionStr: String?): SemanticVersion? {
            if (versionStr.isNullOrBlank()) return null
            val clean = versionStr.trim()
                .replaceFirst(Regex("""^[^\d]+"""), "")
                .trim()
            val match = Regex("""^(\d+)(?:\.(\d+))?(?:\.(\d+))?""").find(clean) ?: return null
            val major = match.groupValues[1].toIntOrNull() ?: 0
            val minor = match.groupValues.getOrNull(2)?.takeIf { it.isNotEmpty() }?.toIntOrNull() ?: 0
            val patch = match.groupValues.getOrNull(3)?.takeIf { it.isNotEmpty() }?.toIntOrNull() ?: 0
            return SemanticVersion(major, minor, patch)
        }
    }
}

/**
 * Metadata representation of a verified GitHub release.
 */
data class AppReleaseInfo(
    val versionName: String,
    val versionCode: Int,
    val releaseTitle: String,
    val releaseNotes: String,
    val apkDownloadUrl: String,
    val apkFileName: String,
    val apkSize: Long,
    val publishedAt: String,
    val isMandatory: Boolean,
    val minSupportedVersionCode: Int,
    val minSupportedVersionName: String?
)

/**
 * Result state returned by the update check operation.
 */
sealed class UpdateCheckResult {
    data class UpdateAvailable(val releaseInfo: AppReleaseInfo, val isManualCheck: Boolean) : UpdateCheckResult()
    data class UpToDate(val currentVersion: String, val isManualCheck: Boolean) : UpdateCheckResult()
    data class Error(val message: String, val isManualCheck: Boolean) : UpdateCheckResult()
    object Throttled : UpdateCheckResult()
}

/**
 * Internal metadata extracted from release descriptions or metadata markers.
 */
data class ReleaseBodyMetadata(
    val versionCode: Int? = null,
    val minSupportedVersionCode: Int? = null,
    val minSupportedVersionName: String? = null,
    val isMandatory: Boolean = false
)

/**
 * Safe, resilient in-app update checker for GitHub-distributed PandalQuest APK releases.
 */
class AppUpdateRepository(
    private val context: Context? = null,
    private val apiEndpoint: String = DEFAULT_GITHUB_RELEASES_URL,
    private val httpFetcher: ((url: String) -> Pair<Int, String>)? = null,
    private val executor: java.util.concurrent.Executor = Executors.newSingleThreadExecutor(),
    private val resultDispatcher: ((Runnable) -> Unit)? = null
) {

    companion object {
        private const val TAG = "AppUpdateRepo"
        const val DEFAULT_GITHUB_RELEASES_URL = "https://api.github.com/repos/d-ghosh717/PandalQuest/releases"
        const val TRUSTED_DOWNLOAD_PREFIX = "https://github.com/d-ghosh717/PandalQuest/releases/download/"
        const val VERIFIED_V211_DOWNLOAD_URL = "https://github.com/d-ghosh717/PandalQuest/releases/download/v2.1.1/PandalQuest-v2.1.1.apk"

        private const val PREFS_NAME = "pandalquest_update_prefs"
        private const val KEY_LAST_CHECK_TIME = "last_update_check_time"
        private const val KEY_DISMISSED_VERSION = "dismissed_update_version"
        private const val KEY_DISMISSED_TIME = "dismissed_update_time"

        const val THROTTLE_INTERVAL_MILLIS = 24 * 60 * 60 * 1000L // 24 hours
        private const val HTTP_TIMEOUT_MILLIS = 8000 // 8 seconds
    }

    private val mainHandler by lazy {
        try {
            Handler(Looper.getMainLooper())
        } catch (_: Exception) {
            null
        }
    }

    private val prefs: SharedPreferences? by lazy {
        context?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Checks for updates.
     * When [isManualCheck] is false, throttles checks to once every 24 hours.
     * When [isManualCheck] is true, executes immediately and provides feedback.
     */
    fun checkForUpdates(
        isManualCheck: Boolean = false,
        onResult: (UpdateCheckResult) -> Unit
    ) {
        val now = System.currentTimeMillis()

        // 1. Automatic check throttle check (24h)
        if (!isManualCheck) {
            val lastCheck = prefs?.getLong(KEY_LAST_CHECK_TIME, 0L) ?: 0L
            if (now - lastCheck < THROTTLE_INTERVAL_MILLIS) {
                Log.d(TAG, "Update check throttled (last check: ${now - lastCheck}ms ago)")
                onResult(UpdateCheckResult.Throttled)
                return
            }
        }

        executor.execute {
            try {
                // Perform HTTP request
                val (httpStatus, responseBody) = httpFetcher?.invoke(apiEndpoint) ?: executeHttpRequest(apiEndpoint)

                if (httpStatus != 200) {
                    val err = when (httpStatus) {
                        403, 429 -> "GitHub rate limit exceeded. Please try again later."
                        404 -> "Release repository not found."
                        else -> "Unable to connect to update server (HTTP $httpStatus)."
                    }
                    Log.w(TAG, "Update check failed: $err")
                    postResult(UpdateCheckResult.Error(err, isManualCheck), onResult)
                    return@execute
                }

                // Update last check time upon successful check
                prefs?.edit()?.putLong(KEY_LAST_CHECK_TIME, now)?.apply()

                // Parse releases list or single release object
                val releases = parseReleases(responseBody)
                if (releases.isEmpty()) {
                    Log.d(TAG, "No valid stable releases with APK found in GitHub response")
                    postResult(UpdateCheckResult.UpToDate(BuildConfig.VERSION_NAME, isManualCheck), onResult)
                    return@execute
                }

                // The releases list is sorted by GitHub descending by publication date.
                // Pick the newest stable release.
                val latestRelease = releases.first()

                val installedVersionName = BuildConfig.VERSION_NAME
                val installedVersionCode = BuildConfig.VERSION_CODE

                val hasUpdate = isUpdateAvailable(
                    installedVersionName = installedVersionName,
                    installedVersionCode = installedVersionCode,
                    remoteRelease = latestRelease
                )

                if (!hasUpdate) {
                    Log.d(TAG, "Installed version $installedVersionName ($installedVersionCode) is up to date.")
                    postResult(UpdateCheckResult.UpToDate(installedVersionName, isManualCheck), onResult)
                    return@execute
                }

                // Check if user previously dismissed this non-mandatory update within the last 24h
                if (!isManualCheck && !latestRelease.isMandatory) {
                    val dismissedVersion = prefs?.getString(KEY_DISMISSED_VERSION, null)
                    val dismissedTime = prefs?.getLong(KEY_DISMISSED_TIME, 0L) ?: 0L
                    if (dismissedVersion == latestRelease.versionName && (now - dismissedTime < THROTTLE_INTERVAL_MILLIS)) {
                        Log.d(TAG, "Update ${latestRelease.versionName} previously dismissed; skipping automatic prompt.")
                        postResult(UpdateCheckResult.Throttled, onResult)
                        return@execute
                    }
                }

                Log.i(TAG, "Update available: ${latestRelease.versionName} (Installed: $installedVersionName)")
                postResult(UpdateCheckResult.UpdateAvailable(latestRelease, isManualCheck), onResult)

            } catch (e: Exception) {
                Log.e(TAG, "Error executing update check", e)
                val errMsg = e.localizedMessage ?: "Network error while checking for updates."
                postResult(UpdateCheckResult.Error(errMsg, isManualCheck), onResult)
            }
        }
    }

    /**
     * Parses a GitHub Releases JSON response (handles both array of releases and single release object).
     */
    fun parseReleases(jsonText: String): List<AppReleaseInfo> {
        val trimmed = jsonText.trim()
        val releases = mutableListOf<AppReleaseInfo>()

        if (trimmed.startsWith("[")) {
            val array = JSONArray(trimmed)
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                parseSingleRelease(obj)?.let { releases.add(it) }
            }
        } else if (trimmed.startsWith("{")) {
            val obj = JSONObject(trimmed)
            parseSingleRelease(obj)?.let { releases.add(it) }
        }

        // Sort descending by versionCode, then semantic version, so the newest stable release is first
        releases.sortWith(
            compareByDescending<AppReleaseInfo> { it.versionCode }
                .thenByDescending { SemanticVersion.parse(it.versionName) }
        )

        return releases
    }

    /**
     * Parses a single GitHub release object, enforcing:
     * - non-draft
     * - non-prerelease
     * - presence of a verified .apk asset with trusted download URL
     */
    fun parseSingleRelease(obj: JSONObject): AppReleaseInfo? {
        val isDraft = obj.optBoolean("draft", false)
        val isPrerelease = obj.optBoolean("prerelease", false)
        if (isDraft || isPrerelease) {
            return null // Ignore drafts and prereleases
        }

        val tagName = obj.optString("tag_name", "").trim()
        val releaseName = obj.optString("name", "").trim()
        val body = obj.optString("body", "")
        val publishedAt = obj.optString("published_at", "")

        // Derive clean versionName from tag_name or release title
        val semVer = SemanticVersion.parse(tagName) ?: SemanticVersion.parse(releaseName) ?: return null
        val versionName = semVer.toString()

        // Extract metadata from body
        val metadata = extractMetadataFromBody(body)

        // Find candidate APK asset
        val assetsArray = obj.optJSONArray("assets") ?: return null
        var chosenApkUrl = ""
        var chosenApkName = ""
        var chosenApkSize = 0L

        for (i in 0 until assetsArray.length()) {
            val asset = assetsArray.optJSONObject(i) ?: continue
            val assetName = asset.optString("name", "")
            val downloadUrl = asset.optString("browser_download_url", "")
            val size = asset.optLong("size", 0L)

            if (assetName.endsWith(".apk", ignoreCase = true) && isValidDownloadUrl(downloadUrl)) {
                // If we find a PandalQuest-*.apk, prioritize it; otherwise use the first valid APK
                if (chosenApkUrl.isEmpty() || assetName.contains("PandalQuest", ignoreCase = true)) {
                    chosenApkUrl = downloadUrl
                    chosenApkName = assetName
                    chosenApkSize = size
                }
            }
        }

        // Verify that the release contains a valid, verified APK asset
        if (chosenApkUrl.isEmpty()) {
            return null
        }

        // Determine remote versionCode:
        // 1. From body metadata if specified
        // 2. From known version code map (v2.1.1 -> 5, v2.1.0 -> 4, v2.0.0 -> 2, v1.0.0 -> 1)
        // 3. Fallback to derived code (major * 1000 + minor * 100 + patch)
        val versionCode = metadata.versionCode
            ?: mapKnownVersionCode(versionName)
            ?: (semVer.major * 1000 + semVer.minor * 100 + semVer.patch)

        val isMandatory = metadata.isMandatory || (metadata.minSupportedVersionCode != null && BuildConfig.VERSION_CODE < metadata.minSupportedVersionCode)

        return AppReleaseInfo(
            versionName = versionName,
            versionCode = versionCode,
            releaseTitle = if (releaseName.isNotBlank()) releaseName else "v$versionName",
            releaseNotes = cleanReleaseNotes(body),
            apkDownloadUrl = chosenApkUrl,
            apkFileName = chosenApkName,
            apkSize = chosenApkSize,
            publishedAt = publishedAt,
            isMandatory = isMandatory,
            minSupportedVersionCode = metadata.minSupportedVersionCode ?: 1,
            minSupportedVersionName = metadata.minSupportedVersionName
        )
    }

    /**
     * Determines if a remote release is newer than the locally installed version.
     * Uses strict numeric SemanticVersion and versionCode comparisons.
     */
    fun isUpdateAvailable(
        installedVersionName: String,
        installedVersionCode: Int,
        remoteRelease: AppReleaseInfo
    ): Boolean {
        val installedSemVer = SemanticVersion.parse(installedVersionName)
        val remoteSemVer = SemanticVersion.parse(remoteRelease.versionName)

        // 1. If remote versionCode is explicitly specified and greater, update is available
        if (remoteRelease.versionCode > installedVersionCode) {
            return true
        }

        // 2. If remote versionCode is equal or less, but remote SemVer is strictly greater
        if (installedSemVer != null && remoteSemVer != null) {
            if (remoteSemVer > installedSemVer) {
                return true
            }
        }

        return false
    }

    /**
     * Validates that the APK download URL points to the trusted official GitHub repository.
     */
    fun isValidDownloadUrl(url: String): Boolean {
        if (url.isBlank()) return false
        val uri = try {
            java.net.URI(url)
        } catch (_: Exception) {
            return false
        }

        if (uri.scheme?.lowercase() != "https") return false
        val host = uri.host?.lowercase() ?: return false

        // Must come from github.com or githubusercontent objects
        if (host == "github.com") {
            return uri.path?.startsWith("/d-ghosh717/PandalQuest/releases/download/") == true
        } else if (host == "objects.githubusercontent.com") {
            return true
        }

        return false
    }

    /**
     * Extracts optional structured metadata from markdown release notes.
     * Supported formats:
     * - `<!-- metadata: versionCode=6, minVersionCode=5, mandatory=true -->`
     * - `versionCode: 6`
     * - `minSupportedVersionCode: 5`
     * - `mandatory: true`
     */
    fun extractMetadataFromBody(body: String): ReleaseBodyMetadata {
        var code: Int? = null
        var minCode: Int? = null
        var minName: String? = null
        var mandatory = false

        val lines = body.lines()
        for (line in lines) {
            val trimmed = line.trim()

            val minCodeMatch = Regex("""\b(?:minSupportedVersionCode|minVersionCode|min_version_code)\s*[:=]\s*(\d+)""", RegexOption.IGNORE_CASE).find(trimmed)
            if (minCodeMatch != null) minCode = minCodeMatch.groupValues[1].toIntOrNull()

            val codeMatch = Regex("""\b(?:versionCode|version_code)\s*[:=]\s*(\d+)""", RegexOption.IGNORE_CASE).find(trimmed)
            if (codeMatch != null && minCodeMatch == null) code = codeMatch.groupValues[1].toIntOrNull()

            val minNameMatch = Regex("""\b(?:minSupportedVersion|min_version)\s*[:=]\s*([0-9.]+)""", RegexOption.IGNORE_CASE).find(trimmed)
            if (minNameMatch != null) minName = minNameMatch.groupValues[1]

            val mandatoryMatch = Regex("""\b(?:mandatory|required_update)\s*[:=]\s*(true|yes|1)""", RegexOption.IGNORE_CASE).find(trimmed)
            if (mandatoryMatch != null) mandatory = true
        }

        return ReleaseBodyMetadata(
            versionCode = code,
            minSupportedVersionCode = minCode,
            minSupportedVersionName = minName,
            isMandatory = mandatory
        )
    }

    /**
     * Cleans release notes by removing internal metadata comments while keeping user-facing notes.
     */
    private fun cleanReleaseNotes(body: String): String {
        return body.replace(Regex("""<!--[\s\S]*?-->"""), "")
            .lines()
            .filter { line ->
                val trimmed = line.trim()
                !trimmed.startsWith("versionCode:", ignoreCase = true) &&
                !trimmed.startsWith("minSupportedVersionCode:", ignoreCase = true) &&
                !trimmed.startsWith("mandatory:", ignoreCase = true)
            }
            .joinToString("\n")
            .trim()
    }

    /**
     * Known historical version codes in PandalQuest.
     */
    private fun mapKnownVersionCode(versionName: String): Int? {
        return when (versionName) {
            "2.1.2" -> 6
            "2.1.1" -> 5
            "2.1.0" -> 4
            "2.0.0" -> 2
            "1.0.0" -> 1
            else -> null
        }
    }

    /**
     * Records that the user clicked "Later" for this version, preventing repetitive prompts for 24h.
     */
    fun recordUpdateDismissed(versionName: String) {
        prefs?.edit()
            ?.putString(KEY_DISMISSED_VERSION, versionName)
            ?.putLong(KEY_DISMISSED_TIME, System.currentTimeMillis())
            ?.apply()
    }

    /**
     * Opens the verified official APK URL using standard Android ACTION_VIEW intent.
     * Delegates download and verification to the browser/system installer, requiring user consent.
     */
    fun launchDownload(context: Context, downloadUrl: String): Boolean {
        return try {
            val uri = Uri.parse(downloadUrl)
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch APK download intent for URL: $downloadUrl", e)
            false
        }
    }

    private fun executeHttpRequest(urlStr: String): Pair<Int, String> {
        val url = URL(urlStr)
        val connection = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = HTTP_TIMEOUT_MILLIS
            readTimeout = HTTP_TIMEOUT_MILLIS
            requestMethod = "GET"
            setRequestProperty("Accept", "application/vnd.github.v3+json")
            setRequestProperty("User-Agent", "PandalQuest-Android/${BuildConfig.VERSION_NAME}")
        }

        val status = connection.responseCode
        val stream = if (status in 200..299) connection.inputStream else connection.errorStream
        val response = stream?.let {
            BufferedReader(InputStreamReader(it)).use { reader -> reader.readText() }
        } ?: ""

        connection.disconnect()
        return Pair(status, response)
    }

    private fun postResult(result: UpdateCheckResult, callback: (UpdateCheckResult) -> Unit) {
        if (context == null || resultDispatcher != null) {
            resultDispatcher?.invoke(Runnable { callback(result) }) ?: callback(result)
        } else {
            val handler = mainHandler
            if (handler != null) {
                handler.post { callback(result) }
            } else {
                callback(result)
            }
        }
    }
}
