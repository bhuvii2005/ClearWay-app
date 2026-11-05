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
        className={`font-data font-bold tabular-nums text-ink ${big ? 'text-3xl' : 'text-xl'}`}
        aria-label={`Score ${formatScore(score)}`}
      >
        {formatScore(score)}
      </span>
      <BandChip score={score} />
      <span className="sr-only">{band.label}</span>
    </div>
  );
}

/**
 * Air score as a ring: the arc fills toward 100 (the top of the European AQI's
 * "very poor" band) in the band's colour, with the number in the middle.
 */
export function AirRing({ score, size = 56, label }) {
  const band = scoreBand(score);
  const stroke = size >= 80 ? 7 : 5;
  const r = (size - stroke) / 2;
  const circumference = 2 * Math.PI * r;
  const fill = score == null ? 0 : Math.min(Math.max(score, 0), 100) / 100;
  return (
    <div
      className="relative shrink-0"
      style={{ width: size, height: size }}
      role="img"
      aria-label={`${label ?? 'Air score'} ${formatScore(score)}, ${band.label.toLowerCase()}`}
    >
      <svg width={size} height={size} viewBox={`0 0 ${size} ${size}`} className="-rotate-90" aria-hidden="true">
        <circle cx={size / 2} cy={size / 2} r={r} fill="none" strokeWidth={stroke} className="stroke-surface-3" />
        <circle
          cx={size / 2}
          cy={size / 2}
          r={r}
          fill="none"
          stroke={band.color}
          strokeWidth={stroke}
          strokeLinecap="round"
          strokeDasharray={circumference}
          strokeDashoffset={circumference * (1 - fill)}
          className="transition-[stroke-dashoffset] duration-700 ease-out motion-reduce:transition-none"
        />
      </svg>
      <span
        className={`absolute inset-0 grid place-items-center font-data font-bold tabular-nums leading-none text-ink ${
          size >= 80 ? 'text-2xl' : 'text-base'
        }`}
        aria-hidden="true"
      >
        {formatScore(score)}
      </span>
    </div>
  );
}

/**
 * One pollutant reading with a bar against its WHO guideline (the tick mark).
 * The bar shows up to 4× the guideline; the text always gives the exact multiple.
 */
export function PollutantReading({ label, value, who, large = false }) {
  const scale = 4;
  const width = who ? Math.min(who.ratio / scale, 1) * 100 : 0;
  return (
    <div className={`rounded-xl bg-canvas/60 ${large ? 'p-3' : 'px-2.5 py-2'}`}>
      <dt className="flex items-baseline justify-between gap-2">
        <span className="text-[11px] font-semibold uppercase tracking-wide text-faint">{label}</span>
        {who && (
          <span className={`text-[11px] font-semibold ${who.over ? 'text-warning' : 'text-good'}`}>{who.short}</span>
        )}
      </dt>
      <dd className="mt-0.5">
        <span className={`font-data font-bold tabular-nums text-ink ${large ? 'text-xl' : 'text-base'}`}>
          {value == null ? '–' : Math.round(value)}
        </span>
        <span className="ml-1 text-[11px] text-faint">µg/m³</span>
        {who && (
          <span className="relative mt-1.5 block h-1.5 rounded-full bg-surface-3" aria-hidden="true">
            <span
              className={`absolute inset-y-0 left-0 rounded-full ${who.over ? 'bg-warning' : 'bg-good'}`}
              style={{ width: `${Math.max(width, 3)}%` }}
            />
            <span className="absolute -inset-y-0.5 w-0.5 rounded-full bg-ink" style={{ left: `${100 / scale}%` }} />
          </span>
        )}
      </dd>
    </div>
  );
}

export function BandChip({ score }) {
  const band = scoreBand(score);
  return (
    <span className="inline-flex items-center gap-1.5 rounded-full border border-line-strong bg-surface/60 px-2 py-0.5 text-[11px] font-semibold text-ink-2">
      <span className="h-2 w-2 rounded-full" style={{ backgroundColor: band.color }} aria-hidden="true" />
      {band.label}
    </span>
  );
}

