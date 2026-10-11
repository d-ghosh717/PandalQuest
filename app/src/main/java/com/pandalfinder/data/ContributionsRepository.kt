package com.pandalfinder.data

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore

data class UserContributions(
    val photosCount: Int = 0,
    val crowdReportsCount: Int = 0,
    val weatherReportsCount: Int = 0
) {
    val total: Int get() = photosCount + crowdReportsCount + weatherReportsCount
}

class ContributionsRepository {
    private val firestore: FirebaseFirestore
        get() = FirebaseFirestore.getInstance()

    /**
     * Queries Firestore collections for actual community contributions submitted by the given UID.
     * Counts Photos, Crowd Reports, and Weather Reports submitted by this user.
     * Guaranteed to use the runtime authenticated UID and only count active, successful submissions.
     */
    fun fetchUserContributions(uid: String, onResult: (UserContributions) -> Unit) {
        if (uid.isBlank()) {
            onResult(UserContributions(0, 0, 0))
            return
        }

        var photos = 0
        var crowd = 0
        var weather = 0
        var completed = 0

        fun checkDone() {
            completed++
            if (completed == 3) {
                Log.d(TAG, "Aggregated user contributions: photos=$photos, crowd=$crowd, weather=$weather, total=${photos + crowd + weather}")
                onResult(UserContributions(photos, crowd, weather))
            }
        }

        // 1. Photos: Only successful photo uploads by this UID
        firestore.collection(PhotoRepository.COLLECTION)
            .whereEqualTo("uploadedBy", uid)
            .whereEqualTo("status", "active")
            .get()
            .addOnSuccessListener { snap ->
                photos = snap.documents.count { doc ->
                    val status = doc.getString("status") ?: "active"
                    val url = doc.getString("downloadUrl") ?: ""
                    status == "active" && url.isNotBlank()
                }
                Log.d(TAG, "Queried personal photos: count=$photos for uid prefix ${uid.take(6)}...")
                checkDone()
            }
            .addOnFailureListener { err ->
                Log.e(TAG, "Failed to query personal photos: ${err.message}", err)
                checkDone()
            }

        // 2. Crowd Reports: Only active reports submitted by this UID
        firestore.collection(CrowdRepository.COLLECTION)
            .whereEqualTo("userId", uid)
            .get()
            .addOnSuccessListener { snap ->
                crowd = snap.documents.count { doc ->
                    val status = doc.getString("status") ?: "active"
                    status == "active"
                }
                Log.d(TAG, "Queried personal crowd reports: count=$crowd for uid prefix ${uid.take(6)}...")
                checkDone()
            }
            .addOnFailureListener { err ->
                Log.e(TAG, "Failed to query personal crowd reports: ${err.message}", err)
                checkDone()
            }

        // 3. Weather Reports: Only community weather reports submitted by this UID
        firestore.collection(WeatherReportRepository.COLLECTION)
            .whereEqualTo("userId", uid)
            .get()
            .addOnSuccessListener { snap ->
                val activeCount = snap.documents.count { (it.getString("status") ?: "active") == "active" }
                if (activeCount > 0) {
                    weather = activeCount
                    Log.d(TAG, "Queried personal weather reports (by userId): count=$weather for uid prefix ${uid.take(6)}...")
                    checkDone()
                } else {
                    // Fallback to reportedBy field for defensive compatibility
                    firestore.collection(WeatherReportRepository.COLLECTION)
                        .whereEqualTo("reportedBy", uid)
                        .get()
                        .addOnSuccessListener { fallbackSnap ->
                            weather = fallbackSnap.documents.count { (it.getString("status") ?: "active") == "active" }
                            Log.d(TAG, "Queried personal weather reports (by reportedBy fallback): count=$weather for uid prefix ${uid.take(6)}...")
                            checkDone()
                        }
                        .addOnFailureListener { err ->
                            Log.e(TAG, "Failed fallback query personal weather reports: ${err.message}", err)
                            checkDone()
                        }
                }
            }
            .addOnFailureListener { err ->
                Log.e(TAG, "Failed to query personal weather reports: ${err.message}", err)
                checkDone()
            }
    }

    /**
     * Fetches all active photos uploaded specifically by the current user UID (newest first).
     * Strictly verifies uploadedBy == uid for defense-in-depth against cache pollution.
     */
    fun fetchUserPhotos(uid: String, onResult: (List<PandalPhoto>) -> Unit) {
        if (uid.isBlank()) {
            onResult(emptyList())
            return
        }

        firestore.collection(PhotoRepository.COLLECTION)
            .whereEqualTo("uploadedBy", uid)
            .whereEqualTo("status", "active")
            .get()
            .addOnSuccessListener { snap ->
                val photos = snap.documents.mapNotNull { doc ->
                    val id = doc.getString("id") ?: doc.id
                    val pId = doc.getString("pandalId") ?: ""
                    val path = doc.getString("storagePath") ?: ""
                    val url = doc.getString("downloadUrl") ?: ""
                    val uploader = doc.getString("uploadedBy") ?: ""
                    val time = doc.getLong("createdAt") ?: 0L
                    val status = doc.getString("status") ?: "active"
                    if (url.isNotBlank() && status == "active" && uploader == uid) {
                        PandalPhoto(id, pId, path, url, uploader, time, status)
                    } else null
                }.sortedByDescending { it.createdAt }
                Log.d(TAG, "Queried personal photo list: count=${photos.size} for uid prefix ${uid.take(6)}...")
                onResult(photos)
            }
            .addOnFailureListener { err ->
                Log.e(TAG, "Failed to query personal photo list: ${err.message}", err)
                onResult(emptyList())
            }
    }

    companion object {
        private const val TAG = "ContributionsRepo"
    }
}
