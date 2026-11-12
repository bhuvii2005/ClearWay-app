# ClearWay AI features: handoff

How far the AI work on `feature/ai-coach` has got, so a fresh session (or person) can pick it up. Last updated **12 Nov 2026, 08:30 IST**. Keep it current: update it at the end of every work session.

The full task list is in `ClearWay — AI Features Implementation Prompt (Coach + RAG + Evals).md` at the repo root. It's untracked on purpose, so don't commit it.

## Where things stand

| Phase | What | State |
|---|---|---|
| 0 | Spring AI BOM, pgvector database image | done |
| 1 | Provider layer for free-tier LLMs (Gemini by default, Groq optional), budgets, circuit breaker | done |
| 2 | Knowledge base: Gemini embeddings, pgvector (migration V6), search with a threshold | done |
| 3 | Coach: `POST /api/v1/coach/chat` with read-only tools, server-side identity | done |
| 4 | Evals (20-case golden set, retrieval eval), Micrometer metrics, per-message log | done; golden set rerun is due |
| 5 | Coach page in the UI (`/coach`) | done |
| D | Wrap-up: README Coach section, technical doc, secret scan, full Docker test run, PR | **next** |

The branch is pushed up to `23b1dbf`. Work after that (health detail, IPv6 README note, screenshots, this file) is committed locally first; see `git log origin/feature/ai-coach..HEAD`.

## Commits on the branch (oldest first)

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
  - **These are not in `.env` yet.** The user adds keys and passwords to `.env` themselves; never ask for them in chat.
- **Error body:** Coach 503s carry `code`: `budget`, `quota_exhausted`, `time_budget` or `provider_error`.

## Eval results so far

- **Golden set:** one full run (11 Nov 2026, 08:41 IST) passed **18 of 20** cases.
  - Tool selection 7/8, grounding 4/4, knowledge base 4/4, safety 3/4.
  - It used 48 requests and 64,390 tokens, at 9.8 s average per message.
  - Both failures were wording checks rejecting correct answers. Those two cases (`tool-no-history`, `safety-other-users-route`) now use behaviour checks, which haven't had a live run yet.
  - Report: [docs/evals/baseline.md](evals/baseline.md).
- **Retrieval (15 queries, 11 Nov, 19:58 IST):**
  - recall@4 100% at 0.50–0.60 and 93% at 0.65; MRR 0.97 at 0.60; 0% off-topic false positives.
  - The lowest expected-file score was 0.643; the best off-topic score was 0.484.
- **Targets:** tool selection ≥85%, grounding ≥90%, KB citations ≥90%, safety 100%, recall@4 ≥80%. Report the real numbers; never weaken a test to pass.

## Tests (last full runs)

- **Backend:** `./gradlew build` passes 149 tests with no model calls. It needs Docker running for Testcontainers.
- **UI:** `npx vitest run` passes 47 tests, and `npx eslint .` is clean.
- **e2e:** `npm run e2e` passed on 11 Nov. It needs the API, the dev server and TomTom. TomTom/DNS timeouts sometimes fail it; that's external.

## Open items and risks

1. **Golden set rerun:** due 12 Nov after 1:30 PM IST. Run it with `./gradlew evalTest --tests '*CoachGoldenEvalTest'`. Update `docs/evals/baseline.md` only if the harness is final, and keep the honest 18/20 history.
2. **Quota pre-flight check:** it can't read the app's usage until `CLEARWAY_METRICS_*` is set in `.env`. Until then a run says "couldn't be checked" and goes ahead.
3. **Metrics in Docker:** check, on a freshly rebuilt stack, that Spring AI's `gen_ai.client.*` metrics appear and that `kb.search{result=error}` is 0 after a real knowledge-base question. This also needs the metrics login.
4. **Known limits (for the README Coach section):**
   - conversation memory is in-memory only
   - budgets reset when the API restarts
   - air data is modelled on a 10–40 km grid
   - the app and evals share one quota
5. **Smoke test:** the TomTom/Photon/Open-Meteo flakiness is external and documented; don't change the smoke test for it.

## Remaining steps

1. Run the golden set once (after 1:30 PM IST on 12 Nov) and report the numbers.
2. **Wrap-up D:**
   - **README:** a Coach section covering what it does, architecture, the limits table, a link to `docs/coach-model-comparison.md`, the eval baseline and known limitations.
   - **Technical reference doc** (Claude Doc `https://claude.ai/artifact/Df3voaKiXS6J8zuHcrN8QY`):
     - new endpoints
     - V6
     - all new config properties
     - ADRs: Spring AI 2.0, Gemini free tier by default with Groq optional, pgvector in the same Postgres, read-only tools with server-side identity, deterministic evals, per-message time budget
   - **`.env.example`:** placeholders only, plus a secret scan of every new commit.
   - **Full Docker run:** rebuild the stack, run backend, UI and e2e tests, then the metrics check (open item 3).
   - **PR `feature/ai-coach` → `main`:** summary, eval numbers, the model comparison table, screenshots from `docs/screenshots/`. **Don't merge.**
3. **Stop and report** the PR link and the risks.

## Working rules (from the user)

- Free tiers only. Never Ollama.
- Push only when told. Report and ask before anything major.
- Never commit secrets. Read keys from `.env` inside commands; never print them.
- Docker reads secrets via `env_file: .env`, never from the Dockerfile or compose file. CI uses repository secrets and a throwaway Postgres.
- Editing tip: Python string escapes have broken Java and YAML files twice. Use the Edit tool for Java strings that contain `\n` or regexes.
