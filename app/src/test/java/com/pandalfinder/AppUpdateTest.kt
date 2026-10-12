package com.pandalfinder

import com.pandalfinder.data.AppReleaseInfo
import com.pandalfinder.data.AppUpdateRepository
import com.pandalfinder.data.SemanticVersion
import com.pandalfinder.data.UpdateCheckResult
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.Executors

class AppUpdateTest {

    private val repo = AppUpdateRepository(
        context = null,
        apiEndpoint = AppUpdateRepository.DEFAULT_GITHUB_RELEASES_URL,
        httpFetcher = null,
        executor = Executors.newSingleThreadExecutor()
    )

    // ── 1. Semantic Version Parsing & Comparison ──

    @Test
    fun testSemanticVersionParsing() {
        val v1 = SemanticVersion.parse("v2.1.1")
        assertNotNull(v1)
        assertEquals(2, v1!!.major)
        assertEquals(1, v1.minor)
        assertEquals(1, v1.patch)

        val v2 = SemanticVersion.parse("PandalQuest v2.10.3")
        assertNotNull(v2)
        assertEquals(2, v2!!.major)
        assertEquals(10, v2.minor)
        assertEquals(3, v2.patch)

        val v3 = SemanticVersion.parse("3.0")
        assertNotNull(v3)
        assertEquals(3, v3!!.major)
        assertEquals(0, v3.minor)
        assertEquals(0, v3.patch)

        assertNull(SemanticVersion.parse(""))
        assertNull(SemanticVersion.parse("invalid_tag"))
    }

    @Test
    fun testSemanticVersionComparisonOrder() {
        val v2_1_0 = SemanticVersion(2, 1, 0)
        val v2_1_1 = SemanticVersion(2, 1, 1)
        val v2_9_0 = SemanticVersion(2, 9, 0)
        val v2_10_0 = SemanticVersion(2, 10, 0)
        val v3_0_0 = SemanticVersion(3, 0, 0)

        assertTrue("2.1.1 must be greater than 2.1.0", v2_1_1 > v2_1_0)
        assertTrue("2.1.0 must be less than 2.1.1", v2_1_0 < v2_1_1)
        assertEquals("Identical versions must compare equal", 0, v2_1_1.compareTo(SemanticVersion(2, 1, 1)))

        // Critical: Non-lexicographical check ("2.10.0" > "2.9.0")
        assertTrue("2.10.0 must be greater than 2.9.0 (non-lexicographical)", v2_10_0 > v2_9_0)
        assertTrue("3.0.0 must be greater than 2.10.0", v3_0_0 > v2_10_0)
    }

    // ── 2. Current Version Handling (v2.1.1, versionCode 5) ──

    @Test
    fun testInstalledVersionUpToDate() {
        val currentRelease = AppReleaseInfo(
            versionName = "2.1.1",
            versionCode = 5,
            releaseTitle = "v2.1.1",
            releaseNotes = "Current stable release",
            apkDownloadUrl = AppUpdateRepository.VERIFIED_V211_DOWNLOAD_URL,
            apkFileName = "PandalQuest-v2.1.1.apk",
            apkSize = 26708401L,
            publishedAt = "2026-10-11T08:42:56Z",
            isMandatory = false,
            minSupportedVersionCode = 1,
            minSupportedVersionName = null
        )

        // Installed v2.1.1 (versionCode 5) vs remote v2.1.1 (versionCode 5) -> No update
        val updateForCurrent = repo.isUpdateAvailable(
            installedVersionName = "2.1.1",
            installedVersionCode = 5,
            remoteRelease = currentRelease
        )
        assertFalse("Installed v2.1.1 should recognize itself as up-to-date", updateForCurrent)

        // Installed newer v2.1.2 vs remote v2.1.1 -> No update (never downgrade)
        val updateForNewer = repo.isUpdateAvailable(
            installedVersionName = "2.1.2",
            installedVersionCode = 6,
            remoteRelease = currentRelease
        )
        assertFalse("Newer installed build must not downgrade to v2.1.1", updateForNewer)
    }

