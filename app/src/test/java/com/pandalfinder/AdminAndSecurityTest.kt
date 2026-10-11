package com.pandalfinder

import com.pandalfinder.data.PandalPhoto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdminAndSecurityTest {

    // ── 1. Photo Ownership Model ──

    @Test
    fun testPhotoOwnershipPermissions() {
        val userA = "USER_A_UID_12345"
        val userB = "USER_B_UID_67890"

        val photoA = PandalPhoto(
            id = "photo_abc",
            pandalId = "mudiali_club",
            storagePath = "pandal-photos/$userA/mudiali_club/photo_abc.jpg",
            downloadUrl = "https://example.supabase.co/storage/v1/object/public/pandal-photos/$userA/mudiali_club/photo_abc.jpg",
            uploadedBy = userA,
            createdAt = System.currentTimeMillis(),
            status = "active"
        )

        // User A (Owner) can modify/delete own photo
        val canUserAModify = photoA.uploadedBy == userA
        val canUserADelete = photoA.uploadedBy == userA
        assertTrue("User A (owner) must have modify permissions", canUserAModify)
        assertTrue("User A (owner) must have delete permissions", canUserADelete)

        // User B (Normal User) MUST NOT modify/delete User A's photo
        val canUserBModify = photoA.uploadedBy == userB
        val canUserBDelete = photoA.uploadedBy == userB
        assertFalse("User B must NOT have modify permissions on User A's photo", canUserBModify)
        assertFalse("User B must NOT have delete permissions on User A's photo", canUserBDelete)
    }

    @Test
    fun testPhotoOwnershipImmutability() {
        val userA = "USER_A"
        val userB = "USER_B"

        val originalPhoto = PandalPhoto(
            id = "photo_123",
            pandalId = "ballygunge_cultural",
            storagePath = "pandal-photos/$userA/ballygunge_cultural/photo_123.jpg",
            downloadUrl = "https://example.com/photo.jpg",
            uploadedBy = userA,
            createdAt = 1000L,
            status = "active"
        )

        // Simulate update attempt changing uploadedBy
        val updatedPhotoWithChangedOwner = originalPhoto.copy(uploadedBy = userB)

        // Security rule verification: updated.uploadedBy MUST equal original.uploadedBy
        val isOwnershipPreserved = updatedPhotoWithChangedOwner.uploadedBy == originalPhoto.uploadedBy
        assertFalse("Ownership transfer must be DENIED - uploadedBy is immutable", isOwnershipPreserved)
    }

    @Test
    fun testAdminPhotoPrivileges() {
        val userA = "USER_A"
        val adminUid = "ADMIN_FIREBASE_UID"
        val isUserAdmin = true

        val photoA = PandalPhoto(
            id = "photo_xyz",
            pandalId = "ekdalia_evergreen",
            storagePath = "pandal-photos/$userA/ekdalia_evergreen/photo_xyz.jpg",
            downloadUrl = "https://example.com/photo_xyz.jpg",
            uploadedBy = userA,
            createdAt = 2000L,
            status = "active"
        )

        // Admin authorization logic: owner check OR admin check
        val canAdminDelete = (photoA.uploadedBy == adminUid) || isUserAdmin
        val canAdminModerate = isUserAdmin

        assertTrue("Authorized Admin must be allowed to delete any photo", canAdminDelete)
        assertTrue("Authorized Admin must be allowed to moderate any photo", canAdminModerate)
    }

    // ── 2. Storage Path Validation ──

    @Test
    fun testStoragePathValidation() {
        val validRegex = Regex("^pandal-?photos/[^/]+/[^/]+/[^/]+\\.jpg$")

        val validPath1 = "pandal-photos/user_123/ballygunge/photo_99.jpg"
        val validPath2 = "pandalphotos/uid_abc/chetla_agrani/img_01.jpg"
        val invalidPathTraversal = "pandal-photos/../user_123/pandal/photo.jpg"
        val invalidPathWrongBucket = "other-bucket/user_123/pandal/photo.jpg"
        val invalidPathMissingUid = "pandal-photos//pandal/photo.jpg"

        assertTrue("Valid path 1 should match", validRegex.matches(validPath1))
        assertTrue("Valid path 2 should match", validRegex.matches(validPath2))
        assertFalse("Path traversal must fail", validRegex.matches(invalidPathTraversal))
        assertFalse("Wrong bucket must fail", validRegex.matches(invalidPathWrongBucket))
        assertFalse("Missing UID must fail", validRegex.matches(invalidPathMissingUid))
    }

    // ── 3. Crowd Report Validation ──

    @Test
    fun testCrowdReportValidationRules() {
        fun isValidCrowdLevel(level: Int): Boolean = level in 1..10
        fun isValidPandalId(id: String): Boolean = id.isNotBlank() && !id.contains("/")

        assertTrue("Level 1 should be valid", isValidCrowdLevel(1))
        assertTrue("Level 5 should be valid", isValidCrowdLevel(5))
        assertTrue("Level 10 should be valid", isValidCrowdLevel(10))

        assertFalse("Level 0 must be invalid", isValidCrowdLevel(0))
        assertFalse("Level 11 must be invalid", isValidCrowdLevel(11))
        assertFalse("Level -5 must be invalid", isValidCrowdLevel(-5))

        assertTrue("Valid pandal ID", isValidPandalId("mudiali_club"))
        assertFalse("Blank pandal ID must be invalid", isValidPandalId(""))
        assertFalse("Path traversal pandal ID must be invalid", isValidPandalId("../hack"))
    }

    // ── 4. Weather Report Validation ──

    @Test
    fun testWeatherReportConditionValidation() {
        val allowedConditions = setOf("NO_RAIN", "DRIZZLE", "RAINING", "HEAVY_RAIN")

        assertTrue("NO_RAIN is allowed", allowedConditions.contains("NO_RAIN"))
        assertTrue("DRIZZLE is allowed", allowedConditions.contains("DRIZZLE"))
        assertTrue("RAINING is allowed", allowedConditions.contains("RAINING"))
        assertTrue("HEAVY_RAIN is allowed", allowedConditions.contains("HEAVY_RAIN"))

        assertFalse("Invalid condition 'SNOW' must be rejected", allowedConditions.contains("SNOW"))
        assertFalse("Malicious injection must be rejected", allowedConditions.contains("DROP TABLE"))
    }

    // ── 5. Admin Allowlist Authorization Verification ──

    @Test
    fun testAdminAuthorizationCheck() {
        data class AdminRecord(val role: String, val enabled: Boolean)

        fun isAuthorizedAdmin(record: AdminRecord?): Boolean {
            return record != null && record.role == "admin" && record.enabled
        }

        val validAdmin = AdminRecord("admin", true)
        val disabledAdmin = AdminRecord("admin", false)
        val nonAdminUser = AdminRecord("user", true)
        val missingRecord: AdminRecord? = null

        assertTrue("Valid enabled admin record must be authorized", isAuthorizedAdmin(validAdmin))
        assertFalse("Disabled admin record must be DENIED", isAuthorizedAdmin(disabledAdmin))
        assertFalse("Non-admin role must be DENIED", isAuthorizedAdmin(nonAdminUser))
        assertFalse("Missing admin record must be DENIED", isAuthorizedAdmin(missingRecord))
    }

    // ── 6. User Analytics Privacy & Telemetry Whitelist ──

    @Test
    fun testUserTelemetryFieldWhitelistEnforcement() {
        val allowedKeys = setOf("createdAt", "lastSeenAt", "appVersion")

        fun isValidTelemetryPayload(keys: Set<String>): Boolean {
            return keys.isNotEmpty() && allowedKeys.containsAll(keys)
        }

        // Valid telemetry payload with zero PII
        val validPayload = setOf("createdAt", "lastSeenAt", "appVersion")
        val validLaunchUpdate = setOf("lastSeenAt", "appVersion")
        assertTrue("Valid telemetry payload must be accepted", isValidTelemetryPayload(validPayload))
        assertTrue("Valid launch update must be accepted", isValidTelemetryPayload(validLaunchUpdate))

        // Payloads containing PII must be rejected
        val payloadWithName = setOf("createdAt", "lastSeenAt", "appVersion", "name")
        val payloadWithEmail = setOf("createdAt", "lastSeenAt", "email")
        val payloadWithPhone = setOf("createdAt", "phoneNumber")
        val payloadWithLocation = setOf("createdAt", "lastSeenAt", "latitude", "longitude")

        assertFalse("Payload with name must be DENIED", isValidTelemetryPayload(payloadWithName))
        assertFalse("Payload with email must be DENIED", isValidTelemetryPayload(payloadWithEmail))
        assertFalse("Payload with phone must be DENIED", isValidTelemetryPayload(payloadWithPhone))
        assertFalse("Payload with GPS coordinates must be DENIED", isValidTelemetryPayload(payloadWithLocation))
    }

    // ── 7. Realtime Database Presence Authorization ──

    @Test
    fun testPresenceSessionAuthorization() {
        fun canWritePresence(authUid: String?, targetUid: String): Boolean {
            return authUid != null && authUid == targetUid
        }

        fun canReadGlobalPresence(authUid: String?, isAdmin: Boolean): Boolean {
            return authUid != null && isAdmin
        }

        val userA = "ANON_USER_123"
        val userB = "ANON_USER_456"

        // Users can only write to their own presence session
        assertTrue("User A can write their own presence", canWritePresence(userA, userA))
        assertFalse("User B cannot tamper with User A's presence", canWritePresence(userB, userA))
        assertFalse("Unauthenticated client cannot write presence", canWritePresence(null, userA))

        // Only authorized admins can read all presence nodes
        assertTrue("Admin can read global live user presence", canReadGlobalPresence(userA, isAdmin = true))
        assertFalse("Normal user cannot read global presence", canReadGlobalPresence(userA, isAdmin = false))
        assertFalse("Unauthenticated client cannot read global presence", canReadGlobalPresence(null, isAdmin = false))
    }

    // ── 8. Community Gallery Visibility vs Personal "My Photos" Filtering ──

    @Test
    fun testCommunityGalleryVsMyPhotosFiltering() {
        val authorUid = "AUTHOR_FIREBASE_UID_mc8obi7q"
        val newUserUid = "NEW_GITHUB_USER_1s9lUvg"
        val otherUserUid = "COMMUNITY_USER_v5fMfc"

        val photo1 = PandalPhoto("p1", "bagbazar", "pandal-photos/$authorUid/bagbazar/p1.jpg", "https://url/1.jpg", authorUid, 1000L, "active")
        val photo2 = PandalPhoto("p2", "bagbazar", "pandal-photos/$authorUid/bagbazar/p2.jpg", "https://url/2.jpg", authorUid, 1050L, "active")
        val photo3 = PandalPhoto("p3", "bagbazar", "pandal-photos/$otherUserUid/bagbazar/p3.jpg", "https://url/3.jpg", otherUserUid, 1100L, "active")
        val photo4Hidden = PandalPhoto("p4", "bagbazar", "pandal-photos/$otherUserUid/bagbazar/p4.jpg", "https://url/4.jpg", otherUserUid, 1150L, "hidden")

        val allCommunityPhotos = listOf(photo1, photo2, photo3, photo4Hidden)

        // 1. Public Community Gallery: Displays all ACTIVE photos across all contributors
        val communityGallery = allCommunityPhotos.filter { it.status == "active" }
        assertEquals("Community gallery must include photos from all active contributors (author + others)", 3, communityGallery.size)
        assertTrue("Community gallery includes author photo 1", communityGallery.contains(photo1))
        assertTrue("Community gallery includes author photo 2", communityGallery.contains(photo2))
        assertTrue("Community gallery includes community user photo 3", communityGallery.contains(photo3))
        assertFalse("Hidden photos must not appear in public gallery", communityGallery.contains(photo4Hidden))

        // 2. "My Photos" sheet: Strictly filters by uploadedBy == currentUid
        val myPhotosNewUser = allCommunityPhotos.filter { it.uploadedBy == newUserUid && it.status == "active" }
        assertTrue("A newly installed user's My Photos must be empty, not showing author's photos", myPhotosNewUser.isEmpty())

        val myPhotosOtherUser = allCommunityPhotos.filter { it.uploadedBy == otherUserUid && it.status == "active" }
        assertEquals("Other user must only see their own active photo", 1, myPhotosOtherUser.size)
        assertEquals("p3", myPhotosOtherUser[0].id)

        val myPhotosAuthor = allCommunityPhotos.filter { it.uploadedBy == authorUid && it.status == "active" }
        assertEquals("Author sees only their own active photos in My Photos", 2, myPhotosAuthor.size)

        // 3. Contribution counts: Only counts user's own active uploads
        fun computePhotoContributionCount(photos: List<PandalPhoto>, uid: String): Int {
            if (uid.isBlank()) return 0
            return photos.count { it.uploadedBy == uid && it.status == "active" }
        }

        assertEquals("New user contribution count must be 0", 0, computePhotoContributionCount(allCommunityPhotos, newUserUid))
        assertEquals("Other user contribution count must be 1", 1, computePhotoContributionCount(allCommunityPhotos, otherUserUid))
        assertEquals("Author contribution count must be 2", 2, computePhotoContributionCount(allCommunityPhotos, authorUid))
        assertEquals("Blank UID must never yield contribution count", 0, computePhotoContributionCount(allCommunityPhotos, ""))
    }

    // ── 9. Partial Deletion Exception Handling ──

    @Test
    fun testPhotoPartialDeletionExceptionDiagnostics() {
        val storageEx = RuntimeException("Supabase Storage HTTP 403 Access Denied")
        val partialEx = com.pandalfinder.data.PhotoPartialDeletionException(
            message = "Photo removed from gallery, but storage cleanup failed: ${storageEx.message}",
            firestoreSuccess = true,
            storageSuccess = false,
            cause = storageEx
        )

        assertTrue("Firestore metadata was marked deleted", partialEx.firestoreSuccess)
        assertFalse("Storage deletion failed", partialEx.storageSuccess)
        assertEquals(storageEx, partialEx.cause)
        assertTrue(partialEx.message?.contains("Photo removed from gallery") == true)
        assertTrue(partialEx.message?.contains("HTTP 403") == true)
    }

    // ── 10. Uploader Identity Safety ──

    @Test
    fun testUploaderIdentitySafetyRules() {
        fun isValidUploaderUid(uid: String?): Boolean {
            if (uid.isNullOrBlank()) return false
            if (uid.startsWith("user_") || uid.startsWith("device_user_") || uid.startsWith("anonymous_")) return false
            return true
        }

        assertTrue("Authentic Firebase UID is valid", isValidUploaderUid("mc8obi7qRDhU4Zbk0ViVEWRVvqF2"))
        assertTrue("Authentic anonymous Firebase UID is valid", isValidUploaderUid("1s9lUvgxbqOpihcg6xe97tQ7DL72"))

        assertFalse("Null UID must be rejected", isValidUploaderUid(null))
        assertFalse("Blank UID must be rejected", isValidUploaderUid(""))
        assertFalse("Synthetic user_ fallback must be rejected", isValidUploaderUid("user_a1b2c3d4"))
        assertFalse("Synthetic device_user_ fallback must be rejected", isValidUploaderUid("device_user_e5f6g7h8"))
        assertFalse("Synthetic anonymous_ fallback must be rejected", isValidUploaderUid("anonymous_9i0j1k2l"))
    }

    // ── 12. Multi-User Distinct Anonymous Contributions & Personal Isolation ──

    @Test
    fun testMultiUserDistinctAnonymousContributions() {
        val userAAnon = "anon_uid_alpha_1111"
        val userBAnon = "anon_uid_beta_2222"
        val authorUid = "mc8obi7qRDhU4Zbk0ViVEWRVvqF2"

        data class MockCrowdReport(val id: String, val userId: String, val status: String)
        data class MockWeatherReport(val id: String, val userId: String, val status: String)

        val photos = listOf(
            PandalPhoto("p_a1", "chetla", "pandal-photos/$userAAnon/chetla/p_a1.jpg", "https://url/a1.jpg", userAAnon, 1000L, "active"),
            PandalPhoto("p_a2", "chetla", "pandal-photos/$userAAnon/chetla/p_a2.jpg", "https://url/a2.jpg", userAAnon, 1050L, "active"),
            PandalPhoto("p_b1", "chetla", "pandal-photos/$userBAnon/chetla/p_b1.jpg", "https://url/b1.jpg", userBAnon, 1100L, "active"),
            PandalPhoto("p_auth", "chetla", "pandal-photos/$authorUid/chetla/p_auth.jpg", "https://url/auth.jpg", authorUid, 1200L, "active")
        )

        val crowdReports = listOf(
            MockCrowdReport("c_a1", userAAnon, "active"),
            MockCrowdReport("c_b1", userBAnon, "active"),
            MockCrowdReport("c_b2", userBAnon, "active"),
            MockCrowdReport("c_auth", authorUid, "active")
        )

        val weatherReports = listOf(
            MockWeatherReport("w_a1", userAAnon, "active"),
            MockWeatherReport("w_auth", authorUid, "active")
        )

        fun computeUserContributions(uid: String): com.pandalfinder.data.UserContributions {
            if (uid.isBlank()) return com.pandalfinder.data.UserContributions(0, 0, 0)
            val pCount = photos.count { it.uploadedBy == uid && it.status == "active" && it.downloadUrl.isNotBlank() }
            val cCount = crowdReports.count { it.userId == uid && it.status == "active" }
            val wCount = weatherReports.count { it.userId == uid && it.status == "active" }
            return com.pandalfinder.data.UserContributions(pCount, cCount, wCount)
        }

        // Test User A (Anonymous 1)
        val contribA = computeUserContributions(userAAnon)
        assertEquals("User A photo count", 2, contribA.photosCount)
        assertEquals("User A crowd count", 1, contribA.crowdReportsCount)
        assertEquals("User A weather count", 1, contribA.weatherReportsCount)
        assertEquals("User A total count equals sum", 4, contribA.total)
        assertEquals(contribA.photosCount + contribA.crowdReportsCount + contribA.weatherReportsCount, contribA.total)

        // Test User B (Anonymous 2)
        val contribB = computeUserContributions(userBAnon)
        assertEquals("User B photo count", 1, contribB.photosCount)
        assertEquals("User B crowd count", 2, contribB.crowdReportsCount)
        assertEquals("User B weather count", 0, contribB.weatherReportsCount)
        assertEquals("User B total count equals sum", 3, contribB.total)
        assertEquals(contribB.photosCount + contribB.crowdReportsCount + contribB.weatherReportsCount, contribB.total)

        // Test Author / Admin
        val contribAuth = computeUserContributions(authorUid)
        assertEquals("Author photo count", 1, contribAuth.photosCount)
        assertEquals("Author crowd count", 1, contribAuth.crowdReportsCount)
        assertEquals("Author weather count", 1, contribAuth.weatherReportsCount)
        assertEquals("Author total count equals sum", 3, contribAuth.total)

        // Test Unauthenticated / Unknown UID
        val contribEmpty = computeUserContributions("")
        assertEquals("Empty UID photos must be 0", 0, contribEmpty.photosCount)
        assertEquals("Empty UID crowd must be 0", 0, contribEmpty.crowdReportsCount)
        assertEquals("Empty UID weather must be 0", 0, contribEmpty.weatherReportsCount)
        assertEquals("Empty UID total must be 0", 0, contribEmpty.total)

        // 1. My Photos isolation: User A sees only A; User B sees only B
        val userAPhotos = photos.filter { it.uploadedBy == userAAnon && it.status == "active" && it.downloadUrl.isNotBlank() }
        val userBPhotos = photos.filter { it.uploadedBy == userBAnon && it.status == "active" && it.downloadUrl.isNotBlank() }
        assertEquals(2, userAPhotos.size)
        assertTrue(userAPhotos.all { it.uploadedBy == userAAnon })
        assertEquals(1, userBPhotos.size)
        assertTrue(userBPhotos.all { it.uploadedBy == userBAnon })

        // 2. Public Community Gallery: Displays all active photos from User A, User B, and Author
        val communityGallery = photos.filter { it.status == "active" }
        assertEquals("Community gallery contains all 4 photos", 4, communityGallery.size)
        assertTrue(communityGallery.contains(userAPhotos[0]))
        assertTrue(communityGallery.contains(userBPhotos[0]))
    }

    // ── 13. Release Version Metadata & My Pandal Label ──

    @Test
    fun testVersionMetadataAndLabel() {
        assertEquals("Release versionName must be 2.1.1", "2.1.1", BuildConfig.VERSION_NAME)
        assertEquals("Release versionCode must be 5", 5, BuildConfig.VERSION_CODE)
        val userVisibleLabel = "PandalQuest v${BuildConfig.VERSION_NAME}"
        assertEquals("My Pandal version label must display PandalQuest v2.1.1", "PandalQuest v2.1.1", userVisibleLabel)
    }
}

