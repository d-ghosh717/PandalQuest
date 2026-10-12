package com.pandalfinder.data

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.util.Locale

/**
 * Pure coordinate representation independent of platform SDKs.
 */
data class GeoPoint(val latitude: Double, val longitude: Double)

/**
 * Encapsulates verified geographic boundary geometry and metadata for supported areas.
 */
data class AreaBoundary(
    val id: String,
    val name: String,
    val isOfficialAdministrative: Boolean,
    val boundaryType: String,
    val description: String,
    val geoJsonString: String,
    val outerRing: List<GeoPoint>,
    val minLat: Double,
    val maxLat: Double,
    val minLng: Double,
    val maxLng: Double,
    val aliases: List<String>
) {
    /**
     * Determines whether a given coordinate falls strictly inside this boundary polygon.
     * Uses fast bounding box test followed by ray-casting point-in-polygon.
     */
    fun contains(lat: Double, lng: Double): Boolean {
        if (lat < minLat || lat > maxLat || lng < minLng || lng > maxLng) {
            return false
        }
        return pointInPolygon(lng, lat, outerRing)
    }

    fun toMapLibreBounds(): org.maplibre.android.geometry.LatLngBounds {
        return org.maplibre.android.geometry.LatLngBounds.Builder()
            .include(org.maplibre.android.geometry.LatLng(minLat, minLng))
            .include(org.maplibre.android.geometry.LatLng(maxLat, maxLng))
            .build()
    }

    companion object {
        fun pointInPolygon(x: Double, y: Double, polygon: List<GeoPoint>): Boolean {
            val n = polygon.size
            if (n < 3) return false
            var inside = false
            var p1 = polygon[0]
            for (i in 1..n) {
                val p2 = polygon[i % n]
                if (y > minOf(p1.latitude, p2.latitude)) {
                    if (y <= maxOf(p1.latitude, p2.latitude)) {
                        if (x <= maxOf(p1.longitude, p2.longitude)) {
                            val xinters = if (p1.latitude != p2.latitude) {
                                (y - p1.latitude) * (p2.longitude - p1.longitude) / (p2.latitude - p1.latitude) + p1.longitude
                            } else {
                                p1.longitude
                            }
                            if (p1.longitude == p2.longitude || x <= xinters) {
                                inside = !inside
                            }
                        }
                    }
                }
                p1 = p2
            }
            return inside
        }
    }
}

/**
 * Repository responsible for loading, caching, and querying verified geographic boundaries.
 * All boundary assets are packaged locally in assets/boundaries/ for instant, 100% offline access.
 */
class AreaBoundaryRepository(private val context: Context? = null) {

    private val boundaries = mutableMapOf<String, AreaBoundary>()

    init {
        loadBoundaries()
    }

