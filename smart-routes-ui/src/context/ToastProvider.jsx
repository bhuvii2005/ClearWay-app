import { useCallback, useMemo, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import { ToastContext } from './contexts';
import Icon from '../components/Icon';

const TONES = {
  success: { icon: 'check', ring: 'border-emerald-500/40', iconColor: 'text-emerald-400' },
  error: { icon: 'alert', ring: 'border-rose-500/40', iconColor: 'text-rose-400' },
  info: { icon: 'info', ring: 'border-sky-500/40', iconColor: 'text-sky-400' },
  poor: { icon: 'wind', ring: 'border-orange-500/50', iconColor: 'text-orange-400' },
  clean: { icon: 'leaf', ring: 'border-emerald-500/50', iconColor: 'text-emerald-400' },
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
        {toasts.map((toast) => {
          const tone = TONES[toast.tone] ?? TONES.info;
          return (
            <div
              key={toast.id}
              role="status"
              className={`toast-in pointer-events-auto flex w-full max-w-sm gap-3 rounded-xl border bg-slate-900/95 p-3.5 shadow-2xl backdrop-blur ${tone.ring}`}
            >
              <Icon name={tone.icon} className={`mt-0.5 h-5 w-5 shrink-0 ${tone.iconColor}`} />
              <div className="min-w-0 flex-1">
                <p className="text-sm font-semibold text-slate-100">{toast.title}</p>
                {toast.body && <p className="mt-0.5 text-[13px] leading-snug text-slate-400">{toast.body}</p>}
                {toast.link && (
                  <Link
                    to={toast.link.to}
                    onClick={() => dismiss(toast.id)}
                    className="mt-1.5 inline-block text-[13px] font-semibold text-emerald-400 hover:text-emerald-300"
                  >
                    {toast.link.label}
                  </Link>
                )}
              </div>
              <button
                type="button"
                onClick={() => dismiss(toast.id)}
                aria-label="Dismiss"
                className="-m-1 h-7 w-7 shrink-0 rounded-md p-1 text-slate-500 hover:bg-slate-800 hover:text-slate-300"
              >
                <Icon name="x" className="h-5 w-5" />
              </button>
            </div>
          );
        })}
      </div>
    </ToastContext.Provider>
  );
}
