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
import { AnimatePresence, motion } from 'motion/react';
import { AirRing, AirStrip, BandChip, Button, ErrorNote, PollutantReading, Skeleton, Spinner, TextField, Toggle } from '../components/ui';

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
  // Phones: the planner is a bottom sheet that can be pulled up to show more.
  const [sheetTall, setSheetTall] = useState(false);
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

      <motion.aside
        aria-label="Route planner"
        initial={{ opacity: 0, y: 16 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ type: 'spring', stiffness: 260, damping: 30 }}
        className={`absolute inset-x-0 bottom-0 z-10 overflow-y-auto rounded-t-3xl transition-[max-height] duration-300 ease-[var(--ease-out-soft)] ${sheetTall ? 'max-h-[88%]' : 'max-h-[58%]'} bg-surface/95 px-4 pb-5 pt-2 shadow-pop backdrop-blur-xl lg:inset-x-auto lg:bottom-auto lg:left-4 lg:top-4 lg:max-h-[calc(100%-2rem)] lg:w-[400px] lg:rounded-3xl lg:p-5`}
      >
        <motion.button
          type="button"
          aria-label={sheetTall ? 'Shrink the planner' : 'Expand the planner'}
          aria-expanded={sheetTall}
          onClick={() => setSheetTall((t) => !t)}
          onPanEnd={(_, info) => {
            if (info.offset.y < -24) setSheetTall(true);
            else if (info.offset.y > 24) setSheetTall(false);
          }}
          className="mx-auto mb-2 flex h-6 w-16 touch-none items-center justify-center lg:hidden"
        >
          <span className="h-1 w-10 rounded-full bg-line-strong" />
        </motion.button>
        <h1 className="font-display text-xl font-bold tracking-tight">Where to?</h1>
        <p className="mt-0.5 text-[13px] text-muted">We rank every route by the air along the way.</p>

        {/* Start and destination share one box, joined by a dotted line like a route. */}
        <div className="relative mt-4 rounded-2xl bg-surface-2/70 p-1">
          <div className="pointer-events-none absolute bottom-[21px] left-[17px] top-[21px] flex flex-col items-center" aria-hidden="true">
            <span className="h-2.5 w-2.5 rounded-full border-[2.5px] border-accent bg-surface" />
            <span className="my-1.5 w-0 flex-1 border-l-2 border-dashed border-faint/60" />
            <span className="h-2.5 w-2.5 rounded-[3px] bg-haze" />
          </div>
          <div className="space-y-1 pl-8 pr-11">
            <PlaceSearch
              bare
              label="Start"
              marker="A"
              placeholder="Choose a start point"
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
              trailing={
                <button
                  type="button"
                  onClick={locateMe}
                  disabled={locating}
                  title="Use my location"
                  aria-label="Use my location as the start"
                  className="flex h-8 w-8 shrink-0 items-center justify-center rounded-lg text-accent-ink transition-colors hover:bg-accent/10 disabled:opacity-50"
                >
                  {locating ? <Spinner className="h-4 w-4" /> : <Icon name="locate" className="h-4 w-4" />}
                </button>
              }
            />
            <PlaceSearch
              bare
              label="Destination"
              marker="B"
              placeholder="Choose a destination"
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
          <motion.button
            type="button"
            onClick={swap}
            disabled={!origin && !destination}
            title="Swap start and destination"
            aria-label="Swap start and destination"
            whileTap={{ rotate: 180, scale: 0.9 }}
            className="absolute right-2 top-1/2 flex h-9 w-9 -translate-y-1/2 items-center justify-center rounded-full bg-surface text-muted shadow-card transition-colors hover:text-ink disabled:opacity-40"
          >
            <Icon name="swap" className="h-4 w-4" />
          </motion.button>
        </div>

        <AnimatePresence initial={false} mode="wait">
          {(nextHint || (origin && destination && !routes.length && !searching)) && (
            <motion.p
              key={nextHint ?? 'move'}
              initial={{ opacity: 0, y: -2 }}
              animate={{ opacity: 1, y: 0 }}
              exit={{ opacity: 0 }}
              className="mt-2 px-1 text-xs text-faint"
            >
              {nextHint ?? 'Tip: click the map or drag a pin to fine-tune a point.'}
            </motion.p>
          )}
        </AnimatePresence>

        <fieldset className="mt-4">
          <legend className="sr-only">Travel mode</legend>
          <div className="grid grid-cols-4 gap-1 rounded-2xl bg-surface-2/70 p-1">
            {MODES.map((m) => (
              <label
                key={m.id}
                className={`relative flex h-11 cursor-pointer items-center justify-center gap-1.5 rounded-xl text-[13px] font-medium transition-colors has-[:focus-visible]:outline-2 has-[:focus-visible]:outline-accent ${
                  mode === m.id ? 'text-ink' : 'text-muted hover:text-ink'
                }`}
              >
                {mode === m.id && (
                  <motion.span layoutId="mode-pill" className="absolute inset-0 rounded-xl bg-surface shadow-card" />
                )}
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
                <Icon name={m.icon} className={`relative h-4 w-4 ${mode === m.id ? 'text-accent-ink' : ''}`} />
                <span className="relative">{m.label}</span>
              </label>
            ))}
          </div>
        </fieldset>

        <Button className="mt-4 h-12 w-full text-[15px]" onClick={findRoutes} disabled={!origin || !destination} loading={searching} icon="search">
          {searching ? 'Checking the air…' : 'Compare routes'}
        </Button>

        <p className="mt-3 px-1 text-xs text-faint">
          {sensitivities.length ? (
            <>Tuned for your sensitivity to {sensitivities.map((s) => s.label).join(', ')}. </>
          ) : (
            'Sensitive to a pollutant? '
          )}
          <Link to="/account" className="font-medium text-muted underline decoration-line-strong underline-offset-2 hover:text-ink">
            {sensitivities.length ? 'Change' : 'Tell us'}
          </Link>
        </p>

        {error && (
          <div className="mt-4">
            <ErrorNote>{error}</ErrorNote>
          </div>
        )}

        {searching && (
          <div className="mt-6 space-y-2.5" aria-hidden="true">
            {[0, 1, 2].map((i) => (
              <div key={i} className="rounded-2xl bg-surface p-4 shadow-card" style={{ opacity: 1 - i * 0.25 }}>
                <div className="flex items-start justify-between gap-3">
                  <div className="flex-1 space-y-2">
                    <Skeleton className="h-3.5 w-20" />
                    <Skeleton className="h-5 w-28" />
                  </div>
                  <Skeleton className="h-[52px] w-[52px] !rounded-full" />
                </div>
                <Skeleton className="mt-3 h-1.5 w-full" />
              </div>
            ))}
          </div>
        )}

        {!searching && routes.length > 0 && (
          <section aria-label="Route options" className="mt-6">
            <h2 className="mb-2.5 flex items-baseline justify-between px-1 text-sm font-semibold text-ink">
              {routes.length} {routes.length === 1 ? 'route' : 'routes'}
              <span className="text-xs font-normal text-faint">Cleanest first · {modeLabel(mode).toLowerCase()}</span>
            </h2>
            <motion.ol
              className="space-y-2.5"
              initial="hidden"
              animate="show"
              variants={{ show: { transition: { staggerChildren: 0.07 } } }}
            >
              {routes.map((route, i) => (
                <motion.li
                  key={`${searchId}-${i}`}
                  variants={{ hidden: { opacity: 0, y: 12 }, show: { opacity: 1, y: 0 } }}
                >
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
                </motion.li>
              ))}
            </motion.ol>
          </section>
        )}
      </motion.aside>
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
      className={`overflow-hidden rounded-2xl bg-surface transition-shadow duration-200 ${
        selected ? 'shadow-float ring-2 ring-accent' : 'shadow-card ring-1 ring-hairline hover:shadow-float'
      }`}
    >
      <button type="button" onClick={onSelect} aria-pressed={selected} className="w-full p-4 text-left focus-visible:outline-none">
        <div className="flex items-start justify-between gap-3">
          <div className="min-w-0">
            <div className="flex flex-wrap items-center gap-1.5 text-[13px] font-medium">
              {rank === 0 ? (
                <span className="inline-flex items-center gap-1 text-accent-ink">
                  <Icon name="leaf" className="h-3.5 w-3.5" /> Cleanest
                </span>
              ) : (
                <span className="text-muted">Route {rank + 1}</span>
              )}
              {route.fastest && <span className="rounded-md bg-surface-2 px-1.5 py-px text-[11px] text-ink-2">Fastest</span>}
            </div>
            <p className="mt-1 flex items-baseline gap-2">
              <span className="font-display text-xl font-bold tracking-tight text-ink">{formatDuration(route.travelTimeSeconds)}</span>
              <span className="text-[13px] text-muted">{formatDistanceMeters(route.distanceMeters)}</span>
            </p>
          </div>
          <AirRing score={route.pollutionScore} size={52} label="Exposure score" />
        </div>

        <AirStrip coords={route.pathCoordinates} samples={route.airSamples} score={route.pollutionScore} className="mt-3" />
        <div className="mt-1.5 flex justify-between text-[11px] text-faint" aria-hidden="true">
          <span>Start</span>
          <span>Destination</span>
        </div>

        <div className="mt-2.5 flex flex-wrap items-center gap-x-3 gap-y-1.5">
          <BandChip score={route.pollutionScore} />
          {trade.pm25 && (
            <span className={`inline-flex items-center gap-1 text-xs font-medium ${trade.pm25.includes('less') ? 'text-good' : 'text-warning'}`}>
              <Icon name="leaf" className="h-3.5 w-3.5" /> {trade.pm25}
            </span>
          )}
          {trade.time && (
            <span className="inline-flex items-center gap-1 text-xs text-muted">
              <Icon name="clock" className="h-3.5 w-3.5" /> {trade.time} vs fastest
            </span>
          )}
          {!trade.pm25 && !trade.time && <span className="text-xs text-muted">{exposureText(route)}</span>}
        </div>
      </button>

      <AnimatePresence initial={false}>
        {selected && (
          <motion.div
            key="details"
            initial={{ height: 0, opacity: 0 }}
            animate={{ height: 'auto', opacity: 1 }}
            exit={{ height: 0, opacity: 0 }}
            transition={{ type: 'spring', stiffness: 320, damping: 34 }}
          >
            <div className="px-4 pb-4">
              <div className="mb-3 h-px bg-line" />
              {data && (
                <>
                  <dl className="grid grid-cols-2 gap-2">
                    {POLLUTANTS.map((p) => (
                      <PollutantReading key={p.key} label={p.label} value={data[p.key]} who={whoComparison(p.key, data[p.key])} />
                    ))}
                  </dl>
                  <p className="mt-2 text-[11px] text-faint">Live average along the route · tick = WHO guideline</p>
                </>
              )}

              {savedId ? (
                <Link
                  to={`/routes/${savedId}`}
                  className="mt-3 flex h-10 items-center justify-center gap-2 rounded-xl bg-accent/10 text-sm font-semibold text-accent-ink transition-colors hover:bg-accent/15"
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
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  );
}
