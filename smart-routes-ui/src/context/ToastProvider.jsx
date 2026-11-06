import { useCallback, useMemo, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import { AnimatePresence, motion } from 'motion/react';
import { ToastContext } from './contexts';
import Icon from '../components/Icon';

const TONES = {
  success: { icon: 'check', chip: 'bg-accent/10', iconColor: 'text-accent-ink' },
  error: { icon: 'alert', chip: 'bg-danger/10', iconColor: 'text-danger' },
  info: { icon: 'info', chip: 'bg-accent/10', iconColor: 'text-accent-ink' },
  poor: { icon: 'wind', chip: 'bg-warning/10', iconColor: 'text-warning' },
  clean: { icon: 'leaf', chip: 'bg-accent/10', iconColor: 'text-accent-ink' },
};

/** Small messages in the corner: confirmations, errors and live air alerts. */
export default function ToastProvider({ children }) {
  const [toasts, setToasts] = useState([]);
  const nextId = useRef(1);

  const dismiss = useCallback((id) => {
    setToasts((list) => list.filter((t) => t.id !== id));
  }, []);

  const push = useCallback(
    ({ title, body, tone = 'info', link, duration = 5000 }) => {
      const id = nextId.current++;
      setToasts((list) => [...list.slice(-3), { id, title, body, tone, link }]);
      if (duration > 0) setTimeout(() => dismiss(id), duration);
      return id;
    },
    [dismiss],
  );

  const value = useMemo(() => ({ push, dismiss }), [push, dismiss]);

  return (
    <ToastContext.Provider value={value}>
      {children}
      <div
        aria-live="polite"
        className="pointer-events-none fixed inset-x-0 bottom-0 z-[2000] flex flex-col items-center gap-2 p-4 sm:inset-x-auto sm:right-0 sm:items-end"
      >
        <AnimatePresence initial={false}>
        {toasts.map((toast) => {
          const tone = TONES[toast.tone] ?? TONES.info;
          return (
            <motion.div
              key={toast.id}
              role="status"
              layout
              initial={{ opacity: 0, y: 24, scale: 0.96 }}
              animate={{ opacity: 1, y: 0, scale: 1 }}
              exit={{ opacity: 0, x: 40, transition: { duration: 0.18 } }}
              className="pointer-events-auto flex w-full max-w-sm gap-3 rounded-2xl bg-surface/95 p-3.5 shadow-pop ring-1 ring-hairline backdrop-blur-xl"
            >
              <span className={`grid h-8 w-8 shrink-0 place-items-center rounded-full ${tone.chip}`}>
                <Icon name={tone.icon} className={`h-4 w-4 ${tone.iconColor}`} />
              </span>
              <div className="min-w-0 flex-1">
                <p className="text-sm font-semibold text-ink">{toast.title}</p>
                {toast.body && <p className="mt-0.5 text-[13px] leading-snug text-muted">{toast.body}</p>}
                {toast.link && (
                  <Link
                    to={toast.link.to}
                    onClick={() => dismiss(toast.id)}
                    className="mt-1.5 inline-block text-[13px] font-semibold text-accent-ink hover:text-accent-hover"
                  >
                    {toast.link.label}
                  </Link>
                )}
              </div>
              <button
                type="button"
                onClick={() => dismiss(toast.id)}
                aria-label="Dismiss"
                className="-m-1 h-7 w-7 shrink-0 rounded-lg p-1.5 text-faint transition-colors hover:bg-surface-2 hover:text-ink-2"
              >
                <Icon name="x" className="h-4 w-4" />
              </button>
            </motion.div>
          );
        })}
        </AnimatePresence>
      </div>
    </ToastContext.Provider>
  );
}
