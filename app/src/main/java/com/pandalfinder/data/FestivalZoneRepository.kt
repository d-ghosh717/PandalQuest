package com.pandalfinder.data

import com.pandalfinder.Pandal
import com.pandalfinder.PandalRepository
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds

data class FestivalZone(
    val name: String,
    val description: String,
    val pandals: List<Pandal>,
    val centerLat: Double,
    val centerLng: Double,
    val bounds: LatLngBounds,
    val boundary: AreaBoundary? = null
) {
    val count: Int get() = pandals.size
    val hasBoundary: Boolean get() = boundary != null
}

class FestivalZoneRepository(
    private val pandalRepository: PandalRepository,
    private val boundaryRepository: AreaBoundaryRepository? = null
) {

    fun getZones(): List<FestivalZone> {
        val allPandals = pandalRepository.all()
        val zoneGroups = mutableMapOf<String, MutableList<Pandal>>()

        val descriptions = mapOf(
            "North Kolkata" to "Traditional, heritage & classical theme barowari pujas",
            "Central Kolkata" to "Historic artistic illumination and grand community spectacles",
            "South Kolkata" to "Innovative thematic mega pandals and vibrant cultural hubs",
            "Salt Lake / Bidhannagar" to "Spacious modern installations and planned park & block pandals",
            "New Town" to "Modern planned city mega architecture and futuristic installations",
            "New Garia & Surroundings" to "Vibrant southern community celebrations and cultural centers",
            "East Kolkata" to "Dynamic neighborhood pujas along EM Bypass and eastern corridors",
            "Howrah" to "Riverside celebrations and historic neighborhood pujas",
            "West Kolkata" to "Traditional riverside community pujas in Maheshtala and Garden Reach",
            "Barrackpore" to "Historic northern suburban celebrations along the riverbank",
            "South 24 Parganas" to "Expansive peri-urban and southern district festive gatherings"
        )

        descriptions.keys.forEach { name ->
            zoneGroups[name] = mutableListOf()
        }

        allPandals.forEach { p ->
            val zoneName = p.area
            zoneGroups.getOrPut(zoneName) { mutableListOf() }.add(p)
        }

        return zoneGroups.mapNotNull { (name, list) ->
            val boundary = boundaryRepository?.getBoundaryForZone(name)
            if (list.isEmpty() && boundary == null) return@mapNotNull null

            val bounds = boundary?.toMapLibreBounds() ?: LatLngBounds.Builder()
                .include(LatLng(list.minOf { it.latitude }, list.minOf { it.longitude }))
                .include(LatLng(list.maxOf { it.latitude }, list.maxOf { it.longitude }))
                .build()

            val centerLat = if (list.isNotEmpty()) list.map { it.latitude }.average() else (bounds.latitudeNorth + bounds.latitudeSouth) / 2
            val centerLng = if (list.isNotEmpty()) list.map { it.longitude }.average() else (bounds.longitudeEast + bounds.longitudeWest) / 2

            FestivalZone(
                name = name,
                description = descriptions[name] ?: "Durga Puja festival zone",
                pandals = list,
                centerLat = centerLat,
                centerLng = centerLng,
                bounds = bounds,
                boundary = boundary
            )
        }.sortedWith(compareByDescending<FestivalZone> { it.count }.thenBy { it.name })
    }
}
