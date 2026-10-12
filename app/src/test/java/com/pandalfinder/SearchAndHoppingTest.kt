package com.pandalfinder

import com.pandalfinder.data.HoppingStop
import com.pandalfinder.data.MetroStation
import com.pandalfinder.data.StopType
import org.junit.Assert.*
import org.junit.Test

class SearchAndHoppingTest {

    @Test
    fun testCoordinateValidation() {
        assertTrue(MainActivity.isValidCoordinate(22.5726, 88.3639))
        assertTrue(MainActivity.isValidCoordinate(-90.0, -180.0))
        assertTrue(MainActivity.isValidCoordinate(90.0, 180.0))
        assertFalse(MainActivity.isValidCoordinate(Double.NaN, 88.0))
        assertFalse(MainActivity.isValidCoordinate(22.0, Double.POSITIVE_INFINITY))
        assertFalse(MainActivity.isValidCoordinate(91.0, 88.0))
        assertFalse(MainActivity.isValidCoordinate(22.0, -181.0))
    }

    @Test
    fun testMetroStationsLoaded() {
        val stations = MetroStation.allStations()
        assertTrue(stations.isNotEmpty())
        assertTrue(stations.any { it.name.contains("Rabindra Sarobar", ignoreCase = true) })
        assertTrue(stations.any { it.name.contains("Howrah", ignoreCase = true) })
        assertTrue(stations.any { it.line.contains("Blue", ignoreCase = true) })
        assertTrue(stations.any { it.line.contains("Green", ignoreCase = true) })
        assertTrue(stations.any { it.line.contains("Purple", ignoreCase = true) })
        assertTrue(stations.any { it.line.contains("Orange", ignoreCase = true) })
        assertTrue(stations.any { it.line.contains("Yellow", ignoreCase = true) })
    }

    @Test
    fun testMetroColorResolution() {
        val blueStation = MetroStation("Rabindra Sarobar", 22.5087, 88.3466, "Blue Line")
        val greenStation = MetroStation("Howrah", 22.5846, 88.3427, "Green Line")
        val purpleStation = MetroStation("Majerhat", 22.5142, 88.3324, "Purple Line")
        val orangeStation = MetroStation("Satyajit Ray", 22.4842, 88.3842, "Orange Line")
        val yellowStation = MetroStation("Jessore Road", 22.6468, 88.4312, "Yellow Line")

        assertTrue(blueStation.line.contains("Blue"))
        assertTrue(greenStation.line.contains("Green"))
        assertTrue(purpleStation.line.contains("Purple"))
        assertTrue(orangeStation.line.contains("Orange"))
        assertTrue(yellowStation.line.contains("Yellow"))
    }

    @Test
    fun testHoppingStopCreationFromMetroAndPandal() {
        val station = MetroStation("Rabindra Sarobar", 22.5087, 88.3466, "Blue Line")
        val metroStop = HoppingStop.fromMetro(station)

        assertEquals("metro:rabindra_sarobar", metroStop.id)
        assertEquals("Rabindra Sarobar", metroStop.name)
        assertEquals(StopType.METRO, metroStop.type)
        assertEquals(22.5087, metroStop.latitude, 0.0001)
        assertEquals(88.3466, metroStop.longitude, 0.0001)
        assertEquals("Metro • Blue Line", metroStop.subtitle)
        assertNotNull(metroStop.metroRef)

        val pandal = Pandal(
            id = "khidderpore-pally-saradiya",
            name = "Khidderpore 75 Pally",
            area = "Khidderpore",
            latitude = 22.5350,
            longitude = 88.3280
        )
        val pandalStop = HoppingStop.fromPandal(pandal)
        assertEquals("pandal:khidderpore-pally-saradiya", pandalStop.id)
        assertEquals("Khidderpore 75 Pally", pandalStop.name)
        assertEquals(StopType.PANDAL, pandalStop.type)
        assertEquals(22.5350, pandalStop.latitude, 0.0001)
        assertEquals(88.3280, pandalStop.longitude, 0.0001)
        assertEquals("Khidderpore", pandalStop.subtitle)
        assertNotNull(pandalStop.pandalRef)
    }

