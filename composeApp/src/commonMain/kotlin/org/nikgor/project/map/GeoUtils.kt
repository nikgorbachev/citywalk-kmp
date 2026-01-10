package org.nikgor.project.map

import kotlin.math.*
import org.nikgor.project.data.CityLocation

fun lonToX(lon: Double): Double = (lon + 180.0) / 360.0

fun latToY(lat: Double): Double {
    val latRad = lat * PI / 180.0
    val merc = ln(tan((PI / 4.0) + (latRad / 2.0)))

    // Web Mercator Y (0 = North, 1 = South)
    return ((1.0 - (merc / PI)) / 2.0).coerceIn(0.0, 1.0)
}


fun distanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val R = 6371.0 // Radius of earth in km
    val dLat = (lat2 - lat1) * PI / 180.0
    val dLon = (lon2 - lon1) * PI / 180.0
    val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(lat1 * PI / 180.0) * cos(lat2 * PI / 180.0) *
            sin(dLon / 2) * sin(dLon / 2)
    val c = 2 * atan2(sqrt(a), sqrt(1 - a))
    return R * c
}

fun distanceKm(loc1: CityLocation, loc2: CityLocation): Double {
    return distanceKm(loc1.lat, loc1.lon, loc2.lat, loc2.lon)
}