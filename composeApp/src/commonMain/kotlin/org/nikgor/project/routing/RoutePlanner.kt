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

        // 2. Determine Start Point (The "Zero" Point)
        var startPoi: Poi? = null

        if (startFromStation) {
            val station = overpass.queryMainStation(bbox, cityCenter)
            if (station != null) {
                startPoi = station
            }
        }

        // If no station requested (or found), we start at the city center
        // We create a "Virtual" POI for the center so it appears on the map
        if (startPoi == null) {
            startPoi = Poi(-1, cityCenter.lat, cityCenter.lon, "Start (City Center)", PoiCategory.OTHER)
        }

        // 3. Fetch & Randomize Candidates
        val allPois = overpass.queryPois(bbox).shuffled()

        // 4. "The Grand Tour" Selection (Diversity First)
        val restaurants = allPois.filter { it.category == PoiCategory.RESTAURANT }
        val cafes = allPois.filter { it.category == PoiCategory.CAFE }
        val landmarks = allPois.filter { it.category == PoiCategory.LANDMARK }
        val historicSites = allPois.filter { it.category == PoiCategory.HISTORIC_SITE }
        val museums = allPois.filter { it.category == PoiCategory.MUSEUM }
        val parks = allPois.filter { it.category == PoiCategory.PARK }
        val others = allPois.filter { it.category == PoiCategory.OTHER }

        val selectedSet = mutableListOf<Poi>()

        // 4a. Must Haves
        landmarks.firstOrNull()?.let { selectedSet.add(it) }
        historicSites.firstOrNull()?.let { selectedSet.add(it) }

//        museums.firstOrNull()?.let { selectedSet.add(it) }
//        parks.firstOrNull()?.let { selectedSet.add(it) }

        if (includeFood) {
            restaurants.firstOrNull()?.let { selectedSet.add(it) }
            cafes.firstOrNull()?.let { selectedSet.add(it) }
        }

        // 4b. Fill the budget
        val estimatedStops = (hours / 0.75).toInt().coerceAtLeast(selectedSet.size + 2)
        val fillerPool = (landmarks.drop(1) + historicSites.drop(1) + museums.drop(1) + parks.drop(1) + others).shuffled()

        // Add unique items until we reach capacity
        for (poi in fillerPool) {
            if (selectedSet.size >= estimatedStops) break
            if (!selectedSet.contains(poi)) selectedSet.add(poi)
        }

        // 5. Optimization & Routing
        val optimizedRoute = mutableListOf<Poi>()

        // [FIX 1] EXPLICITLY ADD THE START POINT FIRST
        optimizedRoute.add(startPoi)

        var currentLocation = CityLocation(startPoi.lat, startPoi.lon)
        var currentTimeMinutes = 0.0
        val maxMinutes = hours * 60.0
        var totalDist = 0.0

        val candidates = selectedSet.toMutableList()
        // Ensure we don't visit the start point again if it happened to be in the list
        candidates.removeAll { it.id == startPoi.id }

        while (candidates.isNotEmpty()) {
            val nearest = if (optimizedRoute.size == 1 && startFromStation) {
                // THE MAGNET HEURISTIC
                // If we just left the station, don't just go to the "Nearest" thing (which might be in the suburbs).
                // Instead, pull the user towards the CITY CENTER.
                // We pick the candidate that is closest to *cityCenter*, not closest to *currentLocation*.
                candidates.minByOrNull { poi ->
                    distanceKm(cityCenter, CityLocation(poi.lat, poi.lon))
                }
            } else {
                // Normal "Greedy" logic: just walk to the next closest thing
                candidates.minByOrNull { poi ->
                    distanceKm(currentLocation, CityLocation(poi.lat, poi.lon))
                }
            } ?: break

            val distKm = distanceKm(currentLocation, CityLocation(nearest.lat, nearest.lon))

            // Calculate costs
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
                // If it doesn't fit, discard it and try the next one
                candidates.remove(nearest)
            }
        }

        return RoutePlan(
            city = city,
            center = CityLocation(startPoi.lat, startPoi.lon), // Center map on start
            stops = optimizedRoute,
            totalDistKm = totalDist,
            estimatedTimeHours = currentTimeMinutes / 60.0
        )
    }
}

