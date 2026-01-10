package org.nikgor.project.map


import org.nikgor.project.data.BoundingBox
import org.nikgor.project.data.GeoPoint

fun normalizePoints(
    points: List<GeoPoint>,
    bbox: BoundingBox
): List<Pair<Float, Float>> {
    return points.map {
        val x = ((it.lon - bbox.west) / (bbox.east - bbox.west)).toFloat()
        val y = (1f - (it.lat - bbox.south) / (bbox.north - bbox.south)).toFloat()
        x to y
    }
}