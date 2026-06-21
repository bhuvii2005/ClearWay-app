# System Design (MVP)

## 1. Overview
ClearWay is my project to find the least polluted paths in a city. This doc explains the stack and how the database is set up.

## 2. Tech Stack
Standard client-server arch.
- **Frontend**: React (Vite), Tailwind, React-Leaflet
- **Backend**: Spring Boot 3 (Java 17)
- **Database**: PostgreSQL with PostGIS extension for spatial data
- **APIs used**: Open-Meteo (for AQI stuff). Going to use OpenRouteService soon for the actual routing.

## 3. App Structure

**React App (`smart-routes-ui`)**
It's a SPA. Currently just using `MapComponent.jsx` to show the map and handle user clicks for start/end points. Uses axios for testing the APIs.

**Spring Boot Backend (`smart-routes-api`)**
- `RouteController`: Endpoint for `POST /api/v1/routes`
- `RouteService`: Saves postgis LineStrings to the DB.
- `PollutionEngineService`: Calls Open-Meteo and does the math for the exposure score.
- `PollutionScheduler`: A cron job running every 15 mins to ping Open-Meteo and save new scores so we can graph it later.

## 4. Database Setup

**Users**
- id (UUID, PK)
- name, email
- fcm_token (added this for later when we do mobile push notifications)

**Routes**
- id (UUID, PK)
- user_id (UUID)
- name, type ('walking', 'cycling' etc)
- path (Geometry: LineString, SRID 4326) -> really enjoying learning postgis for spatial mapping!
- is_active, last_pollution_score, avg_pollution_score

**Route Metrics**
History of scores.
- id (UUID), route_id (UUID), pollution_score, timestamp

## 5. Flow

Right now, user clicks start and end point. Frontend passes the 2 points to backend. Backend saves it as a LineString, calls Open-Meteo for the coordinate, creates a RouteMetrics row, and sends the score back. 
Every 15 mins the scheduler fetches new AQI for active routes and updates the DB.
