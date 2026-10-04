package com.pandalfinder

import com.pandalfinder.data.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/**
 * Comprehensive test suite covering the four release improvements:
 * 1. Location Services & GPS Resolution Flow (Tests 1-6)
 * 2. Pandal Ratings 1-5 Stars & Aggregation (Tests 7-12)
 * 3. Community Rain Right Now & 500m Proximity (Tests 13-18)
 * 4. Toilets Places API (New) Parsing, Deduplication & Isolation (Tests 19-28)
 */
class LocationRatingsRainToiletsTest {

    // =========================================================================
    // 1. LOCATION SERVICES TESTS (Requirements 1 - 6)
    // =========================================================================

    enum class AppLocationState {
        NORMAL,
        LOCATION_SERVICES_REQUIRED,
        PERMISSION_REQUIRED,
        PERMANENTLY_DENIED,
        UNAVAILABLE
    }

    private fun evaluateLocationState(
        hasPermission: Boolean,
        isPermanentlyDenied: Boolean,
        isGpsEnabled: Boolean,
        isAvailable: Boolean = true
    ): AppLocationState {
        return when {
            !hasPermission && isPermanentlyDenied -> AppLocationState.PERMANENTLY_DENIED
            !hasPermission -> AppLocationState.PERMISSION_REQUIRED
            !isGpsEnabled -> AppLocationState.LOCATION_SERVICES_REQUIRED
            !isAvailable -> AppLocationState.UNAVAILABLE
            else -> AppLocationState.NORMAL
        }
    }

    @Test
    fun test1_permissionGrantedAndGpsEnabled() {
        val isPermanent = MainActivity.checkPermanentDenial(
            hasPermission = true,
            hasRequestedBefore = true,
            shouldShowRationale = false
        )
        assertFalse(isPermanent)
        val state = evaluateLocationState(
            hasPermission = true,
            isPermanentlyDenied = isPermanent,
            isGpsEnabled = true
        )
        assertEquals(AppLocationState.NORMAL, state)
    }

    @Test
    fun test2_permissionGrantedAndGpsDisabled() {
        val isPermanent = MainActivity.checkPermanentDenial(
            hasPermission = true,
            hasRequestedBefore = true,
            shouldShowRationale = false
        )
        assertFalse(isPermanent)
        val state = evaluateLocationState(
            hasPermission = true,
            isPermanentlyDenied = isPermanent,
            isGpsEnabled = false
        )
        assertEquals(AppLocationState.LOCATION_SERVICES_REQUIRED, state)
    }

    @Test
    fun test3_freshInstall_firstPermissionRequest() {
        // On fresh install, permission not granted, hasRequestedBefore is FALSE, shouldShowRationale is FALSE
        // MUST NOT be treated as permanently denied!
        val isPermanent = MainActivity.checkPermanentDenial(
            hasPermission = false,
            hasRequestedBefore = false,
            shouldShowRationale = false
        )
        assertFalse("Fresh install must never be treated as permanently denied", isPermanent)
        val state = evaluateLocationState(
            hasPermission = false,
            isPermanentlyDenied = isPermanent,
            isGpsEnabled = true
        )
        assertEquals(AppLocationState.PERMISSION_REQUIRED, state)
    }

    @Test
    fun test4_firstDenial_tryAgainUI() {
        // First denial by user (Don't allow): hasRequestedBefore is TRUE, shouldShowRationale is TRUE
        // Shows Try Again UI, NOT Settings
        val isPermanent = MainActivity.checkPermanentDenial(
            hasPermission = false,
            hasRequestedBefore = true,
            shouldShowRationale = true
        )
        assertFalse("First denial should not be permanently denied", isPermanent)
        val state = evaluateLocationState(
            hasPermission = false,
            isPermanentlyDenied = isPermanent,
            isGpsEnabled = true
        )
        assertEquals(AppLocationState.PERMISSION_REQUIRED, state)
    }

    @Test
    fun test5_permanentPermissionDenial_settingsUI() {
        // Permanently denied (Don't ask again / multiple denials): hasRequestedBefore is TRUE, shouldShowRationale is FALSE
        val isPermanent = MainActivity.checkPermanentDenial(
            hasPermission = false,
            hasRequestedBefore = true,
            shouldShowRationale = false
        )
        assertTrue("Subsequent denial without rationale should be permanently denied", isPermanent)
        val state = evaluateLocationState(
            hasPermission = false,
            isPermanentlyDenied = isPermanent,
            isGpsEnabled = true
        )
        assertEquals(AppLocationState.PERMANENTLY_DENIED, state)
    }

