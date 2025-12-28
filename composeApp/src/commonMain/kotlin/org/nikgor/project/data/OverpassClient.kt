package org.nikgor.project.data

import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.delay

class OverpassClient {

    private val maxRetries = 3
    private val baseDelayMs = 1_000L

    suspend fun queryPois(bbox: BoundingBox): List<Poi> {
        val bboxStr = "${bbox.south},${bbox.west},${bbox.north},${bbox.east}"

        val query = """
            [out:json][timeout:25];
            (
              node["tourism"="attraction"]($bboxStr);
              node["historic"]($bboxStr);
              node["tourism"="museum"]($bboxStr);
              node["leisure"="park"]($bboxStr);
            );
            out center;
        """.trimIndent()

        repeat(maxRetries) { attempt ->
            try {
                println("Overpass request attempt ${attempt + 1}/$maxRetries")

                val response = HttpClientProvider.client.post(
                    "https://overpass-api.de/api/interpreter"
                ) {
                    setBody(query)
                    header(HttpHeaders.UserAgent, "marcheroute-kmp/0.1")
                    accept(ContentType.Application.Json)
                }

                if (!response.status.isSuccess()) {
                    println(
                        "Overpass HTTP error: ${response.status} " +
                                "(attempt ${attempt + 1})"
                    )
                    throw IllegalStateException("HTTP ${response.status}")
                }

                val body = response.body<OverpassResponse>()

                return body.elements.mapNotNull {
                    val lat = it.lat ?: it.center?.lat
                    val lon = it.lon ?: it.center?.lon
                    if (lat != null && lon != null) {
                        Poi(
                            id = it.id,
                            lat = lat,
                            lon = lon,
                            name = it.tags?.get("name") ?: "(no name)"
                        )
                    } else null
                }

            } catch (e: Exception) {
                println("Overpass error on attempt ${attempt + 1}: ${e.message}")
                e.printStackTrace()

                // last attempt → give up gracefully
                if (attempt == maxRetries - 1) {
                    println("Overpass failed after $maxRetries attempts")
                    return emptyList()
                }

                // exponential backoff
                delay(baseDelayMs * (attempt + 1))
            }
        }

        return emptyList()
    }
}
