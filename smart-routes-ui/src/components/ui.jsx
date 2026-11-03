import { useId } from 'react';
import Icon from './Icon';
import { formatScore, scoreBand } from '../lib/format';

/** Air-quality score with its band name; colour is never the only signal. */
export function ScoreBadge({ score, size = 'md' }) {
  const band = scoreBand(score);
  const big = size === 'lg';
  return (
    <div className="flex items-center gap-2.5">
      <span
        className={`font-black tabular-nums text-slate-100 ${big ? 'text-3xl' : 'text-xl'}`}
        aria-label={`Score ${formatScore(score)}`}
      >
        {formatScore(score)}
      </span>
      <BandChip score={score} />
      <span className="sr-only">{band.label}</span>
    </div>
  );
}

export function BandChip({ score }) {
  const band = scoreBand(score);
  return (
    <span className="inline-flex items-center gap-1.5 rounded-full border border-slate-700 bg-slate-900/60 px-2 py-0.5 text-[11px] font-semibold text-slate-300">
      <span className="h-2 w-2 rounded-full" style={{ backgroundColor: band.color }} aria-hidden="true" />
      {band.label}
    </span>
  );
}

const BUTTON_VARIANTS = {
  primary:
    'bg-emerald-500 text-slate-950 hover:bg-emerald-400 disabled:bg-slate-800 disabled:text-slate-500',
  secondary:
    'border border-slate-700 bg-slate-800/60 text-slate-200 hover:bg-slate-700/70 hover:border-slate-600 disabled:opacity-50',
  dangerGhost: 'text-rose-300 hover:bg-rose-950/50 hover:text-rose-200 disabled:opacity-50',
  ghost: 'text-slate-400 hover:bg-slate-800 hover:text-slate-200 disabled:opacity-50',
  danger: 'bg-rose-600 text-white hover:bg-rose-500 disabled:opacity-50',
};

export function Button({ variant = 'primary', loading, children, className = '', icon, ...props }) {
  return (
    <button
      type="button"
      {...props}
      disabled={props.disabled || loading}
      className={`inline-flex min-h-10 items-center justify-center gap-2 rounded-lg px-4 py-2 text-sm font-semibold transition-colors focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-emerald-400 disabled:cursor-not-allowed ${BUTTON_VARIANTS[variant]} ${className}`}
    >
      {loading ? <Spinner className="h-4 w-4" /> : icon ? <Icon name={icon} className="h-4 w-4" /> : null}
      {children}
    </button>
  );
}

export function Spinner({ className = 'h-5 w-5' }) {
  return (
    <svg className={`animate-spin ${className}`} viewBox="0 0 24 24" fill="none" aria-hidden="true">
      <circle cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" className="opacity-25" />
      <path d="M4 12a8 8 0 0 1 8-8" stroke="currentColor" strokeWidth="4" strokeLinecap="round" />
    </svg>
  );
}

export function TextField({ label, hint, error, className = '', ...props }) {
  const id = useId();
  return (
    <div className={className}>
      <label htmlFor={id} className="mb-1.5 block text-[13px] font-medium text-slate-300">
        {label}
      </label>
      <input
        id={id}
        {...props}
        aria-invalid={error ? true : undefined}
        className="w-full rounded-lg border border-slate-700 bg-slate-950/60 px-3 py-2.5 text-sm text-slate-100 placeholder:text-slate-500 focus:border-emerald-500 focus:outline-none focus:ring-2 focus:ring-emerald-500/20 disabled:opacity-60"
      />
      {hint && !error && <p className="mt-1 text-xs text-slate-500">{hint}</p>}
      {error && <p className="mt-1 text-xs text-rose-400">{error}</p>}
    </div>
  );
}

export function Toggle({ checked, onChange, label, description, disabled }) {
  const id = useId();
  return (
    <label htmlFor={id} className="flex cursor-pointer items-start justify-between gap-4 py-1">
      <span>
        <span className="block text-sm font-medium text-slate-200">{label}</span>
        {description && <span className="mt-0.5 block text-xs text-slate-500">{description}</span>}
      </span>
      <span className="relative mt-0.5 inline-flex shrink-0">
        <input
          id={id}
          type="checkbox"
          role="switch"
          className="peer sr-only"
          checked={checked}
          disabled={disabled}
          onChange={(e) => onChange(e.target.checked)}
        />
        <span className="h-6 w-11 rounded-full bg-slate-700 transition-colors peer-checked:bg-emerald-500 peer-focus-visible:outline-2 peer-focus-visible:outline-offset-2 peer-focus-visible:outline-emerald-400 peer-disabled:opacity-50" />
        <span className="absolute left-0.5 top-0.5 h-5 w-5 rounded-full bg-white shadow transition-transform peer-checked:translate-x-5" />
      </span>
    </label>
  );
}

export function Card({ children, className = '' }) {
  return <section className={`rounded-2xl border border-slate-800 bg-slate-900/70 p-5 ${className}`}>{children}</section>;
}

export function ErrorNote({ children }) {
  if (!children) return null;
  return (
    <p role="alert" className="flex items-start gap-2 rounded-lg border border-rose-900/60 bg-rose-950/40 px-3 py-2 text-[13px] text-rose-300">
      <Icon name="alert" className="mt-px h-4 w-4 shrink-0" />
      <span>{children}</span>
    </p>
  );
}

export function EmptyState({ icon, title, children, action }) {
  return (
    <div className="flex flex-col items-center rounded-2xl border border-dashed border-slate-800 px-6 py-14 text-center">
      <span className="mb-4 flex h-12 w-12 items-center justify-center rounded-full bg-slate-800/80 text-slate-400">
        <Icon name={icon} className="h-6 w-6" />
      </span>
      <h2 className="text-base font-semibold text-slate-200">{title}</h2>
      {children && <p className="mt-1.5 max-w-sm text-sm text-slate-400">{children}</p>}
      {action && <div className="mt-5">{action}</div>}
    </div>
  );
}

export function PageLoader({ label = 'Loading…' }) {
  return (
    <div className="flex h-full min-h-60 items-center justify-center gap-3 text-sm text-slate-400">
      <Spinner />
      {label}
    </div>
  );
}
