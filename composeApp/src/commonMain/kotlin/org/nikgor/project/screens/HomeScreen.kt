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
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextDecoration
import kotlin.math.roundToInt

@Composable
fun HomeScreen() {

    // ---------- UI STATE ----------
    var city by remember { mutableStateOf("") }
    var hours by remember { mutableStateOf("3") }

    var startFromStation by remember { mutableStateOf(false) }
    var needFood by remember { mutableStateOf(true) }

    var loading by remember { mutableStateOf(false) }
    var plan by remember { mutableStateOf<RoutePlan?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }

    val mapViewModel = remember { MapViewModel() }
    val scope = rememberCoroutineScope()
    var zoomJob by remember { mutableStateOf<Job?>(null) }

    val uriHandler = LocalUriHandler.current
    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { paddingValues ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {

            Text("MarcheRoute", style = MaterialTheme.typography.headlineMedium)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextField(
                    value = city,
                    onValueChange = { city = it },
                    label = { Text("City") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                TextField(
                    value = hours,
                    onValueChange = { hours = it },
                    label = { Text("Hours") },
                    singleLine = true,
                    modifier = Modifier.width(100.dp)
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = startFromStation, onCheckedChange = { startFromStation = it })
                Text("Start from Station")
                Spacer(Modifier.width(16.dp))
                Checkbox(checked = needFood, onCheckedChange = { needFood = it })
                Text("Include Food")
            }

            Button(
                enabled = !loading && city.isNotBlank(),
                onClick = {
                    scope.launch {
                        loading = true
                        try {
                            val planner = RoutePlanner()
                            var route = planner.planRoute(
                                city = city,
                                hours = hours.toDoubleOrNull() ?: 3.0,
                                startFromStation = startFromStation,
                                includeFood = needFood
                            )
                            if (route.stops.size <= 1) {
                                println("First attempt yielded ${route.stops.size} stops. Retrying...")
                                route = planner.planRoute(
                                    city = city,
                                    hours = hours.toDoubleOrNull() ?: 3.0,
                                    startFromStation = startFromStation,
                                    includeFood = needFood
                                )
                            }

                            if (route.stops.size > 1) {
                                plan = route
                            } else {
                                snackbarHostState.showSnackbar("No points of interest found. Please try again or try a different city.")
                            }

                        } catch (e: Exception) {
                            println("Error generating route: ${e.message}")
                            e.printStackTrace()
                            snackbarHostState.showSnackbar("Failed to generate route. Internet issues? Please try again in a minute")
                        } finally {
                            loading = false
                        }
                    }
                }
            ) {
                Text(if (loading) "Generating..." else "Generate Route")
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

                                    // Cancel the previous job before starting a new one
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


            LaunchedEffect(plan) {
                val currentPlan = plan ?: return@LaunchedEffect

                // 1. CLEAR OLD MARKERS (Fixes duplicate marker bug)
                mapViewModel.clearMarkers()
                mapViewModel.mapState.removePath("route")

                // 2. Add New POI markers
                currentPlan.stops.forEachIndexed { index, poi ->
                    mapViewModel.addClusterMarker(
                        id = "poi-${poi.id}",
                        lat = poi.lat,
                        lon = poi.lon
                    ) {
                        Icon(
                            imageVector = Icons.Default.Place,
                            contentDescription = poi.name,
                            modifier = Modifier.size(32.dp),
                            tint = if (index == 0) Color.Green else Color.Red
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

                mapViewModel.centerOn(currentPlan.center.lat, currentPlan.center.lon)
            }



            plan?.let { currentPlan ->
                Spacer(Modifier.height(8.dp))

                // Format Time: "2h 45m"
                val totalHours = currentPlan.estimatedTimeHours.toInt()
                val totalMinutes = ((currentPlan.estimatedTimeHours - totalHours) * 60).roundToInt()

                Text(
                    "Route: ${currentPlan.totalDistKm.toInt()}km • ${totalHours}h ${totalMinutes}m",
                    style = MaterialTheme.typography.titleMedium
                )

                androidx.compose.foundation.lazy.LazyColumn(
                    modifier = Modifier.fillMaxWidth()
                        .height(200.dp), // Fixed height list at bottom
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(currentPlan.stops.size) { i ->
                        val poi = currentPlan.stops[i]
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .background(
                                            MaterialTheme.colorScheme.primary,
                                            shape = androidx.compose.foundation.shape.CircleShape
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        "${i + 1}",
                                        color = Color.White,
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text(
                                        poi.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                                    )
                                    Text(
                                        "${
                                            poi.category.name.replace(
                                                "_",
                                                " "
                                            )
                                        } • ${poi.category.dwellTimeMin} min",
                                        style = MaterialTheme.typography.labelSmall
                                    )


                                    if (poi.link != null) {
                                        Text(
                                            text = "Open Website/Wiki ↗",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary,
                                            textDecoration = TextDecoration.Underline,
                                            modifier = Modifier.clickable {
                                                try {
                                                    val cleanUrl = poi.link.replace(" ", "%20")
                                                    uriHandler.openUri(cleanUrl)
                                                } catch (e: Exception) {
                                                    println("Failed to open link: ${poi.link}: ${e.message}")
                                                    scope.launch {
                                                        snackbarHostState.showSnackbar("Could not open link. Invalid URL format.")
                                                    }
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