    private fun loadBoundaries() {
        val boundaryFiles = listOf(
            "howrah.geojson",
            "south_kolkata.geojson",
            "north_kolkata.geojson",
            "central_kolkata.geojson",
            "salt_lake_east.geojson",
            "kolkata.geojson"
        )

        for (fileName in boundaryFiles) {
            val jsonString = readAssetOrFile(fileName) ?: continue
            try {
                val boundary = parseBoundary(jsonString)
                if (boundary != null) {
                    boundaries[boundary.id] = boundary
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to parse boundary asset $fileName: ${e.message}")
            }
        }
    }

    private fun readAssetOrFile(fileName: String): String? {
        // 1. Try Android Context assets
        if (context != null) {
            try {
                context.assets.open("boundaries/$fileName").use { inputStream ->
                    return inputStream.bufferedReader().use { it.readText() }
                }
            } catch (_: Exception) {}
        }

        // 2. Try ClassLoader resources (for JVM tests)
        try {
            val stream: InputStream? = javaClass.classLoader?.getResourceAsStream("boundaries/$fileName")
                ?: javaClass.classLoader?.getResourceAsStream("assets/boundaries/$fileName")
            if (stream != null) {
                return stream.bufferedReader().use { it.readText() }
            }
        } catch (_: Exception) {}

        // 3. Try standard filesystem paths (for Gradle unit tests)
        val candidatePaths = listOf(
            "app/src/main/assets/boundaries/$fileName",
            "src/main/assets/boundaries/$fileName",
            "boundaries/$fileName"
        )
        for (path in candidatePaths) {
            val file = File(path)
            if (file.exists() && file.isFile) {
                try {
                    return file.readText()
                } catch (_: Exception) {}
            }
        }

        return null
    }

    private fun parseBoundary(jsonString: String): AreaBoundary? {
        val root = JSONObject(jsonString)
        val props = root.optJSONObject("properties") ?: JSONObject()
        val id = props.optString("id", "")
        val name = props.optString("name", "")
        if (id.isBlank() || name.isBlank()) return null

        val isOfficial = props.optBoolean("isOfficialAdministrative", false)
        val boundaryType = props.optString("boundaryType", "REGIONAL_ZONE")
        val desc = props.optString("description", "")

        val aliasArray = props.optJSONArray("aliases")
        val aliases = mutableListOf<String>()
        if (aliasArray != null) {
            for (i in 0 until aliasArray.length()) {
                aliases.add(aliasArray.optString(i).lowercase(Locale.US))
            }
        }
        if (!aliases.contains(name.lowercase(Locale.US))) {
            aliases.add(name.lowercase(Locale.US))
        }

        val geom = root.optJSONObject("geometry") ?: return null
        val coordsArr = geom.optJSONArray("coordinates") ?: return null
        val outerArr = coordsArr.optJSONArray(0) ?: return null

        val points = mutableListOf<GeoPoint>()
        var minLat = 90.0
        var maxLat = -90.0
        var minLng = 180.0
        var maxLng = -180.0

        for (i in 0 until outerArr.length()) {
            val pt = outerArr.optJSONArray(i) ?: continue
            val lng = pt.optDouble(0)
            val lat = pt.optDouble(1)
            points.add(GeoPoint(lat, lng))

            if (lat < minLat) minLat = lat
            if (lat > maxLat) maxLat = lat
            if (lng < minLng) minLng = lng
            if (lng > maxLng) maxLng = lng
        }

        if (points.size < 3) return null

        return AreaBoundary(
            id = id,
            name = name,
            isOfficialAdministrative = isOfficial,
            boundaryType = boundaryType,
            description = desc,
            geoJsonString = jsonString,
            outerRing = points,
            minLat = minLat,
            maxLat = maxLat,
            minLng = minLng,
            maxLng = maxLng,
            aliases = aliases
        )
    }

    /**
     * Resolves a user search query to a recognized geographic area boundary.
     * Returns null if the query is a pandal name or unrecognized search term.
     */
    fun findBoundaryForQuery(query: String): AreaBoundary? {
        val q = query.trim().lowercase(Locale.US)
        if (q.length < 3) return null

        // Check exact alias matches
        for (boundary in boundaries.values) {
            for (alias in boundary.aliases) {
                if (q == alias) {
                    return boundary
                }
            }
        }

        // Check containment only for recognized multi-word area names
        for (boundary in boundaries.values) {
            val canonicalLower = boundary.name.lowercase(Locale.US)
            if (q == canonicalLower || canonicalLower.startsWith(q) || q.startsWith(canonicalLower)) {
                return boundary
            }
        }

        return null
    }

    fun getBoundary(id: String): AreaBoundary? = boundaries[id]

    /**
     * Resolves a festival zone or area name to its verified boundary geometry.
     * Returns null if no official or verified boundary exists for the given zone.
     */
    fun getBoundaryForZone(zoneName: String): AreaBoundary? {
        val trimmed = zoneName.trim()
        if (trimmed.isEmpty()) return null

        // 1. Direct name match (case-insensitive)
        val byName = boundaries.values.firstOrNull { it.name.equals(trimmed, ignoreCase = true) }
        if (byName != null) return byName

        // 2. Direct ID match
        val idCandidate = trimmed.lowercase(Locale.US).replace(Regex("[^a-z0-9]+"), "_").trim('_')
        val byId = boundaries[idCandidate]
        if (byId != null) return byId

        // 3. Match via query / alias resolution
        return findBoundaryForQuery(trimmed)
    }

    fun getAllBoundaries(): List<AreaBoundary> = boundaries.values.toList()

    /**
     * Canonical spatial classifier for pandals based on verified boundaries.
     */
    fun classifyPandal(lat: Double, lng: Double): String {
        val howrah = boundaries["howrah"]
        if (howrah != null && howrah.contains(lat, lng)) {
            return "Howrah"
        }

        val saltLake = boundaries["salt_lake_east"]
        if (saltLake != null && (saltLake.contains(lat, lng) || (lng > 88.400 && lat > 22.560))) {
            return "Salt Lake & East Kolkata"
        }

        val south = boundaries["south_kolkata"]
        if (south != null && south.contains(lat, lng)) {
            return "South Kolkata"
        }

        val north = boundaries["north_kolkata"]
        if (north != null && north.contains(lat, lng)) {
            return "North Kolkata"
        }

        val central = boundaries["central_kolkata"]
        if (central != null && central.contains(lat, lng)) {
            return "Central Kolkata"
        }

        val kolkata = boundaries["kolkata"]
        if (kolkata != null && kolkata.contains(lat, lng)) {
            return when {
                lat < 22.545 -> "South Kolkata"
                lat >= 22.580 -> "North Kolkata"
                else -> "Central Kolkata"
            }
        }

        // Geometric fallback
        return when {
            lng < 88.330 && lat in 22.56..22.65 -> "Howrah"
            lng > 88.400 -> "Salt Lake & East Kolkata"
            lat >= 22.585 -> "North Kolkata"
            lat >= 22.545 -> "Central Kolkata"
            else -> "South Kolkata"
        }
    }

    companion object {
        private const val TAG = "AreaBoundaryRepo"

        @Volatile
        private var instance: AreaBoundaryRepository? = null

        fun getInstance(context: Context? = null): AreaBoundaryRepository {
            return instance ?: synchronized(this) {
                instance ?: AreaBoundaryRepository(context?.applicationContext ?: context).also {
                    instance = it
                }
            }
        }
    }
}
