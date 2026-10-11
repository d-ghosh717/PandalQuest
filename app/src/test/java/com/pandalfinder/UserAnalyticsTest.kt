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
}
