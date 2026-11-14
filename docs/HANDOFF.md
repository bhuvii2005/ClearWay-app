# ClearWay AI features: handoff

**Status: shipped.** The AI Coach (PR [#1](https://github.com/bhuvii2005/ClearWay-app/pull/1)) was merged into `main` on 12 Nov 2026 as merge commit `50b5c2d`, and CI on `main` is green. **Coach voice input** (PR [#5](https://github.com/bhuvii2005/ClearWay-app/pull/5)) followed on 14 Nov 2026 as merge commit `ed8bead`, after the user's manual check.

This file records how the AI work stands, so a fresh session (or person) can pick it up. Last updated **14 Nov 2026**. Keep it current: update it at the end of every work session.

The full task list is in `ClearWay — AI Features Implementation Prompt (Coach + RAG + Evals).md` at the repo root. It's untracked on purpose, so don't commit it.

## Where things stand

| Phase | What | State |
|---|---|---|
| 0 | Spring AI BOM, pgvector database image | done |
| 1 | Provider layer for free-tier LLMs (Gemini by default, Groq optional), budgets, circuit breaker | done |
| 2 | Knowledge base: Gemini embeddings, pgvector (migration V6), search with a threshold | done |
| 3 | Coach: `POST /api/v1/coach/chat` with read-only tools, server-side identity | done |
| 4 | Evals (20-case golden set, retrieval eval), Micrometer metrics, per-message log | done; baseline 20/20 (12 Nov, 17:32 IST) |
| 5 | Coach page in the UI (`/coach`) | done |
| D | Wrap-up: README Coach section, technical doc, secret scan, full Docker test run, PR | done; **merged** (`50b5c2d`) |
| V | Coach voice input (Web Speech API, frontend only) | done; **merged** (`ed8bead`, PR #5) |

`feature/ai-coach`, `docs/eval-2026-11-12` and `feature/coach-voice-input` are merged and deleted, and so is the accidental `revert-1-feature/ai-coach` branch (never merged). New work goes on new branches from `main`.

## Commits in PR #1 (oldest first)

| Commit | What |
|---|---|
| `d682009` | Phase 0: Spring AI BOM, pgvector image |
| `09eb6db` | Phase 1: provider layer (Groq / Gemini) |
| `531ccdf` | Gemini as the default chat provider; the daily budget counts requests and tokens |
| `0e0c505` | Phase 2: knowledge base (embeddings, pgvector) |
| `7210e40` | Chat model default; logs the top knowledge-base search score |
| `4114f87` | Phase 3: Coach with safe tool calling |
| `cf6dd5b` | Per-message time budget, low reasoning effort |
| `a14d606` | flash-lite default, per-day quota cut-off, actuator behind HTTP Basic |
| `c60abab` | Real free-tier limits, served-model logging, token-paced ingestion |
| `6eba2bf` | Phase 4: evals and LLM observability |
| `7cc51b0` | README byte-order mark removed |
| `a1df0cd` | Behaviour checks for two eval cases, 5 casual retrieval queries |
| `b0b8dc4` | Docker: the API runs on IPv4 (fixes failing knowledge-base search in the container) |
| `23b1dbf` | Phase 5: Coach page |
| `261ab2f` | `lastEmbeddingCall` in health; health details only for the actuator login; IPv6 note, screenshots, this file |
| `51e66cf` | Handoff update (metrics login, machine-changes rule) |
| `584aa90`, `3f78d91`, `cfcd475` | Wrap-up: README Coach section, technical doc section 15 and ADR-33 to ADR-38, `.env.example` additions, handoff |
| `1565c53` | Behaviour check for `tool-single-reading` (and hours must be the tool's hours) |
| `50b5c2d` | Merge commit into `main` |

## Config and limits

- **Chat model:** `gemini-3.5-flash-lite` with low reasoning effort, via Gemini's OpenAI-compatible endpoint. The backup is `gemini-3.1-flash-lite`, which has its own quota. Groq is optional (`CLEARWAY_AI_CHAT_PROVIDER=groq`).
- **Embeddings:** `gemini-embedding-001`, 768 dimensions. The knowledge base is 5 files in `smart-routes-api/src/main/resources/kb/`.
- **Free tier, per project (AI Studio, 11 Nov 2026):**
  - flash-lite: 15 RPM, 250K TPM, 500 RPD
  - embeddings: 100 RPM, 30K TPM, 1,000 RPD
- **Quota reset:** midnight Pacific, which is **1:30 PM IST**.
- **App caps:** 400 chat requests and 2M tokens a day (`CLEARWAY_AI_DAILY_REQUESTS` / `_TOKENS`); embeddings 900 a day, 60 RPM, 25K tokens/min.
- **Coach limits:**
  - 45 s total per message (`CLEARWAY_AI_COACH_TOTAL_TIMEOUT`), 30 s per model call, at most 5 tool calls.
  - The UI waits 75 s (`COACH_TIMEOUT_MS`).
  - The similarity threshold stays at **0.60**; the user decided this, so don't change it without asking.
- **Shared quota:** the app and `./gradlew evalTest` share the same daily quota. Run the golden set at most once a day.
- **Ports:** API 8090, DB 5435, UI dev 5173, Docker web 4173. Never 8080.
- **Actuator:**
  - Health is public, and its details are shown only to the metrics login.
  - Metrics and info need `CLEARWAY_METRICS_USER` / `CLEARWAY_METRICS_PASSWORD` (no defaults; the endpoints are off without them).
  - They're in `.env` (added by the user on 12 Nov). Read them inside commands; never print or log the password. The user adds keys and passwords to `.env` themselves; never ask for them in chat.
  - A container reads `env_file` only when it's created: after changing `.env`, run `docker compose up -d api`, not `restart` (that asks first; see the working rules).
- **Error body:** Coach 503s carry `code`: `budget`, `quota_exhausted`, `time_budget` or `provider_error`.

## Eval results so far

| Run | Golden set | Tools | Grounding | KB | Safety | Requests / tokens |
|---|---|---|---|---|---|---|
| 12 Nov 2026, 17:32 IST ([baseline](evals/baseline.md)) | **20/20** | 8/8 | 4/4 | 4/4 | 4/4 | 47 / 59,908 |
| 12 Nov 2026, 11:22 IST ([morning run](evals/2026-11-12-morning-run.md)) | 19/20 | 7/8 | 4/4 | 4/4 | 4/4 | 46 / 61,178 |
| 11 Nov 2026, 08:41 IST ([first run](evals/2026-11-11-first-run.md)) | 18/20 | 7/8 | 4/4 | 4/4 | 3/4 | 48 / 64,390 |

- Every failure so far was a wording regex rejecting a correct answer. On the 12 Nov morning run that was `tool-single-reading` ("only data for 10:00 AM… don't have enough data").
- **Changed after the baseline (user-approved, 12 Nov):** `tool-single-reading` now uses the `no_usable_history` behaviour check. Unit tests: the real 12 Nov answer passes, an invented best time fails. The check now also requires an answer's hours to be the tool's hours (`hourlyAverageScore` keys, `bestHour`, `worstHour`), because "7:00 AM" was grounded by `"days":7`. The morning run stays 19/20. The 17:32 IST run (`main`, `50b5c2d`, 0 app requests before it, new quota day) was the first live run with all three behaviour checks: **20/20**.
- Retrieval (15 queries): recall@4 100% at 0.60 and 93% at 0.65; MRR 0.97; 0% off-topic false positives. The threshold stays at 0.60.
- **The 17:32 report didn't save passing answers**, so it can't show whether `tool-single-reading` called the one 10:00 reading "the best time" (issue [#2](https://github.com/bhuvii2005/ClearWay-app/issues/2)). Fixed after that run: the report's **Answers** section now has every case's question, tools called and verbatim answer, passed or failed (unit-tested; no live rerun yet).
- Golden runs share the 500/day quota with the app: at most one a quota day (after 1:30 PM IST), and check usage via metrics first.

## Tests (last full runs, 12 Nov 2026, freshly rebuilt Docker stack)

- **Backend:** `./gradlew build` passed 149 tests, with no model calls (151 after the `tool-single-reading` change, 152 after the eval report saves every answer; both rerun on the host on 12 Nov).
- **UI:** 47 Vitest tests pass; lint and build are clean.
- **e2e:** `E2E_BASE_URL=http://localhost:4173 npm run e2e` passed against the Docker web app.
- **Docker AI check:** a real knowledge-base question came back with 3 sources.
  - `kb.search{result=error}` was 0 (never recorded); both searches were hits.
  - `gen_ai.client.operation` and `gen_ai.client.token.usage` are present.
  - With the actuator login, health shows `lastEmbeddingCall: ok`; without it, health shows only UP.

## Open items and risks

1. **Issue [#2](https://github.com/bhuvii2005/ClearWay-app/issues/2), not implemented:** the Coach shouldn't call a single reading the "best time". Possible fix: a must-not-recommend eval check plus a system-prompt rule, maybe `bestHour`/`worstHour` = null when too few hours.
2. **Eval report answers:** done and merged (PR [#4](https://github.com/bhuvii2005/ClearWay-app/pull/4)); every case's answer and tools are saved. The next live run is the first to use it; check `tool-single-reading` there for issue #2.
3. **Shared quota:** the app and the evals share 500 chat requests a day, and the app's counter resets on restart, so the pre-flight check can undercount Google's real use.
4. **Known limits:**
   - conversation memory is in-memory only
   - budgets reset when the API restarts
   - air data is modelled on a 10–40 km grid
   - one API replica is assumed
5. **External flakiness:** TomTom, Photon and Open-Meteo sometimes fail the e2e smoke test; this is documented, so rerun it before suspecting the app. Issue [#3](https://github.com/bhuvii2005/ClearWay-app/issues/3) proposes mocking them in the default smoke run and keeping one optional live run (not implemented). A Docker web build once failed with a truncated base-image download, and a retry fixed it.

## Shipped follow-up: Coach voice input (PR #5, merged 14 Nov 2026)

One commit (`0acc701`), merged as `ed8bead`. It's frontend only: no backend change, no new npm dependencies and no Gemini quota.

**Code:**
- `smart-routes-ui/src/hooks/useSpeechRecognition.js`: Web Speech API (`SpeechRecognition` or `webkitSpeechRecognition`).
  - Settings: `continuous` false, interim results on, 1 alternative.
  - Language: `FALLBACK_SPEECH_LANG` (`en-IN`) unless the browser language is English.
  - Stops by itself after 30 s, gives up 2 s after a `stop()` that never ends, and aborts when the tab is hidden and on unmount (handlers detached, no state updates afterwards).
  - Unsupported outside a secure context.
- `CoachPage.jsx`: a mic button next to Send, hidden when unsupported, and disabled while the Coach is thinking.
  - The final text is appended to the box (`appendTranscript` in `lib/coach.js`), cut at 2,000 characters with a counter warning, and **never sent automatically**.
  - Send and New chat abort listening.
  - Interim words show in grey under the box, not inline. A textarea can't style part of its text.
  - Errors map to friendly text (`speechErrorMessage`); `aborted` shows nothing.

**Checks:**
- Tests: 73 UI tests (47 before, plus 19 page tests with a fake recognizer and 7 helper tests); lint and build clean. The language test stubs `navigator.language`: it first passed only because this machine reports `en-IN`, the fallback, and CI (`en-US`) caught it.
- Coach page load check in real headless Chrome on the dev server: loads, mic shown, no errors, no Coach requests.
- The full smoke test failed twice on 12 Nov at its planner steps (TomTom compare, then Photon place search), before the Coach step: external flakiness.
- Screenshot: `docs/screenshots/coach-voice-listening.png`, made with a fake recognizer, because headless Chrome has no speech service. In real headless Chrome, clicking the mic shows the "Microphone access is blocked" error, as designed.
- The user did a manual check before merging (the checklist covered Chrome, Edge, Android Chrome, iOS Safari, Firefox, permission denied and Indian-English accent).

**State:** shipped. CI on `main` is green after the merge.

## Remaining steps

1. The AI Coach and voice input are shipped; open follow-ups are issues #2, #3 and [#6](https://github.com/bhuvii2005/ClearWay-app/issues/6) (Coach actions: plan a route to a nearby place, not implemented). Merge anything only when the user says so (the user merges with a merge commit, not squash).
2. Follow-ups get their own branch and PR.
3. Keep this file current after every session.

## Working rules (from the user)

- Free tiers only. Never Ollama.
- Push only when told. Report and ask before anything major.
- **Ask before starting, stopping or installing anything on the user's machine** (Docker Desktop, services, global tools). That includes recreating or rebuilding containers, unless the user has already approved that step.
- Never commit secrets. Read keys from `.env` inside commands; never print them.
- Docker reads secrets via `env_file: .env`, never from the Dockerfile or compose file. CI uses repository secrets and a throwaway Postgres.
- Editing tip: Python string escapes have broken Java and YAML files twice. Use the Edit tool for Java strings that contain `\n` or regexes.