    @Test
    fun testOlderInstalledVersionsDiscoverV211() {
        val v211Release = AppReleaseInfo(
            versionName = "2.1.1",
            versionCode = 5,
            releaseTitle = "v2.1.1",
            releaseNotes = "Fixed OnePlus installation compatibility and admin analytics",
            apkDownloadUrl = AppUpdateRepository.VERIFIED_V211_DOWNLOAD_URL,
            apkFileName = "PandalQuest-v2.1.1.apk",
            apkSize = 26708401L,
            publishedAt = "2026-10-11T08:42:56Z",
            isMandatory = false,
            minSupportedVersionCode = 1,
            minSupportedVersionName = null
        )

        // Older user on v2.1.0 (versionCode 4) discovers v2.1.1
        val updateForV210 = repo.isUpdateAvailable(
            installedVersionName = "2.1.0",
            installedVersionCode = 4,
            remoteRelease = v211Release
        )
        assertTrue("Older v2.1.0 installation must discover v2.1.1 update", updateForV210)
        assertEquals(
            "Must provide exact verified official download URL",
            "https://github.com/d-ghosh717/PandalQuest/releases/download/v2.1.1/PandalQuest-v2.1.1.apk",
            v211Release.apkDownloadUrl
        )

        // Older user on v2.0.0 (versionCode 2) discovers v2.1.1
        val updateForV200 = repo.isUpdateAvailable(
            installedVersionName = "2.0.0",
            installedVersionCode = 2,
            remoteRelease = v211Release
        )
        assertTrue("Older v2.0.0 installation must discover v2.1.1 update", updateForV200)
    }

    // ── 3. Draft & Prerelease Filtering ──

    @Test
    fun testDraftAndPrereleaseFiltering() {
        val draftJson = """
            {
              "tag_name": "v2.2.0",
              "name": "v2.2.0-draft",
              "draft": true,
              "prerelease": false,
              "assets": [
                {
                  "name": "PandalQuest-v2.2.0.apk",
                  "browser_download_url": "https://github.com/d-ghosh717/PandalQuest/releases/download/v2.2.0/PandalQuest-v2.2.0.apk"
                }
              ]
            }
        """.trimIndent()
        assertNull("Draft release must be filtered out", repo.parseSingleRelease(org.json.JSONObject(draftJson)))

        val prereleaseJson = """
            {
              "tag_name": "v2.2.0-beta1",
              "name": "v2.2.0 Beta 1",
              "draft": false,
              "prerelease": true,
              "assets": [
                {
                  "name": "PandalQuest-v2.2.0-beta1.apk",
                  "browser_download_url": "https://github.com/d-ghosh717/PandalQuest/releases/download/v2.2.0-beta1/PandalQuest-v2.2.0-beta1.apk"
                }
              ]
            }
        """.trimIndent()
        assertNull("Prerelease must be filtered out", repo.parseSingleRelease(org.json.JSONObject(prereleaseJson)))
    }

    // ── 4. Missing or Invalid APK Asset Detection ──

    @Test
    fun testMissingApkAssetDetection() {
        val noAssetsJson = """
            {
              "tag_name": "v2.2.0",
              "name": "v2.2.0",
              "draft": false,
              "prerelease": false,
              "assets": []
            }
        """.trimIndent()
        assertNull("Release without assets must be rejected", repo.parseSingleRelease(org.json.JSONObject(noAssetsJson)))

        val onlyZipAssetJson = """
            {
              "tag_name": "v2.2.0",
              "name": "v2.2.0",
              "draft": false,
              "prerelease": false,
              "assets": [
                {
                  "name": "source-code.zip",
                  "browser_download_url": "https://github.com/d-ghosh717/PandalQuest/releases/download/v2.2.0/source-code.zip"
                }
              ]
            }
        """.trimIndent()
        assertNull("Release without .apk asset must be rejected", repo.parseSingleRelease(org.json.JSONObject(onlyZipAssetJson)))
    }

    // ── 5. Download URL Security Validation ──

