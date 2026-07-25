# ClearWay – System Improvement Tasks

> **Purpose**: This document is a machine-readable, prioritised task list for an AI agent.
> Each task is self-contained: it explains *what* is wrong, *where* the relevant code lives, *why* it matters, and *exactly* what needs to be done.
> Tasks are grouped by domain and ordered by priority (P0 = critical, P1 = high, P2 = medium, P3 = nice-to-have).

---

## Table of Contents

1. [Authentication & Session Management](#1-authentication--session-management)
2. [Password Hashing & Credential Security](#2-password-hashing--credential-security)
3. [Secrets & Configuration Management](#3-secrets--configuration-management)
4. [API Security Hardening](#4-api-security-hardening)
5. [Input Validation & Error Handling](#5-input-validation--error-handling)
6. [CORS Configuration](#6-cors-configuration)
7. [Backend Architecture Improvements](#7-backend-architecture-improvements)
8. [External API Resilience](#8-external-api-resilience)
9. [Database & Data Integrity](#9-database--data-integrity)
10. [Scheduler Improvements](#10-scheduler-improvements)
11. [Frontend Improvements](#11-frontend-improvements)
12. [Infrastructure & DevOps](#12-infrastructure--devops)
13. [Testing](#13-testing)
14. [Documentation Sync](#14-documentation-sync)

---

## 1. Authentication & Session Management

### TASK-1.1 · P0 · Implement JWT-Based Authentication

**Problem**: Login (`POST /api/v1/auth/login`) returns a raw `UserDto` JSON object containing the user's ID. The frontend stores this in React `useState`. There is **zero** server-side session or token management — any client can forge a userId and access any endpoint. This is the single biggest security flaw in the system.

**Files involved**:
- `smart-routes-api/src/main/java/com/cleanroute/api/controller/AuthController.java` (lines 61-86)
- `smart-routes-api/src/main/java/com/cleanroute/api/controller/RouteController.java` (entire file — no auth check)
- `smart-routes-api/build.gradle` (missing spring-boot-starter-security dependency)
- `smart-routes-ui/src/MapComponent.jsx` (lines 91-142 — auth handlers)

**Required changes**:
1. Add `spring-boot-starter-security` and a JWT library (e.g., `io.jsonwebtoken:jjwt-api:0.12.6`, `jjwt-impl`, `jjwt-jackson`) to `build.gradle`.
2. Create a new class `config/JwtTokenProvider.java` that:
   - Generates a signed JWT on login/signup containing `userId` (subject), `email`, and an expiration (e.g., 24 hours).
   - Validates and parses incoming JWTs.
   - Uses a configurable secret key stored in `application.yml` (property: `jwt.secret`).
3. Create a `config/SecurityConfig.java` class extending `SecurityFilterChain`:
   - Permit all on: `POST /api/v1/auth/login`, `POST /api/v1/auth/signup`.
   - Require authentication on all other endpoints.
   - Disable CSRF (REST API with JWT).
   - Set session creation policy to `STATELESS`.
4. Create a `config/JwtAuthenticationFilter.java` (extends `OncePerRequestFilter`) that:
   - Reads the `Authorization: Bearer <token>` header.
   - Validates the JWT via `JwtTokenProvider`.
   - Sets the Spring `SecurityContext` with the authenticated user.
5. Update `AuthController.login()` and `AuthController.signup()` to return:
   ```json
   {
     "token": "<jwt>",
     "user": { ...UserDto fields... }
   }
   ```
6. Create a new DTO: `dto/AuthResponse.java` containing `String token` and `UserDto user`.
7. Update `AuthController.updatePreferences()` to derive the userId from the JWT `SecurityContext` instead of trusting `request.getUserId()`.
8. Update `RouteController` to extract the authenticated userId from `SecurityContext` — never trust `request.getUserId()` from the client body.
9. **Frontend changes** in `MapComponent.jsx`:
   - Store the JWT token (e.g., in `localStorage`).
   - Attach `Authorization: Bearer <token>` header to every Axios request.
   - Create an Axios interceptor or wrapper that adds the header globally.
   - On 401 responses, redirect to login.

---

### TASK-1.2 · P1 · Add Logout / Token Invalidation

**Problem**: `handleLogout` in the frontend (line 144) just clears React state. There is no server-side invalidation.

**Required changes**:
1. For MVP: Since JWTs are stateless, client-side logout (clearing the stored token) is acceptable.
2. For production: Implement a token blocklist (Redis or in-memory cache) and a `POST /api/v1/auth/logout` endpoint that adds the current token's JTI to the blocklist.
3. Add a `POST /api/v1/auth/refresh` endpoint that issues a new JWT if the current one is close to expiry.

---

## 2. Password Hashing & Credential Security

### TASK-2.1 · P0 · Replace SHA-256 with BCrypt

**Problem**: `SecurityHelper.java` uses `SHA-256` for password hashing. SHA-256 is a fast general-purpose hash and is **not designed for password storage** — it is vulnerable to brute-force and rainbow-table attacks. Industry standard is BCrypt, SCrypt, or Argon2.

**File**: `smart-routes-api/src/main/java/com/cleanroute/api/util/SecurityHelper.java`

**Required changes**:
1. Replace the entire `SecurityHelper` implementation with Spring Security's `BCryptPasswordEncoder`:
   ```java
   import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

   public class SecurityHelper {
       private static final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(12);

       public static String hashPassword(String rawPassword) {
           return encoder.encode(rawPassword);
       }

       public static boolean verifyPassword(String rawPassword, String storedHash) {
           return encoder.matches(rawPassword, storedHash);
       }
   }
   ```
2. The `salt` column becomes unnecessary because BCrypt embeds the salt in the hash string. The column can be retained for backward compatibility but should not be used for new registrations.
3. Update `AuthController.signup()` to call `SecurityHelper.hashPassword(password)` (no separate salt needed).
4. Update `AuthController.login()` to call `SecurityHelper.verifyPassword(password, user.getPasswordHash())`.
5. Write a Flyway migration `V3__migrate_password_hashing.sql` that adds a `password_version` column (INTEGER DEFAULT 1). New BCrypt passwords get version=2. On login, if version=1, re-hash with BCrypt and update.

---

### TASK-2.2 · P1 · Add Password Strength Enforcement

**Problem**: The only password validation is `password.length() < 6` (AuthController.java line 33). This is far too weak.

**Required changes**:
1. Add validation annotations on `SignupRequest.java`:
   ```java
   @NotBlank(message = "Password is required")
   @Size(min = 8, max = 128, message = "Password must be 8-128 characters")
   @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).*$",
            message = "Password must contain uppercase, lowercase, and a number")
   private String password;
   ```
2. Add `@Valid` annotation on the `@RequestBody` parameter in `signup()`.
3. Create a `config/GlobalExceptionHandler.java` with `@ControllerAdvice` to return structured error responses for `MethodArgumentNotValidException`.

---

### TASK-2.3 · P1 · Timing-Safe Password Comparison

**Problem**: `SecurityHelper.verifyPassword()` (line 43) uses `computedHash.equals(hash)` which is vulnerable to timing attacks.

**Required changes**:
1. Replace with `MessageDigest.isEqual(computedHash.getBytes(), hash.getBytes())` or, better yet, use BCrypt (TASK-2.1) which handles this internally.

---

## 3. Secrets & Configuration Management

### TASK-3.1 · P0 · Remove Hardcoded Secrets from Source Control

**Problem**: Multiple secrets are hardcoded and committed to Git:
- **Database password**: `REMOVED_DB_PASSWORD` in `application.yml` (line 7) AND `docker-compose.yml` (line 9).
- **TomTom API key**: `REMOVED_TOMTOM_KEY` in `application.yml` (line 26).
- **Database password in README.md** (line 65).

**Required changes**:
1. Update `application.yml` to use environment variable placeholders:
   ```yaml
   spring:
     datasource:
       url: ${SPRING_DATASOURCE_URL:jdbc:postgresql://localhost:5434/cleanroute_db}
       username: ${SPRING_DATASOURCE_USERNAME:postgres}
       password: ${SPRING_DATASOURCE_PASSWORD}
   tomtom:
     api:
       key: ${TOMTOM_API_KEY}
   jwt:
     secret: ${JWT_SECRET}
     expiration-ms: ${JWT_EXPIRATION_MS:86400000}
   ```
2. Update `docker-compose.yml` to use an `.env` file:
   ```yaml
   environment:
     POSTGRES_USER: ${POSTGRES_USER}
     POSTGRES_PASSWORD: ${POSTGRES_PASSWORD}
     POSTGRES_DB: ${POSTGRES_DB}
   ```
3. Create a `.env.example` template file at project root with placeholder values.
4. Add `.env` to `.gitignore` (both root and backend).
5. Remove the hardcoded password from `README.md`.
6. **Rotate the TomTom API key** — the existing one is compromised since it was committed to Git.
7. Add `TOMTOM_API_KEY` and `SPRING_DATASOURCE_PASSWORD` to the README's environment variables section as required values.

---

## 4. API Security Hardening

### TASK-4.1 · P1 · Add Rate Limiting

**Problem**: No rate limiting exists on any endpoint. The `/auth/login` and `/auth/signup` endpoints are vulnerable to credential stuffing and brute-force attacks.

**Required changes**:
1. Add the `bucket4j-spring-boot-starter` dependency to `build.gradle`.
2. Create rate limit configuration:
   - Auth endpoints (`/api/v1/auth/**`): 10 requests per minute per IP.
   - Route endpoints (`/api/v1/routes/**`): 30 requests per minute per user.
3. Alternatively, use a simpler approach with a Spring `HandlerInterceptor` and an in-memory `ConcurrentHashMap<String, AtomicInteger>` with TTL-based cleanup.

---

### TASK-4.2 · P1 · Add HTTPS Enforcement

**Problem**: All traffic is over HTTP. The README instructs users to access `http://localhost:5173`.

**Required changes**:
1. For production: Configure TLS in `application.yml` or use a reverse proxy (Nginx/Caddy) that terminates TLS.
2. Add `server.ssl.*` configuration properties in `application.yml` (commented out for local dev, enabled for prod profile).
3. Add a `production` Spring profile with HTTPS enforcement.

---

### TASK-4.3 · P2 · Add Security Headers

**Problem**: No security headers are set on responses (e.g., `X-Content-Type-Options`, `X-Frame-Options`, `Strict-Transport-Security`).

**Required changes**:
1. In `SecurityConfig.java` (created in TASK-1.1), add:
   ```java
   http.headers(headers -> headers
       .contentTypeOptions(Customizer.withDefaults())
       .frameOptions(frame -> frame.deny())
       .httpStrictTransportSecurity(hsts -> hsts.maxAgeInSeconds(31536000))
   );
   ```

---

## 5. Input Validation & Error Handling

### TASK-5.1 · P1 · Add Bean Validation to All DTOs

**Problem**: No `@Valid` annotations or Jakarta Validation constraints exist on any DTO. The `spring-boot-starter-validation` dependency IS in `build.gradle` but is unused.

**Files to modify**:
- `dto/SignupRequest.java` — Add `@NotBlank` on name, `@Email` on email, `@Size` on password.
- `dto/LoginRequest.java` — Add `@NotBlank` on email and password.
- `dto/CreateRouteRequest.java` — Add `@NotNull` on userId, `@NotBlank` on name, `@NotEmpty` on pathCoordinates, `@Size(min=2)` on pathCoordinates.
- `dto/AllergyPreferencesDto.java` — Add `@NotNull` on userId.
- All controller method `@RequestBody` parameters — Add `@Valid`.

---

### TASK-5.2 · P1 · Create Structured Error Responses

**Problem**: Error responses are plain strings (e.g., `"Email is required"`, `"An error occurred during signup: <exception message>"`). Returning raw exception messages (`e.getMessage()`) can leak internal details.

**Required changes**:
1. Create `dto/ErrorResponse.java`:
   ```java
   public record ErrorResponse(
       int status,
       String error,
       String message,
       String timestamp
   ) {}
   ```
2. Create `config/GlobalExceptionHandler.java` with `@ControllerAdvice`:
   - Handle `MethodArgumentNotValidException` → 400 with field-level errors.
   - Handle `IllegalArgumentException` → 400.
   - Handle `AuthenticationException` → 401.
   - Handle generic `Exception` → 500 with a generic message (never expose `e.getMessage()` to clients).
3. Remove all `catch (Exception e) { return ResponseEntity.internalServerError().body("..." + e.getMessage()); }` patterns from controllers — let the `@ControllerAdvice` handle them.

---

### TASK-5.3 · P2 · Validate Coordinate Ranges

**Problem**: `CreateRouteRequest.pathCoordinates` accepts any double values. Invalid coordinates (e.g., latitude > 90 or longitude > 180) could cause downstream API failures or garbage data.

**Required changes**:
1. Add a custom validator or add validation logic in `RouteService.fetchAlternatives()`:
   - Latitude must be between -90 and 90.
   - Longitude must be between -180 and 180.
   - Each coordinate pair must have exactly 2 elements.
2. The `OpenMeteoAqiClient` already has clamping logic (line 41-43) but the longitude normalization formula appears buggy:
   ```java
   double lon = (longitude - 180) % 360;
   double normalizedLon = (lon > 0) ? (lon - 180) : (lon + 180);
   ```
   This should be simplified and tested — for a standard longitude input of 77.2 (Delhi), this produces `-102.8` which is then corrected to `77.2` by coincidence. **Fix this formula** (see TASK-8.3).

---

## 6. CORS Configuration

### TASK-6.1 · P1 · Replace Wildcard CORS with Explicit Origins

**Problem**: Both controllers use `@CrossOrigin(origins = "*")` which allows ANY origin to call the API. This is a security risk, especially with JWT authentication.

**Files**:
- `AuthController.java` (line 16)
- `RouteController.java` (line 19)

**Required changes**:
1. Remove `@CrossOrigin` from both controllers.
2. Create a centralized `config/WebConfig.java`:
   ```java
   @Configuration
   public class WebConfig implements WebMvcConfigurer {
       @Value("${cors.allowed-origins:http://localhost:5173}")
       private String[] allowedOrigins;

       @Override
       public void addCorsMappings(CorsRegistry registry) {
           registry.addMapping("/api/**")
               .allowedOrigins(allowedOrigins)
               .allowedMethods("GET", "POST", "PUT", "DELETE")
               .allowedHeaders("*")
               .allowCredentials(true)
               .maxAge(3600);
       }
   }
   ```
3. Add `cors.allowed-origins` to `application.yml`.

---

## 7. Backend Architecture Improvements

### TASK-7.1 · P1 · Extract Auth Logic into a Service Layer

**Problem**: `AuthController.java` contains business logic directly (password hashing, user lookup, validation). Controllers should delegate to a service.

**Required changes**:
1. Create `service/AuthService.java` with methods:
   - `UserDto signup(SignupRequest request)`
   - `AuthResponse login(LoginRequest request)`
   - `UserDto updatePreferences(UUID userId, AllergyPreferencesDto prefs)`
2. Move all business logic from `AuthController` into `AuthService`.
3. `AuthController` should only handle HTTP mapping and delegate.

---

### TASK-7.2 · P1 · Add Authorization Checks (Resource Ownership)

**Problem**: Any authenticated user can:
- Update any other user's preferences by sending a different `userId` in the request body (AuthController line 89-113).
- View any other user's routes via `GET /api/v1/routes/user/{userId}` (RouteController line 56-66).
- Create routes under any user's account.

**Required changes**:
1. After implementing JWT auth (TASK-1.1), extract the authenticated userId from `SecurityContext`.
2. In `updatePreferences()`: ignore the `userId` from the request body; use the authenticated user's ID.
3. In `getUserRoutes()`: validate that `{userId}` matches the authenticated user's ID, or return 403.
4. In `createRoute()`: set `request.userId` from the authenticated user, not from the client.

---

### TASK-7.3 · P2 · Remove Mock User / DataInitializer

**Problem**: `DataInitializer.java` inserts a hardcoded mock user with no password hash into the database on every startup. This creates a user (`demo@cleanroute.com`) with no password that could be exploited.

**File**: `smart-routes-api/src/main/java/com/cleanroute/api/config/DataInitializer.java`

**Required changes**:
1. Delete `DataInitializer.java` entirely, or gate it behind a `dev` Spring profile:
   ```java
   @Profile("dev")
   @Configuration
   public class DataInitializer { ... }
   ```
2. Remove the `MOCK_USER_ID` constant from `MapComponent.jsx` (line 32) and require login before using route features.

---

### TASK-7.4 · P2 · Use RestClient Instead of RestTemplate

**Problem**: `TomTomRoutingClient` and `OpenMeteoAqiClient` manually construct `new RestTemplate()`. Spring Boot 3+ recommends `RestClient` (or `WebClient` for reactive). Manual instantiation also bypasses Spring's auto-configured connection pools and metrics.

**Required changes**:
1. Register a `RestClient` bean in a `config/RestClientConfig.java` with proper connection and read timeouts.
2. Inject the `RestClient` bean into `TomTomRoutingClient` and `OpenMeteoAqiClient` via constructor injection.

---

### TASK-7.5 · P3 · Add `@Transactional` Where Needed

**Problem**: `RouteService.createRoute()` performs multiple database writes (save route, compute score, save metrics, update route again) without a wrapping `@Transactional`. If the score computation fails, a partially-saved route remains.

**File**: `smart-routes-api/src/main/java/com/cleanroute/api/service/RouteService.java` (line 53)

**Required changes**:
1. Add `@Transactional` to `createRoute()`.
2. Add `@Transactional(readOnly = true)` to `getActiveRoutesForUser()`.
3. Add `@Transactional` to `computeScoreForRoute()`.

---

## 8. External API Resilience

### TASK-8.1 · P1 · Add Fallback / Circuit Breaker for External APIs

**Problem**: If TomTom or Open-Meteo is down, the entire route calculation fails with a 500 error. NFR3 ("App shouldn't crash if the weather or routing API goes down") is explicitly NOT implemented.

**Files**:
- `TomTomRoutingClient.java` (line 36-38 — throws RuntimeException)
- `OpenMeteoAqiClient.java` (line 67-69 — returns null, but caller doesn't handle gracefully)

**Required changes**:
1. Add `resilience4j-spring-boot3` dependency to `build.gradle`.
2. Annotate `TomTomRoutingClient.getDirections()` with `@CircuitBreaker` and a fallback method that returns an empty `TomTomRouteResponse` or cached data.
3. Annotate `OpenMeteoAqiClient.getPollutionData()` with `@CircuitBreaker` with fallback returning `PollutionData` with moderate default values.
4. Add `@Retry(name = "tomtom", maxAttempts = 3)` for transient failures.
5. Configure circuit breaker properties in `application.yml`.

---

### TASK-8.2 · P2 · Add AQI Response Caching

**Problem**: `PollutionEngineService` queries Open-Meteo for every sampled coordinate point. For a route with 100 points sampling every 10th, that's still 10 HTTP calls per route calculation. Nearby points will return nearly identical data.

**Required changes**:
1. Add Spring Cache support: `spring-boot-starter-cache` in `build.gradle`.
2. Enable caching with `@EnableCaching` on the main application class.
3. Cache AQI responses by rounding coordinates to 2 decimal places (~1.1km grid):
   ```java
   @Cacheable(value = "aqiData", key = "#roundedLat + ',' + #roundedLon")
   public PollutionData getPollutionData(double latitude, double longitude) { ... }
   ```
4. Set TTL to 15 minutes (aligned with the scheduler interval).

---

### TASK-8.3 · P2 · Fix OpenMeteo Longitude Normalization Bug

**Problem**: The longitude normalization in `OpenMeteoAqiClient.java` (lines 42-43) is mathematically incorrect:
```java
double lon = (longitude - 180) % 360;
double normalizedLon = (lon > 0) ? (lon - 180) : (lon + 180);
```
For input `77.2` (Delhi): `lon = (77.2 - 180) % 360 = -102.8`, then `normalizedLon = -102.8 + 180 = 77.2`. This happens to work but only by coincidence. For `longitude = 200`: `lon = 20`, `normalizedLon = 20 - 180 = -160`. The intent is unclear and the logic is fragile.

**Required changes**:
1. Replace with standard normalization:
   ```java
   double normalizedLon = ((longitude + 180) % 360 + 360) % 360 - 180;
   ```
2. Add unit tests for edge cases: -180, 0, 180, 360, -360, 200, -200.

---

## 9. Database & Data Integrity

### TASK-9.1 · P1 · Add NOT NULL Constraint to password_hash

**Problem**: The `V2__auth_and_allergies.sql` migration adds `password_hash VARCHAR(255)` without NOT NULL. Combined with `DataInitializer.java` inserting a user with no password, this allows users with null passwords in the database.

**Required changes**:
1. Create a migration `V3__enforce_password_not_null.sql`:
   ```sql
   -- First, delete or update any users without passwords
   DELETE FROM users WHERE password_hash IS NULL;
   -- Then add the constraint
   ALTER TABLE users ALTER COLUMN password_hash SET NOT NULL;
   ALTER TABLE users ALTER COLUMN salt SET NOT NULL;
   ```

---

### TASK-9.2 · P2 · Add Database Index for Active Routes

**Problem**: The scheduler queries active routes but there is no partial index on `is_active`.

**Required changes**:
1. Add a partial index in a new migration:
   ```sql
   CREATE INDEX idx_routes_is_active ON routes(is_active) WHERE is_active = TRUE;
   ```

---

### TASK-9.3 · P2 · Add Cascade Delete for User → Routes

**Problem**: The `routes` table has `user_id UUID NOT NULL REFERENCES users(id)` but no `ON DELETE CASCADE`. If a user is deleted, their routes become orphaned or the delete fails.

**Required changes**:
1. Create migration `V4__add_cascade_delete.sql`:
   ```sql
   ALTER TABLE routes DROP CONSTRAINT routes_user_id_fkey;
   ALTER TABLE routes ADD CONSTRAINT routes_user_id_fkey
       FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;
   ```

---

## 10. Scheduler Improvements

### TASK-10.1 · P2 · Use Repository Query for Active Routes Only

**Problem**: `PollutionScheduler.evaluateActiveRoutes()` (line 35) calls `routeRepository.findAll()` and then filters in Java with an `if` check. This loads ALL routes including inactive ones into memory.

**File**: `smart-routes-api/src/main/java/com/cleanroute/api/scheduler/PollutionScheduler.java`

**Required changes**:
1. Add a method to `RouteRepository`:
   ```java
   List<Route> findByIsActiveTrue();
   ```
2. Update the scheduler to call `routeRepository.findByIsActiveTrue()` instead of `findAll()`.
3. For large-scale: use `@Query` with pagination via `Pageable` and process in batches.

---

### TASK-10.2 · P3 · Add Scheduler Health Monitoring

**Problem**: If the scheduler throws an exception, it silently fails and logs an error. There is no monitoring or alerting.

**Required changes**:
1. Add a `last_scheduler_run` metric or actuator endpoint.
2. Add `spring-boot-starter-actuator` to `build.gradle`.
3. Expose health and metrics endpoints.

---

## 11. Frontend Improvements

### TASK-11.1 · P1 · Persist Auth State Across Page Refreshes

**Problem**: User authentication state is stored only in React `useState`. Refreshing the page logs the user out.

**File**: `smart-routes-ui/src/MapComponent.jsx`

**Required changes**:
1. After TASK-1.1 (JWT), store the token in `localStorage`.
2. On app mount, check `localStorage` for a stored token.
3. If found, validate it (optionally via a `GET /api/v1/auth/me` endpoint) and restore the user state.
4. On logout, clear `localStorage`.

---

### TASK-11.2 · P1 · Create an Axios Interceptor for Auth Headers

**Problem**: The frontend makes API calls by manually constructing Axios requests. There is no centralized place to attach the JWT token or handle 401 errors.

**Required changes**:
1. Create `src/api.js`:
   ```javascript
   import axios from 'axios';

   const api = axios.create({
     baseURL: import.meta.env.VITE_API_URL || 'http://localhost:8080',
   });

   api.interceptors.request.use((config) => {
     const token = localStorage.getItem('authToken');
     if (token) {
       config.headers.Authorization = `Bearer ${token}`;
     }
     return config;
   });

   api.interceptors.response.use(
     (response) => response,
     (error) => {
       if (error.response?.status === 401) {
         localStorage.removeItem('authToken');
         window.location.reload();
       }
       return Promise.reject(error);
     }
   );

   export default api;
   ```
2. Replace all `axios.post('http://localhost:8080/...')` calls in `MapComponent.jsx` with `api.post('/api/v1/...')`.

---

### TASK-11.3 · P2 · Extract Hardcoded API URL

**Problem**: The API URL `http://localhost:8080` is hardcoded in 5 places in `MapComponent.jsx` (lines 50, 71, 96, 121, 161).

**Required changes**:
1. Create a `.env` file in `smart-routes-ui/`:
   ```
   VITE_API_URL=http://localhost:8080
   ```
2. Use `import.meta.env.VITE_API_URL` everywhere (or use the Axios interceptor from TASK-11.2).

---

### TASK-11.4 · P2 · Split MapComponent into Smaller Components

**Problem**: `MapComponent.jsx` is a 483-line monolith handling authentication, route calculation, preference management, and map rendering — all in one component.

**Required changes**:
1. Extract into separate components:
   - `src/components/AuthPanel.jsx` — Login/Signup forms.
   - `src/components/PreferencesPanel.jsx` — Allergy toggles.
   - `src/components/RoutePanel.jsx` — Route planning controls.
   - `src/components/RouteResults.jsx` — Route alternatives display.
   - `src/components/MapView.jsx` — Map container with markers and polylines.
2. Use React Context (`AuthContext`) to share user state across components.

---

### TASK-11.5 · P3 · Remove Unused App.jsx

**Problem**: `App.jsx` contains the default Vite + React boilerplate (counter demo) and is never imported — `main.jsx` renders `MapComponent` directly.

**Required changes**:
1. Either delete `App.jsx` and `App.css`, OR refactor `main.jsx` to render `App` which in turn renders `MapComponent`.

---

## 12. Infrastructure & DevOps

### TASK-12.1 · P2 · Full Docker Compose Stack

**Problem**: Only PostgreSQL runs in Docker. The backend and frontend must be started manually.

**Required changes**:
1. Create `smart-routes-api/Dockerfile`:
   ```dockerfile
   FROM eclipse-temurin:17-jdk-alpine AS build
   WORKDIR /app
   COPY . .
   RUN ./gradlew bootJar --no-daemon
   FROM eclipse-temurin:17-jre-alpine
   COPY --from=build /app/build/libs/*.jar app.jar
   ENTRYPOINT ["java", "-jar", "app.jar"]
   ```
2. Create `smart-routes-ui/Dockerfile`:
   ```dockerfile
   FROM node:20-alpine AS build
   WORKDIR /app
   COPY package*.json ./
   RUN npm ci
   COPY . .
   RUN npm run build
   FROM nginx:alpine
   COPY --from=build /app/dist /usr/share/nginx/html
   ```
3. Update `docker-compose.yml` to include `api` and `ui` services with proper `depends_on` and healthchecks.

---

### TASK-12.2 · P2 · Add .env.example File

**Problem**: No template exists for required environment variables.

**Required changes**:
1. Create `.env.example` at project root:
   ```
   # Database
   POSTGRES_USER=postgres
   POSTGRES_PASSWORD=your_secure_password_here
   POSTGRES_DB=cleanroute_db
   SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5434/cleanroute_db
   SPRING_DATASOURCE_USERNAME=postgres
   SPRING_DATASOURCE_PASSWORD=your_secure_password_here

   # TomTom API
   TOMTOM_API_KEY=your_tomtom_api_key_here

   # JWT
   JWT_SECRET=your_jwt_secret_min_256_bits_here
   JWT_EXPIRATION_MS=86400000

   # Frontend
   VITE_API_URL=http://localhost:8080
   ```

---

## 13. Testing

### TASK-13.1 · P1 · Add Unit Tests for Security-Critical Code

**Problem**: No test files exist in the project. The `src/test` directory appears empty or minimal.

**Required changes**:
1. `SecurityHelperTest.java` — Test password hashing and verification.
2. `AuthControllerTest.java` — Integration test for signup, login, duplicate email handling.
3. `PollutionEngineServiceTest.java` — Unit test for score calculation with and without allergy preferences.
4. `OpenMeteoAqiClientTest.java` — Unit test the longitude normalization logic (TASK-8.3).
5. `RouteServiceTest.java` — Test route creation and alternative computation with mocked external APIs.

---

### TASK-13.2 · P2 · Add Integration Tests with Testcontainers

**Required changes**:
1. Add `testcontainers` and `postgresql` testcontainer dependencies to `build.gradle`.
2. Create a test configuration that spins up a PostGIS container for integration tests.
3. Write end-to-end tests that verify the full flow: signup → login → create route → get alternatives.

---

## 14. Documentation Sync

### TASK-14.1 · P2 · Update Design Document After Changes

**Problem**: The Software_Design_Document.md is already outdated (as noted in the existing review). After implementing the above changes, it will need further updates.

**Required changes** (post-implementation):
1. Add JWT authentication flow to the architecture diagram.
2. Document the new `SecurityConfig`, `JwtTokenProvider`, `JwtAuthenticationFilter` classes.
3. Update the API endpoint table to include `Authorization` header requirements.
4. Document the circuit breaker and caching layers.
5. Update the database schema to include the new columns from V3/V4 migrations.

---

## Execution Order (Recommended)

For an AI agent implementing these changes, the recommended execution order is:

```
Phase 1 — Critical Security (do first, these are blocking issues):
  TASK-3.1 → TASK-2.1 → TASK-1.1 → TASK-5.2 → TASK-6.1

Phase 2 — API Hardening:
  TASK-5.1 → TASK-4.1 → TASK-7.1 → TASK-7.2 → TASK-7.3

Phase 3 — Resilience & Performance:
  TASK-8.1 → TASK-8.3 → TASK-8.2 → TASK-10.1

Phase 4 — Database Integrity:
  TASK-9.1 → TASK-9.3 → TASK-9.2

Phase 5 — Frontend Modernisation:
  TASK-11.2 → TASK-11.1 → TASK-11.3 → TASK-11.4 → TASK-11.5

Phase 6 — Infrastructure & Testing:
  TASK-13.1 → TASK-12.2 → TASK-12.1 → TASK-13.2

Phase 7 — Documentation:
  TASK-14.1
```

---

## Summary Statistics

| Priority | Count | Domain |
|----------|-------|--------|
| P0 (Critical) | 3 | JWT Auth, BCrypt, Secrets removal |
| P1 (High) | 13 | Validation, CORS, rate limiting, service layer, tests |
| P2 (Medium) | 11 | Caching, Docker, database constraints, frontend refactor |
| P3 (Nice-to-have) | 3 | Actuator, unused file cleanup, transaction annotations |
| **Total** | **30** | |
