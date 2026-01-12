package org.nikgor.project.map

import androidx.compose.runtime.mutableStateListOf
import ovh.plrapps.mapcompose.api.*
import ovh.plrapps.mapcompose.ui.state.MapState
import kotlin.math.pow
import kotlin.math.pow
import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import org.nikgor.project.data.Poi

class MapViewModel {
    private val maxZoom = 19
    private val tileSize = 256
    private val mapSize = (tileSize.toDouble() * 2.0.pow(maxZoom)).toInt()

    // Tracking marker IDs to clear them later
    private val markerIds = mutableListOf<String>()


    private val poiMap = mutableMapOf<String, Poi>()

    val mapState = MapState(
        levelCount = maxZoom + 1,
        fullWidth = mapSize,
        fullHeight = mapSize,
        workerCount = 16
    ).apply {
        addLayer(osmTileProvider())
        onMarkerClick { id, x, y ->
            val poi = poiMap[id] ?: return@onMarkerClick

            // Remove any old callout
            removeCallout("callout")

            // Add new callout above the marker
            addCallout(
                id = "callout",
                x = x,
                y = y,
                absoluteOffset = DpOffset(0.dp, (-50).dp), // Shift up to sit on top of pin
                autoDismiss = true // Dismiss on map touch
            ) {
                // `addCallout` expects a composable.
            }
        }
    }



    fun addClusterMarker(poi: Poi, c: @Composable () -> Unit) {
        val id = "poi-${poi.id}"
        markerIds.add(id)
        poiMap[id] = poi

        mapState.addMarker(
            id = id,
            x = lonToX(poi.lon),
            y = latToY(poi.lat),
            relativeOffset = Offset(-0.5f, -1.0f),
            c = c
        )
    }

    fun showCallout(poi: Poi, content: @Composable () -> Unit) {
        mapState.addCallout(
            id = "active-callout",
            x = lonToX(poi.lon),
            y = latToY(poi.lat),
            absoluteOffset = DpOffset(0.dp, (-50).dp),
            autoDismiss = true,
            c = content
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