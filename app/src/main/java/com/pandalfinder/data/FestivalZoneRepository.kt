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

        allPandals.forEach { p ->
            val zoneName = p.area
            zoneGroups.getOrPut(zoneName) { mutableListOf() }.add(p)
        }

        val descriptions = mapOf(
            "North Kolkata" to "Traditional, heritage & classical theme barowari pujas",
            "Central Kolkata" to "Historic artistic illumination and grand community spectacles",
            "South Kolkata" to "Innovative thematic mega pandals and vibrant cultural hubs",
            "Salt Lake & East Kolkata" to "Spacious modern installations and planned park pandals",
            "Howrah" to "Riverside celebrations and historic neighborhood pujas"
        )

        return zoneGroups.mapNotNull { (name, list) ->
            if (list.isEmpty()) return@mapNotNull null
            val minLat = list.minOf { it.latitude }
            val maxLat = list.maxOf { it.latitude }
            val minLng = list.minOf { it.longitude }
            val maxLng = list.maxOf { it.longitude }
            val avgLat = list.map { it.latitude }.average()
            val avgLng = list.map { it.longitude }.average()

            val bounds = LatLngBounds.Builder()
                .include(LatLng(minLat, minLng))
                .include(LatLng(maxLat, maxLng))
                .build()

            val boundary = boundaryRepository?.getBoundaryForZone(name)

            FestivalZone(
                name = name,
                description = descriptions[name] ?: "Durga Puja festival zone",
                pandals = list,
                centerLat = avgLat,
                centerLng = avgLng,
                bounds = bounds,
                boundary = boundary
            )
        }.sortedByDescending { it.count }
    }
}
