# ClearWay

ClearWay is a full-stack web application designed to calculate real-time pollution exposure along commuter travel paths and recommend cleaner alternative routes.

## Features

* **Geospatial Map:** Interactive map built with React and Leaflet.
* **Real Street Routing:** Snaps waypoints to actual roads and generates up to three alternative routes via the TomTom Routing API.
* **Live Air Quality:** Queries the Open-Meteo Air Quality API for real-time measurements of AQI, PM2.5, PM10, CO, NO2, and Ozone.
* **Secure Authentication:** Built-in registration and login features securing user profiles with SHA-256 salted password hashing.
* **Allergy Avoidance Toggles:** Allows users to select sensitivity triggers (PM2.5, Ozone, PM10, NO2). The routing engine dynamically adds weight penalties to segments high in selected pollutants, immediately re-prioritizing cleaner options on the map.
* **Commute Scheduler:** Background worker running every 15 minutes to evaluate exposure trends along saved active routes and trigger warnings.

## Tech Stack

* **Frontend:** React 19 (Vite), Tailwind CSS v4, React-Leaflet, Axios
* **Backend:** Java 17, Spring Boot 3, Hibernate Spatial, Spring Data JPA
* **Database:** PostgreSQL 15+ with the PostGIS extension, managed inside a Docker container
* **Migrations:** Flyway Database Migrations

## Setup Guide

### 1. Start the Database (Docker)

Ensure Docker Desktop is running on your machine. Start the PostGIS database container in the root directory:
```bash
docker compose up -d
```
This starts the PostgreSQL PostGIS container mapped to port 5434.

### 2. Start the Backend API

1. Navigate to the backend directory:
   ```bash
   cd smart-routes-api
   ```
2. Configure the Java 17 path (Windows PowerShell example):
   ```powershell
   $env:JAVA_HOME="C:\Program Files\Java\jdk-17.0.2"; $env:Path="$env:JAVA_HOME\bin;$env:Path"
   ```
3. Run the Spring Boot server (Flyway migrations will run automatically on startup):
   ```bash
   ./gradlew bootRun
   ```

### 3. Start the Frontend Server

1. Navigate to the frontend directory:
   ```bash
   cd smart-routes-ui
   ```
2. Install dependencies and start the Vite server:
   ```bash
   npm install
   npm run dev
   ```
3. Open `http://localhost:5173` in your browser.

## Configuration / Environment Variables

The backend relies on the following environment variables. In a production environment or for custom local setups, these should be configured:

- `SPRING_DATASOURCE_URL`: JDBC URL for PostgreSQL (default: `jdbc:postgresql://localhost:5434/cleanroute_db`)
- `SPRING_DATASOURCE_USERNAME`: Database user (default: `postgres`)
- `SPRING_DATASOURCE_PASSWORD`: Database password (default: `bhuvan#15`)
- `TOMTOM_API_KEY`: API Key for the TomTom Routing API. A hardcoded fallback is currently used if not provided.

## API Reference

The application exposes the following REST APIs:

### Authentication (`/api/v1/auth`)
- `POST /signup`: Register a new user (requires `name`, `email`, `password`).
- `POST /login`: Authenticate a user (requires `email`, `password`). Returns the user profile.
- `POST /preferences`: Update user allergy sensitivities (requires `userId`, and `avoidPm25`, `avoidOzone`, `avoidPm10`, `avoidNo2` booleans).

### Routes (`/api/v1/routes`)
- `POST /`: Create and save a new route to the database.
- `POST /alternatives`: Fetch road-snapped alternative routes between a start and end point, scored and ranked by pollution exposure.

## License

MIT License.