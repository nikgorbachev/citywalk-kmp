package org.nikgor.project.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Refresh

import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.material.icons.filled.Share
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen() {

    // ---------- STATE ----------
    var isSearching by remember { mutableStateOf(true) }

    // Inputs
    var city by remember { mutableStateOf("") }
    var hours by remember { mutableStateOf("3") }
    var startFromStation by remember { mutableStateOf(false) }
    var needFood by remember { mutableStateOf(true) }

    // Logic
    var loading by remember { mutableStateOf(false) }
    var plan by remember { mutableStateOf<RoutePlan?>(null) }

    // UI Helpers
    val snackbarHostState = remember { SnackbarHostState() }
    val mapViewModel = remember { MapViewModel() }
    val scope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current
    val clipboardManager = LocalClipboardManager.current

    // Bottom Sheet State
    val scaffoldState = rememberBottomSheetScaffoldState(
        bottomSheetState = rememberStandardBottomSheetState(
            initialValue = SheetValue.PartiallyExpanded
        )
    )

    fun collapseSheet() {
        scope.launch { scaffoldState.bottomSheetState.partialExpand() }
    }

    // ---------- MAIN LAYOUT ----------
    BottomSheetScaffold(
        scaffoldState = scaffoldState,
        // Hide sheet peek when searching so it doesn't block the view
        sheetPeekHeight = if (isSearching) 0.dp else 140.dp,
        sheetShape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        sheetContainerColor = MaterialTheme.colorScheme.surface,
        sheetShadowElevation = 16.dp,
        sheetContent = {
            // We pass the full plan only if it exists
            if (plan != null) {
                ResultsSheet(
                    plan = plan!!,
                    onItemClick = { poi ->
                        scope.launch {
                            mapViewModel.centerOn(poi.lat, poi.lon)
                            collapseSheet()
                        }
                    },
                    uriHandler = uriHandler
                )
            } else {
                Box(Modifier.height(1.dp))
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { _ ->
        // NOTE: We ignore the 'padding' provided by Scaffold here.
        // This allows the Map/Content to draw BEHIND the bottom sheet,
        // fixing the white space issue on Desktop.

        Box(
            modifier = Modifier.fillMaxSize()
        ) {

            // 1. BACKGROUND LAYER (Map or Placeholder)
            // ------------------------------------------------------------------
            if (plan != null) {
                // Only load map logic when we actually have a plan
                MapLayer(
                    mapViewModel = mapViewModel,
                    onMapTouched = { collapseSheet() }
                )
            } else {
                // Nice gradient placeholder so we don't load "Ocean tiles" on start
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primaryContainer,
                                    MaterialTheme.colorScheme.surface
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    // Optional: Put a Logo or Icon here
                    Icon(
                        imageVector = Icons.Default.Place,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                        modifier = Modifier.size(120.dp)
                    )
                }
            }

            // 2. COMPACT HEADER (Top Bar)
            // ------------------------------------------------------------------
            // We use a Box to handle Status Bar insets safely
            AnimatedVisibility(
                visible = !isSearching,
                enter = slideInVertically { -it } + fadeIn(),
                exit = slideOutVertically { -it } + fadeOut(),
                modifier = Modifier.align(Alignment.TopCenter)
            ) {
                CompactHeader(
                    city = city,
                    hours = hours,
                    onEditClick = { isSearching = true },
                    onShareClick = {
                        plan?.let { currentPlan ->
                            shareItinerary(currentPlan, clipboardManager)
                            scope.launch { snackbarHostState.showSnackbar("Itinerary copied!") }
                        }
                    }
                )
            }

            // 3. SEARCH OVERLAY (The "Airbnb" Card)
            // ------------------------------------------------------------------
            AnimatedVisibility(
                visible = isSearching,
                enter = fadeIn() + slideInVertically { 50 },
                exit = fadeOut() + slideOutVertically { -50 },
                modifier = Modifier.align(Alignment.Center)
            ) {
                // Dim background
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background.copy(alpha = 0.8f))
                        .clickable(enabled = false) {} // block clicks
                ) {
                    SearchCard(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(24.dp)
                            // Limit width on Desktop so it doesn't stretch too wide
                            .widthIn(max = 500.dp),
                        city = city, onCityChange = { city = it },
                        hours = hours, onHoursChange = { hours = it },
                        station = startFromStation, onStationChange = { startFromStation = it },
                        food = needFood, onFoodChange = { needFood = it },
                        loading = loading,
                        onGenerate = {
                            if (city.isNotBlank()) {
                                loading = true
                                scope.launch {
                                    try {
                                        val planner = RoutePlanner()
                                        var route = planner.planRoute(
                                            city = city,
                                            hours = hours.toDoubleOrNull() ?: 3.0,
                                            startFromStation = startFromStation,
                                            includeFood = needFood
                                        )
                                        // Simple retry logic
                                        if (route.stops.size <= 1) {
                                            route = planner.planRoute(city, hours.toDoubleOrNull()?:3.0, startFromStation, needFood)
                                        }

                                        if (route.stops.size > 1) {
                                            plan = route
                                            isSearching = false
                                            updateMapMarkers(mapViewModel, route, uriHandler, scope, snackbarHostState)
                                        } else {
                                            snackbarHostState.showSnackbar("No POIs found. Try another city.")
                                        }
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                        snackbarHostState.showSnackbar("Error: ${e.message}")
                                    } finally {
                                        loading = false
                                    }
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}

// ------------------------------------------------------------------------
// SUB-COMPONENTS
// ------------------------------------------------------------------------

@Composable
fun SearchCard(
    modifier: Modifier = Modifier,
    city: String, onCityChange: (String) -> Unit,
    hours: String, onHoursChange: (String) -> Unit,
    station: Boolean, onStationChange: (Boolean) -> Unit,
    food: Boolean, onFoodChange: (Boolean) -> Unit,
    loading: Boolean,
    onGenerate: () -> Unit
) {
    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 8.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Text("Where to next?", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)

            OutlinedTextField(
                value = city, onValueChange = onCityChange,
                label = { Text("City Name") },
                leadingIcon = { Icon(Icons.Default.LocationOn, null) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = hours, onValueChange = onHoursChange,
                label = { Text("Time Available (Hours)") },
                leadingIcon = { Icon(Icons.Default.Refresh, null) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Divider()

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Start from Train Station")
                Switch(checked = station, onCheckedChange = onStationChange)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Include Food Stop")
                Switch(checked = food, onCheckedChange = onFoodChange)
            }

            Spacer(Modifier.height(8.dp))

            Button(
                onClick = onGenerate,
                enabled = !loading && city.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) {
                if (loading) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                else Text("Explore City", fontSize = 18.sp)
            }
        }
    }
}

@Composable
fun CompactHeader(
    city: String,
    hours: String,
    onEditClick: () -> Unit,
    onShareClick: () -> Unit
) {
    // We add a surface that stretches across the top
    Surface(
        shadowElevation = 4.dp,
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth()
    ) {
        // WindowInsets.statusBars gives us the correct top padding for Android
        Row(
            modifier = Modifier
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.clickable { onEditClick() }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(city, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Icon(Icons.Default.Edit, "Edit", modifier = Modifier.size(14.dp).padding(start = 4.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text("$hours hours • Walking", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onShareClick) {
                Icon(Icons.Default.Share, "Share")
            }
        }
    }
}

@Composable
fun ResultsSheet(
    plan: RoutePlan,
    onItemClick: (Poi) -> Unit,
    uriHandler: androidx.compose.ui.platform.UriHandler
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Box(modifier = Modifier.width(40.dp).height(4.dp).background(Color.LightGray, CircleShape).align(Alignment.CenterHorizontally).padding(vertical = 8.dp))
        Spacer(Modifier.height(16.dp))

        Text("Your Itinerary", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("${plan.totalDistKm.toInt()} km • ~${plan.estimatedTimeHours.toString().take(3)} hours", color = MaterialTheme.colorScheme.secondary)

        Spacer(Modifier.height(16.dp))

        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(plan.stops.size) { i ->
                val poi = plan.stops[i]
                Card(onClick = { onItemClick(poi) }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(28.dp).background(MaterialTheme.colorScheme.primary, CircleShape), contentAlignment = Alignment.Center) {
                            Text("${i + 1}", color = Color.White, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.width(16.dp))
                        Column {
                            Text(poi.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("${poi.category.name.lowercase().capitalize()} • ${poi.category.dwellTimeMin}m", style = MaterialTheme.typography.bodySmall)
                                if (poi.link != null) {
                                    Spacer(Modifier.width(8.dp))
                                    Text("Wiki ↗", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, textDecoration = TextDecoration.Underline, modifier = Modifier.clickable { uriHandler.openUri(poi.link.replace(" ", "%20")) })
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MapLayer(mapViewModel: MapViewModel, onMapTouched: () -> Unit) {
    val scope = rememberCoroutineScope()
    var zoomJob by remember { mutableStateOf<Job?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.changes.any { it.pressed }) onMapTouched()
                        if (event.type == PointerEventType.Scroll) {
                            val delta = event.changes.first().scrollDelta.y
                            val zoomFactor = if (delta > 0) 0.95f else 1.05f
                            val currentScale = mapViewModel.mapState.scale
                            val newScale = (currentScale * zoomFactor).coerceIn(0.005, 2.0)
                            zoomJob?.cancel()
                            zoomJob = scope.launch {
                                mapViewModel.mapState.scrollTo(x = mapViewModel.mapState.centroidX, y = mapViewModel.mapState.centroidY, destScale = newScale, animationSpec = SnapSpec())
                            }
                        }
                    }
                }
            }
    ) {
        MapUI(modifier = Modifier.fillMaxSize(), state = mapViewModel.mapState)
    }
}

// Helpers
fun updateMapMarkers(viewModel: MapViewModel, plan: RoutePlan, uriHandler: androidx.compose.ui.platform.UriHandler, scope: kotlinx.coroutines.CoroutineScope, snackbarHostState: SnackbarHostState) {
    viewModel.clearMarkers()
    viewModel.mapState.removePath("route")
    viewModel.mapState.addPath(id = "route", color = Color(0xFF1E88E5), width = 4.dp) {
        addPoints(plan.stops.map { lonToX(it.lon) to latToY(it.lat) })
    }
    plan.stops.forEachIndexed { index, poi ->
        viewModel.addClusterMarker(poi) {
            Icon(imageVector = Icons.Default.Place, contentDescription = poi.name, modifier = Modifier.size(32.dp).clickable {
                viewModel.showCallout(poi) {
                    Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surface, shadowElevation = 6.dp) {
                        Text(poi.name, modifier = Modifier.padding(8.dp), style = MaterialTheme.typography.labelMedium)
                    }
                }
            }, tint = if (index == 0) Color(0xFF2E7D32) else Color(0xFFD32F2F))
        }
    }
    scope.launch { viewModel.centerOn(plan.center.lat, plan.center.lon) }
}

fun shareItinerary(plan: RoutePlan, clipboardManager: androidx.compose.ui.platform.ClipboardManager) {
    val sb = StringBuilder().apply {
        appendLine("🚶 CityWalk: ${plan.city}")
        appendLine("📏 ${plan.totalDistKm.toInt()}km • ⏳ ~${plan.estimatedTimeHours.toString().take(3)}h")
        appendLine("---")
        plan.stops.forEachIndexed { i, poi -> appendLine("${i + 1}. ${poi.name} (${poi.category.name})") }
    }
    clipboardManager.setText(AnnotatedString(sb.toString()))
}

fun String.capitalize() = replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }