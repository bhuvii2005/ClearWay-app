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
| Database | PostgreSQL 15 with PostGIS and pgvector (image built from `db/Dockerfile`), Flyway migrations |
| Tooling | Docker Compose, GitHub Actions, JUnit 5 + Testcontainers, Vitest, Playwright smoke test |

Every external service used is free and needs at most a free key (TomTom, plus Gemini for the optional AI Coach).

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
| `GEMINI_API_KEY` | Optional. A free key from [aistudio.google.com/apikey](https://aistudio.google.com/apikey); one key covers Coach chat and knowledge-base search. Without it the Coach answers 503 and everything else works |
| `GROQ_API_KEY` | Optional. Only if you switch the Coach to Groq with `CLEARWAY_AI_CHAT_PROVIDER=groq` ([console.groq.com/keys](https://console.groq.com/keys)) |

Never commit `.env` or put real values in `.env.example`.

**Coach chat model.** The default is `gemini-3.5-flash-lite` with low reasoning effort: it was the fastest and cheapest in our test, and still used the right tools ([model comparison](docs/coach-model-comparison.md), 11 Nov 2026, single runs). To use another Gemini model, set `GEMINI_CHAT_MODEL` in `.env`. `gemini-3.5-flash` also works, but its free tier allows only 20 requests a day. `gemini-3.8-flash` never replied on the free tier. Each hosted chat call times out after 30 seconds, and a timeout isn't retried.

**AI free-tier limits.** These are Gemini's free limits, per Google Cloud project, as shown in AI Studio on 11 November 2026. They change, so check AI Studio before relying on them.

| Model | Used for | Requests/min | Tokens/min | Requests/day |
|---|---|---|---|---|
| `gemini-3.5-flash-lite` | Coach chat (default) and evals | 15 | 250K | 500 |
| `gemini-3.1-flash-lite` | Backup chat model (its own quota) | 15 | 250K | 500 |
| `gemini-embedding-001` | Knowledge-base embeddings | 100 | 30K | 1,000 |

No daily token limit is listed. Daily quotas reset at midnight Pacific. The app keeps its own caps below these:
- **Chat:** `CLEARWAY_AI_DAILY_REQUESTS` (default 400) and `CLEARWAY_AI_DAILY_TOKENS` (default 2,000,000, only a safety net).
- **Embeddings:** `CLEARWAY_AI_EMBED_DAILY_REQUESTS` (default 900).
- **Knowledge-base ingestion pacing:** it stays under `CLEARWAY_AI_EMBED_RPM` (60) requests and `CLEARWAY_AI_EMBED_TPM` (25,000) estimated tokens per minute.

Once a cap is reached, the Coach answers 503 until midnight Pacific.

**The app and the evals share the same daily quota.** `./gradlew evalTest` uses the same key and model, so the 500 requests a day cover both. The app's cap of 400 leaves about 100 for one eval run, and an eval run uses at most 70.

**When Google answers 429:**
- **Per-minute limit:** the API retries once after the wait Google asks for, then answers 503.
- **Per-day quota used up:** the API stops calling the provider until midnight Pacific, and the Coach answers 503 straight away.

**Usage log:** the API logs each Coach message's chat requests, tokens, embedding requests, and the model that actually answered. It warns when that model differs from the configured one, because an alias can move to a newer model without notice: in November 2026 `gemini-3.5-flash` was served as `gemini-3.6-flash`.

**Backup model.** If `gemini-3.5-flash-lite` has used today's quota or is misbehaving, set `GEMINI_CHAT_MODEL=gemini-3.1-flash-lite` in `.env` and restart the API (`docker compose up -d api`). It has its own 500 requests a day.

**ClearWay Coach.** `POST /api/v1/coach/chat` answers questions about your saved routes and the air. It can call five read-only tools: list your routes, a route's history (with its cleanest and most polluted hours), current air quality at a point, your pollutant sensitivities, and a knowledge-base search. Tools get your identity from your login, never from the model, so the Coach can't see anyone else's routes. Every number in an answer has to come from a tool, and sources are listed only when the knowledge-base search actually returned them. A message can use at most 5 tool calls. The Coach remembers the last 10 messages of each conversation in memory only, so history is lost when the API restarts, and it's cleared when you delete your account.

**Coach page.** Signed-in users get a **Coach** tab (`/coach`) for chatting with the Coach. It shows which tools were used and links to the sources. Answers are plain text, and every error keeps your typed question. A route's page also has an "Ask Coach about this route" link, which fills in a question without sending it.

| Answer with sources | Dark theme | Mobile (error state) |
|---|---|---|
| ![Coach answer with three knowledge-base sources](docs/screenshots/coach-answer-sources.png) | ![Coach answer in the dark theme](docs/screenshots/coach-answer-dark.png) | ![Coach on a phone, showing a "took too long" error](docs/screenshots/coach-mobile.png) |

The empty page, with its three suggested questions: [docs/screenshots/coach-empty.png](docs/screenshots/coach-empty.png).

**Coach speed and timeouts.** Each Coach call asks the model for low reasoning effort (`CLEARWAY_AI_REASONING_EFFORT`, default `low`; leave it blank for the model's own default), which uses fewer tokens. One message also has a total time budget, `CLEARWAY_AI_COACH_TOTAL_TIMEOUT` (default 45 s). Each model call's 30-second timeout is cut to whatever is left, and once the budget runs out no further model call or tool is started and the Coach answers 503, so a slow model can't keep a message open for 5 × 30 seconds. A tool that's already running when time runs out finishes first, which adds at most 20 seconds and usually about 1. The browser's Coach timeout (`COACH_TIMEOUT_MS` in `smart-routes-ui/src/lib/api.js`, 75 s) is set above that. Nothing else in between has a shorter limit: the UI calls the API directly (nginx only serves the built UI files), and the Coach endpoint runs synchronously, so Spring's async request timeout doesn't apply. If you raise the budget, raise `COACH_TIMEOUT_MS` too.

**Actuator.** `/actuator/health` is public; Docker's health check uses it. Anonymous callers get only UP or DOWN; with the actuator login it also shows details, including the `ai` block (provider, model, whether the knowledge base is available, and `lastEmbeddingCall`). The health check never calls Google and never reports DOWN because of AI. `/actuator/metrics` and `/actuator/info` need HTTP Basic with `CLEARWAY_METRICS_USER` and `CLEARWAY_METRICS_PASSWORD` from `.env` (password at least 12 characters). There are no default credentials: if they aren't set, those endpoints are switched off. They have their own security chain, so an app user's login token doesn't open them. Example: `curl -u "$CLEARWAY_METRICS_USER:$CLEARWAY_METRICS_PASSWORD" http://localhost:8090/actuator/metrics`.

**Coach metrics and logs.** These are under `/actuator/metrics`, behind the actuator login.
- **Spring AI's own model metrics:**
  - `gen_ai.client.operation`: the duration of each model call.
  - `gen_ai.client.token.usage`: the tokens each call used.
- **The Coach's counters:**
  - `coach.tool.calls{tool,outcome}`: outcome is `ok`, `invalid`, `not_found`, `unavailable`, `out_of_time` or `error`.
  - `coach.fallbacks{reason}`: reason is `budget`, `quota_exhausted`, `time_budget`, `provider_error`, `tool_cap` or `rate_limit`.
  - `kb.search{result}`: result is `hit`, `empty` or `error`.
- **Today's use of each daily cap:** `clearway.ai.budget.requests{budget=chat|embedding}`, `clearway.ai.budget.tokens{budget=chat}` and `clearway.ai.budget.exhausted{budget}`.

Each Coach message also writes one log line:

`coach_message conversation=… provider=… model=… served_model=… chat_requests=… tokens=… embedding_requests=… latency_ms=… tools=… outcome=…`

It holds ids, counts and names only. The question, the answer and keys are never logged.

**Evals.** `./gradlew evalTest` runs the live checks:
- **Coach golden set:** 20 cases in `smart-routes-api/src/test/resources/evals/coach-golden.yaml`, split into tool selection (8), grounding (4), knowledge-base citations (4), and safety and security (4).
- **Retrieval eval:** 15 queries with an expected source file (10 close to the knowledge base's wording, 5 casual paraphrases), plus 5 off-topic queries.

They run against the real model, on fixed seeded data in a throwaway database, with Open-Meteo and TomTom mocked. Scoring is deterministic, with no judge model:
- the right tools were called
- citations are real knowledge-base sources returned in that turn
- every number in a grounding answer appears in that turn's tool output
- required and forbidden text
- behaviour checks for two cases, which accept any wording: asking about another user's route must get "not found" from the tool and leak none of that route's name, owner id or numbers; asking about a route with no or too little history must not produce an hour or score that isn't in the tool output

Retrieval reports recall@4, MRR and the off-topic false-positive rate at thresholds 0.50–0.65, plus each query's rank. The report goes to `smart-routes-api/build/reports/evals/summary.md`, and the first full run is kept as [docs/evals/baseline.md](docs/evals/baseline.md).

**Results so far:** one full run (11 Nov 2026) passed **18 of 20** cases. Both failures were the test's wording checks rejecting correct answers, not Coach mistakes; those two cases now use the behaviour checks above, which haven't had a live run yet. Model answers vary from run to run, so a single run is a sample, not a guarantee. Retrieval with the 15 queries: recall@4 100% at the current 0.60 threshold (93% at 0.65), with no off-topic false positives.

To rerun only the retrieval eval (embeddings, no chat requests): `CLEARWAY_EVAL_RETRIEVAL_ONLY=true ./gradlew evalTest --tests '*CoachGoldenEvalTest'`. It writes `build/reports/evals/retrieval.md`. The `--tests` filter matters: without it, `evalTest` also runs the other live checks, which use a few chat requests.

A run uses at most `CLEARWAY_EVAL_MAX_REQUESTS` (70) chat requests, spaced at `CLEARWAY_EVAL_RPM` (12 a minute). If the budget or the provider's daily quota runs out partway, the run stops: the remaining cases are marked "not run" and the report is still written. Before starting, it asks the running app how many chat requests it has used today (`CLEARWAY_EVAL_APP_URL`, default `http://localhost:8090`, with the actuator login). If that's more than `CLEARWAY_EVAL_APP_LIMIT` (400), the run refuses. Because the quota is shared, run the golden set at most once a day.

On GitHub, the **Evals** workflow (run it by hand from the Actions tab) does the same. It uses the provider in `CLEARWAY_EVAL_PROVIDER` with the `GEMINI_API_KEY` or `GROQ_API_KEY` repository secret, skips with a notice when the secret is missing, and uploads `summary.md`.

**Coach knowledge base.** Five short guidance files in [`smart-routes-api/src/main/resources/kb/`](smart-routes-api/src/main/resources/kb/) (WHO guideline levels, European AQI bands, health effects, exercising in poor air, how ClearWay scores routes) are embedded with Gemini and stored in the `vector_store` table (migration V6, pgvector). This happens in the background at start-up, and only for files that are new or changed, so a normal restart uses no embedding requests. Each file is stored all-or-nothing: if Gemini answers 429 partway through, that file is left as it was and is retried on the next start-up. Without `GEMINI_API_KEY` the knowledge base is simply off.

**Bug found: IPv6 in Docker (fixed 11 Nov 2026).**
- **Symptom:** in the Docker stack, every Coach knowledge-base search failed after exactly 20 seconds, so answers came back without sources. Chat calls worked, and the log showed `AI embedding call failed (status network/timeout, GenAiIOException)`.
- **Cause:** Google's API hosts have IPv6 addresses, but Docker's default network has no IPv6 route. The Gemini embedding client waited on an IPv6 connection until its 20-second timeout instead of moving on to IPv4. The chat client (a different HTTP library) wasn't affected. `wget` from inside the container got an answer in 3 seconds.
- **Fix:** the API image runs Java with `-Djava.net.preferIPv4Stack=true`. Its health check now calls `127.0.0.1` instead of `localhost`, because `localhost` can resolve to IPv6 (`::1`), where Java no longer listens.
- **Why tests didn't catch it:** the unit and integration tests mock the provider or use a fake one on localhost. The live evals run on the host, which has working IPv6 (or falls back properly), not inside the container. Only a real question to the Docker stack showed it. The `ai` health details now include `lastEmbeddingCall` (ok or failed, with the time) so this kind of failure is visible without calling Google.

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
| `cd smart-routes-api && ./gradlew test` | 149 tests: services, security, the AI gateway, limits, knowledge base and Coach (scripted models and a fake provider on localhost, no internet), and full API + WebSocket flows against a throwaway database built from `db/Dockerfile` (needs Docker running) |
| `cd smart-routes-api && ./gradlew evalTest` | Live AI checks (see **Evals** above): the 20-case Coach golden set, the retrieval eval, and a few quick live checks of the gateway, the knowledge base and the Coach. They need `GEMINI_API_KEY`, use at most 70 chat requests a run at 12 a minute, and write `build/reports/evals/summary.md`. Run them at most once a day, because the app shares the quota. Never part of `build` |
| `cd smart-routes-ui && npm test` | Air-quality bands, wording, and the API client |
| `cd smart-routes-ui && npm run lint` | Code style and React rules |
| `cd smart-routes-ui && npm run e2e` | Full browser journey against a running app (set `E2E_BASE_URL=http://localhost:4173` for the Docker version). It uses the real outside services (Photon place search, TomTom, Open-Meteo), so it sometimes fails at those steps when one is slow or briefly unreachable; run it again before suspecting the app |

Tests never use your real database or API keys (only `evalTest` uses an AI key, on purpose): outside services are mocked and the database is created fresh for each run.

## Continuous integration

[`.github/workflows/ci.yml`](.github/workflows/ci.yml) runs on every push and pull request:

1. **API:** build and all backend tests, using a throwaway database container built from `db/Dockerfile`.
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
| `POST /api/v1/coach/chat` | Ask the Coach. Body: `conversationId` (a UUID the client makes up), `message` (1–2,000 characters), optional `timeZone` (IANA, e.g. `Asia/Kolkata`). Returns `answer`, `citations` (title and URL) and `toolsUsed`. 429 over 20 messages an hour, 503 when the AI is unavailable |
| WebSocket `/ws` → `/user/queue/alerts` | Live alerts (STOMP, token in the connect headers) |

## Project structure

```
db/Dockerfile             Database image: PostGIS plus pgvector
docker-compose.yml        Database, API and web app
.env.example              Settings template (placeholders only)
.github/workflows/ci.yml  Build and test on every push
smart-routes-api/         Spring Boot API (+ Dockerfile)
smart-routes-ui/          React web app (+ Dockerfile, nginx.conf); see its README
```

## License

MIT License.