const BUTTON_VARIANTS = {
  primary:
    'bg-accent text-on-accent hover:bg-accent-hover disabled:bg-surface-2 disabled:text-faint',
  secondary:
    'border border-line-strong bg-surface-2/60 text-ink hover:bg-surface-3/70 hover:border-line-strong disabled:opacity-50',
  dangerGhost: 'text-danger hover:bg-danger/10 hover:text-danger disabled:opacity-50',
  ghost: 'text-muted hover:bg-surface-2 hover:text-ink disabled:opacity-50',
  danger: 'bg-danger-solid text-white hover:bg-danger-solid/90 disabled:opacity-50',
};

export function Button({ variant = 'primary', loading, children, className = '', icon, ...props }) {
  return (
    <button
      type="button"
      {...props}
      disabled={props.disabled || loading}
      className={`inline-flex min-h-10 items-center justify-center gap-2 rounded-lg px-4 py-2 text-sm font-semibold transition-colors focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent disabled:cursor-not-allowed ${BUTTON_VARIANTS[variant]} ${className}`}
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
      <label htmlFor={id} className="mb-1.5 block text-[13px] font-medium text-ink-2">
        {label}
      </label>
      <input
        id={id}
        {...props}
        aria-invalid={error ? true : undefined}
        className="w-full rounded-lg border border-line-strong bg-surface px-3 py-2.5 text-sm text-ink placeholder:text-faint focus:border-accent focus:outline-none focus:ring-2 focus:ring-accent/20 disabled:opacity-60"
      />
      {hint && !error && <p className="mt-1 text-xs text-faint">{hint}</p>}
      {error && <p className="mt-1 text-xs text-danger">{error}</p>}
    </div>
  );
}

export function Toggle({ checked, onChange, label, description, disabled }) {
  const id = useId();
  return (
    <label htmlFor={id} className="flex cursor-pointer items-start justify-between gap-4 py-1">
      <span>
        <span className="block text-sm font-medium text-ink">{label}</span>
        {description && <span className="mt-0.5 block text-xs text-faint">{description}</span>}
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
        <span className="h-6 w-11 rounded-full bg-surface-3 transition-colors peer-checked:bg-accent peer-focus-visible:outline-2 peer-focus-visible:outline-offset-2 peer-focus-visible:outline-accent peer-disabled:opacity-50" />
        <span className="absolute left-0.5 top-0.5 h-5 w-5 rounded-full bg-white shadow transition-transform peer-checked:translate-x-5" />
      </span>
    </label>
  );
}

export function Card({ children, className = '' }) {
  return <section className={`rounded-2xl border border-line bg-surface/70 p-5 ${className}`}>{children}</section>;
}

export function ErrorNote({ children }) {
  if (!children) return null;
  return (
    <p role="alert" className="flex items-start gap-2 rounded-lg border border-danger/30 bg-danger/10 px-3 py-2 text-[13px] text-danger">
      <Icon name="alert" className="mt-px h-4 w-4 shrink-0" />
      <span>{children}</span>
    </p>
  );
}

export function EmptyState({ icon, title, children, action }) {
  return (
    <div className="flex flex-col items-center rounded-2xl border border-dashed border-line px-6 py-14 text-center">
      <span className="mb-4 flex h-12 w-12 items-center justify-center rounded-full bg-surface-2/80 text-muted">
        <Icon name={icon} className="h-6 w-6" />
      </span>
      <h2 className="text-base font-semibold text-ink">{title}</h2>
      {children && <p className="mt-1.5 max-w-sm text-sm text-muted">{children}</p>}
      {action && <div className="mt-5">{action}</div>}
    </div>
  );
}

export function PageLoader({ label = 'Loading…' }) {
  return (
    <div className="flex h-full min-h-60 items-center justify-center gap-3 text-sm text-muted">
      <Spinner />
      {label}
    </div>
  );
}