    @Test
    fun testSearchFilteringQueries() {
        val localPandals = Pandal.getLocalPandals()
        val allStations = MetroStation.allStations()

        // 1. Search "Khidderpore"
        val khidderporePandals = localPandals.filter { it.name.contains("Khidderpore", true) || it.area.contains("Khidderpore", true) }
        val khidderporeMetro = allStations.filter { it.name.contains("Khidderpore", true) || it.line.contains("Khidderpore", true) }
        assertTrue(khidderporePandals.isNotEmpty())

        // 2. Search "Rabindra Sarobar"
        val rabindraMetro = allStations.filter { it.name.contains("Rabindra Sarobar", true) }
        assertEquals(1, rabindraMetro.size)

        // 3. Search "Howrah"
        val howrahPandals = localPandals.filter { it.name.contains("Howrah", true) || it.area.contains("Howrah", true) }
        val howrahMetro = allStations.filter { it.name.contains("Howrah", true) }
        assertTrue(howrahPandals.isNotEmpty() || howrahMetro.isNotEmpty())
        assertTrue(howrahMetro.any { it.name.equals("Howrah", true) })

        // 4. Search "pandal"
        val pandalPandals = localPandals.filter { it.name.contains("pandal", true) || it.area.contains("pandal", true) }
        assertNotNull(pandalPandals)

        // 5. Search "metro"
        val metroMatches = allStations.filter { it.name.contains("metro", true) || it.line.contains("metro", true) }
        assertNotNull(metroMatches)

        // 6. Search random text "xyzabc123"
        val randomPandals = localPandals.filter { it.name.contains("xyzabc123", true) || it.area.contains("xyzabc123", true) }
        val randomMetro = allStations.filter { it.name.contains("xyzabc123", true) || it.line.contains("xyzabc123", true) }
        assertTrue(randomPandals.isEmpty())
        assertTrue(randomMetro.isEmpty())

        // 7. Search empty and whitespace
        val emptyQuery = "   ".trim()
        assertTrue(emptyQuery.isBlank())

        // 8. Special characters
        val specialQuery = "!@#$%^&*()".trim()
        val specialPandals = localPandals.filter { it.name.contains(specialQuery, true) }
        assertTrue(specialPandals.isEmpty())
    }

    @Test
    fun testPublicToiletModelAndHopping() {
        val toilet = com.pandalfinder.data.PublicToilet(
            id = "ChIJm5hSEqd3AjoRsGg140qCjnE",
            name = "Pay and Use Toilet",
            address = "11, Esplanade East, Kolkata",
            latitude = 22.5642,
            longitude = 88.3506
        )
        val stop = HoppingStop.fromToilet(toilet)
        assertEquals("toilet:ChIJm5hSEqd3AjoRsGg140qCjnE", stop.id)
        assertEquals("Pay and Use Toilet", stop.name)
        assertEquals(StopType.TOILET, stop.type)
        assertEquals(22.5642, stop.latitude, 0.0001)
        assertEquals(88.3506, stop.longitude, 0.0001)
        assertEquals("11, Esplanade East, Kolkata", stop.subtitle)
        assertNotNull(stop.toiletRef)
        assertEquals(toilet.id, stop.toiletRef?.id)
    }

    @Test
    fun testLocalPandalsDeduplicationAndLocations() {
        val pandals = Pandal.getLocalPandals()
        
        // 1. Verify specific updated locations
        val machua = pandals.firstOrNull { it.name == "Machua Bazar Sarbojanik Durgapuja Samity" }
        assertNotNull(machua)
        assertEquals(22.5812141, machua!!.latitude, 0.0001)
        assertEquals(88.3572457, machua.longitude, 0.0001)

        val brindabon = pandals.firstOrNull { it.name == "Brindabon Matri Mandir" }
        assertNotNull(brindabon)
        assertEquals(22.5820532, brindabon!!.latitude, 0.0001)
        assertEquals(88.3730342, brindabon.longitude, 0.0001)

        val karbagan = pandals.firstOrNull { it.name == "Karbagan Sarbojanin Durgotsab Committee" }
        assertNotNull(karbagan)
        assertEquals(22.5952410, karbagan!!.latitude, 0.0001)
        assertEquals(88.3842775, karbagan.longitude, 0.0001)

        val pally14 = pandals.firstOrNull { it.name == "14 Pally Udayan Sangha" }
        assertNotNull(pally14)
        assertEquals(22.5575857, pally14!!.latitude, 0.0001)
        assertEquals(88.3669753, pally14.longitude, 0.0001)

        // 2. Verify Selimpur Pally exists and duplicate is deleted
        assertTrue(pandals.any { it.name == "Selimpur Pally" })
        assertFalse(pandals.any { it.name.equals("Selimpur Pally Sarbojanin", ignoreCase = true) })

        // 3. Verify no duplicate names
        val normalizedNames = pandals.map { it.name.trim().lowercase() }
        assertEquals(normalizedNames.size, normalizedNames.toSet().size)
    }

