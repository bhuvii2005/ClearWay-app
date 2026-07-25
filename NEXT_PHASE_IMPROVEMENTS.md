# ClearWay – Next-Phase Improvement Suggestions

> **Purpose**: This document lists actionable improvement tasks for an AI agent to execute.
> Each task explains the problem, the files involved, and the exact changes needed.
> Tasks are prioritized: **P0** = Critical, **P1** = High, **P2** = Medium, **P3** = Nice-to-have.
>
> **Context**: The project already has JWT authentication, BCrypt password hashing,
> centralized CORS, bean validation, structured error handling, and environment variable
> configuration. These suggestions address what remains.

---

## Table of Contents

1. [Missing @Transactional on RouteService](#1-missing-transactional-on-routeservice)
2. [RouteService.createRoute Swallows Errors Silently](#2-routeservicecreateroute-swallows-errors-silently)
3. [TomTom API Key Still in application.yml Default](#3-tomtom-api-key-still-in-applicationyml-default)
4. [RestTemplate Manually Instantiated](#4-resttemplate-manually-instantiated)
5. [Scheduler Exception Resilience](#5-scheduler-exception-resilience)
6. [RouteController Returns Raw Strings](#6-routecontroller-returns-raw-strings)
7. [AQI Response Caching](#7-aqi-response-caching)
8. [Circuit Breaker for External APIs](#8-circuit-breaker-for-external-apis)
9. [RouteService Average Score Is Mathematically Incorrect](#9-routeservice-average-score-is-mathematically-incorrect)
10. [README.md Still Says SHA-256](#10-readmemd-still-says-sha-256)
11. [Frontend MapComponent Is a 495-Line Monolith](#11-frontend-mapcomponent-is-a-495-line-monolith)
12. [No Unit or Integration Tests](#12-no-unit-or-integration-tests)
13. [DataInitializer Inserts User Without Password Hash](#13-datainitializer-inserts-user-without-password-hash)
14. [Unused `useEffect` Import in MapComponent](#14-unused-useeffect-import-in-mapcomponent)
15. [NotificationEngineService Is Log-Only](#15-notificationengineservice-is-log-only)
16. [No Route Delete / Deactivate Endpoint](#16-no-route-delete--deactivate-endpoint)
17. [No User Profile / Password Change Endpoint](#17-no-user-profile--password-change-endpoint)
18. [V4 Migration Will Fail If Null Password Hashes Exist](#18-v4-migration-will-fail-if-null-password-hashes-exist)
19. [AlternativeRouteDto Missing Per-Pollutant Breakdown](#19-alternativeroutedto-missing-per-pollutant-breakdown)
20. [Frontend Has No Loading Skeleton or Empty State UX](#20-frontend-has-no-loading-skeleton-or-empty-state-ux)
21. [Route History Dashboard](#21-route-history-dashboard)
22. [Full Dockerization](#22-full-dockerization)
23. [Spring Boot Actuator for Health Monitoring](#23-spring-boot-actuator-for-health-monitoring)

---

## 1. Missing @Transactional on RouteService

**Priority**: P1

**Problem**: `RouteService.createRoute()` (line 53) performs multiple database writes — saves the route, then calls `computeScoreForRoute()` which saves metrics and updates the route again — without a wrapping `@Transactional`. If the pollution API call fails midway, a partially-saved route with no score will persist in the database.

**File**: `smart-routes-api/src/main/java/com/cleanroute/api/service/RouteService.java`

**Required changes**:
1. Add `@Transactional` to `createRoute()` method (line 53).
2. Add `@Transactional(readOnly = true)` to `getActiveRoutesForUser()` method (line 80).
3. Add `@Transactional` to `computeScoreForRoute()` method (line 84).
4. Note: The `@Transactional` import already exists on line 16 so no new import is needed.

---

## 2. RouteService.createRoute Swallows Errors Silently

**Priority**: P1

**Problem**: `computeScoreForRoute()` (line 84-114) wraps the entire scoring flow in a try-catch that logs the error but silently continues. When called from `createRoute()`, the caller has no idea the score computation failed — the route is returned with `lastPollutionScore = null`. The frontend then shows "null" as the score.

**File**: `smart-routes-api/src/main/java/com/cleanroute/api/service/RouteService.java` (lines 84-114)

**Required changes**:
1. In `computeScoreForRoute()`, if an exception occurs, set a default fallback score (e.g., `route.setLastPollutionScore(-1.0)`) and save, so the UI can show "Score unavailable" instead of null.
2. Alternatively, create a separate `computeScoreForRouteOrDefault()` method that returns a boolean indicating success, and let `createRoute()` handle the failure case explicitly.

---

## 3. TomTom API Key Still in application.yml Default

**Priority**: P0

**Problem**: `application.yml` line 26 still has the actual TomTom API key as the default value:
```yaml
key: ${TOMTOM_API_KEY:REMOVED_TOMTOM_KEY}
```
The entire purpose of TASK-3.1 was to remove hardcoded secrets. The key is still exposed as a fallback default. Anyone cloning the repo gets free access to your TomTom account.

**File**: `smart-routes-api/src/main/resources/application.yml` (line 26)

**Required changes**:
1. Remove the default value entirely:
   ```yaml
   key: ${TOMTOM_API_KEY}
   ```
2. Add a startup validation that fails fast if `TOMTOM_API_KEY` is not set (use a `@Value` with no default, or add an `@PostConstruct` check in `TomTomRoutingClient`).
3. Similarly, the JWT secret on line 29 has a hardcoded fallback hex string. Remove it:
   ```yaml
   secret: ${JWT_SECRET}
   ```

---

## 4. RestTemplate Manually Instantiated

**Priority**: P2

**Problem**: Both `TomTomRoutingClient` (line 19-21) and `OpenMeteoAqiClient` (line 22-27) create `new RestTemplate()` in their constructors. This bypasses Spring's managed bean lifecycle, meaning:
- No shared connection pool or thread pool.
- No centralized timeout configuration.
- No Spring metrics/tracing integration.
- Cannot be mocked cleanly in tests.

**Files**:
- `smart-routes-api/src/main/java/com/cleanroute/api/service/integration/TomTomRoutingClient.java` (lines 19-21)
- `smart-routes-api/src/main/java/com/cleanroute/api/service/integration/OpenMeteoAqiClient.java` (lines 22-27)

**Required changes**:
1. Create a `config/RestClientConfig.java` that defines a `RestTemplate` bean (or `RestClient` for Spring Boot 3+) with configured connection and read timeouts:
   ```java
   @Configuration
   public class RestClientConfig {
       @Bean
       public RestTemplate restTemplate() {
           var factory = new SimpleClientHttpRequestFactory();
           factory.setConnectTimeout(5000);
           factory.setReadTimeout(10000);
           return new RestTemplate(factory);
       }
   }
   ```
2. Inject the `RestTemplate` bean via constructor injection into both clients instead of `new RestTemplate()`.
3. The `TomTomRoutingClient` currently has no timeouts at all — the OpenMeteoAqiClient at least sets 5s timeouts, but TomTom has none, meaning a hung TomTom API will block the request thread indefinitely.

---

## 5. Scheduler Exception Resilience

**Priority**: P2

**Problem**: `PollutionScheduler.evaluateActiveRoutes()` (line 31-43) iterates over routes and calls `computeScoreForRoute()` for each. If one route's computation throws an unhandled exception, the remaining routes are skipped entirely for that scheduler cycle.

**File**: `smart-routes-api/src/main/java/com/cleanroute/api/scheduler/PollutionScheduler.java`

**Required changes**:
1. Wrap each iteration in a try-catch so one route's failure does not prevent others from being evaluated:
   ```java
   for (Route route : routes) {
       try {
           routeService.computeScoreForRoute(route);
           evaluated++;
       } catch (Exception e) {
           logger.error("Failed to evaluate route {}: {}", route.getId(), e.getMessage());
       }
   }
   ```
2. Add a counter for failures and log the summary: `"Completed evaluation: {} succeeded, {} failed"`.

---

## 6. RouteController Returns Raw Strings

**Priority**: P2

**Problem**: `RouteController.createRoute()` (line 66-67) returns a plain string:
```java
return ResponseEntity.ok("Route Created successfully with ID: " + createdRoute.getId() + ...);
```
And `getUserRoutes()` (line 79-82) returns `List<String>` of formatted strings. This is inconsistent with the structured JSON responses elsewhere in the API. Frontend code would need to parse strings instead of accessing typed fields.

**File**: `smart-routes-api/src/main/java/com/cleanroute/api/controller/RouteController.java`

**Required changes**:
1. Create a `dto/RouteDto.java` containing `id`, `name`, `type`, `distanceKm`, `lastPollutionScore`, `avgPollutionScore`, `lastCheckedAt`, `createdAt`, `isActive`.
2. Update `createRoute()` to return `ResponseEntity<RouteDto>`.
3. Update `getUserRoutes()` to return `ResponseEntity<List<RouteDto>>`.
4. This also avoids a potential `NullPointerException` in the `String.format` at line 80 if `lastPollutionScore` is null (because `%.2f` will NPE on null `Double`).

---

## 7. AQI Response Caching

**Priority**: P2

**Problem**: `PollutionEngineService` queries Open-Meteo for every sampled coordinate point. For a route with 100 geometry points sampling every 10th, that is still 10 sequential HTTP calls per route computation. For the `/alternatives` endpoint which scores up to 3 routes, this means 30 HTTP calls per user request. Nearby coordinates (within ~1km) return nearly identical AQI data.

**Files**:
- `smart-routes-api/build.gradle` (add spring-boot-starter-cache)
- `smart-routes-api/src/main/java/com/cleanroute/api/SmartRoutesApiApplication.java` (add @EnableCaching)
- `smart-routes-api/src/main/java/com/cleanroute/api/service/integration/OpenMeteoAqiClient.java`

**Required changes**:
1. Add `spring-boot-starter-cache` to `build.gradle`.
2. Add `@EnableCaching` to `SmartRoutesApiApplication.java`.
3. Round coordinates to 2 decimal places (~1.1km grid) and cache by rounded key:
   ```java
   @Cacheable(value = "aqiData", key = "T(Math).round(#latitude * 100) + ',' + T(Math).round(#longitude * 100)")
   public PollutionData getPollutionData(double latitude, double longitude) { ... }
   ```
4. Configure cache TTL to 15 minutes (aligned with the scheduler interval) in `application.yml`:
   ```yaml
   spring:
     cache:
       caffeine:
         spec: maximumSize=500,expireAfterWrite=900s
   ```
5. Add `com.github.ben-manes.caffeine:caffeine` dependency to `build.gradle`.

---

## 8. Circuit Breaker for External APIs

**Priority**: P2

**Problem**: If the TomTom API or Open-Meteo API is down:
- `TomTomRoutingClient.getDirections()` throws a `RuntimeException` (line 37), which surfaces as a 500 error to the user.
- `OpenMeteoAqiClient.getPollutionData()` returns `null` (line 70), which is handled in `PollutionEngineService` with a fallback score of 50.0, but silently degrades accuracy.
- SRS NFR3 states: "App shouldn't crash if the weather or routing API goes down."

**Files**:
- `smart-routes-api/build.gradle`
- `smart-routes-api/src/main/java/com/cleanroute/api/service/integration/TomTomRoutingClient.java`
- `smart-routes-api/src/main/java/com/cleanroute/api/service/integration/OpenMeteoAqiClient.java`

**Required changes**:
1. Add `io.github.resilience4j:resilience4j-spring-boot3` to `build.gradle`.
2. Annotate `TomTomRoutingClient.getDirections()` with `@CircuitBreaker(name = "tomtom", fallbackMethod = "getDirectionsFallback")`.
3. Add a `@Retry(name = "tomtom", maxAttempts = 2)` for transient network failures.
4. Create a fallback method that returns an empty `TomTomRouteResponse` so the UI can show "Routing service temporarily unavailable" instead of crashing.
5. Similarly, annotate `OpenMeteoAqiClient.getPollutionData()` with `@CircuitBreaker` having a fallback returning a `PollutionData` object with moderate defaults (AQI=50).

---

## 9. RouteService Average Score Is Mathematically Incorrect

**Priority**: P1

**Problem**: `RouteService.computeScoreForRoute()` lines 100-104 calculate the running average as:
```java
Double avg = route.getAvgPollutionScore() == null ?
    metrics.getPollutionScore() :
    (route.getAvgPollutionScore() + metrics.getPollutionScore()) / 2;
```
This is wrong. After N measurements, this formula always averages the current score with only the previous average, weighting recent data exponentially higher. After 10 measurements, the first measurement contributes only 1/1024th of the average. This is an exponential moving average, not a true mean.

**File**: `smart-routes-api/src/main/java/com/cleanroute/api/service/RouteService.java` (lines 100-104)

**Required changes**:
Option A (Simple fix): Add a `score_count` column to the `routes` table and compute a proper rolling average:
```java
int count = (route.getScoreCount() == null ? 0 : route.getScoreCount()) + 1;
double avg = ((route.getAvgPollutionScore() == null ? 0 : route.getAvgPollutionScore()) * (count - 1) + metrics.getPollutionScore()) / count;
route.setAvgPollutionScore(avg);
route.setScoreCount(count);
```
This requires a Flyway migration to add `score_count INTEGER DEFAULT 0` to the `routes` table and a new field on the `Route` entity.

Option B (Compute from metrics): Query the `route_metrics` table directly:
```java
Double avg = metricsRepository.findAveragePollutionScoreByRouteId(route.getId());
```
This is more accurate but adds a DB query per computation.

---

## 10. README.md Still Says SHA-256

**Priority**: P1

**Problem**: README.md line 10 says:
```
Secure Authentication: Built-in registration and login features securing user profiles with SHA-256 salted password hashing.
```
The system now uses BCrypt and JWT. The README is misleading.

**File**: `README.md` (line 10)

**Required changes**:
1. Update line 10 to:
   ```
   Secure Authentication: JWT-based stateless authentication with BCrypt password hashing and Spring Security integration.
   ```
2. Update the API Reference section (lines 73-75) to mention that `/login` and `/signup` return a JWT token, and that `/preferences` and all route endpoints require an `Authorization: Bearer <token>` header.
3. Add `JWT_SECRET` and `JWT_EXPIRATION_MS` to the Environment Variables section (lines 59-66).

---

## 11. Frontend MapComponent Is a 495-Line Monolith

**Priority**: P2

**Problem**: `MapComponent.jsx` handles authentication, route planning, preference management, and map rendering in a single 495-line component with 15+ state variables. This makes it difficult to maintain, test, or extend.

**File**: `smart-routes-ui/src/MapComponent.jsx`

**Required changes**:
1. Create a `src/context/AuthContext.jsx` providing user state and auth functions:
   ```jsx
   const AuthContext = createContext();
   export const AuthProvider = ({ children }) => { ... };
   export const useAuth = () => useContext(AuthContext);
   ```
2. Extract into separate components:
   - `src/components/AuthPanel.jsx` — Login/Signup forms (lines 326-396).
   - `src/components/PreferencesPanel.jsx` — Allergy toggles (lines 254-325).
   - `src/components/RoutePanel.jsx` — Route planning controls (lines 399-456).
   - `src/components/RouteResults.jsx` — Route alternatives display (lines 458-486).
   - `src/components/MapView.jsx` — Map container with markers and polylines (lines 190-237).
3. `MapComponent.jsx` becomes a thin orchestrator that composes these sub-components.

---

## 12. No Unit or Integration Tests

**Priority**: P1

**Problem**: The `src/test` directory contains only the default `SmartRoutesApiApplicationTest.java` (likely empty/placeholder). There are zero tests for any business logic, controller, or service.

**File**: `smart-routes-api/src/test/java/com/cleanroute/api/`

**Required changes**:
1. **SecurityHelperTest.java**: Test BCrypt hashing, BCrypt verification, legacy SHA-256 verification, and the migration path (SHA-256 → BCrypt).
2. **AuthServiceTest.java**: Test signup (happy path, duplicate email), login (correct password, wrong password, v1→v2 migration), updatePreferences.
3. **PollutionEngineServiceTest.java**: Mock the `OpenMeteoAqiClient`, test score calculation with and without allergy preferences, test empty coordinates.
4. **RouteServiceTest.java**: Mock `TomTomRoutingClient` and `PollutionEngineService`, test `fetchAlternatives()` sorting, test `createRoute()`.
5. **AuthControllerTest.java**: Integration test using `@WebMvcTest` — test signup returns JWT, login returns JWT, unauthenticated access to protected endpoints returns 401.
6. Add test dependencies to `build.gradle` if not present: `mockito-core`, `h2` (for in-memory DB tests), or `testcontainers` for PostGIS.

---

## 13. DataInitializer Inserts User Without Password Hash

**Priority**: P2

**Problem**: Even gated behind `@Profile("dev")`, `DataInitializer.java` inserts a user via raw SQL with no `password_hash` value (line 30-33). Migration V4 adds a `NOT NULL` constraint on `password_hash`. If the dev profile is activated after V4 runs, the insert will fail with a constraint violation.

**File**: `smart-routes-api/src/main/java/com/cleanroute/api/config/DataInitializer.java`

**Required changes**:
1. Either update the INSERT to include a BCrypt-hashed default password:
   ```java
   String defaultPasswordHash = SecurityHelper.hashPassword("demo123");
   jdbcTemplate.update(
       "INSERT INTO users (id, name, email, phone, password_hash, password_version, created_at, last_active) " +
       "VALUES (?, 'Demo User', 'demo@cleanroute.com', '+1234567890', ?, 2, NOW(), NOW())",
       UUID.fromString(MOCK_USER_ID), defaultPasswordHash
   );
   ```
2. Or delete `DataInitializer.java` entirely — with proper authentication, there is no need for a mock user. Users can register through the UI.

---

## 14. Unused `useEffect` Import in MapComponent

**Priority**: P3

**Problem**: `MapComponent.jsx` line 1 imports `useEffect` but it is never used anywhere in the component.

**File**: `smart-routes-ui/src/MapComponent.jsx` (line 1)

**Required changes**:
1. Remove `useEffect` from the import: `import React, { useState } from 'react';`

---

## 15. NotificationEngineService Is Log-Only

**Priority**: P3

**Problem**: `NotificationEngineService.sendNotification()` (lines 41-50) only writes to logs. No actual notification delivery channel is wired (FCM, email, WebSocket, etc.). The `fcm_token` column exists on the users table and the SRS lists push notifications as a feature, but nothing is connected.

**File**: `smart-routes-api/src/main/java/com/cleanroute/api/service/NotificationEngineService.java`

**Required changes**:
1. For MVP: Add a WebSocket endpoint (`/ws/notifications`) using Spring WebSocket so the React frontend can receive real-time pollution alerts.
2. For production: Wire Firebase Cloud Messaging using the existing `fcm_token` field on the User entity.
3. At minimum, expose a `GET /api/v1/notifications/user/{userId}` endpoint that returns recent notification history from a new `notifications` table so users can see past alerts.

---

## 16. No Route Delete / Deactivate Endpoint

**Priority**: P2

**Problem**: Users can create routes but have no way to delete or deactivate them. The `is_active` column exists on the `routes` table but there is no endpoint to set it to `false`. This means the scheduler will keep evaluating routes forever.

**File**: `smart-routes-api/src/main/java/com/cleanroute/api/controller/RouteController.java`

**Required changes**:
1. Add a `DELETE /api/v1/routes/{routeId}` endpoint that deactivates (or deletes) a route.
2. Enforce authorization: only the authenticated owner can deactivate their own route.
3. Add corresponding UI in the frontend (a "My Routes" panel with deactivate buttons).

---

## 17. No User Profile / Password Change Endpoint

**Priority**: P2

**Problem**: There is no endpoint to change a user's password, update their name/email, or delete their account. Once registered, the user is locked into their credentials forever.

**Required changes**:
1. Add `PUT /api/v1/auth/profile` to update name, email, phone.
2. Add `POST /api/v1/auth/change-password` requiring current password + new password.
3. Add `DELETE /api/v1/auth/account` to delete the user's account (cascading to routes via V4 migration).

---

## 18. V4 Migration Will Fail If Null Password Hashes Exist

**Priority**: P0

**Problem**: `V4__enforce_constraints.sql` adds `ALTER TABLE users ALTER COLUMN password_hash SET NOT NULL` but does NOT first delete or update rows that have null password hashes. If the `DataInitializer` ran before V4 (creating a user without a password hash), the migration will fail with:
```
ERROR: column "password_hash" of relation "users" contains null values
```
V3 only adds the `password_version` column — it does not clean up null hashes.

**File**: `smart-routes-api/src/main/resources/db/migration/V4__enforce_constraints.sql`

**Required changes**:
1. Add a cleanup step before the NOT NULL constraint:
   ```sql
   -- Remove users with no password (mock/test users)
   DELETE FROM users WHERE password_hash IS NULL;

   -- Then enforce the constraint
   ALTER TABLE users ALTER COLUMN password_hash SET NOT NULL;
   ```

---

## 19. AlternativeRouteDto Missing Per-Pollutant Breakdown

**Priority**: P3

**Problem**: `AlternativeRouteDto` only returns `pollutionScore` (a single aggregate number). Users who have toggled specific allergy preferences cannot see which pollutant is contributing most to the score.

**File**: `smart-routes-api/src/main/java/com/cleanroute/api/dto/AlternativeRouteDto.java`

**Required changes**:
1. Add per-pollutant breakdown fields: `avgPm25`, `avgPm10`, `avgNo2`, `avgOzone`.
2. Populate them from `PollutionEngineService` during scoring.
3. Display them in the frontend route results panel as a mini bar chart or pollutant badges.

---

## 20. Frontend Has No Loading Skeleton or Empty State UX

**Priority**: P3

**Problem**: When routes are loading, only a spinner SVG appears inside the button. The route results area is empty — no skeleton, no animation. When no routes exist, there is no empty state message encouraging the user to click on the map.

**File**: `smart-routes-ui/src/MapComponent.jsx`

**Required changes**:
1. Add a skeleton loader when `loading` is true (pulsing gray boxes in the results area).
2. Add an empty state message when `alternativeRoutes.length === 0` and `waypoints.length === 0`: "Click on the map to set your start and destination."
3. Add a toast notification component for success/error messages instead of using `alert()`.

---

## 21. Route History Dashboard

**Priority**: P3

**Problem**: The `route_metrics` table stores historical pollution data for each route (collected every 15 minutes by the scheduler), but there is no way for users to view this data. The data is being collected but never exposed.

**Required changes**:
1. Add a `GET /api/v1/routes/{routeId}/history` endpoint that returns the last N `route_metrics` entries.
2. Create a frontend `RouteHistoryPanel.jsx` showing a line chart (using Chart.js or Recharts) of pollution score over time for saved routes.
3. This is a high-value feature since the data is already being collected — it just needs to be surfaced.

---

## 22. Full Dockerization

**Priority**: P3

**Problem**: Only PostgreSQL runs in Docker. The backend and frontend must be started manually with separate terminal commands.

**Required changes**:
1. Create `smart-routes-api/Dockerfile` (multi-stage: build with Gradle, run with JRE).
2. Create `smart-routes-ui/Dockerfile` (multi-stage: build with Node, serve with Nginx).
3. Update `docker-compose.yml` to include `api` and `ui` services with `depends_on` and healthchecks.
4. This makes the entire app a single `docker compose up` command.

---

## 23. Spring Boot Actuator for Health Monitoring

**Priority**: P3

**Problem**: No health check or metrics endpoints exist. There is no way to monitor the running application, check database connectivity, or observe scheduler execution.

**Required changes**:
1. Add `spring-boot-starter-actuator` to `build.gradle`.
2. Configure exposed endpoints in `application.yml`:
   ```yaml
   management:
     endpoints:
       web:
         exposure:
           include: health,info,metrics
   ```
3. Optionally secure the actuator endpoints to only permit access from internal networks.

---

## Recommended Execution Order

```
Phase 1 — Critical Fixes (do first):
  TASK-3  → TASK-18 → TASK-9 → TASK-10

Phase 2 — Backend Quality:
  TASK-1  → TASK-2  → TASK-4 → TASK-5 → TASK-6

Phase 3 — Performance & Resilience:
  TASK-7  → TASK-8

Phase 4 — Testing:
  TASK-12 → TASK-13

Phase 5 — Feature Completions:
  TASK-16 → TASK-17 → TASK-15

Phase 6 — Frontend & UX:
  TASK-11 → TASK-14 → TASK-19 → TASK-20 → TASK-21

Phase 7 — Infrastructure:
  TASK-22 → TASK-23
```

---

## Summary Statistics

| Priority     | Count | Key Areas                                                     |
|-------------|-------|---------------------------------------------------------------|
| P0 (Critical)| 2     | API key still hardcoded as default, V4 migration will fail    |
| P1 (High)    | 5     | Missing @Transactional, silent errors, wrong avg, no tests, stale README |
| P2 (Medium)  | 7     | RestTemplate, scheduler resilience, raw strings, caching, circuit breaker, route CRUD, profile |
| P3 (Nice-to-have) | 9 | Component split, notifications, pollutant breakdown, history dashboard, Docker, actuator, UX |
| **Total**    | **23** |                                                              |
