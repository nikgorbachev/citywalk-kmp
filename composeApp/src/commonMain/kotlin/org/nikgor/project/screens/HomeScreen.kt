package org.nikgor.project.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.nikgor.project.data.*
import org.nikgor.project.routing.RoutePlanner
import ovh.plrapps.mapcompose.ui.MapUI
import ovh.plrapps.mapcompose.api.*

import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.Icons
import kotlinx.coroutines.Job

import org.nikgor.project.map.*

import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.animation.core.SnapSpec


@Composable
fun HomeScreen() {

    // ---------- UI STATE ----------
    var city by remember { mutableStateOf("") }
    var hours by remember { mutableStateOf("3") }
    var loading by remember { mutableStateOf(false) }

    // ---------- DATA ----------
    var plan by remember { mutableStateOf<RoutePlan?>(null) }

    // Used ONLY to trigger centering after layout
    var pendingCenter by remember { mutableStateOf<Pair<Double, Double>?>(null) }

    val mapViewModel = remember { MapViewModel() }
    val scope = rememberCoroutineScope()

    var zoomJob by remember { mutableStateOf<Job?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {

        Text("MarcheRoute", style = MaterialTheme.typography.headlineMedium)

        TextField(
            value = city,
            onValueChange = { city = it },
            label = { Text("City") },
            singleLine = true
        )

        TextField(
            value = hours,
            onValueChange = { hours = it },
            label = { Text("Hours available") },
            singleLine = true
        )

        Button(
            enabled = !loading && city.isNotBlank(),
            onClick = {
                scope.launch {
                    loading = true
                    try {
                        val route = RoutePlanner().planRoute(
                            city = city,
                            hours = hours.toDoubleOrNull() ?: 3.0
                        )
                        plan = route
                        pendingCenter = route.center.lat to route.center.lon
                    } catch (e: Exception) {
                        println("Error generating route: ${e.message}")
                        e.printStackTrace()
                    } finally {
                        loading = false
                    }
                }
            }
        ) {
            Text("Generate route")
        }

        if (loading) CircularProgressIndicator()

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(400.dp)
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            if (event.type == PointerEventType.Scroll) {
                                val delta = event.changes.first().scrollDelta.y


                                // 0.95/1.05 is much less "jumpy" for trackpads than 0.90/1.10
                                val zoomFactor = if (delta > 0) 0.95f else 1.05f

                                val currentScale = mapViewModel.mapState.scale
                                val newScale = (currentScale * zoomFactor).coerceIn(0.005, 2.0)

                                val cx = mapViewModel.mapState.centroidX
                                val cy = mapViewModel.mapState.centroidY

                                // 2. THE FIX: Cancel the previous job before starting a new one
                                // This stops the "glitch" where old scrolls overwrite new ones
                                zoomJob?.cancel()

                                zoomJob = scope.launch {
                                    mapViewModel.mapState.scrollTo(
                                        x = cx,
                                        y = cy,
                                        destScale = newScale,
                                        animationSpec = SnapSpec()
                                    )
                                }
                            }
                        }
                    }
                }
        ) {
            MapUI(
                modifier = Modifier.fillMaxSize(),
                state = mapViewModel.mapState
            )
        }

        // ---------- APPLY ROUTE ----------
        // ... inside HomeScreen Composable ...
        LaunchedEffect(plan) {
            val currentPlan = plan ?: return@LaunchedEffect

            // 1. Clear previous markers/paths if necessary
            mapViewModel.mapState.removePath("route")

            // 2. Add POI markers
            currentPlan.stops.forEach { poi ->
                mapViewModel.mapState.addMarker(
                    id = "poi-${poi.id}",
                    x = lonToX(poi.lon),
                    y = latToY(poi.lat)
                ) {
                    Icon(
                        imageVector = Icons.Default.Place,
                        contentDescription = poi.name,
                        modifier = Modifier.size(32.dp),
                        tint = Color.Red
                    )
                }
            }

            // 3. Draw the walking route
            mapViewModel.mapState.addPath(
                id = "route",
                color = Color(0xFF1E88E5),
                width = 4.dp
            ) {
                addPoints(currentPlan.stops.map {
                    lonToX(it.lon) to latToY(it.lat)
                })
            }

            // 4. Center the map
            mapViewModel.centerOn(currentPlan.center.lat, currentPlan.center.lon)
        }


        // ---------- TEXT LIST ----------
        plan?.let {
            Spacer(Modifier.height(8.dp))
            Text("Stops:", style = MaterialTheme.typography.titleMedium)
            it.stops.forEachIndexed { i, poi ->
                Text("${i + 1}. ${poi.name}")
            }
        }
    }
}