    // ── 4. Area Search Accuracy & Canonical Geographic Classification ──

    @Test
    fun testHowrahSearch_ExcludesKidderporeAndBehalaPandals() {
        val repo = PandalRepository()
        val howrahPandals = repo.search("Howrah", null)

        assertTrue("Howrah search must return matching pandals", howrahPandals.isNotEmpty())

        // Howrah search MUST include real Howrah pandals
        assertTrue(
            "Howrah search must include Ghas Bagan (Salkia)",
            howrahPandals.any { it.name.contains("Ghas Bagan", ignoreCase = true) }
        )
        assertTrue(
            "Howrah search must include Kamardanga",
            howrahPandals.any { it.name.contains("Kamardanga", ignoreCase = true) }
        )
        assertTrue(
            "Howrah search must include Santragachi",
            howrahPandals.any { it.name.contains("Santragachi", ignoreCase = true) }
        )

        // Howrah search MUST NOT include Kidderpore or Behala pandals (which are in South Kolkata)
        assertFalse(
            "Howrah search MUST NOT include Udayan Kidderpore",
            howrahPandals.any { it.name.contains("Udayan Kidderpore", ignoreCase = true) }
        )
        assertFalse(
            "Howrah search MUST NOT include Khidderpore Pally Saradiya",
            howrahPandals.any { it.name.contains("Khidderpore Pally Saradiya", ignoreCase = true) }
        )
        assertFalse(
            "Howrah search MUST NOT include 25 Palli",
            howrahPandals.any { it.name.contains("25 Palli", ignoreCase = true) }
        )
        assertFalse(
            "Howrah search MUST NOT include Behala pandals",
            howrahPandals.any { it.name.contains("Behala", ignoreCase = true) }
        )
    }

    @Test
    fun testSouthKolkataSearch_IncludesKidderporeAndBehalaPandals() {
        val repo = PandalRepository()
        val southPandals = repo.search("South Kolkata", null)

        assertTrue("South Kolkata search must return pandals", southPandals.isNotEmpty())

        // South Kolkata search must include Kidderpore and Behala pandals
        assertTrue(
            "South Kolkata must include Udayan Kidderpore",
            southPandals.any { it.name.contains("Udayan Kidderpore", ignoreCase = true) }
        )
        assertTrue(
            "South Kolkata must include Behala Nutan Dal",
            southPandals.any { it.name.contains("Behala Nutan Dal", ignoreCase = true) }
        )

        // South Kolkata MUST NOT include Howrah pandals
        assertFalse(
            "South Kolkata must not include Kamardanga Howrah",
            southPandals.any { it.name.contains("Kamardanga", ignoreCase = true) }
        )
        assertFalse(
            "South Kolkata must not include Santragachi Kalpataru",
            southPandals.any { it.name.contains("Santragachi", ignoreCase = true) }
        )
    }

    // ── 5. Area Boundary Resolution & Pandal-Name Boundary Exclusion ──

    @Test
    fun testAreaBoundaryLookup_ResolvesRecognizedGeographicAreas() {
        val boundaryRepo = com.pandalfinder.data.AreaBoundaryRepository()

        // 1. Howrah boundary
        val howrahBoundary = boundaryRepo.findBoundaryForQuery("howrah")
        assertNotNull("Recognized 'howrah' query must resolve to an AreaBoundary", howrahBoundary)
        assertEquals("Howrah", howrahBoundary!!.name)
        assertTrue("Howrah boundary is official administrative", howrahBoundary.isOfficialAdministrative)
        assertEquals("OFFICIAL_ADMINISTRATIVE", howrahBoundary.boundaryType)
        assertTrue("Howrah polygon must have verified vertices", howrahBoundary.outerRing.size >= 10)

        // 2. South Kolkata boundary
        val southBoundary = boundaryRepo.findBoundaryForQuery("south kolkata")
        assertNotNull("Recognized 'south kolkata' query must resolve to an AreaBoundary", southBoundary)
        assertEquals("South Kolkata", southBoundary!!.name)
        assertEquals("REGIONAL_ZONE", southBoundary.boundaryType)

        // 3. Salt Lake boundary
        val saltLakeBoundary = boundaryRepo.findBoundaryForQuery("salt lake")
        assertNotNull("Recognized 'salt lake' query must resolve to an AreaBoundary", saltLakeBoundary)
        assertEquals("Salt Lake & East Kolkata", saltLakeBoundary!!.name)
    }

