# System Design (MVP)

## 1. Overview
ClearWay is a full-stack web application designed to calculate real-time pollution exposure along commuter travel paths and recommend cleaner alternative routes. This document outlines the technology stack, application architecture, and database design.

## 2. Tech Stack
Standard client-server architecture.
- **Frontend**: React (Vite), Tailwind CSS, React-Leaflet
- **Backend**: Spring Boot 3 (Java 17)
- **Database**: PostgreSQL with PostGIS extension for spatial data
- **Infrastructure**: Docker for database containerization
- **Database Migrations**: Flyway
- **External APIs**: 
  - Open-Meteo (for live Air Quality Index and pollutant data)
  - TomTom Routing API (for calculating road-snapped alternatives)

## 3. App Structure

**React App (`smart-routes-ui`)**
A Single Page Application (SPA). The primary view is `MapComponent.jsx`, which displays the interactive map, handles user interactions for start/end points, manages user authentication state, and provides toggles for allergy preferences. It communicates with the backend REST APIs using Axios.

**Spring Boot Backend (`smart-routes-api`)**

**Controllers:**
- `AuthController`: Manages user authentication and preferences.
  - `POST /api/v1/auth/signup`: Registers a new user.
  - `POST /api/v1/auth/login`: Authenticates a user.
  - `POST /api/v1/auth/preferences`: Updates user allergy preferences.
- `RouteController`: Manages routing operations.
  - `POST /api/v1/routes`: Creates and saves a new route.
  - `POST /api/v1/routes/alternatives`: Fetches alternative routes between two points.

**Services:**
- `RouteService`: Orchestrates route creation, interacts with the TomTom client to fetch alternatives, and evaluates them using the pollution engine.
- `PollutionEngineService`: Interacts with Open-Meteo, calculates exposure scores based on raw pollutant levels (PM2.5, PM10, NO2, Ozone), and applies weight penalties based on user allergy preferences.
- `NotificationEngineService`: Analyzes pollution metrics against thresholds to trigger necessary alerts or warnings for users.
- `PollutionScheduler`: A cron job running every 15 minutes to fetch new AQI data for active routes and update the database for historical tracking.
- `TomTomRoutingClient`: Integrates with the TomTom Routing API to generate realistic paths.
- `OpenMeteoAqiClient`: Integrates with the Open-Meteo API for real-time air quality data.

## 4. Database Setup

The database schema is managed via Flyway migrations (`V1__init_schema.sql`, `V2__auth_and_allergies.sql`).

**Users**
- id (UUID, PK)
- name, email (UNIQUE)
- phone, preferred_channels
- fcm_token
- whatsapp_opt_in (BOOLEAN)
- password_hash, salt
- avoid_pm25, avoid_ozone, avoid_pm10, avoid_no2 (BOOLEAN)
- created_at, last_active

**Routes**
- id (UUID, PK)
- user_id (UUID, FK to Users)
- name, type (e.g., 'walking', 'cycling')
- is_active (BOOLEAN)
- path (Geometry: LineString, SRID 4326)
- distance_km (DOUBLE PRECISION)
- last_pollution_score, avg_pollution_score (DOUBLE PRECISION)
- created_at, last_checked_at

**Route Metrics**
Maintains a history of scores for analytics.
- id (UUID, PK)
- route_id (UUID, FK to Routes, ON DELETE CASCADE)
- timestamp
- pollution_score, traffic_index, temperature (DOUBLE PRECISION)

## 5. Flow

The primary route calculation flow is as follows:
1. The user selects a start and end point on the frontend map.
2. The frontend sends these coordinates to the `RouteController` requesting alternatives.
3. The `RouteService` queries the `TomTomRoutingClient` to fetch up to three realistic, road-snapped alternative routes.
4. For each alternative route, the `PollutionEngineService` fetches live pollutant data from `OpenMeteoAqiClient` along the path coordinates.
5. The engine calculates an exposure score for each path, applying dynamic penalties if the route contains high levels of pollutants that the user has marked as allergies (e.g., PM2.5).
6. The alternatives are ranked by their pollution score (lowest is best) and returned to the frontend for display.
7. If the user saves a route, a scheduled job (`PollutionScheduler`) periodically evaluates it to maintain a history in `RouteMetrics` and triggers `NotificationEngineService` if conditions worsen.

## 6. Infrastructure

**Docker:** The PostgreSQL/PostGIS database runs inside a Docker container configured via `docker-compose.yml`, mapping port 5432 internally to 5434 on the host.
**Flyway:** Schema definitions and updates are automatically applied during Spring Boot application startup, ensuring consistency.
