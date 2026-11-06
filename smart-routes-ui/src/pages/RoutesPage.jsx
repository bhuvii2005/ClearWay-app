import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { AnimatePresence, motion } from 'motion/react';
import { routesApi, errorMessage } from '../lib/api';
import { formatDistanceKm, formatDuration, formatRelative, formatScore, modeLabel, MODES, scoreBand } from '../lib/format';
import { useToast } from '../context/contexts';
import Icon from '../components/Icon';
import { AirRing, BandChip, Button, EmptyState, ErrorNote, PageHeader, Skeleton, Toggle } from '../components/ui';
import { buttonClass } from '../components/buttonStyles';

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
    <div className="mx-auto max-w-5xl px-4 py-8 sm:px-6 sm:py-10">
      <PageHeader
        title="My routes"
        subtitle={routes?.length ? `${routes.length} saved · ${watching} watched for poor air` : 'Routes you save are checked for you.'}
        action={
          <Link to="/" className={buttonClass('primary')}>
            <Icon name="map" className="h-4 w-4" /> Plan a new route
          </Link>
        }
      />

      <div className="mt-8">
        {error ? (
          <ErrorNote>{error}</ErrorNote>
        ) : !routes ? (
          <div className="grid gap-4 md:grid-cols-2" aria-label="Loading your routes">
            {[0, 1].map((i) => (
              <div key={i} className="rounded-3xl bg-surface p-5 shadow-card">
                <div className="flex gap-3">
                  <Skeleton className="h-10 w-10 !rounded-xl" />
                  <div className="flex-1 space-y-2">
                    <Skeleton className="h-4 w-2/3" />
                    <Skeleton className="h-3 w-1/3" />
                  </div>
                  <Skeleton className="h-14 w-14 !rounded-full" />
                </div>
                <Skeleton className="mt-6 h-12 w-full !rounded-2xl" />
              </div>
            ))}
          </div>
        ) : routes.length === 0 ? (
          <EmptyState
            icon="route"
            title="No saved routes yet"
            action={
              <Link to="/" className={buttonClass('primary')}>
                Plan your first route
              </Link>
            }
          >
            Compare routes on the planner, then save the ones you travel often. ClearWay keeps checking their air for you.
          </EmptyState>
        ) : (
          <motion.ul
            className="grid gap-4 md:grid-cols-2"
            initial="hidden"
            animate="show"
            variants={{ show: { transition: { staggerChildren: 0.06 } } }}
          >
            <AnimatePresence>
              {routes.map((route) => (
                <motion.li
                  key={route.id}
                  layout
                  variants={{ hidden: { opacity: 0, y: 12 }, show: { opacity: 1, y: 0 } }}
                  exit={{ opacity: 0, scale: 0.96, transition: { duration: 0.2 } }}
                >
                  <RouteCard route={route} onChange={replace} onDelete={remove} />
                </motion.li>
              ))}
            </AnimatePresence>
          </motion.ul>
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
    <article className="group flex h-full flex-col rounded-3xl bg-surface p-5 shadow-card ring-1 ring-hairline transition-[box-shadow,transform] duration-200 hover:-translate-y-0.5 hover:shadow-float">
      <div className="flex items-start gap-3.5">
        <span className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-accent/10 text-accent-ink" title={modeLabel(route.type)}>
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
                className="min-w-0 flex-1 rounded-lg border border-line-strong bg-surface px-2.5 py-1 text-sm text-ink focus:border-accent focus:outline-none focus:ring-4 focus:ring-accent/15"
              />
              <Button type="submit" className="min-h-8 px-3 py-1" loading={busy}>
                Save
              </Button>
            </form>
          ) : (
            <Link to={`/routes/${route.id}`} className="block truncate text-base font-semibold text-ink transition-colors hover:text-accent-ink">
              {route.name}
            </Link>
          )}
          <p className="mt-0.5 text-[13px] text-muted">
            {modeLabel(route.type)} · {formatDistanceKm(route.distanceKm)}
            {route.travelTimeSeconds ? ` · ${formatDuration(route.travelTimeSeconds)}` : ''}
          </p>
        </div>
        <AirRing score={route.lastPollutionScore} size={56} label="Latest score" />
      </div>

      <dl className="mt-5 grid grid-cols-3 gap-px overflow-hidden rounded-2xl bg-line text-sm">
        <div className="bg-surface-2/60 px-3 py-2.5">
          <dt className="text-xs text-muted">Now</dt>
          <dd className="mt-1">
            <BandChip score={route.lastPollutionScore} />
          </dd>
        </div>
        <div className="bg-surface-2/60 px-3 py-2.5">
          <dt className="text-xs text-muted">Average</dt>
          <dd className="mt-0.5 font-data font-semibold tabular-nums text-ink">
            {formatScore(route.avgPollutionScore)}
            <span className="ml-1 text-xs font-normal text-muted">
              {route.avgPollutionScore == null ? '' : scoreBand(route.avgPollutionScore).label.toLowerCase()}
            </span>
          </dd>
        </div>
        <div className="bg-surface-2/60 px-3 py-2.5">
          <dt className="text-xs text-muted">Checked</dt>
          <dd className="mt-0.5 font-medium text-ink">{formatRelative(route.lastCheckedAt)}</dd>
        </div>
      </dl>

      <div className="mt-4 px-0.5">
        <Toggle
          checked={Boolean(route.isActive)}
          disabled={busy}
          onChange={(on) => patch({ isActive: on }, on ? 'Watching this route' : 'Stopped watching this route')}
          label="Watch for poor air"
          description={route.isActive ? 'Checked every 15 minutes' : 'Not being checked'}
        />
      </div>

      <div className="mt-auto flex items-center justify-between gap-2 border-t border-line pt-3.5">
        <Link
          to={`/routes/${route.id}`}
          className="inline-flex items-center gap-1 text-sm font-semibold text-accent-ink transition-colors hover:text-accent-hover"
        >
          History & best times <Icon name="chevron" className="h-4 w-4 transition-transform group-hover:translate-x-0.5" />
        </Link>
        <AnimatePresence mode="wait" initial={false}>
          {confirmDelete ? (
            <motion.div
              key="confirm"
              initial={{ opacity: 0, x: 8 }}
              animate={{ opacity: 1, x: 0 }}
              exit={{ opacity: 0, x: 8 }}
              className="flex items-center gap-1"
            >
              <span className="mr-1 text-xs text-muted">Delete this route?</span>
              <Button variant="danger" className="min-h-8 px-3 py-1" loading={busy} onClick={destroy}>
                Delete
              </Button>
              <Button variant="ghost" className="min-h-8 px-2 py-1" onClick={() => setConfirmDelete(false)}>
                Keep
              </Button>
            </motion.div>
          ) : (
            <motion.div key="actions" initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }} className="flex items-center">
              <IconButton icon="edit" label={`Rename ${route.name}`} title="Rename" onClick={() => setEditing(true)} />
              <IconButton icon="trash" label={`Delete ${route.name}`} title="Delete" danger onClick={() => setConfirmDelete(true)} />
            </motion.div>
          )}
        </AnimatePresence>
      </div>
    </article>
  );
}

function IconButton({ icon, label, title, onClick, danger }) {
  return (
    <button
      type="button"
      onClick={onClick}
      aria-label={label}
      title={title}
      className={`flex h-9 w-9 items-center justify-center rounded-xl text-faint transition-colors ${
        danger ? 'hover:bg-danger/10 hover:text-danger' : 'hover:bg-surface-2 hover:text-ink'
      }`}
    >
      <Icon name={icon} className="h-4 w-4" />
    </button>
  );
}
