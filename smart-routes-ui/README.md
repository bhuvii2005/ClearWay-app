# ClearWay web app

The React front end for ClearWay. It talks to the Spring Boot API in `../smart-routes-api`.

## Run it

```bash
npm install
npm run dev        # http://localhost:5173
```

The API address comes from `VITE_API_URL` in `.env` (default `http://localhost:8090`). Copy `.env.example` to `.env` to change it.

Other scripts: `npm run build` (production build into `dist/`), `npm run preview`, `npm run lint`.

## Tests

- `npm test`: unit tests (Vitest) for air-quality bands, wording and formatting, and the API client's sign-out-on-401 behaviour.
- `npm run e2e`: browser smoke test with your installed Chrome. It signs up a throwaway `@test.dev` user, compares and saves a route, opens every page and fails on any browser error or failed request. Start the API and `npm run dev` first; route search needs the TomTom key. Screenshots land in `e2e/screenshots/` (git-ignored).

## Screens

| Path | Page | What it does |
|---|---|---|
| `/login`, `/signup` | `AuthPage` | Sign in or create an account |
| `/` | `PlannerPage` | Search addresses or click the map, pick a travel mode, compare routes by air quality, save one |
| `/routes` | `RoutesPage` | Saved routes with latest and average scores; watch on/off, rename, delete |
| `/routes/:id` | `RouteDetailPage` | Route map, latest pollutants, score history chart, average by hour, cleanest and worst hour |
| `/alerts` | `AlertsPage` | Stored alerts, unread markers, mark all read |
| `/account` | `AccountPage` | Profile, pollutant sensitivities, password change, sign out everywhere, delete account |

## Structure

```
src/
  main.jsx               Router + providers
  App.jsx                Routes and sign-in guard; secondary pages load lazily
  lib/
    api.js               Axios client, token storage, all API calls, error messages
    format.js            Air-quality bands, units, durations, times
    geocode.js           Address search via Photon (free, no key)
    mapUtils.js          Map pins and coordinate helpers
  context/
    contexts.js          React contexts and hooks (useAuth, useToast, useAlerts)
    AuthProvider.jsx     Session: token, current user, login/logout
    ToastProvider.jsx    Corner notifications
    AlertsProvider.jsx   Live alerts over STOMP/SockJS + unread count
  components/            Layout, map pieces, address search, charts, icons, form controls
  pages/                 One file per screen
```

## Free services used

- **Map tiles:** OpenStreetMap standard tiles, darkened with a CSS filter. Attribution is shown on the map. Fine for development and light use. A busy public deployment should move to its own tile provider.
- **Address search:** [Photon](https://photon.komoot.io) by Komoot, built on OpenStreetMap. No key needed.
- **Charts:** Recharts.
- **Live alerts:** `@stomp/stompjs` + `sockjs-client`, connecting to the API's `/ws` endpoint with the login token.
