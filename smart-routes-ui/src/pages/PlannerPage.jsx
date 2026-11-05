import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import { routesApi, errorMessage } from '../lib/api';
import { coordinateLabel, reverseGeocode } from '../lib/geocode';
import {
  MODES,
  POLLUTANTS,
  exposureText,
  formatDistanceMeters,
  formatDuration,
  modeLabel,
  tradeOff,
  whoComparison,
} from '../lib/format';
import { useAuth, useToast } from '../context/contexts';
import { AirRoute, BaseMap, FitBounds, OtherRoutes, Pin } from '../components/map';
import PlaceSearch from '../components/PlaceSearch';
import Icon from '../components/Icon';
import { AirRing, BandChip, Button, ErrorNote, PollutantReading, TextField, Toggle } from '../components/ui';

const MAX_SAVED_POINTS = 10000;
const ALT_LAYERS = ['alt-routes'];

function useIsWide() {
  const query = '(min-width: 1024px)';
  const [wide, setWide] = useState(() => window.matchMedia(query).matches);
  useEffect(() => {
    const mq = window.matchMedia(query);
    const onChange = () => setWide(mq.matches);
    mq.addEventListener('change', onChange);
    return () => mq.removeEventListener('change', onChange);
  }, []);
  return wide;
}

/** Keeps saved routes under the API's point limit while preserving both ends. */
function limitPoints(coords) {
  if (coords.length <= MAX_SAVED_POINTS) return coords;
  const step = Math.ceil(coords.length / (MAX_SAVED_POINTS - 1));
  const kept = coords.filter((_, i) => i % step === 0);
  kept.push(coords[coords.length - 1]);
  return kept;
}

const shortName = (place) => (place?.name ?? '').split(',')[0].slice(0, 40);