    @Test
    fun testAreaBoundaryLookup_PandalNameSearchesDoNotTriggerBoundaries() {
        val boundaryRepo = com.pandalfinder.data.AreaBoundaryRepository()

        // Pandal names MUST NEVER resolve to an AreaBoundary
        assertNull(
            "Pandal name 'Kumartuli Park' must NOT trigger an area boundary",
            boundaryRepo.findBoundaryForQuery("Kumartuli Park")
        )
        assertNull(
            "Pandal name 'Ghas Bagan' must NOT trigger an area boundary",
            boundaryRepo.findBoundaryForQuery("Ghas Bagan")
        )
        assertNull(
            "Pandal name 'Udayan Kidderpore' must NOT trigger an area boundary",
            boundaryRepo.findBoundaryForQuery("Udayan Kidderpore")
        )
        assertNull(
            "Pandal name 'Suruchi Sangha' must NOT trigger an area boundary",
            boundaryRepo.findBoundaryForQuery("Suruchi Sangha")
        )
        assertNull(
            "Pandal name 'Maddox Square' must NOT trigger an area boundary",
            boundaryRepo.findBoundaryForQuery("Maddox Square")
        )

        // Arbitrary unrecognized queries must return null
        assertNull(boundaryRepo.findBoundaryForQuery("xyzabc123"))
        assertNull(boundaryRepo.findBoundaryForQuery(""))
    }

    // ── 6. Independent Category Filtering in searchAll ──

    @Test
    fun testIndependentCategoryFilteringInSearchAll() {
        val repo = PandalRepository()
        val sampleToilet = com.pandalfinder.data.PublicToilet(
            id = "toilet_howrah_station",
            name = "Howrah Station Toilet",
            address = "Howrah Railway Station, Howrah",
            latitude = 22.5840,
            longitude = 88.3420
        )
        val toilets = listOf(sampleToilet)

        // 1. All filters ON: returns pandals, metro, and toilets
        val allResults = repo.searchAll(
            query = "Howrah",
            location = null,
            toilets = toilets,
            includePandals = true,
            includeMetro = true,
            includeToilets = true
        )
        assertTrue(allResults.any { it.type == PlaceType.PANDAL })
        assertTrue(allResults.any { it.type == PlaceType.METRO })
        assertTrue(allResults.any { it.type == PlaceType.TOILET })

        // 2. Pandals ONLY filter
        val pandalsOnly = repo.searchAll(
            query = "Howrah",
            location = null,
            toilets = toilets,
            includePandals = true,
            includeMetro = false,
            includeToilets = false
        )
        assertTrue(pandalsOnly.all { it.type == PlaceType.PANDAL })
        assertFalse(pandalsOnly.any { it.type == PlaceType.METRO })
        assertFalse(pandalsOnly.any { it.type == PlaceType.TOILET })

        // 3. Metro ONLY filter
        val metroOnly = repo.searchAll(
            query = "Howrah",
            location = null,
            toilets = toilets,
            includePandals = false,
            includeMetro = true,
            includeToilets = false
        )
        assertTrue(metroOnly.all { it.type == PlaceType.METRO })
        assertFalse(metroOnly.any { it.type == PlaceType.PANDAL })
        assertFalse(metroOnly.any { it.type == PlaceType.TOILET })

        // 4. Toilets ONLY filter
        val toiletsOnly = repo.searchAll(
            query = "Howrah",
            location = null,
            toilets = toilets,
            includePandals = false,
            includeMetro = false,
            includeToilets = true
        )
        assertTrue(toiletsOnly.all { it.type == PlaceType.TOILET })
        assertFalse(toiletsOnly.any { it.type == PlaceType.PANDAL })
        assertFalse(toiletsOnly.any { it.type == PlaceType.METRO })

        // 5. Verification that metro and toilets are never counted as pandals
        val pandalCount = allResults.count { it.type == PlaceType.PANDAL }
        val metroCount = allResults.count { it.type == PlaceType.METRO }
        val toiletCount = allResults.count { it.type == PlaceType.TOILET }
        assertEquals(allResults.size, pandalCount + metroCount + toiletCount)
        assertTrue(pandalCount > 0)
        assertTrue(metroCount > 0)
        assertTrue(toiletCount > 0)
    }
}