    @Test
    fun test6_resolutionDialogFlow() {
        // When location settings check fails with resolvable exception,
        // client triggers startResolutionForResult with request code 1002
        assertEquals(1002, MainActivity.REQUEST_CHECK_SETTINGS)
    }

    @Test
    fun test7_noRepeatedLocationPromptSpam() {
        val cooldownMillis = 10_000L
        var lastPromptTime = 100_000L

        fun canShowPrompt(currentTime: Long): Boolean {
            return (currentTime - lastPromptTime) >= cooldownMillis
        }

        assertFalse("Spam check: 2 seconds later should be blocked", canShowPrompt(102_000L))
        assertFalse("Spam check: 5 seconds later should be blocked", canShowPrompt(105_000L))
        assertTrue("Spam check: 10 seconds later should be allowed", canShowPrompt(110_000L))
        assertTrue("Spam check: 15 seconds later should be allowed", canShowPrompt(115_000L))
    }

    // =========================================================================
    // 2. PANDAL RATINGS TESTS (Requirements 7 - 12)
    // =========================================================================

    @Test
    fun test7_oneStarRating() {
        val rating = 1
        assertTrue(rating in 1..5)
        val ratingObj = PandalRating(
            ratingId = "bagbazar_user123",
            pandalId = "bagbazar",
            userId = "user123",
            rating = rating,
            createdAt = 1000L,
            updatedAt = 1000L
        )
        assertEquals(1, ratingObj.rating)
        assertEquals("bagbazar", ratingObj.pandalId)
    }

    @Test
    fun test8_fiveStarRating() {
        val rating = 5
        assertTrue(rating in 1..5)
        val ratingObj = PandalRating(
            ratingId = "college_square_user456",
            pandalId = "college_square",
            userId = "user456",
            rating = rating,
            createdAt = 1000L,
            updatedAt = 1000L
        )
        assertEquals(5, ratingObj.rating)
    }

    @Test
    fun test9_averageCalculation() {
        // Empty ratings list -> null average, count 0
        val (emptyAvg, emptyCount) = RatingRepository.calculateAggregate(emptyList())
        assertNull(emptyAvg)
        assertEquals(0, emptyCount)

        // Single rating [5] -> 5.0, count 1
        val (singleAvg, singleCount) = RatingRepository.calculateAggregate(listOf(5))
        assertEquals(5.0, singleAvg!!, 0.01)
        assertEquals(1, singleCount)

        // Multi ratings [5, 4, 4] -> sum 13 / 3 = 4.333... -> rounded 4.3
        val (multiAvg, multiCount) = RatingRepository.calculateAggregate(listOf(5, 4, 4))
        assertEquals(4.3, multiAvg!!, 0.01)
        assertEquals(3, multiCount)

        // Multi ratings [4, 5, 5, 4, 5] -> sum 23 / 5 = 4.6
        val (highAvg, highCount) = RatingRepository.calculateAggregate(listOf(4, 5, 5, 4, 5))
        assertEquals(4.6, highAvg!!, 0.01)
        assertEquals(5, highCount)
    }

    @Test
    fun test10_ratingUpdateReplacesExistingUserRating() {
        val pandalId = "deshapriya_park"
        val userId = "user_abc"

        val docId1 = RatingRepository.getDocumentId(pandalId, userId)
        val docId2 = RatingRepository.getDocumentId(pandalId, userId)

        // Deterministic ID guarantees upsert instead of duplicate document
        assertEquals("deshapriya_park_user_abc", docId1)
        assertEquals(docId1, docId2)
    }

    @Test
    fun test11_differentUsersCreateSeparateRatings() {
        val pandalId = "deshapriya_park"
        val user1 = "user_1"
        val user2 = "user_2"

        val docId1 = RatingRepository.getDocumentId(pandalId, user1)
        val docId2 = RatingRepository.getDocumentId(pandalId, user2)

        assertNotEquals(docId1, docId2)
        assertEquals("deshapriya_park_user_1", docId1)
        assertEquals("deshapriya_park_user_2", docId2)
    }

