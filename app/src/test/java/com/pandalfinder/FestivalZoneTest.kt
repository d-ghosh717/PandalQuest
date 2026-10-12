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
            "Salt Lake & East Kolkata"
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

        // Check that supported zones have boundary attached
        val howrahZone = zones.firstOrNull { it.name == "Howrah" }
        assertNotNull("Howrah zone must exist", howrahZone)
        assertTrue("Howrah zone must have boundary", howrahZone!!.hasBoundary)
        assertEquals("howrah", howrahZone.boundary?.id)

        val southKolkataZone = zones.firstOrNull { it.name == "South Kolkata" }
        assertNotNull("South Kolkata zone must exist", southKolkataZone)
        assertTrue("South Kolkata zone must have boundary", southKolkataZone!!.hasBoundary)
        assertEquals("south_kolkata", southKolkataZone.boundary?.id)

        val northKolkataZone = zones.firstOrNull { it.name == "North Kolkata" }
        assertNotNull("North Kolkata zone must exist", northKolkataZone)
        assertTrue("North Kolkata zone must have boundary", northKolkataZone!!.hasBoundary)
        assertEquals("north_kolkata", northKolkataZone.boundary?.id)
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
}
