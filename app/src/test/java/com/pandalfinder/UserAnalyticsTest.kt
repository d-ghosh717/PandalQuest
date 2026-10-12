package com.pandalfinder

import com.pandalfinder.data.UserAnalyticsRepository
import com.pandalfinder.data.UserTelemetryRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class UserAnalyticsTest {

    private val kolkataTz = TimeZone.getTimeZone("Asia/Kolkata")

    @Test
    fun testStartOfDayTimestampInKolkata() {
        val todayStart = UserAnalyticsRepository.getTodayStartTimestamp(kolkataTz)
        val cal = Calendar.getInstance(kolkataTz).apply {
            timeInMillis = todayStart
        }

        assertEquals(0, cal.get(Calendar.HOUR_OF_DAY))
        assertEquals(0, cal.get(Calendar.MINUTE))
        assertEquals(0, cal.get(Calendar.SECOND))
        assertEquals(0, cal.get(Calendar.MILLISECOND))
    }

    @Test
    fun testComputeAnalyticsSummary_CountsCorrectly() {
        val todayStart = UserAnalyticsRepository.getTodayStartTimestamp(kolkataTz)
        val oneHourAgo = todayStart + (3600 * 1000 * 2) // 2 hours into today
        val yesterday = todayStart - (3600 * 1000 * 12) // 12 hours before today start
        val lastWeek = todayStart - (3600 * 1000 * 24 * 7)

        val records = listOf(
            // User 1: Created today, active today (v2.1.1)
            UserTelemetryRecord(
                uid = "anon_user_1",
                createdAtMillis = oneHourAgo,
                lastSeenAtMillis = oneHourAgo,
                appVersion = "2.1.1"
            ),
            // User 2: Created yesterday, active today (v2.1.1)
            UserTelemetryRecord(
                uid = "anon_user_2",
                createdAtMillis = yesterday,
                lastSeenAtMillis = oneHourAgo,
                appVersion = "2.1.1"
            ),
            // User 3: Created last week, last seen yesterday (v2.1.0)
            UserTelemetryRecord(
                uid = "anon_user_3",
                createdAtMillis = lastWeek,
                lastSeenAtMillis = yesterday,
                appVersion = "2.1.0"
            ),
            // User 4: Created last week, active today (v2.0.0)
            UserTelemetryRecord(
                uid = "anon_user_4",
                createdAtMillis = lastWeek,
                lastSeenAtMillis = oneHourAgo,
                appVersion = "2.0.0"
            )
        )

        val summary = UserAnalyticsRepository.computeAnalyticsSummary(records, todayStart)

        // 4 total registered
        assertEquals(4, summary.totalRegistered)
        // Only User 1 created today
        assertEquals(1, summary.newUsersToday)
        // Users 1, 2, 4 active today
        assertEquals(3, summary.activeToday)

        // Version distribution: v2.1.1: 2, v2.1.0: 1, v2.0.0: 1
        assertEquals(3, summary.versionDistribution.size)
        assertEquals(2, summary.versionDistribution["2.1.1"])
        assertEquals(1, summary.versionDistribution["2.1.0"])
        assertEquals(1, summary.versionDistribution["2.0.0"])
    }

    @Test
    fun testComputeAnalyticsSummary_EmptyList() {
        val todayStart = UserAnalyticsRepository.getTodayStartTimestamp(kolkataTz)
        val summary = UserAnalyticsRepository.computeAnalyticsSummary(emptyList(), todayStart)

        assertEquals(0, summary.totalRegistered)
        assertEquals(0, summary.newUsersToday)
        assertEquals(0, summary.activeToday)
        assertTrue(summary.versionDistribution.isEmpty())
    }

    @Test
    fun testUserTelemetry_DoesNotStorePii() {
        val record = UserTelemetryRecord(
            uid = "anon_firebase_uid_12345",
            createdAtMillis = 1728000000L,
            lastSeenAtMillis = 1728000000L,
            appVersion = "2.1.1"
        )

        // Validate that fields are strictly anonymous telemetry
        assertTrue(record.uid.startsWith("anon_"))
        assertEquals("2.1.1", record.appVersion)
        assertTrue(record.createdAtMillis > 0)
        assertTrue(record.lastSeenAtMillis > 0)
    }

    @Test
    fun testLivePresence_DeduplicatesMultipleSessionsForSameUid() {
        val now = 1728000000000L
        val userSessions = mapOf(
            // User 1 has 3 active sessions across multiple devices/tabs
            "uid_user_1" to listOf(
                com.pandalfinder.data.PresenceSessionData(online = true, lastSeenMillis = now - 10_000L, appVersion = "2.1.2"),
                com.pandalfinder.data.PresenceSessionData(online = true, lastSeenMillis = now - 25_000L, appVersion = "2.1.2"),
                com.pandalfinder.data.PresenceSessionData(online = true, lastSeenMillis = now - 5_000L, appVersion = "2.1.2")
            ),
            // User 2 has 1 active session
            "uid_user_2" to listOf(
                com.pandalfinder.data.PresenceSessionData(online = true, lastSeenMillis = now - 30_000L, appVersion = "2.1.2")
            )
        )

        // Must count 2 unique users, NOT 4 raw sessions
        val liveCount = UserAnalyticsRepository.countUniqueLiveUsersFromSessions(userSessions, now)
        assertEquals(2, liveCount)
    }

    @Test
    fun testLivePresence_ExcludesStaleSessionsBeyondThreshold() {
        val now = 1728000000000L
        val staleThreshold = UserAnalyticsRepository.STALE_SESSION_THRESHOLD_MS // 150_000L (2.5 min)

        val userSessions = mapOf(
            // User 1: Heartbeat fresh (60s ago) -> ACTIVE
            "uid_active" to listOf(
                com.pandalfinder.data.PresenceSessionData(online = true, lastSeenMillis = now - 60_000L, appVersion = "2.1.2")
            ),
            // User 2: Exactly at threshold boundary (149s ago) -> ACTIVE
            "uid_boundary_active" to listOf(
                com.pandalfinder.data.PresenceSessionData(online = true, lastSeenMillis = now - 149_000L, appVersion = "2.1.2")
            ),
            // User 3: Past threshold (180s = 3 minutes ago) -> STALE, must NOT count
            "uid_stale_3min" to listOf(
                com.pandalfinder.data.PresenceSessionData(online = true, lastSeenMillis = now - 180_000L, appVersion = "2.1.2")
            ),
            // User 4: Leftover session from yesterday (86400s ago) -> STALE, must NOT count
            "uid_stale_yesterday" to listOf(
                com.pandalfinder.data.PresenceSessionData(online = true, lastSeenMillis = now - (86400 * 1000L), appVersion = "2.1.0")
            )
        )

        val liveCount = UserAnalyticsRepository.countUniqueLiveUsersFromSessions(userSessions, now, staleThreshold)
        // Only uid_active and uid_boundary_active should be counted
        assertEquals(2, liveCount)
    }

    @Test
    fun testLivePresence_ExcludesOfflineSessions() {
        val now = 1728000000000L
        val userSessions = mapOf(
            // User 1: Recent timestamp but online = false -> OFFLINE
            "uid_offline" to listOf(
                com.pandalfinder.data.PresenceSessionData(online = false, lastSeenMillis = now - 10_000L, appVersion = "2.1.2")
            ),
            // User 2: Online with fresh heartbeat -> ACTIVE
            "uid_online" to listOf(
                com.pandalfinder.data.PresenceSessionData(online = true, lastSeenMillis = now - 10_000L, appVersion = "2.1.2")
            )
        )

        val liveCount = UserAnalyticsRepository.countUniqueLiveUsersFromSessions(userSessions, now)
        assertEquals(1, liveCount)
    }

    @Test
    fun testLivePresence_MixedUserLifecycleScenarios() {
        val now = 1728000000000L
        val userSessions = mapOf(
            // User 1: 1 stale session + 1 active session on another device -> ACTIVE (counts 1)
            "uid_multi_device_one_stale" to listOf(
                com.pandalfinder.data.PresenceSessionData(online = true, lastSeenMillis = now - 300_000L, appVersion = "2.1.0"),
                com.pandalfinder.data.PresenceSessionData(online = true, lastSeenMillis = now - 20_000L, appVersion = "2.1.2")
            ),
            // User 2: 2 offline sessions -> NOT ACTIVE (counts 0)
            "uid_all_offline" to listOf(
                com.pandalfinder.data.PresenceSessionData(online = false, lastSeenMillis = now - 5_000L, appVersion = "2.1.2"),
                com.pandalfinder.data.PresenceSessionData(online = false, lastSeenMillis = now - 15_000L, appVersion = "2.1.2")
            ),
            // User 3: zero sessions -> NOT ACTIVE (counts 0)
            "uid_empty" to emptyList(),
            // User 4: active session -> ACTIVE (counts 1)
            "uid_active_single" to listOf(
                com.pandalfinder.data.PresenceSessionData(online = true, lastSeenMillis = now - 45_000L, appVersion = "2.1.2")
            )
        )

        val liveCount = UserAnalyticsRepository.countUniqueLiveUsersFromSessions(userSessions, now)
        assertEquals(2, liveCount)
    }
}
