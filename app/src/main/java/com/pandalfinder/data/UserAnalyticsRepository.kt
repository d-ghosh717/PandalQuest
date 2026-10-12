package com.pandalfinder.data

import android.util.Log
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import java.util.Calendar
import java.util.TimeZone
import java.util.UUID

/**
 * Encapsulates aggregate telemetry calculations for the admin dashboard.
 * All data is strictly non-PII, referencing only anonymous Firebase UIDs and server timestamps.
 */
data class UserAnalyticsSummary(
    val totalRegistered: Int = 0,
    val newUsersToday: Int = 0,
    val activeToday: Int = 0,
    val liveUsers: Int = 0,
    val versionDistribution: Map<String, Int> = emptyMap()
)

/**
 * Simplified record representation used for pure testable business logic.
 */
data class UserTelemetryRecord(
    val uid: String,
    val createdAtMillis: Long,
    val lastSeenAtMillis: Long,
    val appVersion: String
)

data class PresenceSessionData(
    val online: Boolean = false,
    val lastSeenMillis: Long = 0L,
    val appVersion: String = ""
)

/**
 * Manages user registration telemetry, live RTDB presence, and administrative analytics.
 *
 * Privacy & Security Guarantees:
 * 1. Zero Personally Identifiable Information (no names, phones, emails, or precise coordinates).
 * 2. Scoped strictly to Firebase Anonymous UIDs.
 * 3. Daily boundaries calculated using Indian Standard Time (Asia/Kolkata, UTC+05:30).
 * 4. Presence handles multiple concurrent sessions per UID without double-counting.
 * 5. Foreground heartbeat with 150s (2.5 min) TTL prevents stale records from counting indefinitely.
 */
class UserAnalyticsRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val database: FirebaseDatabase = try {
        FirebaseDatabase.getInstance("https://pandalquest-default-rtdb.firebaseio.com")
    } catch (e: Exception) {
        FirebaseDatabase.getInstance()
    }
) {

    private var activeSessionId: String? = null
    private var activeUid: String? = null
    private var connectedListener: ValueEventListener? = null
    private var connectedRef: com.google.firebase.database.DatabaseReference? = null
    private var serverTimeOffsetMillis: Long = 0L

    private val heartbeatHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var heartbeatRunnable: Runnable? = null

    init {
        try {
            database.getReference(".info/serverTimeOffset").addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    serverTimeOffsetMillis = snapshot.getValue(Long::class.java) ?: 0L
                }
                override fun onCancelled(error: DatabaseError) {}
            })
        } catch (_: Exception) {}
    }

    /**
     * Registers a user launch in Firestore (`users/{uid}`).
     * If the document does not exist, stores `createdAt`, `lastSeenAt`, and `appVersion`.
     * If it already exists, updates `lastSeenAt` and `appVersion` without altering `createdAt`.
     */
    fun recordUserLaunch(
        uid: String,
        appVersion: String,
        onComplete: ((Boolean) -> Unit)? = null
    ) {
        if (uid.isBlank()) {
            onComplete?.invoke(false)
            return
        }

        val userDoc = firestore.collection(USERS_COLLECTION).document(uid)
        userDoc.get().addOnSuccessListener { snapshot ->
            if (!snapshot.exists()) {
                val newUserData = hashMapOf(
                    "createdAt" to FieldValue.serverTimestamp(),
                    "lastSeenAt" to FieldValue.serverTimestamp(),
                    "appVersion" to appVersion
                )
                userDoc.set(newUserData).addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        Log.i(TAG, "New anonymous user registered: ${uid.take(6)}...")
                    } else {
                        Log.w(TAG, "Failed to register new user: ${task.exception?.message}")
                    }
                    onComplete?.invoke(task.isSuccessful)
                }
            } else {
                val updateData = hashMapOf<String, Any>(
                    "lastSeenAt" to FieldValue.serverTimestamp(),
                    "appVersion" to appVersion
                )
                userDoc.update(updateData).addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        Log.d(TAG, "User launch updated: ${uid.take(6)}...")
                    }
                    onComplete?.invoke(task.isSuccessful)
                }
            }
        }.addOnFailureListener { error ->
            Log.w(TAG, "Error checking user registration document: ${error.message}")
            onComplete?.invoke(false)
        }
    }

    /**
     * Starts a live session in Firebase Realtime Database at `/presence/{uid}/{sessionId}`.
     * Sets up `.info/connected` listening, `onDisconnect().removeValue()`, and foreground heartbeat.
     */
    fun startPresenceSession(uid: String, appVersion: String) {
        if (uid.isBlank()) return

        // Stop existing session if user or session changed
        if (activeUid == uid && activeSessionId != null) {
            return // already active for this session
        }
        stopPresenceSession()

        val sessionId = UUID.randomUUID().toString().take(12)
        activeUid = uid
        activeSessionId = sessionId

        try {
            val presenceRef = database.getReference(PRESENCE_PATH).child(uid).child(sessionId)
            val infoConnected = database.getReference(".info/connected")
            connectedRef = infoConnected

            val listener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val connected = snapshot.getValue(Boolean::class.java) ?: false
                    if (connected) {
                        presenceRef.onDisconnect().removeValue()
                        val sessionPayload = hashMapOf(
                            "online" to true,
                            "lastSeen" to ServerValue.TIMESTAMP,
                            "appVersion" to appVersion
                        )
                        presenceRef.setValue(sessionPayload)
                        startHeartbeat(presenceRef)
                        Log.d(TAG, "Live presence registered for ${uid.take(6)}... [session $sessionId]")
                    } else {
                        stopHeartbeat()
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.w(TAG, "Presence connection listener cancelled: ${error.message}")
                }
            }

            connectedListener = listener
            infoConnected.addValueEventListener(listener)
        } catch (e: Exception) {
            Log.w(TAG, "Unable to initialize RTDB presence: ${e.message}")
        }
    }

    private fun startHeartbeat(presenceRef: com.google.firebase.database.DatabaseReference) {
        stopHeartbeat()
        val runnable = object : Runnable {
            override fun run() {
                val updates = hashMapOf<String, Any>(
                    "online" to true,
                    "lastSeen" to ServerValue.TIMESTAMP
                )
                presenceRef.updateChildren(updates).addOnFailureListener { e ->
                    Log.w(TAG, "Presence heartbeat failed: ${e.message}")
                }
                heartbeatHandler.postDelayed(this, HEARTBEAT_INTERVAL_MS)
            }
        }
        heartbeatRunnable = runnable
        heartbeatHandler.postDelayed(runnable, HEARTBEAT_INTERVAL_MS)
    }

    private fun stopHeartbeat() {
        heartbeatRunnable?.let { heartbeatHandler.removeCallbacks(it) }
        heartbeatRunnable = null
    }

    /**
     * Cleans up the active presence session when the app is backgrounded or destroyed.
     */
    fun stopPresenceSession() {
        stopHeartbeat()
        val uid = activeUid
        val sessionId = activeSessionId

        connectedListener?.let { listener ->
            connectedRef?.removeEventListener(listener)
        }
        connectedListener = null
        connectedRef = null

        if (uid != null && sessionId != null) {
            try {
                database.getReference(PRESENCE_PATH).child(uid).child(sessionId).removeValue()
                Log.d(TAG, "Presence removed for ${uid.take(6)}... [session $sessionId]")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to remove presence node: ${e.message}")
            }
        }

        activeUid = null
        activeSessionId = null
    }

    /**
     * Admin-only real-time listener for current live users.
     * Correctly handles multiple devices/sessions for the same UID by counting unique user keys,
     * while filtering out stale or offline records.
     *
     * @return A cleanup function that removes the listener when the admin view is closed.
     */
    fun listenToLiveUserCount(
        onLiveCountChanged: (Int) -> Unit,
        onError: ((Exception) -> Unit)? = null
    ): () -> Unit {
        val presenceRef = database.getReference(PRESENCE_PATH)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val estimatedServerNow = System.currentTimeMillis() + serverTimeOffsetMillis
                val uniqueLiveUsers = countUniqueLiveUsers(snapshot, estimatedServerNow, STALE_SESSION_THRESHOLD_MS)
                onLiveCountChanged(uniqueLiveUsers)
            }

            override fun onCancelled(error: DatabaseError) {
                Log.w(TAG, "Live count listener cancelled: ${error.message}")
                onError?.invoke(error.toException())
            }
        }

        presenceRef.addValueEventListener(listener)
        return {
            try {
                presenceRef.removeEventListener(listener)
            } catch (e: Exception) {
                Log.w(TAG, "Error removing presence listener: ${e.message}")
            }
        }
    }

    /**
     * Admin-only query to fetch aggregate daily and total analytics from Firestore.
     */
    fun fetchUserAnalyticsSummary(
        onResult: (Result<UserAnalyticsSummary>) -> Unit
    ) {
        val todayStartMillis = getTodayStartTimestamp(KOLKATA_TIMEZONE)

        firestore.collection(USERS_COLLECTION)
            .get()
            .addOnSuccessListener { snapshot ->
                val records = snapshot.documents.mapNotNull { doc ->
                    val uid = doc.id
                    val created = doc.getTimestamp("createdAt")?.toDate()?.time ?: 0L
                    val lastSeen = doc.getTimestamp("lastSeenAt")?.toDate()?.time ?: 0L
                    val version = doc.getString("appVersion") ?: "2.1.0"
                    UserTelemetryRecord(uid, created, lastSeen, version)
                }

                val summary = computeAnalyticsSummary(records, todayStartMillis)
                onResult(Result.success(summary))
            }
            .addOnFailureListener { error ->
                Log.e(TAG, "Failed to fetch user analytics: ${error.message}")
                onResult(Result.failure(error))
            }
    }

    companion object {
        private const val TAG = "UserAnalyticsRepo"
        const val USERS_COLLECTION = "users"
        const val PRESENCE_PATH = "presence"

        /**
         * Stale session expiry threshold (2.5 minutes).
         * Trade-off rationale: Absorbs momentary network drops or metro blackouts (15-45s)
         * without false-dropping active users, while ensuring dead or crashed sessions
         * are completely excluded within 2.5 minutes.
         */
        const val STALE_SESSION_THRESHOLD_MS: Long = 150_000L

        /**
         * Foreground heartbeat interval (60 seconds).
         */
        const val HEARTBEAT_INTERVAL_MS: Long = 60_000L

        val KOLKATA_TIMEZONE: TimeZone = TimeZone.getTimeZone("Asia/Kolkata")

        /**
         * Pure calculation function: computes start of current day in the given timezone.
         */
        fun getTodayStartTimestamp(timeZone: TimeZone = KOLKATA_TIMEZONE): Long {
            val cal = Calendar.getInstance(timeZone).apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            return cal.timeInMillis
        }

        /**
         * Counts unique UIDs with at least one valid, non-stale foreground session from RTDB.
         * Multi-device and multi-session instances of the same UID are counted exactly once.
         */
        fun countUniqueLiveUsers(
            snapshot: DataSnapshot,
            currentTimeMillis: Long = System.currentTimeMillis(),
            staleThresholdMillis: Long = STALE_SESSION_THRESHOLD_MS
        ): Int {
            var uniqueCount = 0
            for (userChild in snapshot.children) {
                // Each userChild key is a distinct UID
                var hasActiveForegroundSession = false
                for (sessionChild in userChild.children) {
                    val isOnline = sessionChild.child("online").getValue(Boolean::class.java) ?: false
                    val lastSeen = sessionChild.child("lastSeen").getValue(Long::class.java) ?: 0L
                    val isFresh = lastSeen > 0L && (currentTimeMillis - lastSeen) in 0..staleThresholdMillis
                    if (isOnline && isFresh) {
                        hasActiveForegroundSession = true
                        break // Deduplicate multiple devices/sessions for this user
                    }
                }
                if (hasActiveForegroundSession) {
                    uniqueCount++
                }
            }
            return uniqueCount
        }

        /**
         * Pure calculation function for unit tests: counts unique live users from in-memory session map.
         */
        fun countUniqueLiveUsersFromSessions(
            userSessions: Map<String, List<PresenceSessionData>>,
            currentTimeMillis: Long,
            staleThresholdMillis: Long = STALE_SESSION_THRESHOLD_MS
        ): Int {
            var uniqueCount = 0
            for ((_, sessions) in userSessions) {
                val hasActive = sessions.any { session ->
                    session.online &&
                    session.lastSeenMillis > 0L &&
                    (currentTimeMillis - session.lastSeenMillis) in 0..staleThresholdMillis
                }
                if (hasActive) {
                    uniqueCount++
                }
            }
            return uniqueCount
        }

        /**
         * Pure calculation function for daily analytics breakdown.
         */
        fun computeAnalyticsSummary(
            records: List<UserTelemetryRecord>,
            todayStartMillis: Long
        ): UserAnalyticsSummary {
            var newToday = 0
            var activeToday = 0
            val versions = mutableMapOf<String, Int>()

            for (record in records) {
                if (record.createdAtMillis >= todayStartMillis) {
                    newToday++
                }
                if (record.lastSeenAtMillis >= todayStartMillis) {
                    activeToday++
                }
                val ver = record.appVersion.ifBlank { "unknown" }
                versions[ver] = (versions[ver] ?: 0) + 1
            }

            return UserAnalyticsSummary(
                totalRegistered = records.size,
                newUsersToday = newToday,
                activeToday = activeToday,
                liveUsers = 0, // Populated via real-time presence listener
                versionDistribution = versions
            )
        }
    }
}