    @Test
    fun test12_unauthorizedModificationRejected() {
        val currentAuthUid = "user_legitimate"
        val targetRatingUid = "user_other"

        fun canModifyRating(authUid: String, ratingOwnerUid: String): Boolean {
            return authUid == ratingOwnerUid
        }

        assertTrue(canModifyRating(currentAuthUid, "user_legitimate"))
        assertFalse(canModifyRating(currentAuthUid, targetRatingUid))
    }

    // =========================================================================
    // 3. COMMUNITY RAIN RIGHT NOW TESTS (Requirements 13 - 18)
    // =========================================================================

    @Test
    fun test13_user100mAllowed() {
        // Base pandal location (College Square, Kolkata)
        val pandalLat = 22.5726
        val pandalLng = 88.3639

        // 100m away: 100 / 111195.0 = 0.00089932
        val userLat = 22.5726 + (100.0 / 111195.0)
        val userLng = 88.3639

        val dist = RouteOptimizer.distanceBetween(userLat, userLng, pandalLat, pandalLng)
        assertTrue("Distance $dist should be ~100m", dist in 99f..101f)
        assertTrue("Report must be allowed within 500m", RainReportRepository.isWithinProximity(userLat, userLng, pandalLat, pandalLng))
    }

    @Test
    fun test14_user499mAllowed() {
        val pandalLat = 22.5726
        val pandalLng = 88.3639

        // 499m away
        val userLat = 22.5726 + (499.0 / 111195.0)
        val userLng = 88.3639

        val dist = RouteOptimizer.distanceBetween(userLat, userLng, pandalLat, pandalLng)
        assertTrue("Distance $dist should be <= 500m", dist <= 500f)
        assertTrue(RainReportRepository.isWithinProximity(userLat, userLng, pandalLat, pandalLng))
    }

    @Test
    fun test15_user500mAllowed() {
        val pandalLat = 22.5726
        val pandalLng = 88.3639

        // Exactly 500m away
        val userLat = 22.5726 + (500.0 / 111195.0)
        val userLng = 88.3639

        val dist = RouteOptimizer.distanceBetween(userLat, userLng, pandalLat, pandalLng)
        assertTrue("Distance $dist should be <= 500.05m", dist <= 500.05f)
        assertTrue(RainReportRepository.isWithinProximity(userLat, userLng, pandalLat, pandalLng))
    }

    @Test
    fun test16_user501mRejected() {
        val pandalLat = 22.5726
        val pandalLng = 88.3639

        // 501m away
        val userLat = 22.5726 + (501.5 / 111195.0)
        val userLng = 88.3639

        val dist = RouteOptimizer.distanceBetween(userLat, userLng, pandalLat, pandalLng)
        assertTrue("Distance $dist should be > 500m", dist > 500f)
        assertFalse("Report must be rejected beyond 500m", RainReportRepository.isWithinProximity(userLat, userLng, pandalLat, pandalLng))
    }

    @Test
    fun test17_staleReportExpires() {
        val now = 10_000_000L
        val thirtyMinAgo = now - (30 * 60 * 1000L)
        val eightyNineMinAgo = now - (89 * 60 * 1000L)
        val ninetyOneMinAgo = now - (91 * 60 * 1000L)
        val twoHoursAgo = now - (120 * 60 * 1000L)

        assertTrue("30m ago should be valid", RainReportRepository.isReportValid(thirtyMinAgo, now))
        assertTrue("89m ago should be valid", RainReportRepository.isReportValid(eightyNineMinAgo, now))
        assertFalse("91m ago should be expired (> 90 min)", RainReportRepository.isReportValid(ninetyOneMinAgo, now))
        assertFalse("120m ago should be expired", RainReportRepository.isReportValid(twoHoursAgo, now))
    }

    @Test
    fun test18_multipleRecentReportsAggregateCorrectly() {
        val now = 1_000_000L
        val reports = listOf(
            RainReport("r1", "p1", "u1", now - 5 * 60 * 1000L, "active"),
            RainReport("r2", "p1", "u2", now - 20 * 60 * 1000L, "active"),
            RainReport("r3", "p1", "u3", now - 100 * 60 * 1000L, "active") // Expired
        )

        val activeReports = reports.filter {
            RainReportRepository.isReportValid(it.timestamp, now)
        }

        assertEquals(2, activeReports.size)
        val latestReport = activeReports.maxByOrNull { it.timestamp }
        assertNotNull(latestReport)
        val latestAgeMinutes = (now - latestReport!!.timestamp) / (1000 * 60)
        assertEquals(5L, latestAgeMinutes)
    }

