# ClearWay

[![CI](https://github.com/bhuvii2005/ClearWay-app/actions/workflows/ci.yml/badge.svg)](https://github.com/bhuvii2005/ClearWay-app/actions/workflows/ci.yml)

ClearWay finds walking, jogging, cycling and driving routes with cleaner air. It compares real street routes by the pollution along them, watches your saved routes, and alerts you when the air on one turns poor.

## Features

- **Compare routes by air quality:** up to three real street routes (TomTom), each scored with live air-quality data (Open-Meteo: European AQI, PM2.5, PM10, NO₂, O₃, CO).
- **Address search or map clicks:** place search as you type (Photon, OpenStreetMap), draggable pins, or your current location.
- **Pollutant sensitivities:** choose pollutants to avoid (PM2.5, PM10, ozone, NO₂) and routes high in them rank lower.
- **Saved and watched routes:** a background job re-checks watched routes every 15 minutes.
- **Live alerts:** sent instantly over a private WebSocket channel and stored, so missed alerts are still listed.
- **History and best times:** score history, average by hour of day, and the cleanest and worst times to travel.
- **Accounts:** JWT sign-in, BCrypt passwords, password change, sign out on all devices, delete account.
- **Clean, calm interface:** air-coloured routes on a vector map, WHO guideline comparisons, a 24-hour best-time strip, light and dark themes, gentle animations (switched off if your system asks for reduced motion), and a landing page for new visitors.

## Tech stack

| Part | Technology |
|---|---|
| Web app | React 19, Vite 7, React Router 7, Tailwind CSS 4, MapLibre GL (OpenFreeMap tiles), Motion, Lucide icons, Recharts, STOMP over SockJS |
| API | Java 17, Spring Boot 4, Spring Security, Hibernate Spatial, Resilience4j, Caffeine |
| Database | PostgreSQL 15 with PostGIS, Flyway migrations |
| Tooling | Docker Compose, GitHub Actions, JUnit 5 + Testcontainers, Vitest, Playwright smoke test |

Every external service used is free and needs at most a free key (only TomTom).

## Setup

### 1. Create your `.env`

All settings and secrets live in one git-ignored file at the project root. Start from the example:

```bash
cp .env.example .env
```

Then replace each `change_me_...` placeholder in `.env`:

| Setting | What to put |
|---|---|
| `POSTGRES_PASSWORD`, `SPRING_DATASOURCE_PASSWORD` | The same strong password in both |
| `TOMTOM_API_KEY` | A free key from [developer.tomtom.com](https://developer.tomtom.com) (Dashboard > Keys) |
| `JWT_SECRET` | A long random value, e.g. the output of `openssl rand -base64 48` |

Never commit `.env` or put real values in `.env.example`.

### 2a. Run everything with Docker (easiest)

Needs only Docker Desktop.

```bash
docker compose up -d --build
```

| Service | Address |
|---|---|
| Web app | http://localhost:4173 |
| API | http://localhost:8090 (API docs at `/swagger-ui.html`) |
| Database | localhost:5435 |

Stop it with `docker compose down`. Your data stays in the `postgres-data` volume; `docker compose down -v` deletes it.

### 2b. Run for development

Needs Docker Desktop, Java 17 and Node 22.

```bash
docker compose up -d db                     # database only

cd smart-routes-api && ./gradlew bootRun    # API on :8090, runs migrations on start

cd smart-routes-ui && npm install && npm run dev   # web app on http://localhost:5173
```

The API reads the root `.env` automatically. On Windows, if Java 17 is not the default:
`$env:JAVA_HOME="C:\Program Files\Java\jdk-17.0.2"` before `./gradlew bootRun`.

## Tests

| Command | What it checks |
|---|---|
| `cd smart-routes-api && ./gradlew test` | 34 tests: services, security, and full API + WebSocket flows against a throwaway PostGIS container (needs Docker running) |
| `cd smart-routes-ui && npm test` | Air-quality bands, wording, and the API client |
| `cd smart-routes-ui && npm run lint` | Code style and React rules |
| `cd smart-routes-ui && npm run e2e` | Full browser journey against a running app (set `E2E_BASE_URL=http://localhost:4173` for the Docker version) |

Tests never use your real database or API keys: outside services are mocked and the database is created fresh for each run.

## Continuous integration

[`.github/workflows/ci.yml`](.github/workflows/ci.yml) runs on every push and pull request:

1. **API:** build and all backend tests, using a throwaway PostGIS container.
2. **Web app:** lint, unit tests and production build.
3. **Docker:** checks `docker-compose.yml` and builds both images (nothing is pushed or deployed).

No repository secrets are needed today. If a future step needs a credential, add it under **Settings > Secrets and variables > Actions** and use `${{ secrets.NAME }}` in the workflow.

## API overview

All endpoints except sign-up, login and health need `Authorization: Bearer <token>`. Full interactive docs: `/swagger-ui.html`.

| Endpoint | Purpose |
|---|---|
| `POST /api/v1/auth/signup`, `/login` | Create an account or sign in; returns a token |
| `GET /api/v1/auth/me` | Current user |
| `PUT /api/v1/auth/profile`, `POST /api/v1/auth/preferences` | Update name or pollutant sensitivities |
| `POST /api/v1/auth/password`, `/logout` | Change password; sign out on all devices |
| `DELETE /api/v1/auth/account` | Delete the account and its data |
| `POST /api/v1/routes/alternatives` | Compare routes between two points for a travel mode |
| `POST /api/v1/routes`, `GET /api/v1/routes` | Save a route; list saved routes |
| `GET`, `PATCH`, `DELETE /api/v1/routes/{id}` | View, rename or watch on/off, delete |
| `GET /api/v1/routes/{id}/metrics` | Score history, hourly averages, best and worst hour |
| `GET /api/v1/notifications`, `/unread-count` | Stored alerts |
| `POST /api/v1/notifications/{id}/read`, `/read-all` | Mark alerts read |
| WebSocket `/ws` → `/user/queue/alerts` | Live alerts (STOMP, token in the connect headers) |

## Project structure

```
docker-compose.yml        Database, API and web app
.env.example              Settings template (placeholders only)
.github/workflows/ci.yml  Build and test on every push
smart-routes-api/         Spring Boot API (+ Dockerfile)
smart-routes-ui/          React web app (+ Dockerfile, nginx.conf); see its README
```

## License

MIT License.
