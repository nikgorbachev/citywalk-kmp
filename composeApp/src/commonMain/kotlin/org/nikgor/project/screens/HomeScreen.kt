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


import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Refresh

import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.material.icons.filled.Share
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.sp


private val MapBlue = Color(0xFF0288D1)         // Main color
private val MapBlueDark = Color(0xFF01579B)     // Darker for contrast
private val MarkerOrange = Color(0xFFF57C00)    // Orange for stops
private val MarkerGreen = Color(0xFF2E7D32)     // Green for start
private val RouteSlate = Color(0xFF37474F)      // Grey for the path
private val TextPrimary = Color(0xFF263238)     // Blue-Grey
private val TextSecondary = Color(0xFF546E7A)   // Blue-Grey Light
private val SurfaceWhite = Color(0xFFFFFFFF)
private val BackgroundGray = Color(0xFFF5F7FA)

private val AppTheme = lightColorScheme(
    primary = MapBlue,
    onPrimary = Color.White,
    secondary = TextSecondary,
    surface = SurfaceWhite,
    background = BackgroundGray,
    primaryContainer = MapBlue.copy(alpha = 0.1f), // Used for light backgrounds
    onSurface = TextPrimary
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen() {

    MaterialTheme(colorScheme = AppTheme) {
        var isSearching by remember { mutableStateOf(true) }
        var city by remember { mutableStateOf("") }
        var hours by remember { mutableStateOf("3") }
        var startFromStation by remember { mutableStateOf(false) }
        var needFood by remember { mutableStateOf(true) }
        var loading by remember { mutableStateOf(false) }
        var plan by remember { mutableStateOf<RoutePlan?>(null) }

        val snackbarHostState = remember { SnackbarHostState() }
        val mapViewModel = remember { MapViewModel() }
        val scope = rememberCoroutineScope()
        val uriHandler = LocalUriHandler.current
        val clipboardManager = LocalClipboardManager.current

        val scaffoldState = rememberBottomSheetScaffoldState(
            bottomSheetState = rememberStandardBottomSheetState(
                initialValue = SheetValue.PartiallyExpanded,
                skipHiddenState = false
            )
        )

        fun collapseSheet() {
            scope.launch { scaffoldState.bottomSheetState.partialExpand() }
        }

        BottomSheetScaffold(
            scaffoldState = scaffoldState,
            sheetPeekHeight = if (isSearching) 0.dp else 140.dp,
            sheetShape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            sheetContainerColor = MaterialTheme.colorScheme.surface,
            sheetShadowElevation = 16.dp,
            sheetContent = {
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
            Box(modifier = Modifier.fillMaxSize()) {

                // MAP LAYER
                if (plan != null) {
                    MapLayer(
                        mapViewModel = mapViewModel,
                        onMapTouched = { collapseSheet() }
                    )
                } else {
                    // Start Screen Gradient
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color(0xFFE1F5FE),
                                        Color.White
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Place,
                            contentDescription = null,
                            tint = MapBlue.copy(alpha = 0.2f),
                            modifier = Modifier.size(120.dp)
                        )
                    }
                }

                // HEADER
                AnimatedVisibility(
                    visible = !isSearching,
                    enter = slideInVertically { -it } + fadeIn(),
                    exit = slideOutVertically { -it } + fadeOut(),
                    modifier = Modifier.align(Alignment.TopCenter)
                ) {
                    CompactHeader(
                        city = city,
                        hours = hours,
                        onEditClick = {
                            isSearching = true
                            scope.launch { scaffoldState.bottomSheetState.partialExpand() }
                        },
                        onShareClick = {
                            plan?.let { currentPlan ->
                                shareItinerary(currentPlan, clipboardManager)
                                scope.launch { snackbarHostState.showSnackbar("Itinerary copied!") }
                            }
                        }
                    )
                }

                // SEARCH CARD
                AnimatedVisibility(
                    visible = isSearching,
                    enter = fadeIn() + slideInVertically { 50 },
                    exit = fadeOut() + slideOutVertically { -50 },
                    modifier = Modifier.align(Alignment.Center)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.8f))
                            .clickable(enabled = false) {}
                    ) {
                        SearchCard(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(24.dp)
                                .widthIn(max = 500.dp),
                            city = city, onCityChange = { city = it },
                            hours = hours, onHoursChange = { hours = it },
                            station = startFromStation, onStationChange = { startFromStation = it },
                            food = needFood, onFoodChange = { needFood = it },
                            loading = loading,
                            showClose = plan != null,
                            onClose = { isSearching = false },
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
                                            if (route.stops.size <= 1) {
                                                route = planner.planRoute(city, hours.toDoubleOrNull()?:3.0, startFromStation, needFood)
                                            }

                                            if (route.stops.size > 1) {
                                                plan = route
                                                isSearching = false
                                                updateMapMarkers(mapViewModel, route, uriHandler, scope, snackbarHostState)
                                                scaffoldState.bottomSheetState.partialExpand()
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
    showClose: Boolean,
    onClose: () -> Unit,
    onGenerate: () -> Unit
) {
    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 8.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Where to next?", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                if (showClose) {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.Gray)
                    }
                }
            }

            OutlinedTextField(
                value = city, onValueChange = onCityChange,
                label = { Text("City Name") },
                leadingIcon = { Icon(Icons.Default.LocationOn, null) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )

            OutlinedTextField(
                value = hours, onValueChange = onHoursChange,
                label = { Text("Time Available (Hours)") },
                leadingIcon = { Icon(Icons.Default.Refresh, null) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )

            Divider(color = Color.LightGray.copy(alpha = 0.3f))


            val switchColors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.primary,
                checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                uncheckedThumbColor = Color.LightGray,
                uncheckedTrackColor = Color.White,
                uncheckedBorderColor = Color.LightGray
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Start from Train Station")
                Switch(checked = station, onCheckedChange = onStationChange, colors = switchColors)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Include Food Stop")
                Switch(checked = food, onCheckedChange = onFoodChange, colors = switchColors)
            }

            Spacer(Modifier.height(8.dp))

            Button(
                onClick = onGenerate,
                enabled = !loading && city.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                if (loading) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                else Text("Explore City", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
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
    Surface(
        shadowElevation = 6.dp,
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp)
    ) {
        Row(
            modifier = Modifier
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 20.dp, vertical = 14.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.clickable { onEditClick() }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(city, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Icon(Icons.Default.Edit, "Edit", modifier = Modifier.size(14.dp).padding(start = 4.dp), tint = MaterialTheme.colorScheme.primary)
                }
                Text("$hours hours • Walking", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
            }
            FilledIconButton(
                onClick = onShareClick,
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Icon(Icons.Default.Share, "Share", tint = MaterialTheme.colorScheme.primary)
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
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        Box(modifier = Modifier.width(40.dp).height(4.dp).background(Color.LightGray.copy(alpha=0.6f), CircleShape).align(Alignment.CenterHorizontally).padding(vertical = 8.dp))
        Spacer(Modifier.height(16.dp))

        Text("Your Itinerary", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("${plan.totalDistKm.toInt()} km • ~${plan.estimatedTimeHours.toString().take(3)} hours", color = MaterialTheme.colorScheme.secondary)

        Spacer(Modifier.height(20.dp))

        LazyColumn(contentPadding = PaddingValues(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            items(plan.stops.size) { i ->
                val poi = plan.stops[i]
                Card(
                    onClick = { onItemClick(poi) },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.LightGray.copy(alpha = 0.2f))
                ) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {

                        Box(
                            modifier = Modifier.size(32.dp).background(MaterialTheme.colorScheme.primary, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("${i + 1}", color = Color.White, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        }

                        Spacer(Modifier.width(16.dp))

                        Column {
                            Text(poi.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("${poi.category.name.lowercase().capitalize()} • ${poi.category.dwellTimeMin}m", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
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

fun updateMapMarkers(viewModel: MapViewModel, plan: RoutePlan, uriHandler: androidx.compose.ui.platform.UriHandler, scope: kotlinx.coroutines.CoroutineScope, snackbarHostState: SnackbarHostState) {
    viewModel.clearMarkers()
    viewModel.mapState.removePath("route")

    // Path
    viewModel.mapState.addPath(id = "route", color = RouteSlate, width = 5.dp) {
        addPoints(plan.stops.map { lonToX(it.lon) to latToY(it.lat) })
    }

    plan.stops.forEachIndexed { index, poi ->
        viewModel.addClusterMarker(poi) {
            // MARKERS
            val tint = if (index == 0) MarkerGreen else MarkerOrange
            Icon(
                imageVector = Icons.Default.Place,
                contentDescription = poi.name,
                modifier = Modifier.size(40.dp).clickable {
                    viewModel.showCallout(poi) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color.White,
                            shadowElevation = 8.dp,
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color.LightGray.copy(alpha=0.3f))
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    poi.name,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.Black
                                )
                                Spacer(Modifier.height(4.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        "${poi.category.name.lowercase().capitalize()} • ${poi.category.dwellTimeMin}m",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.Gray
                                    )
                                    if (poi.link != null) {
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            "Wiki ↗",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MapBlue,
                                            textDecoration = TextDecoration.Underline,
                                            modifier = Modifier.clickable { uriHandler.openUri(poi.link.replace(" ", "%20")) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                },
                tint = tint
            )
        }
    }
    scope.launch { viewModel.centerOn(plan.center.lat, plan.center.lon) }
}

fun shareItinerary(plan: RoutePlan, clipboardManager: androidx.compose.ui.platform.ClipboardManager) {
    val sb = StringBuilder().apply {
        appendLine("🚶 CityWalk: ${plan.city}")
        appendLine("📏 ${plan.totalDistKm.toInt()}km • ⏳ ~${plan.estimatedTimeHours.toString().take(3)}h")
        appendLine("---")
        plan.stops.forEachIndexed { i, poi ->
            append("${i + 1}. ${poi.name} (${poi.category.name})")
            if (poi.link != null) {
                append(" - ${poi.link.replace(" ", "%20")}")
            }
            appendLine()
        }
    }
    clipboardManager.setText(AnnotatedString(sb.toString()))
}

fun String.capitalize() = replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }