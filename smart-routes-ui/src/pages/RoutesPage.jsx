import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { routesApi, errorMessage } from '../lib/api';
import { formatDistanceKm, formatDuration, formatRelative, formatScore, modeLabel, MODES } from '../lib/format';
import { useToast } from '../context/contexts';
import Icon from '../components/Icon';
import { AirRing, BandChip, Button, EmptyState, ErrorNote, PageLoader, Toggle } from '../components/ui';

export default function RoutesPage() {
  const [routes, setRoutes] = useState(null);
  const [error, setError] = useState('');

  useEffect(() => {
    routesApi
      .list()
      .then(setRoutes)
      .catch((err) => setError(errorMessage(err, 'Could not load your routes.')));
  }, []);

  const replace = (updated) => setRoutes((list) => list.map((r) => (r.id === updated.id ? updated : r)));
  const remove = (id) => setRoutes((list) => list.filter((r) => r.id !== id));

  const watching = routes?.filter((r) => r.isActive).length ?? 0;

  return (
    <div className="mx-auto max-w-5xl px-4 py-8 sm:px-6">
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <h1 className="font-display text-2xl font-bold tracking-tight">My routes</h1>
          {routes && routes.length > 0 && (
            <p className="mt-1 text-sm text-muted">
              {routes.length} saved · {watching} watched for poor air
            </p>
          )}
        </div>
        <Link
          to="/"
          className="inline-flex min-h-10 items-center gap-2 rounded-lg bg-accent px-4 text-sm font-semibold text-on-accent hover:bg-accent-hover"
        >
          <Icon name="map" className="h-4 w-4" /> Plan a new route
        </Link>
      </div>

      <div className="mt-6">
        {error ? (
          <ErrorNote>{error}</ErrorNote>
        ) : !routes ? (
          <PageLoader label="Loading your routes…" />
        ) : routes.length === 0 ? (
          <EmptyState
            icon="route"
            title="No saved routes yet"
            action={
              <Link to="/" className="text-sm font-semibold text-accent-ink hover:text-accent-hover">
                Plan your first route →
              </Link>
            }
          >
            Compare routes on the planner, then save the ones you travel often. ClearWay will keep checking their air for you.
          </EmptyState>
        ) : (
          <ul className="grid gap-4 md:grid-cols-2">
            {routes.map((route) => (
              <li key={route.id}>
                <RouteCard route={route} onChange={replace} onDelete={remove} />
              </li>
            ))}
          </ul>
        )}
      </div>
    </div>
  );
}

