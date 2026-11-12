import { useEffect, useMemo, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { motion } from 'motion/react';
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
  whoComparison,
} from '../lib/format';
import { useToast } from '../context/contexts';
import { AirRoute, BaseMap, Pin } from '../components/map';
import { boundsOf } from '../lib/mapUtils';
import { HourlyChart, ScoreTrendChart } from '../components/charts';
import Icon from '../components/Icon';
import { AirRing, BandChip, Card, EmptyState, ErrorNote, PageLoader, PollutantReading, Segmented, Skeleton, Toggle } from '../components/ui';

const PERIODS = [
  { value: 1, label: '24 h' },
  { value: 7, label: '7 days' },
  { value: 30, label: '30 days' },
  { value: 90, label: '90 days' },
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

  const path = useMemo(() => route?.pathCoordinates ?? [], [route]);
  const points = history?.points ?? [];
  const latest = points.length ? points[points.length - 1] : null;
  const scored = points.filter((p) => p.pollutionScore != null);
  // Best and worst hours only mean something once readings cover at least two hours of the day.
  const hoursCovered = Object.keys(history?.hourlyAverages ?? {}).length;
  const bestHour = hoursCovered >= 2 ? history.bestHour : null;
  const worstHour = hoursCovered >= 2 ? history.worstHour : null;
  const periodAverage = scored.length ? scored.reduce((sum, p) => sum + p.pollutionScore, 0) / scored.length : null;
  const latestScore = latest?.pollutionScore ?? route?.lastPollutionScore;
  const periodLabel = PERIODS.find((p) => p.value === days)?.label;

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
    <div className="mx-auto max-w-6xl px-4 py-8 sm:px-6 sm:py-10">
      <BackLink />

      <div className="mt-3 flex flex-wrap items-end justify-between gap-4">
        <div className="min-w-0">
          <h1 className="truncate font-display text-[28px] font-bold leading-tight tracking-tight">{route.name}</h1>
          <p className="mt-1 text-sm text-muted">
            {modeLabel(route.type)} · {formatDistanceKm(route.distanceKm)}
            {route.travelTimeSeconds ? ` · ${formatDuration(route.travelTimeSeconds)}` : ''} · checked {formatRelative(route.lastCheckedAt)}
          </p>
          <Link
            to="/coach"
            state={{ prefill: `When is the best time to travel my "${route.name}" route, and how has its air been this week?` }}
            className="mt-2 inline-flex items-center gap-1.5 rounded-lg text-sm font-medium text-accent-ink underline-offset-2 hover:underline focus-visible:outline-2 focus-visible:outline-accent"
          >
            <Icon name="chat" className="h-4 w-4" />
            Ask Coach about this route
          </Link>
        </div>
        <div className="w-full rounded-2xl bg-surface px-4 py-2 shadow-card ring-1 ring-hairline sm:w-80">
          <Toggle
            checked={Boolean(route.isActive)}
            disabled={togglingWatch}
            onChange={toggleWatch}
            label="Watch for poor air"
            description={route.isActive ? 'Checked every 15 minutes, with alerts' : 'Not being checked'}
          />
        </div>
      </div>

      {/* Map with the current air reading floating over it. */}
      <div className="relative mt-6 h-[340px] overflow-hidden rounded-3xl shadow-card ring-1 ring-hairline sm:h-[400px]">
        <BaseMap
          initialViewState={
            path.length > 1 ? { bounds: boundsOf(path), fitBoundsOptions: { padding: { top: 48, bottom: 48, left: 48, right: 48 }, maxZoom: 16 } } : undefined
          }
          scrollZoom={false}
        >
          {path.length > 1 && <AirRoute id="saved-route" coords={path} score={latestScore} animateKey={route.id} width={5} />}
          {path.length > 0 && <Pin kind="origin" longitude={path[0][0]} latitude={path[0][1]} />}
          {path.length > 1 && <Pin kind="destination" longitude={path[path.length - 1][0]} latitude={path[path.length - 1][1]} />}
        </BaseMap>
        <motion.div
          initial={{ opacity: 0, y: 10 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ delay: 0.3 }}
          className="absolute left-4 top-4 flex items-center gap-3.5 rounded-2xl bg-surface/95 py-3 pl-3 pr-5 shadow-pop backdrop-blur-xl"
        >
          <AirRing score={latestScore} size={60} label="Latest reading" />
          <div>
            <p className="text-xs text-muted">Air on this route now</p>
            <p className="mt-0.5 font-display text-lg font-bold leading-tight text-ink">{scoreBand(latestScore).label}</p>
            {latest && <p className="text-xs text-faint">{formatRelative(latest.timestamp)}</p>}
          </div>
        </motion.div>
      </div>

      {/* Key numbers in one row. */}
      <dl className="mt-4 grid grid-cols-2 gap-px overflow-hidden rounded-3xl bg-line shadow-card ring-1 ring-hairline lg:grid-cols-4">
        <Stat label={`Average · ${periodLabel}`} value={formatScore(periodAverage)}>
          <BandChip score={periodAverage} />
        </Stat>
        <Stat label="Readings" value={history ? scored.length : '–'}>
          <span className="text-xs text-muted">in the last {periodLabel}</span>
        </Stat>
        <Stat label="Cleanest time" value={formatHour(bestHour)} tone="good">
          <HourNote hour={bestHour} averages={history?.hourlyAverages} />
        </Stat>
        <Stat label="Worst time" value={formatHour(worstHour)} tone="warning">
          <HourNote hour={worstHour} averages={history?.hourlyAverages} />
        </Stat>
      </dl>

      <div className="mt-4 grid gap-4 lg:grid-cols-[minmax(0,3fr)_minmax(0,2fr)]">
        <Card>
          <div className="flex items-baseline justify-between gap-3">
            <h2 className="text-base font-semibold text-ink">Best time to go</h2>
            <span className="text-xs text-muted">Average by hour · {history?.timeZone ?? timeZone}</span>
          </div>
          {!history ? (
            <Skeleton className="mt-5 h-12 w-full" />
          ) : (
            <BestTimeStrip averages={history.hourlyAverages} bestHour={bestHour} />
          )}
        </Card>

        <Card>
          <div className="flex items-baseline justify-between gap-3">
            <h2 className="text-base font-semibold text-ink">Pollutants now</h2>
            {latest && <span className="text-xs text-muted">{formatDateTime(latest.timestamp)}</span>}
          </div>
          {latest ? (
            <>
              <dl className="mt-4 grid grid-cols-2 gap-2">
                {POLLUTANTS.map((p) => (
                  <PollutantReading key={p.key} label={p.label} value={latest[p.key]} who={whoComparison(p.key, latest[p.key])} />
                ))}
              </dl>
              <p className="mt-2.5 text-[11px] text-faint">Average along the route · tick = WHO guideline</p>
            </>
          ) : (
            <p className="mt-4 text-sm text-muted">No readings in this period yet.</p>
          )}
        </Card>
      </div>

      <section className="mt-10" aria-labelledby="history-heading">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <h2 id="history-heading" className="font-display text-xl font-bold tracking-tight">
            Air-quality history
          </h2>
          <Segmented id="period" label="Period" options={PERIODS} value={days} onChange={setDays} />
        </div>

        <div className="mt-4">
          {historyError ? (
            <ErrorNote>{historyError}</ErrorNote>
          ) : !history ? (
            <div className="grid gap-4 lg:grid-cols-2">
              <Skeleton className="h-[330px] !rounded-3xl" />
              <Skeleton className="h-[330px] !rounded-3xl" />
            </div>
          ) : scored.length < 2 ? (
            <EmptyState icon="clock" title="Not enough readings yet">
              {route.isActive
                ? 'Watched routes are checked every 15 minutes. Come back in a few hours to see trends and the best time to travel.'
                : 'Turn on “Watch for poor air” so ClearWay checks this route regularly and builds up its history.'}
            </EmptyState>
          ) : (
            <div className="grid gap-4 lg:grid-cols-2">
              <Card>
                <h3 className="text-sm font-semibold text-ink">Score over time</h3>
                <p className="mt-0.5 text-xs text-muted">Lower is cleaner. Alerts are sent above the dashed line.</p>
                <div className="mt-4">
                  <ScoreTrendChart points={points} days={days} />
                </div>
              </Card>
              <Card>
                <h3 className="text-sm font-semibold text-ink">Average by hour of day</h3>
                <p className="mt-0.5 text-xs text-muted">In your time zone ({history.timeZone}).</p>
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
    <Link to="/routes" className="group inline-flex items-center gap-1.5 text-sm text-muted transition-colors hover:text-ink">
      <Icon name="back" className="h-4 w-4 transition-transform group-hover:-translate-x-0.5" /> My routes
    </Link>
  );
}

function Stat({ label, value, tone, children }) {
  return (
    <div className="bg-surface p-5">
      <dt className="text-xs text-muted">{label}</dt>
      <dd
        className={`mt-1 font-display text-[26px] font-bold leading-tight tabular-nums ${
          tone === 'good' && value !== '–' ? 'text-good' : tone === 'warning' && value !== '–' ? 'text-warning' : 'text-ink'
        }`}
      >
        {value}
      </dd>
      <div className="mt-1.5 min-h-5">{children}</div>
    </div>
  );
}

function HourNote({ hour, averages }) {
  if (hour == null) return <span className="text-xs text-faint">Needs more readings</span>;
  const score = averages?.[hour] ?? averages?.[String(hour)];
  return (
    <span className="text-xs text-muted">
      Average {formatScore(score)} · {scoreBand(score).label.toLowerCase()}
    </span>
  );
}

/**
 * 24 small blocks, one per hour of the day, coloured by that hour's average air.
 * The cleanest hour is outlined; hours with no readings stay grey.
 */
function BestTimeStrip({ averages, bestHour }) {
  const hours = Array.from({ length: 24 }, (_, h) => {
    const score = averages?.[h] ?? averages?.[String(h)];
    return { h, score: score ?? null };
  });
  const now = new Date().getHours();
  const covered = hours.filter((x) => x.score != null).length;

  return (
    <div className="mt-5">
      <div className="flex h-12 items-end gap-[3px]" role="img" aria-label={`Average air by hour. ${bestHour != null ? `Cleanest at ${formatHour(bestHour)}.` : ''}`}>
        {hours.map(({ h, score }, i) => (
          <motion.span
            key={h}
            title={score == null ? `${formatHour(h)}: no readings` : `${formatHour(h)}: ${formatScore(score)} · ${scoreBand(score).label}`}
            initial={{ scaleY: 0 }}
            animate={{ scaleY: 1 }}
            transition={{ delay: i * 0.015, type: 'spring', stiffness: 400, damping: 30 }}
            style={{ backgroundColor: score == null ? undefined : scoreBand(score).color, transformOrigin: 'bottom' }}
            className={`relative h-full flex-1 rounded-[5px] ${score == null ? 'bg-surface-3' : ''} ${
              h === bestHour ? 'ring-2 ring-ink ring-offset-2 ring-offset-surface' : ''
            }`}
          >
            {h === now && <span className="absolute -bottom-2 left-1/2 h-1 w-1 -translate-x-1/2 rounded-full bg-ink" aria-hidden="true" />}
          </motion.span>
        ))}
      </div>
      <div className="mt-3 flex justify-between text-[11px] text-faint" aria-hidden="true">
        <span>12 AM</span>
        <span>6 AM</span>
        <span>12 PM</span>
        <span>6 PM</span>
        <span>12 AM</span>
      </div>
      <p className="mt-3 text-sm text-ink-2">
        {bestHour != null ? (
          <>
            Cleanest around <strong className="font-semibold text-good">{formatHour(bestHour)}</strong>. The dot marks the current hour.
          </>
        ) : covered ? (
          'Keep watching this route. The cleanest hour shows once readings cover more of the day.'
        ) : (
          'No readings yet. Watched routes are checked every 15 minutes.'
        )}
      </p>
    </div>
  );
}

/** The same hourly numbers as a table, for screen readers and exact values. */
function HourlyTable({ averages }) {
  const rows = Object.entries(averages ?? {})
    .map(([h, v]) => [Number(h), v])
    .sort((a, b) => a[0] - b[0]);
  if (!rows.length) return null;
  return (
    <details className="mt-3 text-sm">
      <summary className="cursor-pointer text-xs font-medium text-muted hover:text-ink">Show as table</summary>
      <table className="mt-2 w-full text-left text-xs">
        <thead>
          <tr className="text-faint">
            <th className="py-1 font-medium">Hour</th>
            <th className="py-1 font-medium">Average score</th>
            <th className="py-1 font-medium">Air quality</th>
          </tr>
        </thead>
        <tbody className="text-ink-2">
          {rows.map(([hour, score]) => (
            <tr key={hour} className="border-t border-line">
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
