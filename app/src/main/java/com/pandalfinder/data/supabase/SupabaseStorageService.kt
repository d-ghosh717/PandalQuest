package com.pandalfinder.data.supabase

import android.util.Base64
import android.util.Log
import com.pandalfinder.BuildConfig
import org.json.JSONObject
import java.io.BufferedOutputStream
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * Dedicated Supabase Storage client service for PandalFinder.
 * Handles binary image upload, deletion, and public URL construction
 * with Firebase Third-Party Authentication token integration and diagnostics.
 */
class SupabaseStorageService(
    private val supabaseUrl: String = BuildConfig.SUPABASE_URL,
    private val supabasePublishableKey: String = BuildConfig.SUPABASE_PUBLISHABLE_KEY,
    val bucketName: String = BUCKET_NAME
) {
    private val executor = Executors.newFixedThreadPool(2)

    val isConfigured: Boolean
        get() = supabaseUrl.isNotBlank() && 
                supabasePublishableKey.isNotBlank() && 
                !supabaseUrl.contains("YOUR_SUPABASE_URL", ignoreCase = true)

    /**
     * Constructs the public URL for an image stored in the pandal-photos bucket.
     */
    fun getPublicUrl(firebaseUid: String, pandalId: String, photoId: String): String {
        val cleanUrl = supabaseUrl.trimEnd('/')
        return "$cleanUrl/storage/v1/object/public/$bucketName/$firebaseUid/$pandalId/$photoId.jpg"
    }

    /**
     * Constructs public URL from a relative or full storagePath.
     */
    fun getPublicUrlFromPath(storagePath: String): String {
        val cleanUrl = supabaseUrl.trimEnd('/')
        val relPath = storagePath.removePrefix("$bucketName/").removePrefix("/")
        return "$cleanUrl/storage/v1/object/public/$bucketName/$relPath"
    }

    /**
     * Uploads JPEG byte array to Supabase Storage at path:
     * pandal-photos/{firebaseUid}/{pandalId}/{photoId}.jpg
     *
     * @param firebaseUid Firebase Anonymous/Authenticated User ID
     * @param pandalId Clean pandal ID
     * @param photoId Unique UUID for this photo
     * @param imageBytes Compressed JPEG byte array (<= 5MB)
     * @param idToken Fresh Firebase ID token (JWT) for third-party auth
     * @param onProgress Callback receiving percentage 0..100
     * @param onComplete Callback receiving Result with public download URL
     */
    fun uploadPhoto(
        firebaseUid: String,
        pandalId: String,
        photoId: String,
        imageBytes: ByteArray,
        idToken: String?,
        onProgress: (Int) -> Unit,
        onComplete: (Result<String>) -> Unit
    ) {
        if (!isConfigured) {
            val err = IllegalStateException("Supabase configuration missing (SUPABASE_URL or SUPABASE_PUBLISHABLE_KEY)")
            Log.e(TAG, "Upload aborted: ${err.message}")
            onComplete(Result.failure(err))
            return
        }

        val objectPath = "$firebaseUid/$pandalId/$photoId.jpg"
        // Safe diagnostics logging (never logs full token or signature)
        logSafeJwtDiagnostics(idToken, firebaseUid, objectPath)

        executor.execute {
            try {
                val cleanUrl = supabaseUrl.trimEnd('/')
                val endpointUrl = "$cleanUrl/storage/v1/object/$bucketName/$objectPath"
                val publicUrl = "$cleanUrl/storage/v1/object/public/$bucketName/$objectPath"

                Log.d(TAG, "Starting Supabase upload: uid=${firebaseUid.take(6)}..., pandal=$pandalId, photoId=$photoId, bytes=${imageBytes.size}")

                val url = URL(endpointUrl)
                val connection = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    doOutput = true
                    connectTimeout = 20_000
                    readTimeout = 40_000
                    setFixedLengthStreamingMode(imageBytes.size)

                    setRequestProperty("apikey", supabasePublishableKey)
                    // Pass the Firebase ID token in Authorization Bearer
                    val bearerToken = if (!idToken.isNullOrBlank()) idToken else supabasePublishableKey
                    setRequestProperty("Authorization", "Bearer $bearerToken")
                    setRequestProperty("Content-Type", "image/jpeg")
                    setRequestProperty("x-upsert", "true")
                }

                val outputStream = BufferedOutputStream(connection.outputStream)
                val totalBytes = imageBytes.size
                var bytesWritten = 0
                val chunkSize = 16 * 1024

                while (bytesWritten < totalBytes) {
                    val nextChunk = Math.min(chunkSize, totalBytes - bytesWritten)
                    outputStream.write(imageBytes, bytesWritten, nextChunk)
                    bytesWritten += nextChunk
                    val progress = ((bytesWritten.toDouble() / totalBytes) * 100).toInt()
                    onProgress(progress.coerceIn(0, 99))
                }
                outputStream.flush()
                outputStream.close()

                val responseCode = connection.responseCode
                if (responseCode in 200..299) {
                    Log.i(TAG, "Supabase upload succeeded [HTTP $responseCode]: $objectPath")
                    onProgress(100)
                    onComplete(Result.success(publicUrl))
                } else {
                    val errorStream = connection.errorStream ?: connection.inputStream
                    val errorBody = errorStream?.bufferedReader()?.use(BufferedReader::readText) ?: ""
                    Log.e(TAG, "Supabase upload failed [HTTP $responseCode]: $errorBody for path $objectPath")
                    val errorMsg = parseErrorMessage(responseCode, errorBody)
                    onComplete(Result.failure(SupabaseStorageException(responseCode, errorMsg)))
                }
                connection.disconnect()
            } catch (e: Exception) {
                Log.e(TAG, "Supabase upload network exception: ${e.message}", e)
                onComplete(Result.failure(e))
            }
        }
    }

    /**
     * Deletes an image from Supabase Storage.
     *
     * @param storagePath Can be full path "pandal-photos/uid/pandal/photo.jpg" or "uid/pandal/photo.jpg"
     * @param idToken Fresh Firebase ID token (JWT) for ownership verification
     * @param onComplete Callback with optional Exception (null on success)
     */
    fun deletePhoto(
        storagePath: String,
        idToken: String?,
        onComplete: (Exception?) -> Unit
    ) {
        if (!isConfigured) {
            onComplete(IllegalStateException("Supabase configuration missing"))
            return
        }

        executor.execute {
            try {
                val cleanUrl = supabaseUrl.trimEnd('/')
                val relPath = storagePath.removePrefix("$bucketName/").removePrefix("/")
                val endpointUrl = "$cleanUrl/storage/v1/object/$bucketName/$relPath"

                Log.d(TAG, "Starting Supabase delete: path=$relPath")

                val url = URL(endpointUrl)
                val connection = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "DELETE"
                    connectTimeout = 15_000
                    readTimeout = 20_000

                    setRequestProperty("apikey", supabasePublishableKey)
                    val bearerToken = if (!idToken.isNullOrBlank()) idToken else supabasePublishableKey
                    setRequestProperty("Authorization", "Bearer $bearerToken")
                }

                val responseCode = connection.responseCode
                // 200 OK, 204 No Content, or 404 Not Found (already deleted) are considered successful deletions
                if (responseCode in 200..299 || responseCode == 404) {
                    Log.i(TAG, "Supabase delete succeeded [HTTP $responseCode]: $relPath")
                    onComplete(null)
                } else {
                    val errorStream = connection.errorStream ?: connection.inputStream
                    val errorBody = errorStream?.bufferedReader()?.use(BufferedReader::readText) ?: ""
                    Log.e(TAG, "Supabase delete failed [HTTP $responseCode]: $errorBody for path $relPath")
                    val errorMsg = parseErrorMessage(responseCode, errorBody)
                    onComplete(SupabaseStorageException(responseCode, errorMsg))
                }
                connection.disconnect()
            } catch (e: Exception) {
                Log.e(TAG, "Supabase delete network exception: ${e.message}", e)
                onComplete(e)
            }
        }
    }

    /**
     * Safely logs token claims and headers without exposing the signature or full secret JWT.
     */
    private fun logSafeJwtDiagnostics(idToken: String?, firebaseUid: String, objectPath: String) {
        if (idToken.isNullOrBlank()) {
            Log.w(TAG, "=== FIREBASE JWT DIAGNOSTICS ===")
            Log.w(TAG, "Firebase user = ${firebaseUid.isNotBlank()}")
            Log.w(TAG, "Firebase ID token is missing or null!")
            Log.w(TAG, "================================")
            return
        }

        try {
            val parts = idToken.split(".")
            if (parts.size >= 2) {
                val headerBytes = Base64.decode(parts[0], Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
                val payloadBytes = Base64.decode(parts[1], Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)

                val headerJson = JSONObject(String(headerBytes))
                val payloadJson = JSONObject(String(payloadBytes))

                val alg = headerJson.optString("alg", "unknown")
                val kid = headerJson.optString("kid", "missing")
                val iss = payloadJson.optString("iss", "unknown")
                val aud = payloadJson.optString("aud", "unknown")
                val sub = payloadJson.optString("sub", "unknown")
                val role = if (payloadJson.has("role")) payloadJson.optString("role") else "none (missing/default)"
                val provider = payloadJson.optJSONObject("firebase")?.optString("sign_in_provider", "anonymous") ?: "anonymous"

                val subMatches = firebaseUid == sub
                val folderMatches = objectPath.startsWith("$sub/")

                Log.i(TAG, "=== FIREBASE JWT DIAGNOSTICS ===")
                Log.i(TAG, "Firebase UID: ${firebaseUid.take(6)}...")
                Log.i(TAG, "JWT sub: ${sub.take(6)}...")
                Log.i(TAG, "JWT alg: $alg")
                Log.i(TAG, "JWT kid: ${if (kid != "missing") "present (${kid.take(8)}...)" else "missing"}")
                Log.i(TAG, "JWT issuer: $iss")
                Log.i(TAG, "JWT audience: $aud")
                Log.i(TAG, "JWT role: $role")
                Log.i(TAG, "JWT provider: $provider")
                Log.i(TAG, "Storage object: $objectPath")
                Log.i(TAG, "Sub match (uid == sub): $subMatches")
                Log.i(TAG, "Folder match (starts with sub/): $folderMatches")
                Log.i(TAG, "================================")

                if (!subMatches) {
                    Log.e(TAG, "CRITICAL MISMATCH: Firebase UID ($firebaseUid) does NOT match JWT sub ($sub)!")
                }
                if (!folderMatches) {
                    Log.e(TAG, "CRITICAL MISMATCH: Object path ($objectPath) does NOT start with JWT sub ($sub/)!")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to decode JWT diagnostics: ${e.message}")
        }
    }

    private fun parseErrorMessage(code: Int, body: String): String {
        return try {
            val trimmed = body.trim()
            if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
                val json = JSONObject(trimmed)
                val msg = json.optString("message", "")
                val error = json.optString("error", "")
                val status = json.optString("statusCode", "")
                val codeStr = json.optString("code", "")
                if (status == "403" || error.equals("Unauthorized", ignoreCase = true) || codeStr == "AccessDenied" || msg.contains("Access denied", ignoreCase = true)) {
                    return "Storage authorization failed (HTTP 403 Access Denied): verified admin policy or owner identity required."
                }
                if (msg.isNotBlank()) return msg
                if (error.isNotBlank()) return error
            }
            when {
                code == 401 || code == 403 -> "Storage authorization failed. Please check permissions."
                code == 413 -> "File exceeds maximum 5MB size limit."
                body.contains("Bucket not found", ignoreCase = true) -> "Storage bucket '$bucketName' not found."
                body.isNotBlank() -> body
                else -> "HTTP $code storage error"
            }
        } catch (_: Exception) {
            if (body.isNotBlank()) body else "HTTP $code storage error"
        }
    }

    companion object {
        const val TAG = "PandalFinder-Supabase"
        const val BUCKET_NAME = "pandal-photos"
    }
}

class SupabaseStorageException(val statusCode: Int, message: String) : Exception(message)