    @Test
    fun testDownloadUrlSecurityValidation() {
        // Valid official GitHub release download URLs
        assertTrue(repo.isValidDownloadUrl("https://github.com/d-ghosh717/PandalQuest/releases/download/v2.1.1/PandalQuest-v2.1.1.apk"))
        assertTrue(repo.isValidDownloadUrl("https://github.com/d-ghosh717/PandalQuest/releases/download/v2.2.0/PandalQuest-v2.2.0.apk"))
        assertTrue(repo.isValidDownloadUrl("https://objects.githubusercontent.com/github-production-release-asset-2e65be/123/456"))

        // Untrusted hosts and HTTP URLs must be rejected
        assertFalse("HTTP URL must be rejected", repo.isValidDownloadUrl("http://github.com/d-ghosh717/PandalQuest/releases/download/v2.1.1/app.apk"))
        assertFalse("Foreign repository must be rejected", repo.isValidDownloadUrl("https://github.com/attacker/malware/releases/download/v2.1.1/app.apk"))
        assertFalse("Third-party host must be rejected", repo.isValidDownloadUrl("https://malicious-site.com/PandalQuest.apk"))
        assertFalse("Empty URL must be rejected", repo.isValidDownloadUrl(""))
    }

    // ── 6. Metadata Parsing & Mandatory Update Support ──

    @Test
    fun testReleaseMetadataAndMandatoryFlag() {
        val bodyWithMetadata = """
            ## PandalQuest v2.2.0
            Critical security patch for Durga Puja live crowd telemetry.
            
            versionCode: 6
            minSupportedVersionCode: 5
            mandatory: true
        """.trimIndent()

        val metadata = repo.extractMetadataFromBody(bodyWithMetadata)
        assertEquals(6, metadata.versionCode)
        assertEquals(5, metadata.minSupportedVersionCode)
        assertTrue(metadata.isMandatory)

        val regularBody = "General improvements and festive map updates."
        val normalMeta = repo.extractMetadataFromBody(regularBody)
        assertNull(normalMeta.versionCode)
        assertNull(normalMeta.minSupportedVersionCode)
        assertFalse(normalMeta.isMandatory)
    }

    // ── 7. Offline, HTTP Error & Rate Limit Handling ──

    @Test
    fun testHttpErrorAndRateLimitHandling() {
        val directExecutor = java.util.concurrent.Executor { it.run() }
        var observedResult: UpdateCheckResult? = null

        // Test 403 Rate Limit
        val rateLimitedRepo = AppUpdateRepository(
            context = null,
            httpFetcher = { Pair(403, "API rate limit exceeded") },
            executor = directExecutor
        )
        rateLimitedRepo.checkForUpdates(isManualCheck = true) { observedResult = it }
        assertTrue("403 must report rate limit error", observedResult is UpdateCheckResult.Error)
        assertTrue((observedResult as UpdateCheckResult.Error).message.contains("rate limit", ignoreCase = true))

        // Test 500 Server Error
        val serverErrorRepo = AppUpdateRepository(
            context = null,
            httpFetcher = { Pair(500, "Internal Server Error") },
            executor = directExecutor
        )
        serverErrorRepo.checkForUpdates(isManualCheck = true) { observedResult = it }
        assertTrue("500 must report connection error", observedResult is UpdateCheckResult.Error)

        // Test Network Exception
        val exceptionRepo = AppUpdateRepository(
            context = null,
            httpFetcher = { throw java.net.UnknownHostException("Unable to resolve host") },
            executor = directExecutor
        )
        exceptionRepo.checkForUpdates(isManualCheck = true) { observedResult = it }
        assertTrue("Network exception must report error", observedResult is UpdateCheckResult.Error)
    }

    // ── 8. Live GitHub Response Parsing Contract ──

    @Test
    fun testParseGitHubReleasePayloadContract() {
        val payload = """
        [
          {
            "tag_name": "v2.1.1",
            "name": "v2.1.1",
            "draft": false,
            "prerelease": false,
            "published_at": "2026-10-11T08:42:56Z",
            "body": "## PandalQuest v2.1.1\n- Fixed APK installation issues on some devices.\n- Reduced app size.",
            "assets": [
              {
                "name": "PandalQuest-v2.1.1.apk",
                "size": 26708401,
                "browser_download_url": "https://github.com/d-ghosh717/PandalQuest/releases/download/v2.1.1/PandalQuest-v2.1.1.apk"
              }
            ]
          }
        ]
        """.trimIndent()

        val releases = repo.parseReleases(payload)
        assertEquals(1, releases.size)
        val release = releases[0]
        assertEquals("2.1.1", release.versionName)
        assertEquals(5, release.versionCode)
        assertEquals("https://github.com/d-ghosh717/PandalQuest/releases/download/v2.1.1/PandalQuest-v2.1.1.apk", release.apkDownloadUrl)
        assertEquals(26708401L, release.apkSize)
        assertFalse(release.isMandatory)
    }

