# ClearWay web app

The React front end for ClearWay. It talks to the Spring Boot API in `../smart-routes-api`.

## Run it

```bash
npm install
npm run dev        # http://localhost:5173
```

The API address comes from `VITE_API_URL` in `.env` (default `http://localhost:8090`). Copy `.env.example` to `.env` to change it.

Other scripts: `npm run build` (production build into `dist/`), `npm run preview`, `npm run lint`.

In Docker (`docker compose up -d --build` from the project root) the app is built with `VITE_API_URL` from the root `.env` and served by nginx on http://localhost:4173. `nginx.conf` sends unknown paths to `index.html` so links like `/routes/42` work.

## Tests

- `npm test`: unit tests (Vitest) for air-quality bands, wording and formatting, and the API client's sign-out-on-401 behaviour.
- `npm run e2e`: browser smoke test with your installed Chrome. It signs up a throwaway `@test.dev` user, compares and saves a route, opens every page and fails on any browser error or failed request. Start the API and `npm run dev` first; route search needs the TomTom key. Screenshots land in `e2e/screenshots/` (git-ignored).

## Screens

| Path | Page | What it does |
|---|---|---|
| `/` (signed out) | `LandingPage` | What ClearWay does, with live examples; links to sign up or sign in |
| `/login`, `/signup` | `AuthPage` | Sign in or create an account, beside an animated route illustration |
| `/` (signed in) | `PlannerPage` | Search addresses or click the map, pick a travel mode, compare routes by air quality, save one |
| `/routes` | `RoutesPage` | Saved routes with latest and average scores; watch on/off, rename, delete |
| `/routes/:id` | `RouteDetailPage` | Route map, air now, 24-hour best-time strip, pollutants against WHO guidelines, history charts |
| `/alerts` | `AlertsPage` | Alerts grouped by day, unread markers, mark all read |
| `/account` | `AccountPage` | Profile, pollutant sensitivities, password change, sign out everywhere, delete account |

## Structure

```
src/
  main.jsx               Router + providers
  App.jsx                Routes and sign-in guard; secondary pages load lazily
  lib/
    api.js               Axios client, token storage, all API calls, error messages
    format.js            Air-quality bands, WHO comparisons, trade-offs, units, durations, times
    geocode.js           Address search via Photon (free, no key)
    mapUtils.js          Route geometry and air-colour stops for the map
    theme.js             Light/dark theme choice
  context/
    contexts.js          React contexts and hooks (useAuth, useToast, useAlerts)
    AuthProvider.jsx     Session: token, current user, login/logout
    ToastProvider.jsx    Corner notifications
    AlertsProvider.jsx   Live alerts over STOMP/SockJS + unread count
  components/            Layout, map (MapLibre), address search, charts, icons (Lucide),
                         shared UI (score rings, air strips, skeletons), route illustration
  pages/                 One file per screen
```

## Free services used

- **Map:** MapLibre GL with [OpenFreeMap](https://openfreemap.org) vector styles (Positron for light, Dark for dark). No key needed; attribution is shown on the map.
- **Address search:** [Photon](https://photon.komoot.io) by Komoot, built on OpenStreetMap. No key needed.
- **Charts:** Recharts.
- **Animation and icons:** Motion (respects the system's reduced-motion setting) and Lucide.
- **Live alerts:** `@stomp/stompjs` + `sockjs-client`, connecting to the API's `/ws` endpoint with the login token.
