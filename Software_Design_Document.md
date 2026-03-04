# ClearWay - System Design (MVP)

## 1. Overview
ClearWay is an app that helps people find the cleanest, least polluted way to get from A to B. This document covers how we built the Minimum Viable Product (MVP)—our tech stack, database layout, and how the different pieces talk to each other.

## 2. Tech Stack
We went with a standard client-server setup with a background worker for scheduled tasks.
* **Frontend**: React (built with Vite), Tailwind for styling, and React-Leaflet for the interactive map.
* **Backend**: Java 17 running Spring Boot 3. 
* **Database**: PostgreSQL 18. We're using the PostGIS extension to handle all the map coordinate stuff natively.
* **Build & Migrations**: Gradle for the build, Flyway handles database schemas.

### External APIs
* **Open-Meteo**: Provides the live Air Quality Index (AQI) data.
* **OpenRouteService (ORS)**: Up next! We'll use this to get the actual street paths instead of drawing straight lines.

## 3. How the App is Split Up

### 3.1 The React Frontend (`smart-routes-ui`)
A single-page app with a dark, glassmorphism vibe.
* `MapComponent.jsx`: This is the main screen. It shows the map, lets users click their start and end points, and displays the route result.
* It uses `axios` to make calls to our Spring Boot backend.

### 3.2 The Spring Boot Backend (`smart-routes-api`)
This is where all the heavy lifting happens.
* **Controllers**: Exposes the REST endpoints the React app calls (like `POST /api/v1/routes`).
* **Services**: 
  * `RouteService`: Manages saving the routes to the database.
  * `PollutionEngineService`: Talks to the Open-Meteo API to get pollution data and calculates the exposure score.
  * `NotificationEngineService`: Checks if the score is dangerously high and triggers mock warnings.
* **Background Jobs (`PollutionScheduler`)**: A worker thread that wakes up every 15 minutes, grabs all active routes from the database, and fetches fresh pollution scores for them without slowing down the main app.

## 4. Database Setup
We are using PostgreSQL with spatial extensions.

### 4.1 Users
* `id` (UUID, Primary Key)
* `name`, `email`
* `fcm_token` - Placeholder for when we add mobile push notifications.

### 4.2 Routes
* `id` (UUID, PK)
* `user_id` (UUID)
* `name`, `type` (like 'walking', 'cycling')
* `path` (Geometry: LineString, SRID 4326) - This is where PostGIS stores the map line shapes.
* `is_active` - Tells the background job whether to keep tracking this route.
* `last_pollution_score`, `avg_pollution_score`, `last_checked_at`

### 4.3 Route Metrics
This table keeps a history of the pollution scores over time for each route.
* `id` (UUID, PK)
* `route_id` (UUID)
* `pollution_score`
* `timestamp`

## 5. How It Actually Works

### When a User Checks a Route (MVP)
1. Someone clicks their start and end points on the React map.
2. React sends those coordinates to `POST /api/v1/routes`.
3. Our `RouteService` turns those coordinates into a PostGIS `LineString` and saves the route to the DB.
4. `PollutionEngineService` makes a quick call to Open-Meteo to get the current AQI for that area (using the local timezone).
5. It saves a new row in the `route_metrics` table and updates the main `Route` with the new score.
6. The backend sends the result to React to show the user.

### When the Background Job Runs
1. Every 15 minutes, `PollutionScheduler` wakes up.
2. It asks the database for all routes where `is_active = true`.
3. It loops through them, pinging Open-Meteo for fresh air quality data for each one.
4. It saves the new scores to `route_metrics` and updates the average score on the parent `Route`.
