# Coach model comparison

**Date:** 11 November 2026. **Single runs**: one run per row, so treat small differences as noise.

**Check:** the live Coach eval (`CoachLiveTest`, `./gradlew evalTest`). The question is "When is the best time to walk my saved route?", asked against fixed route data. The cleanest hour is 07:00 (score 18.0) and the most polluted is 18:00 (score 72.0).
- **Pass:** the Coach calls `listMyRoutes`, then `getRouteHistory`, and names 7 AM.
- **How it was run:** Gemini's OpenAI-compatible endpoint, free tier, from a home connection in India, with eval pacing turned off (`CLEARWAY_EVAL_RPM=60`).

| Model | Reasoning effort | Each call | Total time | Tokens | Tools, in order | Right hour |
|---|---|---|---|---|---|---|
| gemini-3.5-flash | model default | 6.6 s, 5.4 s, 5.3 s | 18.8 s | 4,648 | listMyRoutes, getRouteHistory | yes (7:00 AM, 18.0) |
| gemini-3.5-flash | low | 6.5 s, 3.4 s, 6.2 s | 17.5 s | 4,121 | listMyRoutes, getRouteHistory | yes |
| **gemini-3.5-flash-lite** | **low** | 5.9 s, 2.7 s, 3.2 s | **13.5 s** | **3,662** | listMyRoutes, getRouteHistory | yes (07:00, 18.0) |
| gemini-3.5-flash-lite | model default | 6.3 s, 2.5 s, 2.7 s | 13.2 s | 3,645 | listMyRoutes, getRouteHistory | yes (07:00, 18.0) |

The gemini-3.5-flash low-effort run sent 4 requests for 3 calls. The extra one was probably a retry after a 429, which is consistent with the rate limits below.

## Free-tier limits seen
- **gemini-3.5-flash:** 20 requests per day, per project and per model (`GenerateRequestsPerDayPerProjectPerModel-FreeTier`). The quota resets at midnight Pacific. Google serves this model as `gemini-3.6-flash`.
- **gemini-3.5-flash-lite:** a separate quota from flash. Its exact limits are still to be confirmed from the AI Studio console.
- **gemini-3.8-flash:** never replied on the free tier in testing (earlier in November 2026).

## Not compared
- **Groq:** no `GROQ_API_KEY` was set.
- **More runs per row:** not done, because flash's daily quota ran out.

## Decision
- **Coach and evals:** `gemini-3.5-flash-lite` with `low` reasoning effort is the default.
- **Daily cap:** 20 requests a day until the real limits are known.
- **When Google's quota runs out:** a per-day 429 from Google stops all chat calls until midnight Pacific.
