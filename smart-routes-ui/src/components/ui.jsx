import { useEffect, useId, useMemo, useRef } from 'react';
import { animate, motion, useReducedMotion } from 'motion/react';
import { airStops } from '../lib/mapUtils';
import Icon from './Icon';
import { buttonClass } from './buttonStyles';
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

/** A whole number that counts up to its value when it first appears or changes. */
export function CountUp({ value, className = '' }) {
  const ref = useRef(null);
  const from = useRef(0);
  const reduce = useReducedMotion();
  useEffect(() => {
    const el = ref.current;
    if (!el || value == null) return undefined;
    if (reduce) {
      el.textContent = Math.round(value);
      return undefined;
    }
    const controls = animate(from.current, value, {
      duration: 0.9,
      ease: [0.22, 1, 0.36, 1],
      onUpdate: (v) => {
        el.textContent = Math.round(v);
      },
    });
    from.current = value;
    return () => controls.stop();
  }, [value, reduce]);
  return (
    <span ref={ref} className={className}>
      {value == null ? '–' : 0}
    </span>
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
          className="ring-arc transition-[stroke-dashoffset] duration-700 ease-out motion-reduce:transition-none"
          style={{ '--ring-length': circumference }}
        />
      </svg>
      <span
        className={`absolute inset-0 grid place-items-center font-data font-bold tabular-nums leading-none text-ink ${
          size >= 80 ? 'text-2xl' : 'text-base'
        }`}
        aria-hidden="true"
      >
        <CountUp value={score == null ? null : Math.round(score)} />
      </span>
    </div>
  );
}

/**
 * A thin bar showing how the air changes from start to end of a route,
 * in the same colours as the line on the map.
 */
export function AirStrip({ coords, samples, score, className = '' }) {
  const gradient = useMemo(() => {
    const stops = airStops(coords ?? [], samples, score);
    return `linear-gradient(90deg, ${stops.map(([at, c]) => `${c} ${(at * 100).toFixed(1)}%`).join(', ')})`;
  }, [coords, samples, score]);
  return (
    <span className={`block h-1.5 overflow-hidden rounded-full bg-surface-3 ${className}`} aria-hidden="true">
      <span className="air-strip block h-full rounded-full" style={{ background: gradient }} />
    </span>
  );
}

/** Grey placeholder block that shimmers while content loads. */
export function Skeleton({ className = '' }) {
  return <span className={`skeleton block ${className}`} aria-hidden="true" />;
}

/**
 * One pollutant reading with a bar against its WHO guideline (the tick mark).
 * The bar shows up to 4× the guideline; the text always gives the exact multiple.
 */
export function PollutantReading({ label, value, who, large = false }) {
  const scale = 4;
  const width = who ? Math.min(who.ratio / scale, 1) * 100 : 0;
  return (
    <div className={`rounded-xl bg-surface-2/60 ${large ? 'p-3.5' : 'px-3 py-2.5'}`}>
      <dt className="flex items-baseline justify-between gap-2">
        <span className="text-xs font-medium text-muted">{label}</span>
        {who && (
          <span className={`text-[11px] font-medium ${who.over ? 'text-warning' : 'text-good'}`}>{who.short}</span>
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
    <span className="inline-flex items-center gap-1.5 text-xs font-medium text-ink-2">
      <span className="h-2 w-2 rounded-full" style={{ backgroundColor: band.color }} aria-hidden="true" />
      {band.label}
    </span>
  );
}

export function Button({ variant = 'primary', loading, children, className = '', icon, ...props }) {
  return (
    <button
      type="button"
      {...props}
      disabled={props.disabled || loading}
      className={buttonClass(variant, className)}
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
        className="w-full rounded-xl border border-line-strong bg-surface px-3.5 py-2.5 text-sm text-ink transition-shadow placeholder:text-faint focus:border-accent focus:outline-none focus:ring-4 focus:ring-accent/15 disabled:opacity-60"
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
        <span className="h-6 w-11 rounded-full bg-line-strong transition-colors duration-200 peer-checked:bg-accent peer-focus-visible:outline-2 peer-focus-visible:outline-offset-2 peer-focus-visible:outline-accent peer-disabled:opacity-50" />
        <span className="absolute left-0.5 top-0.5 h-5 w-5 rounded-full bg-white shadow-[0_1px_3px_rgb(0_0_0/0.25)] transition-transform duration-200 ease-[var(--ease-out-soft)] peer-checked:translate-x-5" />
      </span>
    </label>
  );
}

export function Card({ children, className = '' }) {
  return <section className={`rounded-2xl bg-surface p-5 shadow-card ${className}`}>{children}</section>;
}

export function ErrorNote({ children }) {
  if (!children) return null;
  return (
    <p role="alert" className="flex items-start gap-2 rounded-xl bg-danger/10 px-3 py-2 text-[13px] text-danger">
      <Icon name="alert" className="mt-px h-4 w-4 shrink-0" />
      <span>{children}</span>
    </p>
  );
}

export function EmptyState({ icon, title, children, action }) {
  return (
    <div className="flex flex-col items-center rounded-2xl bg-surface px-6 py-14 text-center shadow-card">
      <span className="mb-4 flex h-12 w-12 items-center justify-center rounded-2xl bg-accent/10 text-accent-ink">
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

/** Page title row: big title, a quiet line under it, and an optional action on the right. */
export function PageHeader({ title, subtitle, action, children }) {
  return (
    <header className="flex flex-wrap items-end justify-between gap-4">
      <div className="min-w-0">
        {children}
        <h1 className="font-display text-[28px] font-bold leading-tight tracking-tight text-ink">{title}</h1>
        {subtitle && <p className="mt-1 text-sm text-muted">{subtitle}</p>}
      </div>
      {action}
    </header>
  );
}

/** A row of choices with a white pill that slides to the chosen one. */
export function Segmented({ options, value, onChange, label, id }) {
  return (
    <div role="radiogroup" aria-label={label} className="inline-flex rounded-xl bg-surface-2/80 p-1">
      {options.map((o) => (
        <button
          key={o.value}
          type="button"
          role="radio"
          aria-checked={value === o.value}
          onClick={() => onChange(o.value)}
          className={`relative min-h-8 rounded-lg px-3 text-xs font-medium transition-colors focus-visible:outline-2 focus-visible:outline-accent ${
            value === o.value ? 'text-ink' : 'text-muted hover:text-ink'
          }`}
        >
          {value === o.value && <motion.span layoutId={`seg-${id}`} className="absolute inset-0 rounded-lg bg-surface shadow-card" />}
          <span className="relative">{o.label}</span>
        </button>
      ))}
    </div>
  );
}
