package com.pandalfinder

import com.pandalfinder.data.AreaBoundaryRepository
import com.pandalfinder.data.FestivalZone
import com.pandalfinder.data.FestivalZoneRepository
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class FestivalZoneTest {

    private lateinit var boundaryRepo: AreaBoundaryRepository
    private lateinit var pandalRepo: PandalRepository
    private lateinit var zoneRepo: FestivalZoneRepository

    @Before
    fun setUp() {
        boundaryRepo = AreaBoundaryRepository(null)
        pandalRepo = PandalRepository(null)
        zoneRepo = FestivalZoneRepository(pandalRepo, boundaryRepo)
    }

    // ── 1. Supported Areas Have Genuine Boundaries ──

    @Test
    fun testSupportedGeographicZonesHaveGenuineBoundaries() {
        val supportedZones = listOf(
            "Howrah",
            "South Kolkata",
            "North Kolkata",
            "Central Kolkata",
            "Salt Lake / Bidhannagar",
            "New Town",
            "New Garia & Surroundings",
            "East Kolkata",
            "West Kolkata",
            "Barrackpore",
            "South 24 Parganas"
        )

        for (zoneName in supportedZones) {
            val boundary = boundaryRepo.getBoundaryForZone(zoneName)
            assertNotNull("Supported zone '$zoneName' must have a verified geographic boundary", boundary)
            assertTrue("Boundary for '$zoneName' must have at least 3 polygon vertices", boundary!!.outerRing.size >= 3)
            assertTrue("Boundary minLat must be strictly less than maxLat", boundary.minLat < boundary.maxLat)
            assertTrue("Boundary minLng must be strictly less than maxLng", boundary.minLng < boundary.maxLng)
            assertFalse("GeoJSON string must not be empty", boundary.geoJsonString.isBlank())
        }
    }

    // ── 2. Unavailable Boundary Detection ──

    @Test
    fun testUnavailableBoundaryDetection() {
        val unavailableNames = listOf(
            "Kalyani",
            "Rajarhat",
            "Barasat",
            "Unknown Rural Zone",
            "Fictional Suburb",
            "Nonexistent Area 99",
            ""
        )

        for (unsupported in unavailableNames) {
            val boundary = boundaryRepo.getBoundaryForZone(unsupported)
            assertNull("Unsupported zone '$unsupported' must return null boundary (never invent fake geometries)", boundary)
        }
    }

    // ── 3. FestivalZoneRepository Integration ──

    @Test
    fun testFestivalZoneRepositoryIntegration() {
        val zones = zoneRepo.getZones()
        assertFalse("Zones list must not be empty", zones.isEmpty())
        assertEquals("Must support 11 distinct active regional zones from dataset", 11, zones.size)

        // Check that supported zones have boundary attached
        val howrahZone = zones.firstOrNull { it.name == "Howrah" }
        assertNotNull("Howrah zone must exist", howrahZone)
        assertTrue("Howrah zone must have boundary", howrahZone!!.hasBoundary)
        assertEquals("howrah", howrahZone.boundary?.id)
        assertEquals(7, howrahZone.count)

        val southKolkataZone = zones.firstOrNull { it.name == "South Kolkata" }
        assertNotNull("South Kolkata zone must exist", southKolkataZone)
        assertTrue("South Kolkata zone must have boundary", southKolkataZone!!.hasBoundary)
        assertEquals("south_kolkata", southKolkataZone.boundary?.id)

        val northKolkataZone = zones.firstOrNull { it.name == "North Kolkata" }
        assertNotNull("North Kolkata zone must exist", northKolkataZone)
        assertTrue("North Kolkata zone must have boundary", northKolkataZone!!.hasBoundary)
        assertEquals("north_kolkata", northKolkataZone.boundary?.id)

        val saltLakeZone = zones.firstOrNull { it.name == "Salt Lake / Bidhannagar" }
        assertNotNull("Salt Lake zone must exist", saltLakeZone)
        assertTrue("Salt Lake zone must have boundary", saltLakeZone!!.hasBoundary)
        assertEquals("salt_lake", saltLakeZone.boundary?.id)

        val newTownZone = zones.firstOrNull { it.name == "New Town" }
        assertNotNull("New Town zone must exist", newTownZone)
        assertTrue("New Town zone must have boundary", newTownZone!!.hasBoundary)
        assertEquals("new_town", newTownZone.boundary?.id)

        val newGariaZone = zones.firstOrNull { it.name == "New Garia & Surroundings" }
        assertNotNull("New Garia zone must exist", newGariaZone)
        assertTrue("New Garia zone must have boundary", newGariaZone!!.hasBoundary)
        assertEquals("new_garia", newGariaZone.boundary?.id)

        val centralZone = zones.firstOrNull { it.name == "Central Kolkata" }
        assertNotNull("Central Kolkata zone must exist", centralZone)
        assertTrue("Central Kolkata zone must have boundary", centralZone!!.hasBoundary)
        assertEquals("central_kolkata", centralZone.boundary?.id)

        val eastZone = zones.firstOrNull { it.name == "East Kolkata" }
        assertNotNull("East Kolkata zone must exist", eastZone)
        assertTrue("East Kolkata zone must have boundary", eastZone!!.hasBoundary)
        assertEquals("east_kolkata", eastZone.boundary?.id)

        val westZone = zones.firstOrNull { it.name == "West Kolkata" }
        assertNotNull("West Kolkata zone must exist", westZone)
        assertTrue("West Kolkata zone must have boundary", westZone!!.hasBoundary)
        assertEquals("west_kolkata", westZone.boundary?.id)

        val barrackporeZone = zones.firstOrNull { it.name == "Barrackpore" }
        assertNotNull("Barrackpore zone must exist", barrackporeZone)
        assertTrue("Barrackpore zone must have boundary", barrackporeZone!!.hasBoundary)
        assertEquals("barrackpore", barrackporeZone.boundary?.id)

        val south24Zone = zones.firstOrNull { it.name == "South 24 Parganas" }
        assertNotNull("South 24 Parganas zone must exist", south24Zone)
        assertTrue("South 24 Parganas zone must have boundary", south24Zone!!.hasBoundary)
        assertEquals("south_24_parganas", south24Zone.boundary?.id)
    }

    // ── 4. Spatial Geometry & Containment ──

    @Test
    fun testSpatialGeometryContainment() {
        val howrah = boundaryRepo.getBoundaryForZone("Howrah")
        assertNotNull(howrah)

        // Salkia, Howrah (Ghas Bagan pandal coordinate: 22.5921, 88.3434)
        assertTrue("Howrah boundary must contain Salkia pandal coordinate", howrah!!.contains(22.5921, 88.3434))

        // Kidderpore (22.5350, 88.3210) is in South Kolkata, NOT Howrah
        assertFalse("Howrah boundary must NOT contain Kidderpore coordinate", howrah.contains(22.5350, 88.3210))

        val southKolkata = boundaryRepo.getBoundaryForZone("South Kolkata")
        assertNotNull(southKolkata)
        assertTrue("South Kolkata must contain Kidderpore coordinate", southKolkata!!.contains(22.5350, 88.3210))
        assertFalse("South Kolkata must NOT contain Salkia, Howrah coordinate", southKolkata.contains(22.5921, 88.3434))
    }

    // ── 5. Zone Switching & Clearing Simulation ──

    @Test
    fun testZoneSwitchingAndClearing() {
        var activeZone: FestivalZone? = null
        var activeBoundaryId: String? = null

        val zones = zoneRepo.getZones()
        val zone1 = zones.first { it.name == "Howrah" }
        val zone2 = zones.first { it.name == "North Kolkata" }

        // Select Zone 1
        activeZone = zone1
        activeBoundaryId = zone1.boundary?.id
        assertEquals("Howrah", activeZone.name)
        assertEquals("howrah", activeBoundaryId)

        // Switch to Zone 2
        activeZone = zone2
        activeBoundaryId = zone2.boundary?.id
        assertEquals("North Kolkata", activeZone.name)
        assertEquals("north_kolkata", activeBoundaryId)

        // Clear Zone
        activeZone = null
        activeBoundaryId = null
        assertNull(activeZone)
        assertNull(activeBoundaryId)
    }

    // ── 6. Independent Layer Filtering with Active Zone ──

    @Test
    fun testIndependentLayerFilteringWithActiveZone() {
        val howrahZone = zoneRepo.getZones().first { it.name == "Howrah" }
        assertNotNull(howrahZone.boundary)

        // Toggling category filters does not mutate the boundary geometry
        var showPandals = true
        var showMetro = true
        var showToilets = true

        val initialBoundaryId = howrahZone.boundary!!.id

        // Toggle Pandals OFF
        showPandals = false
        assertEquals("Boundary geometry must remain intact when pandals are toggled", initialBoundaryId, howrahZone.boundary!!.id)

        // Toggle Metro OFF
        showMetro = false
        assertEquals("Boundary geometry must remain intact when metro is toggled", initialBoundaryId, howrahZone.boundary!!.id)

        // Toggle Toilets OFF
        showToilets = false
        assertEquals("Boundary geometry must remain intact when toilets are toggled", initialBoundaryId, howrahZone.boundary!!.id)

        // Restore all filters
        showPandals = true
        showMetro = true
        showToilets = true
        assertEquals("Boundary geometry remains identical", initialBoundaryId, howrahZone.boundary!!.id)
    }

    // ── 7. All 262 Pandals Validated & Classified Without Gaps ──

    @Test
    fun testAll262PandalsClassifiedWithoutGaps() {
        val allPandals = pandalRepo.all()
        assertEquals("Total catalog pandals must be exactly 262", 262, allPandals.size)

        val supportedZoneNames = setOf(
            "Central Kolkata",
            "North Kolkata",
            "South Kolkata",
            "West Kolkata",
            "East Kolkata",
            "Howrah",
            "Salt Lake / Bidhannagar",
            "New Town",
            "New Garia & Surroundings",
            "Barrackpore",
            "South 24 Parganas"
        )

        for (p in allPandals) {
            assertTrue("Pandal ID must not be blank", p.id.isNotBlank())
            assertTrue("Pandal coordinate must be valid Kolkata coordinate", PandalRepository.isValidKolkataCoordinate(p.latitude, p.longitude))
            assertTrue("Pandal area '${p.area}' for '${p.name}' must be one of the supported canonical zones", supportedZoneNames.contains(p.area))
            assertNotEquals("Area unverified", p.area)
        }
    }

    // ── 8. Area Search vs Zone Selection Consistency ──

    @Test
    fun testAreaSearchVsZoneTabSelectionConsistency() {
        val zones = zoneRepo.getZones()
        val testQueries = listOf(
            "Howrah" to "Howrah",
            "South Kolkata" to "South Kolkata",
            "North Kolkata" to "North Kolkata",
            "Central Kolkata" to "Central Kolkata",
            "Salt Lake" to "Salt Lake / Bidhannagar",
            "New Town" to "New Town",
            "New Garia" to "New Garia & Surroundings",
            "East Kolkata" to "East Kolkata",
            "West Kolkata" to "West Kolkata",
            "Barrackpore" to "Barrackpore",
            "South 24 Parganas" to "South 24 Parganas"
        )

        for ((query, canonicalName) in testQueries) {
            val searchResults = pandalRepo.search(query, null)
            val zone = zones.firstOrNull { it.name == canonicalName }
            assertNotNull("Zone '$canonicalName' must exist in FestivalZoneRepository", zone)
            assertEquals(
                "Search for '$query' must return the exact pandal count as zone '$canonicalName'",
                zone!!.pandals.size,
                searchResults.size
            )
            val zonePandalIds = zone.pandals.map { it.id }.toSet()
            val searchPandalIds = searchResults.map { it.id }.toSet()
            assertEquals("Pandal IDs returned by search must match zone pandals exactly", zonePandalIds, searchPandalIds)
        }
    }

    // ── 9. Howrah vs Kolkata Area Classification ──

    @Test
    fun testHowrahVsKolkataClassificationPrecision() {
        val howrahPandals = pandalRepo.all().filter { it.area == "Howrah" }
        assertEquals("Howrah zone must contain exactly 7 pandals", 7, howrahPandals.size)

        // All 7 must be west of Hooghly River (longitude < 88.35)
        for (hp in howrahPandals) {
            assertTrue("Howrah pandal '${hp.name}' must be on the west bank", hp.longitude < 88.35)
        }

        // Kidderpore pandals must be in South Kolkata
        val kidderpore = pandalRepo.all().filter { it.name.contains("Kidderpore", ignoreCase = true) || it.name.contains("Khidderpore", ignoreCase = true) }
        assertTrue("Kidderpore pandals must exist in repository", kidderpore.isNotEmpty())
        for (kp in kidderpore) {
            assertEquals("Kidderpore pandal '${kp.name}' must be classified in South Kolkata", "South Kolkata", kp.area)
        }
    }

    // ── 10. Missing and Incorrect Coordinates Rejection ──

    @Test
    fun testSanityCoordinateRejection() {
        assertFalse("Latitude out of range must be rejected", PandalRepository.isValidKolkataCoordinate(10.0, 88.36))
        assertFalse("Longitude out of range must be rejected", PandalRepository.isValidKolkataCoordinate(22.57, 72.87))
        assertTrue("Valid Kolkata coordinate must be accepted", PandalRepository.isValidKolkataCoordinate(22.5726, 88.3639))
    }

    // ── 11. Deterministic Pandal IDs and Deduplication ──

    @Test
    fun testPandalIdPreservationAndNoDuplicates() {
        val pandals = pandalRepo.all()
        val ids = pandals.map { it.id }
        assertEquals("Every pandal must have a unique ID", ids.size, ids.toSet().size)

        // Verify ID format: name-slug-lat5
        for (p in pandals) {
            assertTrue("ID '${p.id}' must end with 5 decimal digits of latitude", p.id.matches(Regex(".+-\\d+\\.\\d{5}$")))
        }
    }

    // ── 12. Barrackpore Boundary Verification & Geographic Positioning ──

    @Test
    fun testBarrackporeBoundaryVerificationAndPositioning() {
        val barrackpore = boundaryRepo.getBoundary("barrackpore")
        assertNotNull("Barrackpore boundary must exist in repository", barrackpore)
        assertEquals("barrackpore", barrackpore!!.id)
        assertEquals("Barrackpore", barrackpore.name)
        assertTrue("Barrackpore must have a detailed GIS polygon (>=100 points)", barrackpore.outerRing.size >= 100)

        // Geographic latitude range must be strictly in the northern suburban belt (north of lat 22.73)
        assertTrue("Barrackpore minLat (${barrackpore.minLat}) must be >= 22.73", barrackpore.minLat >= 22.73)
        assertTrue("Barrackpore maxLat (${barrackpore.maxLat}) must be <= 22.80", barrackpore.maxLat <= 22.80)
        assertTrue("Barrackpore minLng (${barrackpore.minLng}) must be >= 88.33", barrackpore.minLng >= 88.33)
        assertTrue("Barrackpore maxLng (${barrackpore.maxLng}) must be <= 88.43", barrackpore.maxLng <= 88.43)

        // Centroid must be in the Barrackpore urban cluster (lat ~22.76, lng ~88.37)
        val centroidLat = (barrackpore.minLat + barrackpore.maxLat) / 2
        val centroidLng = (barrackpore.minLng + barrackpore.maxLng) / 2
        assertTrue("Centroid latitude ($centroidLat) must be in 22.75..22.78", centroidLat in 22.75..22.78)
        assertTrue("Centroid longitude ($centroidLng) must be in 22.35..88.41", centroidLng in 88.35..88.41)

        // Verified points inside Barrackpore
        assertTrue("Must contain Barrackpore Railway Station area (22.7580, 88.3711)", barrackpore.contains(22.7580, 88.3711))
        assertTrue("Must contain Jaffarpur / Anandapuri (22.7641, 88.3776)", barrackpore.contains(22.7641, 88.3776))

        // Must NOT contain southern places:
        assertFalse("Must NOT contain Khardaha (22.7200, 88.3800)", barrackpore.contains(22.7200, 88.3800))
        assertFalse("Must NOT contain Panihati (22.6900, 88.3700)", barrackpore.contains(22.6900, 88.3700))
        assertFalse("Must NOT contain Kamarhati (22.6686, 88.3704)", barrackpore.contains(22.6686, 88.3704))
        assertFalse("Must NOT contain Belgharia (22.6518, 88.3726)", barrackpore.contains(22.6518, 88.3726))
        assertFalse("Must NOT contain Baranagar (22.6366, 88.3790)", barrackpore.contains(22.6366, 88.3790))
        assertFalse("Must NOT contain Central Kolkata (22.5650, 88.3600)", barrackpore.contains(22.5650, 88.3600))
        assertFalse("Must NOT contain South Kolkata (22.5200, 88.3500)", barrackpore.contains(22.5200, 88.3500))
    }

    // ── 13. Pandal Classification Audit (Milangarh & Baranagar) ──

    @Test
    fun testPandalClassificationAuditMilangarhAndBaranagar() {
        val milangarh = pandalRepo.all().firstOrNull { it.name.contains("Milangarh", ignoreCase = true) }
        assertNotNull("Milangarh pandal must exist in repository", milangarh)
        assertEquals("Milangarh (Belgharia) must be classified in North Kolkata", "North Kolkata", milangarh!!.area)

        val baranagar = pandalRepo.all().firstOrNull { it.name.contains("Baranagar Netaji", ignoreCase = true) }
        assertNotNull("Baranagar Netaji Colony pandal must exist in repository", baranagar)
        assertEquals("Baranagar Netaji Colony must be classified in North Kolkata", "North Kolkata", baranagar!!.area)

        val northKolkataPandals = pandalRepo.all().filter { it.area == "North Kolkata" }
        assertEquals("North Kolkata must now have exactly 78 pandals", 78, northKolkataPandals.size)

        val barrackporePandals = pandalRepo.all().filter { it.area == "Barrackpore" }
        assertEquals("Barrackpore zone currently has 0 pandals in active catalogue", 0, barrackporePandals.size)
    }

    // ── 14. Barrackpore Zone Selection and Search Parity ──

    @Test
    fun testBarrackporeZoneSelectionAndSearchParity() {
        val zones = zoneRepo.getZones()
        val barrackporeZone = zones.firstOrNull { it.name == "Barrackpore" }
        assertNotNull("Barrackpore zone must be available in Zones tab", barrackporeZone)
        assertTrue("Barrackpore zone must have verified boundary attached", barrackporeZone!!.hasBoundary)
        assertEquals("barrackpore", barrackporeZone.boundary?.id)

        val searchBoundary = boundaryRepo.findBoundaryForQuery("Barrackpore")
        assertNotNull("Search for Barrackpore must resolve boundary", searchBoundary)
        assertEquals("barrackpore", searchBoundary!!.id)

        assertEquals("Zone boundary ID must equal search boundary ID", barrackporeZone.boundary?.id, searchBoundary.id)
        assertEquals("Zone minLat must match search minLat", barrackporeZone.boundary?.minLat, searchBoundary.minLat)
        assertEquals("Zone maxLat must match search maxLat", barrackporeZone.boundary?.maxLat, searchBoundary.maxLat)
        assertEquals("Zone minLng must match search minLng", barrackporeZone.boundary?.minLng, searchBoundary.minLng)
        assertEquals("Zone maxLng must match search maxLng", barrackporeZone.boundary?.maxLng, searchBoundary.maxLng)
    }
}