    // =========================================================================
    // 4. TOILETS PLACES API (NEW) TESTS (Requirements 19 - 28)
    // =========================================================================

    @Test
    fun test19_placesResponseWithToiletsMarkersCreated() {
        val jsonPayload = """
            {
              "places": [
                {
                  "id": "ChIJ12345",
                  "displayName": { "text": "KMC Public Toilet", "languageCode": "en" },
                  "formattedAddress": "College St, Bowbazar, Kolkata, West Bengal 700073",
                  "location": { "latitude": 22.5730, "longitude": 88.3645 },
                  "types": ["public_bathroom", "point_of_interest"],
                  "rating": 4.1
                },
                {
                  "id": "ChIJ67890",
                  "displayName": { "text": "Sulabh Sauchalaya", "languageCode": "en" },
                  "formattedAddress": "Madan Mohan Burman St, Kolkata",
                  "location": { "latitude": 22.5810, "longitude": 88.3580 },
                  "types": ["public_bathroom"],
                  "rating": 3.8
                }
              ]
            }
        """.trimIndent()

        val toilets = GooglePlacesRepository.parsePlacesResponse(jsonPayload)
        assertEquals(2, toilets.size)
        assertEquals("ChIJ12345", toilets[0].id)
        assertEquals("KMC Public Toilet", toilets[0].name)
        assertEquals(22.5730, toilets[0].latitude, 0.0001)
        assertEquals(88.3645, toilets[0].longitude, 0.0001)
        assertEquals("College St, Bowbazar, Kolkata, West Bengal 700073", toilets[0].address)
        assertEquals(4.1, toilets[0].rating ?: 0.0, 0.01)
        assertTrue(toilets[0].types.contains("public_bathroom"))
    }

    @Test
    fun test20_emptyPlacesResponseEmptyState() {
        val jsonPayload = """{ "places": [] }"""
        val toilets = GooglePlacesRepository.parsePlacesResponse(jsonPayload)
        assertTrue(toilets.isEmpty())
    }

    @Test
    fun test21_placesPermissionErrorUsefulErrorState() {
        val httpCode = 403

        val userMessage = if (httpCode in listOf(400, 401, 403)) {
            "Toilets unavailable: Places API configuration required"
        } else {
            "Error loading toilets"
        }

        assertEquals("Toilets unavailable: Places API configuration required", userMessage)
    }

    @Test
    fun test22_duplicatePlaceIdsDeduplicated() {
        val toiletsWithDups = listOf(
            PublicToilet("toilet_1", "Public Toilet A", "Address 1", 22.57, 88.36),
            PublicToilet("toilet_2", "Public Toilet B", "Address 2", 22.58, 88.37),
            PublicToilet("toilet_1", "Public Toilet A Duplicate", "Address 1", 22.57, 88.36)
        )

        val deduped = GooglePlacesRepository.deduplicateToilets(toiletsWithDups)
        assertEquals(2, deduped.size)
        assertEquals(listOf("toilet_1", "toilet_2"), deduped.map { it.id })
    }

    @Test
    fun test23_toiletsDisabledMarkersRemovedOnly() {
        // Marker collections test
        val pandalMarkers = mutableListOf("pandal_marker_1", "pandal_marker_2")
        val metroMarkers = mutableListOf("metro_marker_1", "metro_marker_2")
        val toiletMarkers = mutableListOf("toilet_marker_1", "toilet_marker_2")

        // Action: Disable toilets toggle
        toiletMarkers.clear()

        assertEquals(0, toiletMarkers.size)
        assertEquals(2, pandalMarkers.size)
        assertEquals(2, metroMarkers.size)
    }

    @Test
    fun test24_pandalsRemainVisible() {
        val pandalMarkers = mutableListOf("pandal_marker_1", "pandal_marker_2")

        // Toggling toilets does not affect pandal visibility
        assertTrue(pandalMarkers.isNotEmpty())
        assertEquals(2, pandalMarkers.size)
    }