function RouteCard({ route, onChange, onDelete }) {
  const { push } = useToast();
  const [editing, setEditing] = useState(false);
  const [name, setName] = useState(route.name);
  const [confirmDelete, setConfirmDelete] = useState(false);
  const [busy, setBusy] = useState(false);
  const icon = MODES.find((m) => m.id === route.type)?.icon ?? (route.type === 'commute' ? 'car' : 'walk');

  async function patch(body, successTitle) {
    setBusy(true);
    try {
      const updated = await routesApi.update(route.id, body);
      onChange(updated);
      if (successTitle) push({ tone: 'success', title: successTitle });
      return true;
    } catch (err) {
      push({ tone: 'error', title: 'Could not update the route', body: errorMessage(err) });
      return false;
    } finally {
      setBusy(false);
    }
  }

  async function rename(e) {
    e.preventDefault();
    if (!name.trim() || name.trim() === route.name) {
      setEditing(false);
      setName(route.name);
      return;
    }
    if (await patch({ name: name.trim() })) setEditing(false);
  }

  async function destroy() {
    setBusy(true);
    try {
      await routesApi.remove(route.id);
      onDelete(route.id);
      push({ tone: 'success', title: 'Route deleted' });
    } catch (err) {
      push({ tone: 'error', title: 'Could not delete the route', body: errorMessage(err) });
      setBusy(false);
    }
  }

  return (
    <article className="flex h-full flex-col rounded-2xl border border-line bg-surface/70 p-5">
      <div className="flex items-start gap-3">
        <span className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-surface-2 text-ink-2" title={modeLabel(route.type)}>
          <Icon name={icon} className="h-5 w-5" />
        </span>
        <div className="min-w-0 flex-1">
          {editing ? (
            <form onSubmit={rename} className="flex gap-2">
              <label className="sr-only" htmlFor={`name-${route.id}`}>
                Route name
              </label>
              <input
                id={`name-${route.id}`}
                autoFocus
                maxLength={100}
                value={name}
                onChange={(e) => setName(e.target.value)}
                onKeyDown={(e) => {
                  if (e.key === 'Escape') {
                    setEditing(false);
                    setName(route.name);
                  }
                }}
                className="min-w-0 flex-1 rounded-md border border-line-strong bg-canvas px-2 py-1 text-sm text-ink focus:border-accent focus:outline-none"
              />
              <Button type="submit" className="min-h-8 px-3 py-1" loading={busy}>
                Save
              </Button>
            </form>
          ) : (
            <Link to={`/routes/${route.id}`} className="block truncate text-base font-semibold text-ink hover:text-accent-hover">
              {route.name}
            </Link>
          )}
          <p className="mt-0.5 text-[13px] text-muted">
            {modeLabel(route.type)} · {formatDistanceKm(route.distanceKm)}
            {route.travelTimeSeconds ? ` · ${formatDuration(route.travelTimeSeconds)}` : ''}
          </p>
        </div>
      </div>

      <div className="mt-4 grid grid-cols-2 gap-3">
        <div className="rounded-xl bg-canvas/50 p-3">
          <div className="flex items-center justify-between gap-2">
            <div>
              <p className="text-[11px] font-semibold uppercase tracking-wide text-faint">Latest</p>
              <div className="mt-2">
                <BandChip score={route.lastPollutionScore} />
              </div>
            </div>
            <AirRing score={route.lastPollutionScore} size={52} label="Latest score" />
          </div>
        </div>
        <div className="rounded-xl bg-canvas/50 p-3">
          <p className="text-[11px] font-semibold uppercase tracking-wide text-faint">Average</p>
          <p className="mt-1 text-2xl font-data font-bold tabular-nums">{formatScore(route.avgPollutionScore)}</p>
          <p className="mt-2 text-[11px] text-faint">Checked {formatRelative(route.lastCheckedAt)}</p>
        </div>
      </div>

      <div className="mt-4 rounded-xl border border-line px-3 py-2">
        <Toggle
          checked={Boolean(route.isActive)}
          disabled={busy}
          onChange={(on) => patch({ isActive: on }, on ? 'Watching this route' : 'Stopped watching this route')}
          label="Watch for poor air"
          description={route.isActive ? 'Checked every 15 minutes' : 'Not being checked'}
        />
      </div>

      <div className="mt-auto flex items-center justify-between gap-2 pt-4">
        <Link
          to={`/routes/${route.id}`}
          className="inline-flex items-center gap-1 text-sm font-semibold text-accent-ink hover:text-accent-hover"
        >
          History & best times <Icon name="chevron" className="h-4 w-4" />
        </Link>
        {confirmDelete ? (
          <div className="flex items-center gap-1">
            <span className="mr-1 text-xs text-muted">Delete?</span>
            <Button variant="danger" className="min-h-8 px-3 py-1" loading={busy} onClick={destroy}>
              Delete
            </Button>
            <Button variant="ghost" className="min-h-8 px-2 py-1" onClick={() => setConfirmDelete(false)}>
              Keep
            </Button>
          </div>
        ) : (
          <div className="flex items-center">
            <button
              type="button"
              onClick={() => setEditing(true)}
              aria-label={`Rename ${route.name}`}
              title="Rename"
              className="flex h-9 w-9 items-center justify-center rounded-lg text-faint hover:bg-surface-2 hover:text-ink"
            >
              <Icon name="edit" className="h-4 w-4" />
            </button>
            <button
              type="button"
              onClick={() => setConfirmDelete(true)}
              aria-label={`Delete ${route.name}`}
              title="Delete"
              className="flex h-9 w-9 items-center justify-center rounded-lg text-faint hover:bg-danger/10 hover:text-danger"
            >
              <Icon name="trash" className="h-4 w-4" />
            </button>
          </div>
        )}
      </div>
    </article>
  );
}
