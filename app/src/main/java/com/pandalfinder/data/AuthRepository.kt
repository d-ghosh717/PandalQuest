package com.pandalfinder.data

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import java.util.UUID

class AuthRepository {
    private val auth: FirebaseAuth
        get() = FirebaseAuth.getInstance()

    val currentUid: String?
        get() = auth.currentUser?.uid

    /**
     * Initializes anonymous auth on startup if no user exists.
     */
    fun initAndSignInAnonymously(onComplete: ((String?) -> Unit)? = null) {
        val existing = auth.currentUser
        if (existing != null) {
            Log.d(TAG, "Existing Firebase Anonymous UID: ${existing.uid}")
            onComplete?.invoke(existing.uid)
            return
        }

        Log.d(TAG, "No Firebase user found. Initiating anonymous sign in...")
        auth.signInAnonymously()
            .addOnSuccessListener { result ->
                val uid = result.user?.uid
                Log.d(TAG, "Anonymous sign in successful. UID: $uid")
                onComplete?.invoke(uid)
            }
            .addOnFailureListener { exception ->
                Log.e(TAG, "Anonymous sign in failed: ${exception.message}", exception)
                onComplete?.invoke(null)
            }
    }

    /**
     * Retrieves the current user's UID or performs an immediate anonymous sign-in.
     * Invokes callback with valid Firebase UID, or null if unauthenticated.
     */
    fun getOrCreateUid(onReady: (String?) -> Unit) {
        val current = auth.currentUser
        if (current != null) {
            onReady(current.uid)
        } else {
            auth.signInAnonymously()
                .addOnSuccessListener { result ->
                    val uid = result.user?.uid
                    onReady(uid)
                }
                .addOnFailureListener { err ->
                    Log.e(TAG, "Anonymous sign-in error on demand: ${err.message}", err)
                    onReady(null)
                }
        }
    }

    companion object {
        private const val TAG = "AuthRepository"
    }
}
