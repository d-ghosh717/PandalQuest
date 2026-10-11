package com.pandalfinder.data

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.pandalfinder.data.supabase.SupabaseStorageService

data class AdminCrowdReport(
    val id: String,
    val pandalId: String,
    val level: Int,
    val userId: String,
    val deviceId: String,
    val timestamp: Long,
    val status: String
)

data class AdminWeatherReport(
    val id: String,
    val pandalId: String,
    val condition: String,
    val userId: String,
    val timestamp: Long
)

/**
 * Represents partial failure state during photo deletion, explicitly distinguishing
 * whether Firestore metadata or Supabase Storage object failed.
 */
class PhotoPartialDeletionException(
    message: String,
    val firestoreSuccess: Boolean,
    val storageSuccess: Boolean,
    cause: Throwable? = null
) : Exception(message, cause)

/**
 * Repository for Admin Authentication, Authorization, and Content Moderation.
 * 
 * Authorization is strictly verified on the server-side via Firestore `/admins/{uid}`
 * and Supabase RLS. No hardcoded emails or client flags are trusted.
 */
class AdminRepository(
    private val supabase: SupabaseStorageService = SupabaseStorageService()
) {
    private val auth: FirebaseAuth get() = FirebaseAuth.getInstance()
    private val firestore: FirebaseFirestore get() = FirebaseFirestore.getInstance()

    var isAdminLoggedIn: Boolean = false
        private set

    var currentAdminUid: String? = null
        private set

    var currentAdminEmail: String? = null
        private set

    /**
     * Checks whether a specific Firebase UID is an authorized and enabled admin in Firestore.
     */
    fun checkIfUserIsAdmin(uid: String, onResult: (Boolean) -> Unit) {
        if (uid.isBlank()) {
            onResult(false)
            return
        }

        firestore.collection(ADMINS_COLLECTION)
            .document(uid)
            .get()
            .addOnSuccessListener { doc ->
                val isRoleAdmin = doc.getString("role") == "admin"
                val isEnabled = doc.getBoolean("enabled") == true
                val allowed = doc.exists() && isRoleAdmin && isEnabled
                onResult(allowed)
            }
            .addOnFailureListener { error ->
                Log.w(TAG, "Admin verification check failed: ${error.message}")
                onResult(false)
            }
    }

    /**
     * Authenticates an admin using Firebase Email and Password.
     * Verifies the authenticated user against the Firestore `/admins/{uid}` allowlist.
     * If unauthorized, immediately signs out and reverts to Anonymous Authentication.
     */
    fun signInAdmin(
        email: String,
        password: String,
        authRepo: AuthRepository,
        onResult: (Result<String>) -> Unit
    ) {
        val cleanEmail = email.trim()
        if (cleanEmail.isBlank() || password.isBlank()) {
            onResult(Result.failure(IllegalArgumentException("Email and password are required.")))
            return
        }

        Log.d(TAG, "Attempting admin sign-in...")
        auth.signInWithEmailAndPassword(cleanEmail, password)
            .addOnSuccessListener { authResult ->
                val user = authResult.user
                val uid = user?.uid
                if (uid == null) {
                    onResult(Result.failure(SecurityException("Admin access denied.")))
                    return@addOnSuccessListener
                }

                // Verify against server-side Firestore admin allowlist
                checkIfUserIsAdmin(uid) { isAuthorized ->
                    if (isAuthorized) {
                        isAdminLoggedIn = true
                        currentAdminUid = uid
                        currentAdminEmail = user.email
                        Log.i(TAG, "Admin login authorized for UID: ${uid.take(6)}...")
                        onResult(Result.success(uid))
                    } else {
                        Log.w(TAG, "User $uid authenticated but is NOT an authorized admin. Revoking session.")
                        auth.signOut()
                        isAdminLoggedIn = false
                        currentAdminUid = null
                        currentAdminEmail = null
                        // Restore anonymous session so normal app functions continue seamlessly
                        authRepo.initAndSignInAnonymously()
                        onResult(Result.failure(SecurityException("Admin access denied.")))
                    }
                }
            }
            .addOnFailureListener { error ->
                Log.w(TAG, "Admin sign-in failure: ${error.message}")
                // Generic access denied to prevent email account probing
                onResult(Result.failure(SecurityException("Admin access denied.")))
            }
    }

    /**
     * Signs out of the Admin session and seamlessly restores Anonymous Authentication.
     */
    fun signOutAdmin(authRepo: AuthRepository, onComplete: () -> Unit) {
        Log.i(TAG, "Signing out of admin session...")
        auth.signOut()
        isAdminLoggedIn = false
        currentAdminUid = null
        currentAdminEmail = null

        // Silently re-authenticate anonymously for the normal user experience
        authRepo.initAndSignInAnonymously {
            onComplete()
        }
    }

    /**
     * Fetches all community photos across all pandals for admin moderation.
     */
    fun fetchAdminPhotos(onResult: (List<PandalPhoto>) -> Unit) {
        firestore.collection(PhotoRepository.COLLECTION)
            .get()
            .addOnSuccessListener { snapshot ->
                val photos = snapshot.documents.mapNotNull { doc ->
                    val id = doc.getString("id") ?: doc.id
                    val pId = doc.getString("pandalId") ?: ""
                    val path = doc.getString("storagePath") ?: ""
                    val url = doc.getString("downloadUrl") ?: ""
                    val uploader = doc.getString("uploadedBy") ?: ""
                    val time = doc.getLong("createdAt") ?: 0L
                    val status = doc.getString("status") ?: "active"
                    if (url.isNotBlank()) {
                        PandalPhoto(id, pId, path, url, uploader, time, status)
                    } else null
                }.sortedByDescending { it.createdAt }
                onResult(photos)
            }
            .addOnFailureListener { error ->
                Log.e(TAG, "Admin fetch photos failed: ${error.message}", error)
                onResult(emptyList())
            }
    }

    /**
     * Deletes a community photo as Admin.
     * 
     * Authorization is verified server-side:
     * - Firestore delete is authorized by `firestore.rules isAdmin()`. Unauthorized requests are rejected with PERMISSION_DENIED.
     * - Supabase Storage delete is authorized by Storage RLS `public.is_admin()`.
     * 
     * Handles partial failures safely: if Firestore metadata is deleted but Storage cleanup fails,
     * reports [PhotoPartialDeletionException] with full diagnostic details.
     */
     fun deletePhotoAsAdmin(
         photo: PandalPhoto,
         onComplete: (Exception?) -> Unit
     ) {
         if (!isAdminLoggedIn && currentAdminUid == null) {
             onComplete(SecurityException("Admin privileges required"))
             return
         }

         val user = auth.currentUser
         if (user == null) {
             onComplete(SecurityException("Not authenticated"))
             return
         }

         user.getIdToken(true)
             .addOnSuccessListener { tokenResult ->
                 val idToken = tokenResult.token
                 
                 // Step 1: Delete Firestore metadata.
                 // This immediately removes the photo from community gallery, searches, and admin moderation.
                 // Strictly authorized by firestore.rules isAdmin() on the server side.
                 firestore.collection(PhotoRepository.COLLECTION)
                     .document(photo.id)
                     .delete()
                     .addOnSuccessListener {
                         Log.i(TAG, "Admin deleted photo doc ${photo.id} from Firestore")
                         
                         // Step 2: Delete Supabase Storage object.
                         if (photo.storagePath.isNotBlank()) {
                             supabase.deletePhoto(photo.storagePath, idToken) { storageErr ->
                                 if (storageErr == null) {
                                     Log.i(TAG, "Admin deleted storage object ${photo.storagePath}")
                                     onComplete(null)
                                 } else {
                                     Log.w(TAG, "Admin storage delete failed for ${photo.storagePath}: ${storageErr.message}")
                                     onComplete(PhotoPartialDeletionException(
                                         message = "Photo removed from gallery, but storage cleanup failed: ${storageErr.message}",
                                         firestoreSuccess = true,
                                         storageSuccess = false,
                                         cause = storageErr
                                     ))
                                 }
                             }
                         } else {
                             onComplete(null)
                         }
                     }
                     .addOnFailureListener { firestoreErr ->
                         Log.e(TAG, "Admin delete Firestore doc failed: ${firestoreErr.message}", firestoreErr)
                         onComplete(PhotoPartialDeletionException(
                             message = "Failed to delete photo metadata from Firestore: ${firestoreErr.message}",
                             firestoreSuccess = false,
                             storageSuccess = false,
                             cause = firestoreErr
                         ))
                     }
             }
             .addOnFailureListener { tokenErr ->
                 onComplete(tokenErr)
             }
     }

    /**
     * Updates moderation status of a photo (e.g. 'active', 'hidden', 'flagged').
     */
    fun moderatePhotoStatus(
        photoId: String,
        newStatus: String,
        onComplete: (Exception?) -> Unit
    ) {
        firestore.collection(PhotoRepository.COLLECTION)
            .document(photoId)
            .update("status", newStatus)
            .addOnSuccessListener {
                Log.i(TAG, "Admin updated photo $photoId status to $newStatus")
                onComplete(null)
            }
            .addOnFailureListener { err ->
                Log.e(TAG, "Admin update photo status failed: ${err.message}", err)
                onComplete(err)
            }
    }

    /**
     * Fetches recent crowd reports for admin review and moderation.
     */
    fun fetchRecentCrowdReports(onResult: (List<AdminCrowdReport>) -> Unit) {
        firestore.collection(CrowdRepository.COLLECTION)
            .get()
            .addOnSuccessListener { snapshot ->
                val reports = snapshot.documents.mapNotNull { doc ->
                    val id = doc.id
                    val pId = doc.getString("pandalId") ?: ""
                    val level = doc.getLong("level")?.toInt() ?: doc.getLong("crowdLevel")?.toInt() ?: 5
                    val uid = doc.getString("userId") ?: ""
                    val devId = doc.getString("deviceId") ?: ""
                    val ts = (doc.getTimestamp("createdAt") ?: doc.getTimestamp("timestamp"))?.toDate()?.time ?: 0L
                    val status = doc.getString("status") ?: "active"
                    AdminCrowdReport(id, pId, level, uid, devId, ts, status)
                }.sortedByDescending { it.timestamp }
                onResult(reports)
            }
            .addOnFailureListener { error ->
                Log.e(TAG, "Admin fetch crowd reports failed: ${error.message}", error)
                onResult(emptyList())
            }
    }

    /**
     * Deletes a crowd report as Admin.
     */
    fun deleteCrowdReport(reportId: String, onComplete: (Exception?) -> Unit) {
        firestore.collection(CrowdRepository.COLLECTION)
            .document(reportId)
            .delete()
            .addOnSuccessListener {
                Log.i(TAG, "Admin deleted crowd report $reportId")
                onComplete(null)
            }
            .addOnFailureListener { err ->
                Log.e(TAG, "Admin delete crowd report failed: ${err.message}", err)
                onComplete(err)
            }
    }

    /**
     * Fetches recent weather reports for admin moderation.
     */
    fun fetchRecentWeatherReports(onResult: (List<AdminWeatherReport>) -> Unit) {
        firestore.collection(WeatherReportRepository.COLLECTION)
            .get()
            .addOnSuccessListener { snapshot ->
                val reports = snapshot.documents.mapNotNull { doc ->
                    val id = doc.id
                    val pId = doc.getString("pandalId") ?: ""
                    val cond = doc.getString("condition") ?: doc.getString("weatherCondition") ?: "NO_RAIN"
                    val uid = doc.getString("userId") ?: ""
                    val ts = (doc.getTimestamp("createdAt") ?: doc.getTimestamp("timestamp"))?.toDate()?.time ?: 0L
                    AdminWeatherReport(id, pId, cond, uid, ts)
                }.sortedByDescending { it.timestamp }
                onResult(reports)
            }
            .addOnFailureListener { error ->
                Log.e(TAG, "Admin fetch weather reports failed: ${error.message}", error)
                onResult(emptyList())
            }
    }

    /**
     * Deletes a weather report as Admin.
     */
    fun deleteWeatherReport(reportId: String, onComplete: (Exception?) -> Unit) {
        firestore.collection(WeatherReportRepository.COLLECTION)
            .document(reportId)
            .delete()
            .addOnSuccessListener {
                Log.i(TAG, "Admin deleted weather report $reportId")
                onComplete(null)
            }
            .addOnFailureListener { err ->
                Log.e(TAG, "Admin delete weather report failed: ${err.message}", err)
                onComplete(err)
            }
    }

    companion object {
        const val TAG = "PandalFinder-Admin"
        const val ADMINS_COLLECTION = "admins"
    }
}
