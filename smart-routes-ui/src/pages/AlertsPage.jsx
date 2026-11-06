import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { AnimatePresence, motion } from 'motion/react';
import { alertsApi, errorMessage } from '../lib/api';
import { formatDateTime, formatRelative, formatScore, scoreBand } from '../lib/format';
import { useAlerts, useToast } from '../context/contexts';
import Icon from '../components/Icon';
import { Button, EmptyState, ErrorNote, PageHeader, Skeleton } from '../components/ui';

/** "Today", "Yesterday", or a date like "Mon, 3 Nov". */
function dayLabel(value) {
  const d = new Date(value);
  const today = new Date();
  const startOf = (x) => new Date(x.getFullYear(), x.getMonth(), x.getDate()).getTime();
  const diff = Math.round((startOf(today) - startOf(d)) / 86400000);
  if (diff === 0) return 'Today';
  if (diff === 1) return 'Yesterday';
  return d.toLocaleDateString(undefined, { weekday: 'short', day: 'numeric', month: 'short' });
}

function groupByDay(alerts) {
  const groups = [];
  for (const alert of alerts) {
    const label = dayLabel(alert.createdAt);
    const last = groups[groups.length - 1];
    if (last?.label === label) last.items.push(alert);
    else groups.push({ label, items: [alert] });
  }
  return groups;
}

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
    <div className="mx-auto max-w-3xl px-4 py-8 sm:px-6 sm:py-10">
      <PageHeader
        title="Alerts"
        subtitle={
          <span className="inline-flex items-center gap-2">
            <span className="relative flex h-2 w-2" aria-hidden="true">
              {connected && <span className="absolute inset-0 animate-ping rounded-full bg-good/60 motion-reduce:animate-none" />}
              <span className={`relative h-2 w-2 rounded-full ${connected ? 'bg-good' : 'bg-line-strong'}`} />
            </span>
            {connected ? 'Live. New alerts appear instantly.' : 'Reconnecting to live alerts…'}
          </span>
        }
        action={
          <AnimatePresence>
            {unreadCount > 0 && (
              <motion.div initial={{ opacity: 0, scale: 0.95 }} animate={{ opacity: 1, scale: 1 }} exit={{ opacity: 0, scale: 0.95 }}>
                <Button variant="secondary" icon="check" loading={markingAll} onClick={markAll}>
                  Mark all as read
                </Button>
              </motion.div>
            )}
          </AnimatePresence>
        }
      />

      <div className="mt-8">
        {error ? (
          <ErrorNote>{error}</ErrorNote>
        ) : !alerts ? (
          <div className="space-y-2" aria-label="Loading alerts">
            {[0, 1, 2].map((i) => (
              <div key={i} className="flex gap-3.5 rounded-2xl bg-surface p-4 shadow-card">
                <Skeleton className="h-10 w-10 !rounded-full" />
                <div className="flex-1 space-y-2">
                  <Skeleton className="h-4 w-1/2" />
                  <Skeleton className="h-3 w-5/6" />
                </div>
              </div>
            ))}
          </div>
        ) : alerts.length === 0 ? (
          <EmptyState icon="bell" title="You're all caught up">
            When the air on a watched route turns poor, or clears up noticeably, you will hear about it here. Each route alerts at most once every
            two hours.
          </EmptyState>
        ) : (
          <div className="space-y-8">
            {groupByDay(alerts).map((group) => (
              <section key={group.label} aria-label={group.label}>
                <h2 className="mb-2.5 px-1 text-sm font-semibold text-muted">{group.label}</h2>
                <motion.ul
                  className="overflow-hidden rounded-3xl bg-surface shadow-card ring-1 ring-hairline"
                  initial="hidden"
                  animate="show"
                  variants={{ show: { transition: { staggerChildren: 0.04 } } }}
                >
                  {group.items.map((alert, i) => (
                    <motion.li
                      key={alert.id}
                      variants={{ hidden: { opacity: 0, y: 8 }, show: { opacity: 1, y: 0 } }}
                      className={i > 0 ? 'border-t border-line' : ''}
                    >
                      <AlertRow alert={alert} onOpen={() => markRead(alert)} />
                    </motion.li>
                  ))}
                </motion.ul>
              </section>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}

function AlertRow({ alert, onOpen }) {
  const poor = alert.kind === 'POOR_AIR';
  const band = scoreBand(alert.pollutionScore);
  return (
    <article className={`group relative flex gap-3.5 px-4 py-4 transition-colors sm:px-5 ${alert.read ? '' : 'bg-accent/[0.04]'}`}>
      <span className="relative shrink-0">
        <span
          className={`flex h-10 w-10 items-center justify-center rounded-full ${
            poor ? 'bg-warning/12 text-warning' : 'bg-good/12 text-good'
          }`}
        >
          <Icon name={poor ? 'wind' : 'leaf'} className="h-5 w-5" />
        </span>
        <AnimatePresence>
          {!alert.read && (
            <motion.span
              initial={{ scale: 0 }}
              animate={{ scale: 1 }}
              exit={{ scale: 0 }}
              className="absolute -right-0.5 -top-0.5 h-3 w-3 rounded-full bg-accent ring-2 ring-surface"
            >
              <span className="sr-only">Unread</span>
            </motion.span>
          )}
        </AnimatePresence>
      </span>

      <div className="min-w-0 flex-1">
        <div className="flex items-baseline justify-between gap-3">
          <h3 className={`truncate text-sm text-ink ${alert.read ? 'font-medium' : 'font-semibold'}`}>
            {poor ? 'Poor air' : 'Cleaner air'}
            {alert.routeName && <span className="font-normal text-muted"> on {alert.routeName}</span>}
          </h3>
          <time dateTime={alert.createdAt} title={formatDateTime(alert.createdAt)} className="shrink-0 text-xs text-faint">
            {formatRelative(alert.createdAt)}
          </time>
        </div>
        <p className="mt-1 text-sm leading-snug text-ink-2">{alert.message}</p>
        <div className="mt-2.5 flex flex-wrap items-center gap-x-4 gap-y-2">
          {alert.pollutionScore != null && (
            <span className="inline-flex items-center gap-1.5 text-xs text-muted">
              <span className="h-2 w-2 rounded-full" style={{ backgroundColor: band.color }} aria-hidden="true" />
              Score <span className="font-data font-semibold tabular-nums text-ink">{formatScore(alert.pollutionScore)}</span> · {band.label}
            </span>
          )}
          {alert.routeId && (
            <Link
              to={`/routes/${alert.routeId}`}
              onClick={onOpen}
              className="inline-flex items-center gap-0.5 text-xs font-semibold text-accent-ink hover:text-accent-hover"
            >
              View route <Icon name="chevron" className="h-3.5 w-3.5" />
            </Link>
          )}
          {!alert.read && (
            <button type="button" onClick={onOpen} className="text-xs text-muted transition-colors hover:text-ink">
              Mark read
            </button>
          )}
        </div>
      </div>
    </article>
  );
}
