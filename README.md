# CityWalk 🚶‍♂️🌍

**CityWalk** is a Kotlin Multiplatform (KMP) application designed for travelers who have a few hours to spare in a new city and want to make the most of it.

Instead of generic "top 10" lists, CityWalk generates an **optimized, time-boxed walking itinerary** tailored to your specific constraints. It prioritizes nearby historic landmarks, museums, and parks, optionally finding the best spot for lunch without taking you off-course.

<video src="media/screencast.webm" width="100%" controls></video>

---

## How It Works

CityWalk acts as your intelligent local guide. You simply enter the **City Name** and your **Time Available** (e.g., 3 hours). The app then:

1.  **Scans the City**: identifying the city's Points of Interest (POIs) using OpenStreetMap data.
2.  **Filters & Prioritizes**: selecting key sights based on importance (landmarks, historic sites) and proximity.
3.  **Routes Intelligently**: connecting these points into a walkable path that fits exactly within your time budget.
4.  **Visualizes**: displaying the route on an interactive map with detailed markers.
5.  **Shares**: allowing you to copy the full text itinerary (with Wiki links) to your clipboard.

---

## Features

* **Smart Itinerary Generation**: Creates a unique route every time based on weighted category logic.
* **Time-Budget Aware**: Automatically calculates walking time (at 4.5 km/h) + specific dwell times (e.g., 60m for museums, 15m for landmarks).
* **Food Finder**: If "Include Food Stop" is checked, the algorithm identifies a highly-rated restaurant located at the geometric "center of mass" of your selected sights to minimize detours.
* **Train Station Support**: Optional toggle to start/end the route specifically at the city's main train station—perfect for layovers.
* **Interactive Map**: Native map rendering with smooth zooming, clustering, and custom "callout" tooltips.
* **Cross-Platform**: Runs natively on **Desktop (JVM)** and **Android**.

---

## Under the Hood

CityWalk leverages the full power of Kotlin Multiplatform to share 99% of its business logic and UI code.

### 1. The Data Layer
* **Geocoding**: Uses the **Nominatim API** to convert city names into geospatial bounding boxes.
* **POI Fetching**: Queries the **Overpass API** (OpenStreetMap) to fetch nodes and ways, filtering for specific tags (e.g., `tourism=attraction`, `historic=castle`).

### 2. The Algorithm: Weighted Greedy Routing
The core of the app is a custom heuristic algorithm found in `RoutePlanner.kt`. It constructs the route step-by-step:

* **Category Weights**: Different POIs are assigned weights (e.g., `LANDMARK = 10`, `PARK = 3`, `OTHER = 1`).
* **Scoring Formula**: To decide the next stop, the algorithm calculates a score for all candidates:

  $$Score = \frac{Weight}{(Distance + 0.5km)^{1.5}}$$

    * *The `0.5km` bias prevents the algorithm from getting stuck in local clusters of low-value items.*
    * *The power of `1.5` penalizes long distances, keeping the walk compact.*
* **Time Budgeting**: As points are added, the app subtracts the walking time and the estimated "dwell time" (e.g., 60 mins for a museum) from your total budget.

### 3. Tech Stack
* **Kotlin Multiplatform**: Shared logic for Android & Desktop.
* **Compose Multiplatform**: Shared UI code (Material 3 Design).
* **Ktor**: Networking (HTTP client for API calls).
* **MapCompose**: High-performance vector map rendering.
* **Coroutines**: Asynchronous data fetching and processing.
* **Kotlinx Serialization**: JSON parsing.

---

## Build & Run

This project relies on Gradle. Ensure you have JDK 17+ installed.

### Desktop (JVM)
To run the desktop application on macOS, Windows, or Linux:

```bash
# macOS / Linux
./gradlew :composeApp:run

# Windows
.\gradlew.bat :composeApp:run
``` 

### Android
To run on an Android emulator or connected device:

```bash
# macOS / Linux
./gradlew :composeApp:installDebug

# Windows
.\gradlew.bat :composeApp:installDebug
``` 

Alternatively, open the project in Android Studio and run the composeApp configuration.

--- 

## License

This project is licensed under the MIT License. See the LICENSE file for details.

---

##  Contact

Created by Nikolai Gorbachev for the Kotlin Student Coding Competition 2025.

Email: nikolai.m.gorbachev@gmail.com
