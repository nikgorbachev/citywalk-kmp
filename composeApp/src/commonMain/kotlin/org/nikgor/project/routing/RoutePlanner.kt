package org.nikgor.project.routing

import org.nikgor.project.data.*
import org.nikgor.project.map.distanceKm

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
        // 1. Geocode
        val (cityCenter, bbox) = geocoder.geocode(city)

        // 2. Start Point
        var startPoi: Poi? = null
        if (startFromStation) {
            val station = overpass.queryMainStation(bbox, cityCenter)
            if (station != null) startPoi = station
        }
        if (startPoi == null) {
            startPoi = Poi(-1, cityCenter.lat, cityCenter.lon, "Start (City Center)", PoiCategory.OTHER)
        }

        // 3. Fetch
        val allPois = overpass.queryPois(bbox) // Don't shuffle yet

        // 4. Selection Buckets
        val restaurants = allPois.filter { it.category == PoiCategory.RESTAURANT }
        val cafes = allPois.filter { it.category == PoiCategory.CAFE }

        // Group everything else for the "Sightseeing" part
        val sights = allPois.filter {
            it.category != PoiCategory.RESTAURANT && it.category != PoiCategory.CAFE
        }

        val selectedSet = mutableListOf<Poi>()

        // 4a. Must Haves (Randomize specific high-value targets)
        val landmarks = sights.filter { it.category == PoiCategory.LANDMARK }.shuffled()
        val historic = sights.filter { it.category == PoiCategory.HISTORIC_SITE }.shuffled()

        landmarks.firstOrNull()?.let { selectedSet.add(it) }
        historic.firstOrNull()?.let { selectedSet.add(it) }

        // 4b. Fill Budget with WEIGHTED Randomness
        // Shuffle first to get variety, THEN sort by weight to prioritize quality
        val fillerPool = sights
            .filter { !selectedSet.contains(it) }
            .shuffled()
            .sortedByDescending { it.category.weight }

        val estimatedStops = (hours / 0.75).toInt().coerceAtLeast(selectedSet.size + 2)

        for (poi in fillerPool) {
            if (selectedSet.size >= estimatedStops) break
            selectedSet.add(poi)
        }

        // 4c. SMART FOOD SELECTION [Fix for Missing Food]
        // Instead of random food, pick food closest to the "Center of Mass" of our selected sights
        if (includeFood && selectedSet.isNotEmpty()) {
            // Calculate average Lat/Lon of selected sights
            val avgLat = selectedSet.map { it.lat }.average()
            val avgLon = selectedSet.map { it.lon }.average()
            val routeCenter = CityLocation(avgLat, avgLon)

            // Find closest restaurant to the sights
            val bestRestaurant = restaurants.minByOrNull {
                distanceKm(routeCenter, CityLocation(it.lat, it.lon))
            }
            bestRestaurant?.let { selectedSet.add(it) }

            // Find closest cafe
            val bestCafe = cafes.minByOrNull {
                distanceKm(routeCenter, CityLocation(it.lat, it.lon))
            }
            bestCafe?.let { selectedSet.add(it) }
        }

        // 5. Optimization (TSP / Nearest Neighbor)
        val optimizedRoute = mutableListOf<Poi>()
        optimizedRoute.add(startPoi)

        var currentLocation = CityLocation(startPoi.lat, startPoi.lon)
        var currentTimeMinutes = 0.0
        val maxMinutes = hours * 60.0
        var totalDist = 0.0

        val candidates = selectedSet.toMutableList()
        candidates.removeAll { it.id == startPoi.id }

        while (candidates.isNotEmpty()) {
            // MAGNET HEURISTIC
            val target = if (optimizedRoute.size == 1 && startFromStation) cityCenter else currentLocation

            val nearest = candidates.minByOrNull { poi ->
                distanceKm(target, CityLocation(poi.lat, poi.lon))
            } ?: break

            val distKm = distanceKm(currentLocation, CityLocation(nearest.lat, nearest.lon))
            val walkTimeMin = (distKm / walkingSpeedKmH) * 60
            val dwellTime = nearest.category.dwellTimeMin.toDouble()
            val cost = walkTimeMin + dwellTime

            if (currentTimeMinutes + cost <= maxMinutes) {
                optimizedRoute.add(nearest)
                currentTimeMinutes += cost
                totalDist += distKm
                currentLocation = CityLocation(nearest.lat, nearest.lon)
                candidates.remove(nearest)
            } else {
                candidates.remove(nearest)
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