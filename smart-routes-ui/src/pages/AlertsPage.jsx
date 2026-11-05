import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { alertsApi, errorMessage } from '../lib/api';
import { formatDateTime, formatRelative, formatScore } from '../lib/format';
import { useAlerts, useToast } from '../context/contexts';
import Icon from '../components/Icon';
import { BandChip, Button, EmptyState, ErrorNote, PageLoader } from '../components/ui';

export default function AlertsPage() {
  const { lastAlert, setUnread, connected } = useAlerts();
  const { push } = useToast();
  const [alerts, setAlerts] = useState(null);
  const [error, setError] = useState('');
  const [markingAll, setMarkingAll] = useState(false);

  const load = useCallback(() => {
    alertsApi
      .list(50)
      .then((list) => {
        setAlerts(list);
        setUnread(list.filter((a) => !a.read).length);
      })
      .catch((err) => setError(errorMessage(err, 'Could not load your alerts.')));
  }, [setUnread]);

  // Reload on open and whenever a live alert arrives.
  useEffect(() => {
    load();
  }, [load, lastAlert]);

  async function markRead(alert) {
    if (alert.read) return;
    setAlerts((list) => list.map((a) => (a.id === alert.id ? { ...a, read: true } : a)));
    setUnread((n) => Math.max(0, n - 1));
    try {
      await alertsApi.markRead(alert.id);
    } catch {
      load();
    }
  }

  async function markAll() {
    setMarkingAll(true);
    try {
      await alertsApi.markAllRead();
      setAlerts((list) => list.map((a) => ({ ...a, read: true })));
      setUnread(0);
    } catch (err) {
      push({ tone: 'error', title: 'Could not mark alerts as read', body: errorMessage(err) });
    } finally {
      setMarkingAll(false);
    }
  }

  const unreadCount = alerts?.filter((a) => !a.read).length ?? 0;

  return (
    <div className="mx-auto max-w-3xl px-4 py-8 sm:px-6">
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <h1 className="font-display text-2xl font-bold tracking-tight">Alerts</h1>
          <p className="mt-1 flex items-center gap-2 text-sm text-muted">
            <span className={`h-2 w-2 rounded-full ${connected ? 'bg-accent' : 'bg-line-strong'}`} aria-hidden="true" />
            {connected ? 'Live: new alerts appear instantly' : 'Reconnecting to live alerts…'}
          </p>
        </div>
        {unreadCount > 0 && (
          <Button variant="secondary" icon="check" loading={markingAll} onClick={markAll}>
            Mark all as read
          </Button>
        )}
      </div>

      <div className="mt-6">
        {error ? (
          <ErrorNote>{error}</ErrorNote>
        ) : !alerts ? (
          <PageLoader label="Loading alerts…" />
        ) : alerts.length === 0 ? (
          <EmptyState icon="bell" title="No alerts yet">
            When the air on a watched route turns poor, or clears up noticeably, you will hear about it here. Each route alerts at most once every
            two hours.
          </EmptyState>
        ) : (
          <ul className="space-y-2">
            {alerts.map((alert) => (
              <li key={alert.id}>
                <AlertRow alert={alert} onOpen={() => markRead(alert)} />
              </li>
            ))}
          </ul>
        )}
      </div>
    </div>
  );
}

function AlertRow({ alert, onOpen }) {
  const poor = alert.kind === 'POOR_AIR';
  return (
    <article
      className={`flex gap-3.5 rounded-xl border p-4 transition-colors ${
        alert.read ? 'border-line bg-surface/40' : 'border-line-strong bg-surface'
      }`}
    >
      <span
        className={`flex h-10 w-10 shrink-0 items-center justify-center rounded-full ${
          poor ? 'bg-warning/15 text-warning' : 'bg-accent/15 text-accent-ink'
        }`}
      >
        <Icon name={poor ? 'wind' : 'leaf'} className="h-5 w-5" />
      </span>
      <div className="min-w-0 flex-1">
        <div className="flex flex-wrap items-center gap-x-2 gap-y-1">
          <h2 className="text-sm font-semibold text-ink">{poor ? 'Poor air' : 'Cleaner air'}</h2>
          {alert.routeName && <span className="truncate text-sm text-muted">on {alert.routeName}</span>}
          {!alert.read && (
            <span className="rounded-full bg-danger-solid/15 px-2 py-0.5 text-[10px] font-bold uppercase tracking-wide text-danger">New</span>
          )}
        </div>
        <p className="mt-1 text-sm leading-snug text-ink-2">{alert.message}</p>
        <div className="mt-2.5 flex flex-wrap items-center gap-3">
          {alert.pollutionScore != null && (
            <span className="flex items-center gap-2 text-xs text-muted">
              Score <span className="font-semibold tabular-nums text-ink">{formatScore(alert.pollutionScore)}</span>
              <BandChip score={alert.pollutionScore} />
            </span>
          )}
          <time dateTime={alert.createdAt} title={formatDateTime(alert.createdAt)} className="text-xs text-faint">
            {formatRelative(alert.createdAt)}
          </time>
        </div>
      </div>
      <div className="flex shrink-0 flex-col items-end justify-between gap-2">
        {alert.routeId && (
          <Link
            to={`/routes/${alert.routeId}`}
            onClick={onOpen}
            className="inline-flex items-center gap-1 text-sm font-semibold text-accent-ink hover:text-accent-hover"
          >
            View <Icon name="chevron" className="h-4 w-4" />
          </Link>
        )}
        {!alert.read && (
          <button type="button" onClick={onOpen} className="text-xs text-faint hover:text-ink-2">
            Mark read
          </button>
        )}
      </div>
    </article>
  );
}
