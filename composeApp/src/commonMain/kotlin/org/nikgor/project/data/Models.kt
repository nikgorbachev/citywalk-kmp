package org.nikgor.project.data

import kotlinx.serialization.Serializable


enum class PoiCategory(val weight: Int, val dwellTimeMin: Int) {
    LANDMARK(10, 15),
    HISTORIC_SITE(weight = 7, dwellTimeMin = 5),
    MUSEUM(5, 60),
    PARK(3, 30),
    RESTAURANT(weight = 8, dwellTimeMin = 45),
    CAFE(weight = 7, dwellTimeMin = 20),
    OTHER(1, 10)
}


@Serializable
data class CityLocation(val lat: Double, val lon: Double)

@Serializable
data class BoundingBox(
    val south: Double,
    val north: Double,
    val west: Double,
    val east: Double
)

@Serializable
data class Poi(
    val id: Long,
    val lat: Double,
    val lon: Double,
    val name: String,
    val category: PoiCategory = PoiCategory.OTHER,
    val link: String? = null
)

@Serializable
data class RoutePlan(
    val city: String,
    val center: CityLocation,
    val stops: List<Poi>,
    val totalDistKm: Double,
    val estimatedTimeHours: Double
)


@kotlinx.serialization.Serializable
data class OverpassResponse(
    val elements: List<OverpassElement>
)

@Serializable
data class OverpassElement(
    val id: Long,
    val type: String? = null,
    val lat: Double? = null,
    val lon: Double? = null,
    val center: Center? = null,
    val tags: Map<String, String>? = null,
    val members: List<OverpassMember>? = null
)

@kotlinx.serialization.Serializable
data class Center(val lat: Double, val lon: Double)



@Serializable
data class CityContour(
    val points: List<GeoPoint>
)

@Serializable
data class GeoPoint(
    val lat: Double,
    val lon: Double
)

@Serializable
data class OverpassRelation(
    val id: Long,
    val members: List<OverpassMember>
)

@Serializable
data class OverpassMember(
    val role: String,
    val geometry: List<OverpassGeometry>? = null
)

@Serializable
data class OverpassGeometry(
    val lat: Double,
    val lon: Double
)
