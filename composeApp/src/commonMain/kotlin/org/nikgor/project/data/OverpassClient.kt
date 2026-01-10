package org.nikgor.project.data

import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.delay

class OverpassClient {
    private val maxRetries = 2
    private val baseDelayMs = 1_200L

    // Main train station inside the city bbox
    suspend fun queryMainStation(bbox: BoundingBox, cityCenter: CityLocation): Poi? {
        val bboxStr = "${bbox.south},${bbox.west},${bbox.north},${bbox.east}"
        // Query for both station nodes and ways (some big stations are drawn as polygons)
        val query = """
            [out:json][timeout:25];
            (
              node["railway"="station"]($bboxStr);
              way["railway"="station"]($bboxStr);
            );
            out center;
        """.trimIndent()

        val stations = executeQuery(query)


        return stations.minByOrNull { station ->
            org.nikgor.project.map.distanceKm(cityCenter, CityLocation(station.lat, station.lon))
        }
    }



    suspend fun queryPois(bbox: BoundingBox): List<Poi> {
        val bboxStr = "${bbox.south},${bbox.west},${bbox.north},${bbox.east}"

        val query = """
            [out:json][timeout:25];
            (
              node["tourism"="attraction"]($bboxStr);
              node["historic"="building"]($bboxStr);
              node["historic"="church"]($bboxStr);
              node["historic"="castle"]($bboxStr);
              node["tourism"="museum"]($bboxStr);
              node["amenity"="restaurant"]($bboxStr);
              node["amenity"="cafe"]($bboxStr);
              node["leisure"="park"]($bboxStr);
            );
            out center;
        """.trimIndent()

        return executeQuery(query)
    }

    private suspend fun executeQuery(query: String): List<Poi> {
        repeat(maxRetries) { attempt ->
            try {
                val response = HttpClientProvider.client.post(
                    "https://overpass-api.de/api/interpreter"
                ) {
                    setBody(query)
                    header(HttpHeaders.UserAgent, "marcheroute-kmp/0.1")
                    accept(ContentType.Application.Json)
                }

                if (!response.status.isSuccess()) throw IllegalStateException("HTTP ${response.status}")

                val body = response.body<OverpassResponse>()

                return body.elements.mapNotNull { el ->
                    val lat = el.lat ?: el.center?.lat
                    val lon = el.lon ?: el.center?.lon
                    val name = el.tags?.get("name")

                    if (lat != null && lon != null && !name.isNullOrBlank()) {
                        Poi(
                            id = el.id,
                            lat = lat,
                            lon = lon,
                            name = name,
                            category = determineCategory(el.tags),
                            link = el.tags["website"] ?: el.tags["wikipedia"]?.let { "https://en.wikipedia.org/wiki/$it" }
                        )
                    } else null
                }
            } catch (e: Exception) {
                if (attempt == maxRetries - 1) return emptyList()
                delay(baseDelayMs * (attempt + 1))
            }
        }
        return emptyList()
    }


    private fun determineCategory(tags: Map<String, String>?): PoiCategory {
        if (tags == null) return PoiCategory.OTHER
        if (tags["railway"] == "station") return PoiCategory.OTHER // Stations handled separately
        if (tags["amenity"] == "restaurant") return PoiCategory.RESTAURANT
        if (tags["amenity"] == "cafe") return PoiCategory.CAFE
        if (tags["tourism"] == "museum") return PoiCategory.MUSEUM
        if (tags["leisure"] == "park") return PoiCategory.PARK
        if (tags["tourism"] == "attraction") return PoiCategory.LANDMARK
        if (tags["historic"] == "building" || tags["historic"] == "church" || tags["historic"] == "castle") return PoiCategory.HISTORIC_SITE
        return PoiCategory.OTHER
    }


    suspend fun queryCityContour(city: String): CityContour? {
        val query = """
        [out:json][timeout:25];
        relation
          ["boundary"="administrative"]
          ["admin_level"="8"]
          ["name"="$city"];
        out geom;
    """.trimIndent()

        val response = HttpClientProvider.client.post(
            "https://overpass-api.de/api/interpreter"
        ) {
            setBody(query)
            header(HttpHeaders.UserAgent, "marcheroute-kmp/0.1")
            accept(ContentType.Application.Json)
        }

        val body = response.body<OverpassResponse>()

        val relation = body.elements.firstOrNull { it.members != null }
            ?: return null

        val points = relation.members
            ?.flatMap { it.geometry.orEmpty() }
            ?.map { GeoPoint(it.lat, it.lon) }
            .orEmpty()

        return CityContour(points)
    }


}