export default function PlannerPage() {
  const { user } = useAuth();
  const { push } = useToast();
  const wide = useIsWide();

  const [mode, setMode] = useState('walking');
  const [origin, setOrigin] = useState(null);
  const [destination, setDestination] = useState(null);
  const [focus, setFocus] = useState('origin');
  const [routes, setRoutes] = useState([]);
  const [selected, setSelected] = useState(0);
  const [searching, setSearching] = useState(false);
  const [error, setError] = useState('');
  const [mapCenter, setMapCenter] = useState(null);
  const [locating, setLocating] = useState(false);
  const [searchId, setSearchId] = useState(0);
  const [hoverRoute, setHoverRoute] = useState(false);
  const lookupRef = useRef({});

  const sensitivities = POLLUTANTS.filter((p) => user?.[p.pref]);

  // Any change to the trip makes previous results out of date.
  const resetResults = () => {
    setRoutes([]);
    setSelected(0);
    setError('');
  };

  const setPoint = useCallback((which, place) => {
    (which === 'origin' ? setOrigin : setDestination)(place);
    setRoutes([]);
    setSelected(0);
    setError('');
  }, []);

  /** A point picked on the map: show coordinates now, swap in the street name when found. */
  const dropPin = useCallback(
    (which, { lat, lng }) => {
      const pin = { lat, lng, name: coordinateLabel(lat, lng), detail: 'Pinned on map' };
      setPoint(which, pin);
      const controller = new AbortController();
      lookupRef.current[which]?.abort();
      lookupRef.current[which] = controller;
      reverseGeocode(lat, lng, { signal: controller.signal })
        .then((place) => {
          if (!place) return;
          const named = { lat, lng, name: place.name, detail: place.detail };
          (which === 'origin' ? setOrigin : setDestination)((current) =>
            current && current.lat === lat && current.lng === lng ? named : current,
          );
        })
        .catch(() => {});
    },
    [setPoint],
  );

  function locateMe() {
    if (!navigator.geolocation) {
      setError('Your browser cannot share its location.');
      return;
    }
    setLocating(true);
    navigator.geolocation.getCurrentPosition(
      (pos) => {
        setLocating(false);
        dropPin('origin', { lat: pos.coords.latitude, lng: pos.coords.longitude });
        setFocus('destination');
      },
      () => {
        setLocating(false);
        setError('Could not get your location. Check the browser permission.');
      },
      { enableHighAccuracy: true, timeout: 10000 },
    );
  }

  function swap() {
    setOrigin(destination);
    setDestination(origin);
    resetResults();
  }

  async function findRoutes() {
    if (!origin || !destination) return;
    setSearching(true);
    setError('');
    try {
      const found = await routesApi.alternatives(mode, [
        [origin.lng, origin.lat],
        [destination.lng, destination.lat],
      ]);
      setRoutes(found);
      setSelected(0);
      setSearchId((n) => n + 1);
      if (!found.length) setError('No routes were found between these points.');
    } catch (err) {
      setRoutes([]);
      setError(errorMessage(err, 'Could not compare routes right now.'));
    } finally {
      setSearching(false);
    }
  }

  const fitPoints = useMemo(() => {
    if (routes.length) return routes.flatMap((r) => r.pathCoordinates);
    return [origin, destination].filter(Boolean).map((p) => [p.lng, p.lat]);
  }, [routes, origin, destination]);

  // Keep routes clear of the planner panel: left on wide screens, bottom sheet on phones.
  const fitPadding = wide
    ? { top: 60, bottom: 60, left: 460, right: 70 }
    : { top: 40, bottom: Math.round(window.innerHeight * 0.58) + 24, left: 32, right: 32 };

  function handleMapClick(e) {
    const hit = e.features?.[0];
    if (hit) {
      setSelected(hit.properties.index);
      return;
    }
    const { lat, lng } = e.lngLat;
    const target = !origin ? 'origin' : !destination ? 'destination' : focus;
    dropPin(target, { lat, lng });
    if (target === 'origin' && !destination) setFocus('destination');
  }

  const nextHint = !origin
    ? 'Search for a start point, or click the map.'
    : !destination
      ? 'Now choose where you are going.'
      : null;

  return (
    <div className="relative h-full">
      <div className="absolute inset-0">
        <BaseMap
          onClick={handleMapClick}
          interactiveLayerIds={ALT_LAYERS}
          cursor={hoverRoute ? 'pointer' : 'crosshair'}
          onMouseEnter={() => setHoverRoute(true)}
          onMouseLeave={() => setHoverRoute(false)}
          onMoveEnd={(e) => setMapCenter({ lat: e.viewState.latitude, lng: e.viewState.longitude })}
        >
          {fitPoints.length > 0 && <FitBounds coords={fitPoints} padding={fitPadding} />}

          {routes.length > 1 && <OtherRoutes routes={routes} selected={selected} />}
          {routes[selected] && (
            <AirRoute
              coords={routes[selected].pathCoordinates}
              samples={routes[selected].airSamples}
              score={routes[selected].pollutionScore}
              animateKey={`${searchId}-${selected}`}
            />
          )}

          {origin && (
            <Pin
              kind="origin"
              longitude={origin.lng}
              latitude={origin.lat}
              draggable
              onDragEnd={(e) => dropPin('origin', { lat: e.lngLat.lat, lng: e.lngLat.lng })}
              title="Start (drag to move)"
            />
          )}
          {destination && (
            <Pin
              kind="destination"
              longitude={destination.lng}
              latitude={destination.lat}
              draggable
              onDragEnd={(e) => dropPin('destination', { lat: e.lngLat.lat, lng: e.lngLat.lng })}
              title="Destination (drag to move)"
            />
          )}
        </BaseMap>
      </div>

      <aside
        aria-label="Route planner"
        className="absolute inset-x-0 bottom-0 z-10 max-h-[58%] overflow-y-auto rounded-t-2xl border-t border-line bg-canvas/95 p-4 shadow-2xl backdrop-blur lg:inset-x-auto lg:bottom-4 lg:left-4 lg:top-4 lg:max-h-none lg:w-[410px] lg:rounded-2xl lg:border lg:p-5"
      >
        <h1 className="font-display text-lg font-bold tracking-tight">Find a cleaner route</h1>
        <p className="mt-0.5 text-[13px] text-muted">Routes are ranked by the air along the way.</p>

        <div className="mt-4 flex gap-2">
          <div className="min-w-0 flex-1 space-y-2">
            <PlaceSearch
              label="Start"
              marker="A"
              placeholder="Start point"
              value={origin}
              near={mapCenter}
              active={focus === 'origin'}
              onFocus={() => setFocus('origin')}
              onSelect={(p) => {
                setPoint('origin', p);
                if (!destination) setFocus('destination');
              }}
              onClear={() => {
                setPoint('origin', null);
                setFocus('origin');
              }}
            />
            <PlaceSearch
              label="Destination"
              marker="B"
              placeholder="Destination"
              value={destination}
              near={mapCenter}
              active={focus === 'destination'}
              onFocus={() => setFocus('destination')}
              onSelect={(p) => setPoint('destination', p)}
              onClear={() => {
                setPoint('destination', null);
                setFocus('destination');
              }}
            />
          </div>
          <div className="flex flex-col justify-between">
            <button
              type="button"
              onClick={locateMe}
              disabled={locating}
              title="Use my location as the start"
              aria-label="Use my location as the start"
              className="flex h-10 w-10 items-center justify-center rounded-lg border border-line-strong text-muted hover:bg-surface-2 hover:text-ink disabled:opacity-50"
            >
              <Icon name="locate" className="h-[18px] w-[18px]" />
            </button>
            <button
              type="button"
              onClick={swap}
              disabled={!origin && !destination}
              title="Swap start and destination"
              aria-label="Swap start and destination"
              className="flex h-10 w-10 items-center justify-center rounded-lg border border-line-strong text-muted hover:bg-surface-2 hover:text-ink disabled:opacity-40"
            >
              <Icon name="swap" className="h-[18px] w-[18px] rotate-90" />
            </button>
          </div>
        </div>

        {nextHint && <p className="mt-2 text-xs text-faint">{nextHint}</p>}
        {origin && destination && !routes.length && (
          <p className="mt-2 text-xs text-faint">Clicking the map now moves the highlighted point. You can also drag the pins.</p>
        )}

        <fieldset className="mt-4">
          <legend className="sr-only">Travel mode</legend>
          <div className="grid grid-cols-4 gap-1 rounded-lg border border-line bg-surface/60 p-1">
            {MODES.map((m) => (
              <label
                key={m.id}
                className={`flex h-14 cursor-pointer flex-col items-center justify-center gap-0.5 rounded-md text-xs font-semibold transition-colors has-[:focus-visible]:outline-2 has-[:focus-visible]:outline-accent ${
                  mode === m.id ? 'bg-surface-2 text-accent-ink' : 'text-muted hover:text-ink'
                }`}
              >
                <input
                  type="radio"
                  name="mode"
                  value={m.id}
                  checked={mode === m.id}
                  onChange={() => {
                    setMode(m.id);
                    resetResults();
                  }}
                  className="sr-only"
                />
                <Icon name={m.icon} className="h-5 w-5" />
                {m.label}
              </label>
            ))}
          </div>
        </fieldset>

        <Button className="mt-4 w-full" onClick={findRoutes} disabled={!origin || !destination} loading={searching} icon="search">
          {searching ? 'Checking the air on each route…' : 'Compare routes'}
        </Button>

        <p className="mt-2.5 text-xs text-faint">
          {sensitivities.length ? (
            <>
              Weighted for your sensitivity to {sensitivities.map((s) => s.label).join(', ')}.{' '}
            </>
          ) : (
            'Sensitive to a particular pollutant? '
          )}
          <Link to="/account" className="font-medium text-muted underline decoration-line-strong underline-offset-2 hover:text-ink">
            {sensitivities.length ? 'Change' : 'Set it in your account'}
          </Link>
        </p>

        {error && (
          <div className="mt-4">
            <ErrorNote>{error}</ErrorNote>
          </div>
        )}

        {routes.length > 0 && (
          <section aria-label="Route options" className="mt-5 border-t border-line pt-4">
            <h2 className="mb-3 text-sm font-semibold text-ink-2">
              {routes.length} {routes.length === 1 ? 'option' : 'options'} · {modeLabel(mode).toLowerCase()}
            </h2>
            <ol className="space-y-2">
              {routes.map((route, i) => (
                <li key={`${searchId}-${i}`}>
                  <RouteOption
                    route={route}
                    rank={i}
                    fastest={routes.find((r) => r.fastest)}
                    selected={i === selected}
                    onSelect={() => setSelected(i)}
                    defaultName={`${shortName(origin)} to ${shortName(destination)}`}
                    mode={mode}
                    onSaved={(saved) =>
                      push({
                        tone: 'success',
                        title: 'Route saved',
                        body: saved.isActive ? 'We will alert you if the air on it turns poor.' : undefined,
                        link: { to: `/routes/${saved.id}`, label: 'Open route' },
                      })
                    }
                  />
                </li>
              ))}
            </ol>
          </section>
        )}
      </aside>
    </div>
  );
}

function RouteOption({ route, rank, fastest, selected, onSelect, defaultName, mode, onSaved }) {
  const [saving, setSaving] = useState(false);
  const [name, setName] = useState(defaultName.slice(0, 100));
  const [monitor, setMonitor] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [savedId, setSavedId] = useState(null);
  const data = route.pollutionData;
  const trade = tradeOff(route, fastest);

  async function save(e) {
    e.preventDefault();
    setBusy(true);
    setError('');
    try {
      const saved = await routesApi.create({
        name: name.trim(),
        type: mode,
        pathCoordinates: limitPoints(route.pathCoordinates),
        distanceMeters: route.distanceMeters,
        travelTimeSeconds: route.travelTimeSeconds,
        monitor,
      });
      setSavedId(saved.id);
      setSaving(false);
      onSaved(saved);
    } catch (err) {
      setError(errorMessage(err, 'Could not save this route.'));
    } finally {
      setBusy(false);
    }
  }

  return (
    <div
      className={`rounded-xl border transition-colors ${
        selected ? 'border-accent bg-surface shadow-md ring-1 ring-accent/30' : 'border-line bg-surface/70 hover:border-line-strong'
      }`}
    >
      <button type="button" onClick={onSelect} aria-pressed={selected} className="w-full p-3.5 text-left">
        <div className="flex items-start justify-between gap-3">
          <div className="min-w-0">
            <div className="flex flex-wrap items-center gap-1.5">
              <span className="text-sm font-semibold text-ink">{rank === 0 ? 'Cleanest' : `Option ${rank + 1}`}</span>
              {route.fastest && (
                <span className="rounded-full bg-surface-2 px-2 py-0.5 text-[11px] font-semibold text-ink-2">Fastest</span>
              )}
            </div>
            <p className="mt-0.5 text-[13px] text-muted">
              {formatDuration(route.travelTimeSeconds)} · {formatDistanceMeters(route.distanceMeters)}
            </p>
          </div>
          <AirRing score={route.pollutionScore} label="Exposure score" />
        </div>
        <div className="mt-2.5 flex flex-wrap items-center gap-x-2 gap-y-1.5">
          <BandChip score={route.pollutionScore} />
          <span className="text-xs text-muted">{exposureText(route)}</span>
        </div>
        {(trade.time || trade.pm25) && (
          <p className="mt-2 flex flex-wrap gap-1.5 text-xs font-semibold">
            {trade.time && (
              <span className="inline-flex items-center gap-1 rounded-md bg-surface-2 px-2 py-0.5 text-ink-2">
                <Icon name="clock" className="h-3.5 w-3.5" /> {trade.time} <span className="font-normal text-muted">vs fastest</span>
              </span>
            )}
            {trade.pm25 && (
              <span
                className={`inline-flex items-center gap-1 rounded-md px-2 py-0.5 ${
                  trade.pm25.includes('less') ? 'bg-good/10 text-good' : 'bg-warning/10 text-warning'
                }`}
              >
                <Icon name="leaf" className="h-3.5 w-3.5" /> {trade.pm25}
              </span>
            )}
          </p>
        )}
      </button>

      {selected && (
        <div className="border-t border-line px-3.5 pb-3.5 pt-3">
          {data && (
            <dl className="grid grid-cols-2 gap-2">
              {POLLUTANTS.map((p) => (
                <PollutantReading key={p.key} label={p.label} value={data[p.key]} who={whoComparison(p.key, data[p.key])} />
              ))}
            </dl>
          )}
          {data && (
            <p className="mt-1.5 text-[11px] text-faint">
              Average along the route now. The tick marks the WHO guideline.
            </p>
          )}

          {savedId ? (
            <Link
              to={`/routes/${savedId}`}
              className="mt-3 flex items-center justify-center gap-2 rounded-lg border border-accent/40 bg-accent/10 py-2 text-sm font-semibold text-accent-ink hover:bg-accent/15"
            >
              <Icon name="check" className="h-4 w-4" /> Saved · open route
            </Link>
          ) : saving ? (
            <form onSubmit={save} className="mt-3 space-y-3">
              <TextField label="Route name" required maxLength={100} value={name} onChange={(e) => setName(e.target.value)} />
              <Toggle
                checked={monitor}
                onChange={setMonitor}
                label="Watch this route"
                description="Check the air every 15 minutes and alert me when it turns poor."
              />
              <ErrorNote>{error}</ErrorNote>
              <div className="flex gap-2">
                <Button type="submit" loading={busy} className="flex-1" disabled={!name.trim()}>
                  Save route
                </Button>
                <Button variant="ghost" onClick={() => setSaving(false)}>
                  Cancel
                </Button>
              </div>
            </form>
          ) : (
            <Button variant="secondary" className="mt-3 w-full" icon="save" onClick={() => setSaving(true)}>
              Save this route
            </Button>
          )}
        </div>
      )}
    </div>
  );
}