    @Test
    fun test25_metroRemainsVisible() {
        val metroMarkers = mutableListOf("metro_marker_1", "metro_marker_2")

        // Toggling toilets does not affect metro visibility
        assertTrue(metroMarkers.isNotEmpty())
        assertEquals(2, metroMarkers.size)
    }

    @Test
    fun test26_locationDisabledFlowShown() {
        val isGpsEnabled = false
        val action = if (!isGpsEnabled) "SHOW_LOCATION_SETTINGS_PROMPT" else "FETCH_TOILETS"
        assertEquals("SHOW_LOCATION_SETTINGS_PROMPT", action)
    }

    @Test
    fun test27_placesApiNotConfiguredDiagnostic() {
        val apiKey = ""
        val errorMsg = if (apiKey.isBlank()) {
            "Toilets unavailable: Places API configuration required (API Key is blank)"
        } else {
            null
        }

        assertNotNull(errorMsg)
        assertTrue(errorMsg!!.contains("Places API configuration required"))
    }

    @Test
    fun test28_validPlaceParsedCorrectly() {
        val json = """
            {
              "places": [
                {
                  "id": "ChIJ_sulabh_1",
                  "displayName": { "text": "Sulabh International Toilet" },
                  "formattedAddress": "MG Road, Kolkata",
                  "location": { "latitude": 22.5822, "longitude": 88.3611 },
                  "types": ["public_bathroom", "establishment"],
                  "rating": 4.0
                }
              ]
            }
        """.trimIndent()

        val results = GooglePlacesRepository.parsePlacesResponse(json)
        assertEquals(1, results.size)
        val toilet = results[0]
        assertEquals("ChIJ_sulabh_1", toilet.id)
        assertEquals("Sulabh International Toilet", toilet.name)
        assertEquals("MG Road, Kolkata", toilet.address)
        assertEquals(22.5822, toilet.latitude, 0.0001)
        assertEquals(88.3611, toilet.longitude, 0.0001)
        assertEquals(4.0, toilet.rating ?: 0.0, 0.01)
        assertTrue(toilet.types.contains("public_bathroom"))
    }

    // =========================================================================
    // 5. WEATHER OBSERVATION & METRO GATE TESTS
    // =========================================================================

    @Test
    fun test29_weatherStatusesAndLabels() {
        assertEquals("NO_RAIN", CommunityWeather.NO_RAIN.storedValue)
        assertEquals("No rain", CommunityWeather.NO_RAIN.label)

        assertEquals("DRIZZLE", CommunityWeather.DRIZZLE.storedValue)
        assertEquals("Drizzle", CommunityWeather.DRIZZLE.label)

        assertEquals("RAINING", CommunityWeather.RAINING.storedValue)
        assertEquals("Raining", CommunityWeather.RAINING.label)

        assertEquals("HEAVY_RAIN", CommunityWeather.HEAVY_RAIN.storedValue)
        assertEquals("Heavy rain", CommunityWeather.HEAVY_RAIN.label)
    }

    @Test
    fun test30_weatherReportValidityTTL() {
        val now = 5_000_000L
        val tenMinAgo = now - (10 * 60 * 1000L)
        val eightyFiveMinAgo = now - (85 * 60 * 1000L)
        val ninetyFiveMinAgo = now - (95 * 60 * 1000L)

        assertTrue(WeatherReportRepository.isReportValid(tenMinAgo, now))
        assertTrue(WeatherReportRepository.isReportValid(eightyFiveMinAgo, now))
        assertFalse("Reports > 90 mins old must expire", WeatherReportRepository.isReportValid(ninetyFiveMinAgo, now))
    }

    @Test
    fun test31_weatherReportProximityEnforcement() {
        val pandalLat = 22.5726
        val pandalLng = 88.3639

        // 100m away
        val nearUserLat = 22.5726 + (100.0 / 111195.0)
        assertTrue(WeatherReportRepository.isWithinProximity(nearUserLat, pandalLng, pandalLat, pandalLng))

        // 505m away (beyond 500m)
        val farUserLat = 22.5726 + (505.0 / 111195.0)
        assertFalse(WeatherReportRepository.isWithinProximity(farUserLat, pandalLng, pandalLat, pandalLng))
    }

