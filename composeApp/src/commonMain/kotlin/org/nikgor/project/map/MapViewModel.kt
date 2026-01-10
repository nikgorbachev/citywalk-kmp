package org.nikgor.project.map

import androidx.compose.runtime.mutableStateListOf
import ovh.plrapps.mapcompose.api.*
import ovh.plrapps.mapcompose.ui.state.MapState
import kotlin.math.pow
import kotlin.math.pow
import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Offset

class MapViewModel {
    private val maxZoom = 19
    private val tileSize = 256
    private val mapSize = (tileSize.toDouble() * 2.0.pow(maxZoom)).toInt()

    // Tracking marker IDs to clear them later
    private val markerIds = mutableListOf<String>()

    val mapState = MapState(
        levelCount = maxZoom + 1,
        fullWidth = mapSize,
        fullHeight = mapSize,
        workerCount = 16
    ).apply {
        addLayer(osmTileProvider())
    }


    fun addClusterMarker(id: String, lat: Double, lon: Double, c: @Composable () -> Unit) {
        markerIds.add(id)
        mapState.addMarker(
            id = id,
            x = lonToX(lon),
            y = latToY(lat),

            // -0.5f centers it horizontally
            // -1.0f aligns the bottom of the icon to the latitude
            relativeOffset = Offset(-0.5f, -1.0f),
            c = c
        )
    }

    fun clearMarkers() {
        markerIds.forEach { id ->
            mapState.removeMarker(id)
        }
        markerIds.clear()
    }

    suspend fun centerOn(lat: Double, lon: Double) {
        mapState.scrollTo(
            x = lonToX(lon),
            y = latToY(lat),
            destScale = 0.05
        )
    }
}