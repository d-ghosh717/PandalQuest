package com.pandalfinder.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.pandalfinder.data.supabase.SupabaseStorageService
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.UUID

data class PandalPhoto(
    val id: String = "",
    val pandalId: String = "",
    val storagePath: String = "",
    val downloadUrl: String = "",
    val uploadedBy: String = "",
    val createdAt: Long = 0L,
    val status: String = "active"
)

/**
 * Pandal photo repository combining Supabase Storage (binary storage)
 * with Firebase Authentication and Cloud Firestore (metadata & real-time listeners).
 *
 * Security Model:
 * - Each photo has ONE owner: uploadedBy = {firebaseUid}.
 * - Owner (User A): View, Upload, Edit, Replace, Delete.
 * - Other Users (User B): READ-ONLY (Cannot edit, replace, delete, or modify metadata).
 * - Admin: View, Edit, Replace, Delete, Moderate any photo.
 */
class PhotoRepository(
    private val supabase: SupabaseStorageService = SupabaseStorageService()
) {
    private val firestore get() = FirebaseFirestore.getInstance()
    private val auth get() = FirebaseAuth.getInstance()

    /**
     * Resolves current Firebase user ID, signing in anonymously if needed.
     * Invokes callback with authentic Firebase UID, or null if unauthenticated.
     * Never generates synthetic or fake device IDs.
     */
    fun currentUserId(onReady: (String?) -> Unit) {
        val current = auth.currentUser
        if (current != null) {
            onReady(current.uid)
        } else {
            auth.signInAnonymously()
                .addOnSuccessListener { result ->
                    onReady(result.user?.uid)
                }
                .addOnFailureListener { err ->
                    Log.e(TAG, "Anonymous sign-in failed during photo op: ${err.message}", err)
                    onReady(null)
                }
        }
    }

    /**
     * Gets a fresh Firebase ID token (JWT) to authorize third-party requests to Supabase.
     */
    private fun getFirebaseIdToken(forceRefresh: Boolean = false, onToken: (String?) -> Unit) {
        val user = auth.currentUser
        if (user == null) {
            onToken(null)
            return
        }
        user.getIdToken(forceRefresh)
            .addOnSuccessListener { result ->
                onToken(result.token)
            }
            .addOnFailureListener { err ->
                Log.w(TAG, "Failed to get fresh Firebase ID token: ${err.message}", err)
                onToken(null)
            }
    }

    /**
     * Listens in real-time to active community photos for a given pandal.
     * Ordered newest first.
     */
    fun listenToPhotos(pandalId: String, onUpdate: (List<PandalPhoto>) -> Unit): ListenerRegistration? {
        val cleanId = pandalId.removePrefix("pandal:")
        return runCatching {
            firestore.collection(COLLECTION)
                .whereEqualTo("pandalId", cleanId)
                .whereEqualTo("status", "active")
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.e(TAG, "Photos listener failed for pandal $cleanId: ${error.message}", error)
                        onUpdate(emptyList())
                        return@addSnapshotListener
                    }
                    if (snapshot != null) {
                        val photos = snapshot.documents.mapNotNull { doc ->
                            val id = doc.getString("id") ?: doc.id
                            val pId = doc.getString("pandalId") ?: ""
                            val path = doc.getString("storagePath") ?: ""
                            val url = doc.getString("downloadUrl") ?: ""
                            val uploader = doc.getString("uploadedBy") ?: ""
                            val time = doc.getLong("createdAt") ?: 0L
                            val status = doc.getString("status") ?: "active"
                            if (url.isNotBlank() && status == "active") {
                                PandalPhoto(id, pId, path, url, uploader, time, status)
                            } else null
                        }.sortedByDescending { it.createdAt }
                        onUpdate(photos)
                    }
                }
        }.getOrNull()
    }

    /**
     * Compresses image, uploads bytes to Supabase Storage, and writes metadata to Firestore.
     *
     * Storage path: pandal-photos/{firebaseUid}/{pandalId}/{photoId}.jpg
     */
    fun uploadPhoto(
        context: Context,
        pandalId: String,
        imageUri: Uri,
        onProgress: (Int) -> Unit,
        onComplete: (Result<PandalPhoto>) -> Unit
    ) {
        val cleanId = pandalId.removePrefix("pandal:")
        currentUserId { uid ->
            if (uid.isNullOrBlank()) {
                Log.e(TAG_UPLOAD, "Upload aborted: Active Firebase authentication is required.")
                onComplete(Result.failure(IllegalStateException("Active Firebase authentication is required to upload photos.")))
                return@currentUserId
            }

            val compressedBytes = compressImage(context, imageUri)
            if (compressedBytes == null) {
                onComplete(Result.failure(IllegalArgumentException("Failed to decode or compress selected image")))
                return@currentUserId
            }

            val photoId = UUID.randomUUID().toString()
            val storagePath = "pandal-photos/$uid/$cleanId/$photoId.jpg"

            Log.d(TAG_UPLOAD, "Initiating photo upload: pandal=$cleanId, uid=${uid.take(6)}..., path=$storagePath, size=${compressedBytes.size} bytes")

            getFirebaseIdToken(forceRefresh = true) { idToken ->
                supabase.uploadPhoto(
                    firebaseUid = uid,
                    pandalId = cleanId,
                    photoId = photoId,
                    imageBytes = compressedBytes,
                    idToken = idToken,
                    onProgress = onProgress,
                    onComplete = { uploadResult ->
                        uploadResult.onSuccess { downloadUrl ->
                            val timestamp = System.currentTimeMillis()
                            val photo = PandalPhoto(
                                id = photoId,
                                pandalId = cleanId,
                                storagePath = storagePath,
                                downloadUrl = downloadUrl,
                                uploadedBy = uid,
                                createdAt = timestamp,
                                status = "active"
                            )

                            val docData = hashMapOf<String, Any>(
                                "id" to photo.id,
                                "pandalId" to photo.pandalId,
                                "storagePath" to photo.storagePath,
                                "downloadUrl" to photo.downloadUrl,
                                "uploadedBy" to photo.uploadedBy,
                                "createdAt" to photo.createdAt,
                                "status" to photo.status
                            )

                            val docPath = "$COLLECTION/$photoId"

                            try {
                                firestore.collection(COLLECTION)
                                    .document(photoId)
                                    .set(docData)
                                    .addOnSuccessListener {
                                        Log.i(TAG_UPLOAD, "Firestore metadata written successfully for doc: $docPath")
                                        onProgress(100)
                                        onComplete(Result.success(photo))
                                    }
                                    .addOnFailureListener { firestoreErr ->
                                        Log.e(TAG_UPLOAD, "Firestore metadata write failed for doc $docPath: ${firestoreErr.message}", firestoreErr)

                                        // Clean up orphaned object on Supabase
                                        supabase.deletePhoto(storagePath, idToken) { cleanupErr ->
                                            if (cleanupErr != null) {
                                                Log.w(TAG_UPLOAD, "Orphaned Supabase cleanup warning: ${cleanupErr.message} at $storagePath")
                                            }
                                        }
                                        onComplete(Result.failure(firestoreErr))
                                    }
                            } catch (writeEx: Exception) {
                                Log.e(TAG_UPLOAD, "Synchronous exception initiating Firestore write: ${writeEx.message}", writeEx)
                                onComplete(Result.failure(writeEx))
                            }
                        }.onFailure { uploadErr ->
                            Log.e(TAG_UPLOAD, "Supabase Storage upload failed: ${uploadErr.message}", uploadErr)
                            onComplete(Result.failure(uploadErr))
                        }
                    }
                )
            }
        }
    }

    /**
     * Uploads multiple photos sequentially in a batch.
     * Reports item-level progress and tracks partial success/failures.
     */
    fun uploadPhotosBatch(
        context: Context,
        pandalId: String,
        imageUris: List<Uri>,
        onItemStart: (index: Int, uri: Uri) -> Unit,
        onItemProgress: (index: Int, progress: Int) -> Unit,
        onItemComplete: (index: Int, uri: Uri, result: Result<PandalPhoto>) -> Unit,
        onBatchComplete: (successCount: Int, failedCount: Int, results: List<Pair<Uri, Result<PandalPhoto>>>) -> Unit
    ) {
        if (imageUris.isEmpty()) {
            onBatchComplete(0, 0, emptyList())
            return
        }

        val results = mutableListOf<Pair<Uri, Result<PandalPhoto>>>()
        var successCount = 0
        var failedCount = 0

        fun uploadNext(index: Int) {
            if (index >= imageUris.size) {
                onBatchComplete(successCount, failedCount, results)
                return
            }

            val uri = imageUris[index]
            onItemStart(index, uri)

            uploadPhoto(
                context = context,
                pandalId = pandalId,
                imageUri = uri,
                onProgress = { progress ->
                    onItemProgress(index, progress)
                },
                onComplete = { result ->
                    results.add(uri to result)
                    if (result.isSuccess) {
                        successCount++
                    } else {
                        failedCount++
                    }
                    onItemComplete(index, uri, result)
                    uploadNext(index + 1)
                }
            )
        }

        uploadNext(0)
    }

    /**
     * Replaces an existing uploaded photo with a new image.
     * Only the owner (or Admin) can perform replacement.
     */
    fun replacePhoto(
        context: Context,
        oldPhoto: PandalPhoto,
        newImageUri: Uri,
        onProgress: (Int) -> Unit,
        onComplete: (Result<PandalPhoto>) -> Unit
    ) {
        val cleanId = oldPhoto.pandalId.removePrefix("pandal:")
        val compressedBytes = compressImage(context, newImageUri)
        if (compressedBytes == null) {
            onComplete(Result.failure(IllegalArgumentException("Failed to decode or compress selected image")))
            return
        }

        currentUserId { uid ->
            if (uid.isNullOrBlank()) {
                onComplete(Result.failure(SecurityException("Active Firebase authentication is required to replace photos.")))
                return@currentUserId
            }

            // Enforce ownership check before attempting replacement
            if (oldPhoto.uploadedBy.isNotBlank() && oldPhoto.uploadedBy != uid) {
                Log.w(TAG_UPLOAD, "Replace rejected: current user $uid is not the owner of photo ${oldPhoto.id}")
                onComplete(Result.failure(SecurityException("You do not have permission to replace this photo")))
                return@currentUserId
            }

            val newPhotoId = UUID.randomUUID().toString()
            val newStoragePath = "pandal-photos/$uid/$cleanId/$newPhotoId.jpg"

            Log.d(TAG_UPLOAD, "Initiating photo replace for old=${oldPhoto.id}, newPath=$newStoragePath")

            getFirebaseIdToken(forceRefresh = true) { idToken ->
                supabase.uploadPhoto(
                    firebaseUid = uid,
                    pandalId = cleanId,
                    photoId = newPhotoId,
                    imageBytes = compressedBytes,
                    idToken = idToken,
                    onProgress = onProgress,
                    onComplete = { uploadResult ->
                        uploadResult.onSuccess { newDownloadUrl ->
                            val updatedPhoto = oldPhoto.copy(
                                storagePath = newStoragePath,
                                downloadUrl = newDownloadUrl,
                                createdAt = System.currentTimeMillis()
                            )

                            // Ownership (uploadedBy) and pandalId remain unchanged
                            firestore.collection(COLLECTION).document(oldPhoto.id)
                                .update(
                                    mapOf(
                                        "storagePath" to newStoragePath,
                                        "downloadUrl" to newDownloadUrl,
                                        "createdAt" to updatedPhoto.createdAt
                                    )
                                )
                                .addOnSuccessListener {
                                    onProgress(100)
                                    // Delete old Supabase object after confirmed Firestore metadata update
                                    if (oldPhoto.storagePath.isNotBlank() && oldPhoto.storagePath != newStoragePath) {
                                        supabase.deletePhoto(oldPhoto.storagePath, idToken) { deleteErr ->
                                            if (deleteErr != null) {
                                                Log.w(TAG_UPLOAD, "Old photo cleanup warning: ${deleteErr.message}")
                                            }
                                        }
                                    }
                                    onComplete(Result.success(updatedPhoto))
                                }
                                .addOnFailureListener { firestoreErr ->
                                    Log.e(TAG_UPLOAD, "Failed to update Firestore metadata during replace: ${firestoreErr.message}", firestoreErr)
                                    // Clean up newly uploaded replacement object
                                    supabase.deletePhoto(newStoragePath, idToken) { /* best effort cleanup */ }
                                    onComplete(Result.failure(firestoreErr))
                                }
                        }.onFailure { uploadErr ->
                            Log.e(TAG_UPLOAD, "Replacement upload to Supabase failed: ${uploadErr.message}", uploadErr)
                            onComplete(Result.failure(uploadErr))
                        }
                    }
                )
            }
        }
    }

    /**
     * Deletes photo from Supabase Storage first, and only then deletes the Firestore document.
     * Prevents orphan Firestore records if storage delete fails.
     */
    fun deletePhoto(photo: PandalPhoto, onComplete: (Exception?) -> Unit) {
        currentUserId { currentUid ->
            if (currentUid.isNullOrBlank()) {
                onComplete(SecurityException("Active Firebase authentication is required to delete photos."))
                return@currentUserId
            }

            if (photo.uploadedBy.isNotBlank() && photo.uploadedBy != currentUid) {
                Log.w(TAG_DELETE, "Delete rejected: current user $currentUid is not the owner of photo ${photo.id}")
                onComplete(SecurityException("You do not have permission to delete this photo"))
                return@currentUserId
            }

            Log.d(TAG_DELETE, "Deleting photo: id=${photo.id}, path=${photo.storagePath}")

            getFirebaseIdToken(forceRefresh = true) { idToken ->
                if (photo.storagePath.isNotBlank()) {
                    supabase.deletePhoto(photo.storagePath, idToken) { storageErr ->
                        if (storageErr == null) {
                            // Only delete Firestore metadata after confirmed storage deletion
                            firestore.collection(COLLECTION).document(photo.id).delete()
                                .addOnSuccessListener {
                                    Log.i(TAG_DELETE, "Photo document ${photo.id} deleted from Firestore")
                                    onComplete(null)
                                }
                                .addOnFailureListener { firestoreErr ->
                                    Log.e(TAG_DELETE, "Firestore delete document failed: ${firestoreErr.message}", firestoreErr)
                                    onComplete(firestoreErr)
                                }
                        } else {
                            Log.e(TAG_DELETE, "Supabase Storage delete failed: ${storageErr.message}", storageErr)
                            onComplete(storageErr)
                        }
                    }
                } else {
                    firestore.collection(COLLECTION).document(photo.id).delete()
                        .addOnSuccessListener { onComplete(null) }
                        .addOnFailureListener { err -> onComplete(err) }
                }
            }
        }
    }

    private fun compressImage(context: Context, uri: Uri): ByteArray? {
        return runCatching {
            val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
            val original = BitmapFactory.decodeStream(inputStream) ?: return null
            inputStream?.close()

            // Handle orientation
            val exifStream = context.contentResolver.openInputStream(uri)
            val orientation = exifStream?.let {
                val exif = ExifInterface(it)
                val ori = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
                it.close()
                ori
            } ?: ExifInterface.ORIENTATION_NORMAL

            val matrix = Matrix()
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            }

            // Downscale to max 1800px width/height while preserving aspect ratio
            val maxDimension = 1800
            val width = original.width
            val height = original.height
            val scale = if (width > maxDimension || height > maxDimension) {
                val maxSide = Math.max(width, height)
                maxDimension.toFloat() / maxSide.toFloat()
            } else 1.0f

            matrix.postScale(scale, scale)

            val transformed = Bitmap.createBitmap(original, 0, 0, width, height, matrix, true)
            val outputStream = ByteArrayOutputStream()
            transformed.compress(Bitmap.CompressFormat.JPEG, 84, outputStream)
            val bytes = outputStream.toByteArray()
            outputStream.close()
            bytes
        }.getOrNull()
    }

    companion object {
        const val TAG = "PandalFinder-Photo"
        const val TAG_UPLOAD = "PandalFinder-PhotoUpload"
        const val TAG_DELETE = "PandalFinder-PhotoDelete"
        const val COLLECTION = "pandalPhotos"
    }
}