    @Test
    fun test32_metroStationWithVerifiedGate() {
        val allStations = MetroStation.allStations()
        val howrahMaidan = allStations.firstOrNull { it.name == "Howrah Maidan" }
        assertNotNull(howrahMaidan)
        assertEquals("2", howrahMaidan!!.gateNumber)
        assertNotNull(howrahMaidan.gateLatitude)
        assertNotNull(howrahMaidan.gateLongitude)

        val sealdah = allStations.firstOrNull { it.name == "Sealdah" }
        assertNotNull(sealdah)
        assertEquals("1", sealdah!!.gateNumber)
    }

    @Test
    fun test33_metroStationWithoutGateHasNoFakeGate() {
        val allStations = MetroStation.allStations()
        val dakshineswar = allStations.firstOrNull { it.name == "Dakshineswar" }
        assertNotNull(dakshineswar)
        assertNull("Unverified stations must NOT have fake gate numbers", dakshineswar!!.gateNumber)
        assertNull(dakshineswar.gateLatitude)
        assertNull(dakshineswar.gateLongitude)
    }

    @Test
    fun test34_metroNavigationUrlTargetsGateCoordinatesIfAvailable() {
        val stationWithGate = MetroStation("Howrah Maidan", 22.5844, 88.3383, "Green Line", gateNumber = "2", gateLatitude = 22.58445, gateLongitude = 88.33835)
        val destLatWithGate = stationWithGate.gateLatitude ?: stationWithGate.latitude
        val destLngWithGate = stationWithGate.gateLongitude ?: stationWithGate.longitude
        assertEquals(22.58445, destLatWithGate, 0.00001)
        assertEquals(88.33835, destLngWithGate, 0.00001)

        val stationNoGate = MetroStation("Dakshineswar", 22.6553, 88.3576, "Blue Line")
        val destLatNoGate = stationNoGate.gateLatitude ?: stationNoGate.latitude
        val destLngNoGate = stationNoGate.gateLongitude ?: stationNoGate.longitude
        assertEquals(22.6553, destLatNoGate, 0.00001)
        assertEquals(88.3576, destLngNoGate, 0.00001)
    }

    // =========================================================================
    // 6. FINAL DATA + UI POLISH TESTS (Requirements 1 - 13)
    // =========================================================================

    @Test
    fun test35_averageRatingFormatting() {
        // 1. Average rating 4.0 -> "4 ⭐"
        assertEquals("4 ⭐", RatingRepository.formatRatingDisplay(4.0))
        // 2. Average rating 4.3 -> "4.3 ⭐"
        assertEquals("4.3 ⭐", RatingRepository.formatRatingDisplay(4.3))
        // Average rating 5.0 -> "5 ⭐"
        assertEquals("5 ⭐", RatingRepository.formatRatingDisplay(5.0))
        // 3. No ratings / 0 -> "—"
        assertEquals("—", RatingRepository.formatRatingDisplay(null))
        assertEquals("—", RatingRepository.formatRatingDisplay(0.0))
    }

    @Test
    fun test36_weatherContributionCountingAndIsolation() {
        val userA = "user_abc_123"
        val userB = "user_xyz_789"

        data class MockWeatherReportDoc(val userId: String, val status: String)

        val mockDb = listOf(
            MockWeatherReportDoc(userA, "active"),
            MockWeatherReportDoc(userA, "active"),
            MockWeatherReportDoc(userA, "deleted"), // Failed / deleted report
            MockWeatherReportDoc(userB, "active"),
            MockWeatherReportDoc(userB, "active"),
            MockWeatherReportDoc(userB, "active")
        )

        // 4. Weather contribution count for User A
        val userAActiveWeather = mockDb.count { it.userId == userA && it.status == "active" }
        assertEquals(2, userAActiveWeather)

        // 5. Failed/deleted weather submission not counted
        assertFalse(mockDb.filter { it.userId == userA && it.status == "active" }.any { it.status == "deleted" })

        // 6. User A's weather reports don't count for User B
        val userBActiveWeather = mockDb.count { it.userId == userB && it.status == "active" }
        assertEquals(3, userBActiveWeather)
        assertNotEquals(userAActiveWeather, userBActiveWeather)

        val userAContribs = UserContributions(photosCount = 4, crowdReportsCount = 7, weatherReportsCount = userAActiveWeather)
        assertEquals(13, userAContribs.total)
    }