    // ── 9. v2.1.2 Future Release Discovery & Comparison ──

    @Test
    fun testV212DiscoversFutureReleases() {
        val futureReleaseV213 = AppReleaseInfo(
            versionName = "2.1.3",
            versionCode = 7,
            releaseTitle = "v2.1.3",
            releaseNotes = "New features",
            apkDownloadUrl = "https://github.com/d-ghosh717/PandalQuest/releases/download/v2.1.3/PandalQuest-v2.1.3.apk",
            apkFileName = "PandalQuest-v2.1.3.apk",
            apkSize = 27000000L,
            publishedAt = "2026-10-15T00:00:00Z",
            isMandatory = false,
            minSupportedVersionCode = 1,
            minSupportedVersionName = null
        )

        // Installed v2.1.2 (versionCode 6) discovers v2.1.3 (versionCode 7)
        val updateAvailable = repo.isUpdateAvailable(
            installedVersionName = "2.1.2",
            installedVersionCode = 6,
            remoteRelease = futureReleaseV213
        )
        assertTrue("Installed v2.1.2 must discover newer v2.1.3 release", updateAvailable)

        // Installed v2.1.2 (versionCode 6) vs remote v2.1.2 (versionCode 6) -> No update
        val sameVersion = AppReleaseInfo(
            versionName = "2.1.2",
            versionCode = 6,
            releaseTitle = "v2.1.2",
            releaseNotes = "Current version",
            apkDownloadUrl = "https://github.com/d-ghosh717/PandalQuest/releases/download/v2.1.2/PandalQuest-v2.1.2.apk",
            apkFileName = "PandalQuest-v2.1.2.apk",
            apkSize = 27000000L,
            publishedAt = "2026-10-12T00:00:00Z",
            isMandatory = false,
            minSupportedVersionCode = 1,
            minSupportedVersionName = null
        )
        assertFalse("Installed v2.1.2 must not trigger update prompt for v2.1.2", repo.isUpdateAvailable("2.1.2", 6, sameVersion))
    }

    @Test
    fun testMultipleReleasesSelectsHighestStableVersion() {
        val multiReleasePayload = """
        [
          {
            "tag_name": "v2.1.0",
            "name": "v2.1.0",
            "draft": false,
            "prerelease": false,
            "assets": [
              {
                "name": "PandalQuest-v2.1.0.apk",
                "browser_download_url": "https://github.com/d-ghosh717/PandalQuest/releases/download/v2.1.0/PandalQuest-v2.1.0.apk"
              }
            ]
          },
          {
            "tag_name": "v2.1.2",
            "name": "v2.1.2",
            "draft": false,
            "prerelease": false,
            "assets": [
              {
                "name": "PandalQuest-v2.1.2.apk",
                "browser_download_url": "https://github.com/d-ghosh717/PandalQuest/releases/download/v2.1.2/PandalQuest-v2.1.2.apk"
              }
            ]
          },
          {
            "tag_name": "v2.1.1",
            "name": "v2.1.1",
            "draft": false,
            "prerelease": false,
            "assets": [
              {
                "name": "PandalQuest-v2.1.1.apk",
                "browser_download_url": "https://github.com/d-ghosh717/PandalQuest/releases/download/v2.1.1/PandalQuest-v2.1.1.apk"
              }
            ]
          }
        ]
        """.trimIndent()

        val parsed = repo.parseReleases(multiReleasePayload)
        assertEquals(3, parsed.size)
        // First element must be the newest/highest version (v2.1.2)
        assertEquals("2.1.2", parsed.first().versionName)
        assertEquals(6, parsed.first().versionCode)
    }

    @Test
    fun testTagParsingVariations() {
        val v1 = SemanticVersion.parse("PandalQuest-v2.1.2")
        assertNotNull(v1)
        assertEquals(SemanticVersion(2, 1, 2), v1)

        val v2 = SemanticVersion.parse("release/v2.1.2")
        assertNotNull(v2)
        assertEquals(SemanticVersion(2, 1, 2), v2)

        val v3 = SemanticVersion.parse("v2.1.2")
        assertNotNull(v3)
        assertEquals(SemanticVersion(2, 1, 2), v3)
    }
}
