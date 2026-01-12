package org.nikgor.project.routing

import org.nikgor.project.data.*
import org.nikgor.project.map.distanceKm
import kotlin.math.pow

class RoutePlanner {

    private val geocoder = GeoCoder()
    private val overpass = OverpassClient()
    private val walkingSpeedKmH = 4.5

    suspend fun planRoute(
        city: String,
        hours: Double,
        startFromStation: Boolean,
        includeFood: Boolean
    ): RoutePlan {
        // 1. Geocoding
        val (cityCenter, bbox) = geocoder.geocode(city)

        // 2. Determining Start Point
        var startPoi: Poi? = null
        if (startFromStation) {
            val station = overpass.queryMainStation(bbox, cityCenter)
            if (station != null) startPoi = station
        }
        if (startPoi == null) {
            val label = if (startFromStation) "City Center (Station not found)" else "Start (City Center)"
            startPoi = Poi(-1, cityCenter.lat, cityCenter.lon, label, PoiCategory.OTHER)
        }

        // 3. Fetching POIs
        val allPois = overpass.queryPois(bbox).shuffled()

        // 4. Selection sorting into buckets
        val restaurants = allPois.filter { it.category == PoiCategory.RESTAURANT }
        val cafes = allPois.filter { it.category == PoiCategory.CAFE }

        val sights = allPois.filter {
            it.category != PoiCategory.RESTAURANT && it.category != PoiCategory.CAFE
        }

        val selectedSet = mutableListOf<Poi>()

        // 4a. Must Haves
        val landmarks = sights.filter { it.category == PoiCategory.LANDMARK }.shuffled()
        val historic = sights.filter { it.category == PoiCategory.HISTORIC_SITE }.shuffled()
        val museums = sights.filter { it.category == PoiCategory.MUSEUM }.shuffled()
        val parks = sights.filter { it.category == PoiCategory.PARK }.shuffled()

        landmarks.firstOrNull()?.let { selectedSet.add(it) }
        historic.firstOrNull()?.let { selectedSet.add(it) }
        museums.firstOrNull()?.let { selectedSet.add(it) }
        parks.firstOrNull()?.let { selectedSet.add(it) }

        // 4b. Filling Time-budget (Just shuffle, the router will sort by weight/dist later)
        val fillerPool = sights
            .filter { !selectedSet.contains(it) }
            .shuffled()

        // Heuristic: Select roughly 1.5x what we think we can fit, so we have options
        val estimatedCapacity = (hours / 0.5).toInt().coerceAtLeast(selectedSet.size + 5)

        for (poi in fillerPool) {
            if (selectedSet.size >= estimatedCapacity) break
            selectedSet.add(poi)
        }

        // 4c. FOOD SELECTION
        // Find food close to the "Center of Mass" of the selected sights
        if (includeFood && selectedSet.isNotEmpty()) {
            val avgLat = selectedSet.map { it.lat }.average()
            val avgLon = selectedSet.map { it.lon }.average()
            val routeCenter = CityLocation(avgLat, avgLon)

            val bestRestaurant = restaurants.minByOrNull {
                distanceKm(routeCenter, CityLocation(it.lat, it.lon))
            }
            bestRestaurant?.let { selectedSet.add(it) }
            cafes.shuffled().firstOrNull()?.let { selectedSet.add(it) }

            // Putting the center-most restaurant and a random cafe
        }

        // 5. WEIGHTED GREEDY ROUTING
        val optimizedRoute = mutableListOf<Poi>()
        optimizedRoute.add(startPoi)

        var currentLocation = CityLocation(startPoi.lat, startPoi.lon)
        var currentTimeMinutes = 0.0
        val maxMinutes = hours * 60.0
        var totalDist = 0.0

        val candidates = selectedSet.toMutableList()
        candidates.removeAll { it.id == startPoi.id }

        while (candidates.isNotEmpty()) {
            val nextPoi = if (optimizedRoute.size == 1 && startFromStation) {
                // HEURISTIC: First step from station -> Go towards City Center
                candidates.minByOrNull { poi ->
                    distanceKm(cityCenter, CityLocation(poi.lat, poi.lon))
                }
            } else {
                // WEIGHTED GREEDY HEURISTIC: Score = Weight / (Distance + Bias)
                // Bias (0.5km) prevents jumping to a boring statue 10m away instead of a Cathedral 500m away.
                candidates.maxByOrNull { poi ->
                    val dist = distanceKm(currentLocation, CityLocation(poi.lat, poi.lon))
                    // Use pow(1.5) to penalize long distances slightly more than linear
                    val score = poi.category.weight / (dist + 0.5).pow(1.5)

//                    if (poi.category == PoiCategory.RESTAURANT || poi.category == PoiCategory.CAFE) {
//                        score *= 5.0
//                    }

                    score
                }
            } ?: break

            val distKm = distanceKm(currentLocation, CityLocation(nextPoi.lat, nextPoi.lon))
            val walkTimeMin = (distKm / walkingSpeedKmH) * 60
            val dwellTime = nextPoi.category.dwellTimeMin.toDouble()
            val cost = walkTimeMin + dwellTime

            if (currentTimeMinutes + cost <= maxMinutes) {
                optimizedRoute.add(nextPoi)
                currentTimeMinutes += cost
                totalDist += distKm
                currentLocation = CityLocation(nextPoi.lat, nextPoi.lon)
                candidates.remove(nextPoi)
            } else {
                // Doesn't fit. Remove and try the next best candidate in the next iteration
                candidates.remove(nextPoi)
            }
        }

        return RoutePlan(
            city = city,
            center = CityLocation(startPoi.lat, startPoi.lon),
            stops = optimizedRoute,
            totalDistKm = totalDist,
            estimatedTimeHours = currentTimeMinutes / 60.0
        )
    }
}