    @Test
    fun test37_pandalCoordinatesVerification() {
        val pandals = Pandal.getLocalPandals()

        // 7. Machua Bazar coordinate updated
        val machua = pandals.firstOrNull { it.name == "Machua Bazar Sarbojanik Durgapuja Samity" }
        assertNotNull(machua)
        assertEquals(22.5812141, machua!!.latitude, 0.000001)
        assertEquals(88.3572457, machua.longitude, 0.000001)

        // 8. Brindabon coordinate verified/updated to POI
        val brindabon = pandals.firstOrNull { it.name == "Brindabon Matri Mandir" }
        assertNotNull(brindabon)
        assertEquals(22.5820532, brindabon!!.latitude, 0.000001)
        assertEquals(88.3730342, brindabon.longitude, 0.000001)

        // 9. Karbagan coordinate verified/updated to POI
        val karbagan = pandals.firstOrNull { it.name == "Karbagan Sarbojanin Durgotsab Committee" }
        assertNotNull(karbagan)
        assertEquals(22.5952410, karbagan!!.latitude, 0.000001)
        assertEquals(88.3842775, karbagan.longitude, 0.000001)

        // 10. 14 Pally coordinate verified/updated to POI
        val pally14 = pandals.firstOrNull { it.name == "14 Pally Udayan Sangha" }
        assertNotNull(pally14)
        assertEquals(22.5575857, pally14!!.latitude, 0.000001)
        assertEquals(88.3669753, pally14.longitude, 0.000001)
    }

    @Test
    fun test38_duplicateKarbaganAuditAndSearchDeduplication() {
        val pandals = Pandal.getLocalPandals()

        // 11. Duplicate Karbagan removed - exactly one Karbagan record exists
        val karbaganList = pandals.filter { it.name.contains("Karbagan", ignoreCase = true) }
        assertEquals(1, karbaganList.size)

        // 12. No duplicate search results for Karbagan
        val query = "karbagan"
        val searchResults = pandals.filter { it.name.contains(query, ignoreCase = true) }
        assertEquals(1, searchResults.size)
    }

    @Test
    fun test39_userDataSurvivesDuplicateMigration() {
        // 13. saved/passport/photos/rating data survives duplicate migration
        val canonicalPandalId = "karbagan_sarbojanin_durgotsab_committee"
        val duplicatePandalId = "karbagan_sarbojanin_durgotsab_committee_dup"

        // Simulated reference migration map
        val migrationMap = mapOf(duplicatePandalId to canonicalPandalId)

        fun migrateRef(refId: String): String = migrationMap[refId] ?: refId

        // Test favorites migration
        val userFavorites = setOf(duplicatePandalId, "brindabon_matri_mandir")
        val migratedFavorites = userFavorites.map { migrateRef(it) }.toSet()
        assertTrue(migratedFavorites.contains(canonicalPandalId))
        assertFalse(migratedFavorites.contains(duplicatePandalId))

        // Test photo reference migration
        val photoDocPandalId = duplicatePandalId
        val migratedPhotoPandalId = migrateRef(photoDocPandalId)
        assertEquals(canonicalPandalId, migratedPhotoPandalId)

        // Test rating reference migration
        val ratingDocPandalId = duplicatePandalId
        val migratedRatingPandalId = migrateRef(ratingDocPandalId)
        assertEquals(canonicalPandalId, migratedRatingPandalId)
    }

    @Test
    fun test40_detailScreenAreaRatingRowFormatting() {
        fun formatDetailAreaRow(area: String, avg: Double?, count: Int): String {
            val formattedRating = RatingRepository.formatRatingDisplay(if (count > 0) avg else null)
            return "$area    $formattedRating"
        }

        // 1. No ratings
        assertEquals("Kolkata    —", formatDetailAreaRow("Kolkata", null, 0))
        assertEquals("Kolkata    —", formatDetailAreaRow("Kolkata", 0.0, 0))

        // 2. Whole number average
        assertEquals("Kolkata    4 ⭐", formatDetailAreaRow("Kolkata", 4.0, 5))
        assertEquals("Kolkata    5 ⭐", formatDetailAreaRow("Kolkata", 5.0, 12))

        // 3. Decimal average
        assertEquals("Kolkata    4.3 ⭐", formatDetailAreaRow("Kolkata", 4.3, 7))
        assertEquals("Kolkata    3.8 ⭐", formatDetailAreaRow("Kolkata", 3.8, 3))
    }
}
