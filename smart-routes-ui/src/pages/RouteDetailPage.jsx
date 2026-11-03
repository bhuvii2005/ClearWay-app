import { useEffect, useMemo, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { Marker, Polyline } from 'react-leaflet';
import { routesApi, errorMessage } from '../lib/api';
import {
  POLLUTANTS,
  formatDateTime,
  formatDistanceKm,
  formatDuration,
  formatHour,
  formatRelative,
  formatScore,
  modeLabel,
  scoreBand,
  userTimeZone,
} from '../lib/format';
import { useToast } from '../context/contexts';
import { BaseMap, FitBounds, InvalidateOnResize } from '../components/map';
import { pinIcon, toLatLngs } from '../lib/mapUtils';
import { HourlyChart, ScoreTrendChart } from '../components/charts';
import Icon from '../components/Icon';
import { BandChip, Card, EmptyState, ErrorNote, PageLoader, Toggle } from '../components/ui';

const PERIODS = [
  { days: 1, label: '24 h' },
  { days: 7, label: '7 days' },
  { days: 30, label: '30 days' },
  { days: 90, label: '90 days' },
];

export default function RouteDetailPage() {
  const { routeId } = useParams();
  const { push } = useToast();
  const [route, setRoute] = useState(null);
  const [history, setHistory] = useState(null);
  const [days, setDays] = useState(7);
  const [error, setError] = useState('');
  const [historyError, setHistoryError] = useState('');
  const [togglingWatch, setTogglingWatch] = useState(false);
  const timeZone = useMemo(userTimeZone, []);

  useEffect(() => {
    routesApi
      .get(routeId)
      .then(setRoute)
      .catch((err) =>
        setError(err.response?.status === 404 ? 'This route does not exist or was deleted.' : errorMessage(err, 'Could not load this route.')),
      );
  }, [routeId]);

  useEffect(() => {
    let cancelled = false;
    setHistoryError('');
    routesApi
      .history(routeId, days, timeZone)
      .then((h) => !cancelled && setHistory(h))
      .catch((err) => !cancelled && setHistoryError(errorMessage(err, 'Could not load the air-quality history.')));
    return () => {
      cancelled = true;
    };
  }, [routeId, days, timeZone]);

  const path = useMemo(() => toLatLngs(route?.pathCoordinates), [route]);
  const points = history?.points ?? [];
  const latest = points.length ? points[points.length - 1] : null;
  const scored = points.filter((p) => p.pollutionScore != null);
  // Best and worst hours only mean something once readings cover at least two hours of the day.
  const hoursCovered = Object.keys(history?.hourlyAverages ?? {}).length;
  const bestHour = hoursCovered >= 2 ? history.bestHour : null;
  const worstHour = hoursCovered >= 2 ? history.worstHour : null;
  const periodAverage = scored.length ? scored.reduce((sum, p) => sum + p.pollutionScore, 0) / scored.length : null;

  async function toggleWatch(on) {
    setTogglingWatch(true);
    try {
      const updated = await routesApi.update(routeId, { isActive: on });
      setRoute((r) => ({ ...r, ...updated }));
      push({ tone: 'success', title: on ? 'Watching this route' : 'Stopped watching this route' });
    } catch (err) {
      push({ tone: 'error', title: 'Could not update the route', body: errorMessage(err) });
    } finally {
      setTogglingWatch(false);
    }
  }

  if (error) {
    return (
      <div className="mx-auto max-w-5xl px-4 py-8 sm:px-6">
        <BackLink />
        <div className="mt-6">
          <ErrorNote>{error}</ErrorNote>
        </div>
      </div>
    );
  }
  if (!route) return <PageLoader label="Loading route…" />;

  return (
    <div className="mx-auto max-w-6xl px-4 py-8 sm:px-6">
      <BackLink />

      <div className="mt-4 flex flex-wrap items-start justify-between gap-4">
        <div className="min-w-0">
          <h1 className="truncate text-2xl font-bold tracking-tight">{route.name}</h1>
          <p className="mt-1 text-sm text-slate-400">
            {modeLabel(route.type)} · {formatDistanceKm(route.distanceKm)}
            {route.travelTimeSeconds ? ` · ${formatDuration(route.travelTimeSeconds)}` : ''} · last checked{' '}
            {formatRelative(route.lastCheckedAt)}
          </p>
        </div>
        <div className="w-full rounded-xl border border-slate-800 bg-slate-900/70 px-4 py-2 sm:w-80">
          <Toggle
            checked={Boolean(route.isActive)}
            disabled={togglingWatch}
            onChange={toggleWatch}
            label="Watch for poor air"
            description={route.isActive ? 'Checked every 15 minutes, with alerts' : 'Not being checked'}
          />
        </div>
      </div>

      <div className="mt-6 grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <Stat label="Latest reading" value={formatScore(latest?.pollutionScore ?? route.lastPollutionScore)}>
          <BandChip score={latest?.pollutionScore ?? route.lastPollutionScore} />
        </Stat>
        <Stat label={`Average · ${PERIODS.find((p) => p.days === days)?.label}`} value={formatScore(periodAverage)}>
          <BandChip score={periodAverage} />
        </Stat>
        <Stat label="Cleanest time to go" value={formatHour(bestHour)}>
          <HourNote hour={bestHour} averages={history?.hourlyAverages} />
        </Stat>
        <Stat label="Worst time to go" value={formatHour(worstHour)}>
          <HourNote hour={worstHour} averages={history?.hourlyAverages} />
        </Stat>
      </div>

      <div className="mt-4 grid gap-4 lg:grid-cols-[minmax(0,3fr)_minmax(0,2fr)]">
        <div className="overflow-hidden rounded-2xl border border-slate-800">
          <div className="h-80">
            <BaseMap className="h-full w-full" scrollWheelZoom={false}>
              <InvalidateOnResize />
              {path.length > 0 && <FitBounds points={path} padding={[32, 32]} />}
              <Polyline positions={path} pathOptions={{ color: '#020617', weight: 9, opacity: 0.85 }} />
              <Polyline positions={path} pathOptions={{ color: '#f8fafc', weight: 4 }} />
              {path.length > 0 && <Marker position={path[0]} icon={pinIcon('origin')} />}
              {path.length > 1 && <Marker position={path[path.length - 1]} icon={pinIcon('destination')} />}
            </BaseMap>
          </div>
        </div>

        <Card>
          <h2 className="text-sm font-semibold text-slate-300">Latest pollutant levels</h2>
          {latest ? (
            <>
              <dl className="mt-3 grid grid-cols-2 gap-3">
                {POLLUTANTS.map((p) => (
                  <div key={p.key} className="rounded-xl bg-slate-950/50 p-3">
                    <dt className="text-[11px] font-semibold uppercase tracking-wide text-slate-500">{p.label}</dt>
                    <dd className="mt-1 text-xl font-bold tabular-nums text-slate-100">
                      {latest[p.key] == null ? '–' : Math.round(latest[p.key])}
                      <span className="ml-1 text-xs font-normal text-slate-500">µg/m³</span>
                    </dd>
                  </div>
                ))}
              </dl>
              <p className="mt-3 text-xs text-slate-500">Measured {formatDateTime(latest.timestamp)}, averaged along the route.</p>
            </>
          ) : (
            <p className="mt-3 text-sm text-slate-500">No readings in this period yet.</p>
          )}
        </Card>
      </div>

      <section className="mt-8" aria-labelledby="history-heading">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <h2 id="history-heading" className="text-lg font-semibold">
            Air-quality history
          </h2>
          <div role="radiogroup" aria-label="Period" className="flex rounded-lg border border-slate-800 bg-slate-900/60 p-1">
            {PERIODS.map((p) => (
              <button
                key={p.days}
                type="button"
                role="radio"
                aria-checked={days === p.days}
                onClick={() => setDays(p.days)}
                className={`min-h-8 rounded-md px-3 text-xs font-semibold transition-colors ${
                  days === p.days ? 'bg-slate-800 text-slate-100' : 'text-slate-400 hover:text-slate-200'
                }`}
              >
                {p.label}
              </button>
            ))}
          </div>
        </div>

        <div className="mt-4">
          {historyError ? (
            <ErrorNote>{historyError}</ErrorNote>
          ) : !history ? (
            <PageLoader label="Loading history…" />
          ) : scored.length < 2 ? (
            <EmptyState icon="clock" title="Not enough readings yet">
              {route.isActive
                ? 'Watched routes are checked every 15 minutes. Come back in a few hours to see trends and the best time to travel.'
                : 'Turn on “Watch for poor air” so ClearWay checks this route regularly and builds up its history.'}
            </EmptyState>
          ) : (
            <div className="grid gap-4 lg:grid-cols-2">
              <Card>
                <h3 className="text-sm font-semibold text-slate-300">Exposure score over time</h3>
                <p className="mt-0.5 text-xs text-slate-500">Lower is cleaner. Alerts are sent above the dashed line.</p>
                <div className="mt-4">
                  <ScoreTrendChart points={points} days={days} />
                </div>
              </Card>
              <Card>
                <h3 className="text-sm font-semibold text-slate-300">Average by hour of day</h3>
                <p className="mt-0.5 text-xs text-slate-500">In your time zone ({history.timeZone}).</p>
                <div className="mt-4">
                  <HourlyChart hourlyAverages={history.hourlyAverages} bestHour={bestHour} />
                </div>
                <HourlyTable averages={history.hourlyAverages} />
              </Card>
            </div>
          )}
        </div>
      </section>
    </div>
  );
}

function BackLink() {
  return (
    <Link to="/routes" className="inline-flex items-center gap-1.5 text-sm text-slate-400 hover:text-slate-200">
      <Icon name="back" className="h-4 w-4" /> My routes
    </Link>
  );
}

function Stat({ label, value, children }) {
  return (
    <div className="rounded-2xl border border-slate-800 bg-slate-900/70 p-4">
      <p className="text-[11px] font-semibold uppercase tracking-wide text-slate-500">{label}</p>
      <p className="mt-1.5 text-3xl font-black tabular-nums text-slate-100">{value}</p>
      <div className="mt-2 min-h-5">{children}</div>
    </div>
  );
}

function HourNote({ hour, averages }) {
  if (hour == null) return <span className="text-xs text-slate-500">Needs more readings</span>;
  const score = averages?.[hour] ?? averages?.[String(hour)];
  return <span className="text-xs text-slate-400">Average {formatScore(score)} · {scoreBand(score).label.toLowerCase()}</span>;
}

/** The same hourly numbers as a table, for screen readers and exact values. */
function HourlyTable({ averages }) {
  const rows = Object.entries(averages ?? {})
    .map(([h, v]) => [Number(h), v])
    .sort((a, b) => a[0] - b[0]);
  if (!rows.length) return null;
  return (
    <details className="mt-3 text-sm">
      <summary className="cursor-pointer text-xs font-medium text-slate-400 hover:text-slate-200">Show as table</summary>
      <table className="mt-2 w-full text-left text-xs">
        <thead>
          <tr className="text-slate-500">
            <th className="py-1 font-medium">Hour</th>
            <th className="py-1 font-medium">Average score</th>
            <th className="py-1 font-medium">Air quality</th>
          </tr>
        </thead>
        <tbody className="text-slate-300">
          {rows.map(([hour, score]) => (
            <tr key={hour} className="border-t border-slate-800">
              <td className="py-1">{formatHour(hour)}</td>
              <td className="py-1 tabular-nums">{formatScore(score)}</td>
              <td className="py-1">{scoreBand(score).label}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </details>
  );
}
