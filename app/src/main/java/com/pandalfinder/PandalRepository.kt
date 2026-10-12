package com.pandalfinder

import android.content.Context
import android.location.Location
import android.util.Log
import com.pandalfinder.data.AreaBoundaryRepository
import com.pandalfinder.data.MetroStation
import java.util.Locale

enum class PlaceType {
    PANDAL,
    METRO,
    TOILET
}

data class SearchResult(
    val id: String,
    val name: String,
    val type: PlaceType,
    val latitude: Double,
    val longitude: Double,
    val subtitle: String,
    val distanceMeters: Float = 0f,
    val pandal: Pandal? = null,
    val metroStation: MetroStation? = null,
    val toilet: com.pandalfinder.data.PublicToilet? = null
)


/** Single source of local pandal data. Keeping the UI behind this class makes a JSON
 * catalogue or a seasonal download a drop-in replacement later. */
class PandalRepository(context: Context? = null) {
    companion object {
        private const val TAG = "PandalRepository"
        
        /** Sanity bounding box covering Greater Kolkata, Howrah, Salt Lake, and New Town */
        fun isValidKolkataCoordinate(lat: Double, lng: Double): Boolean {
            return lat in 22.35..22.75 && lng in 88.20..88.55
        }
    }

    private val boundaryRepo = AreaBoundaryRepository.getInstance(context)

    private val pandals: List<Pandal> = Pandal.getLocalPandals()
        .filter { pandal ->
            val valid = isValidKolkataCoordinate(pandal.latitude, pandal.longitude)
            if (!valid) {
                Log.w(TAG, "Sanity check rejected invalid coordinate for pandal: ${pandal.name} (${pandal.latitude}, ${pandal.longitude})")
            }
            valid
        }
        .map { pandal ->
            pandal.copy(
                id = pandal.name.lowercase(Locale.US)
                    .replace(Regex("[^a-z0-9]+"), "-")
                    .trim('-') + "-${"%.5f".format(Locale.US, pandal.latitude)}"
                , area = pandal.area.takeUnless { it == "Kolkata" || it == "West Kolkata / Howrah" }
                    ?: boundaryRepo.classifyPandal(pandal.latitude, pandal.longitude)
            )
        }
        .distinctBy { "${it.name.lowercase(Locale.US)}:${it.latitude}:${it.longitude}" }

    private val metroStations: List<MetroStation> = MetroStation.allStations()

    fun all(): List<Pandal> = pandals

    fun nearby(location: Location): List<Pandal> =
        pandals.map { it.withDistanceFrom(location) }.sortedBy { it.distanceMeters }

    fun search(query: String, location: Location?): List<Pandal> {
        val normalized = query.trim()
        if (normalized.isBlank()) return emptyList()
        val areaMatch = boundaryRepo.findBoundaryForQuery(normalized)
        val matches = pandals
            .filter { pandal ->
                if (areaMatch != null) {
                    areaMatch.contains(pandal.latitude, pandal.longitude) || pandal.area.equals(areaMatch.name, ignoreCase = true)
                } else {
                    pandal.name.contains(normalized, true) || pandal.area.contains(normalized, true)
                }
            }
            .map { if (location == null) it else it.withDistanceFrom(location) }
        return if (location == null) matches.sortedBy { it.name } else matches.sortedBy { it.distanceMeters }
    }

    fun searchAll(
        query: String,
        location: Location?,
        toilets: List<com.pandalfinder.data.PublicToilet> = emptyList(),
        includePandals: Boolean = true,
        includeMetro: Boolean = true,
        includeToilets: Boolean = true
    ): List<SearchResult> {
        val normalized = query.trim()
        if (normalized.isBlank()) return emptyList()
        val areaMatch = boundaryRepo.findBoundaryForQuery(normalized)

        val pandalResults = if (includePandals) {
            pandals
                .filter { pandal ->
                    if (areaMatch != null) {
                        areaMatch.contains(pandal.latitude, pandal.longitude) || pandal.area.equals(areaMatch.name, ignoreCase = true)
                    } else {
                        pandal.name.contains(normalized, true) || pandal.area.contains(normalized, true)
                    }
                }
                .map { pandal ->
                    val p = if (location != null) pandal.withDistanceFrom(location) else pandal
                    SearchResult(
                        id = "pandal:${p.id}",
                        name = p.name,
                        type = PlaceType.PANDAL,
                        latitude = p.latitude,
                        longitude = p.longitude,
                        subtitle = "Pandal • ${p.area}",
                        distanceMeters = p.distanceMeters,
                        pandal = p
                    )
                }
        } else emptyList()

        val metroResults = if (includeMetro) {
            metroStations
                .filter { station ->
                    if (areaMatch != null) {
                        areaMatch.contains(station.latitude, station.longitude)
                    } else {
                        station.name.contains(normalized, true) || station.line.contains(normalized, true)
                    }
                }
                .map { station ->
                    val dist = if (location != null) {
                        val sLoc = Location("").apply {
                            latitude = station.latitude
                            longitude = station.longitude
                        }
                        location.distanceTo(sLoc)
                    } else 0f
                    SearchResult(
                        id = "metro:${station.name.lowercase().replace(" ", "_")}",
                        name = station.name,
                        type = PlaceType.METRO,
                        latitude = station.latitude,
                        longitude = station.longitude,
                        subtitle = "Metro • ${station.line}",
                        distanceMeters = dist,
                        metroStation = station
                    )
                }
        } else emptyList()

        val toiletResults = if (includeToilets) {
            toilets
                .filter { toilet ->
                    if (areaMatch != null) {
                        areaMatch.contains(toilet.latitude, toilet.longitude)
                    } else {
                        toilet.name.contains(normalized, true) ||
                        toilet.address.contains(normalized, true) ||
                        normalized.contains("toilet", true) ||
                        normalized.contains("restroom", true) ||
                        normalized.contains("washroom", true)
                    }
                }
                .map { toilet ->
                    val dist = if (location != null) {
                        val tLoc = Location("").apply {
                            latitude = toilet.latitude
                            longitude = toilet.longitude
                        }
                        location.distanceTo(tLoc)
                    } else 0f
                    SearchResult(
                        id = "toilet:${toilet.id}",
                        name = toilet.name,
                        type = PlaceType.TOILET,
                        latitude = toilet.latitude,
                        longitude = toilet.longitude,
                        subtitle = "Toilet • ${toilet.address}",
                        distanceMeters = dist,
                        toilet = toilet
                    )
                }
        } else emptyList()

        val combined = (pandalResults + metroResults + toiletResults)
        return if (location != null) {
            combined.sortedBy { it.distanceMeters }
        } else {
            combined.sortedBy { it.name }
        }
    }
}
