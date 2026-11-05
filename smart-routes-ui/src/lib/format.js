/**
 * Air-quality bands follow the European AQI the scores are based on (0–100+).
 * Colour uses four status steps and is always shown next to the band's name,
 * so meaning never depends on colour alone.
 */
const STATUS = {
  good: '#0ca30c',
  warning: '#fab219',
  serious: '#ec835a',
  critical: '#d03b3b',
};

const BANDS = [
  { max: 20, label: 'Good', status: 'good' },
  { max: 40, label: 'Fair', status: 'good' },
  { max: 60, label: 'Moderate', status: 'warning' },
  { max: 80, label: 'Poor', status: 'serious' },
  { max: 100, label: 'Very poor', status: 'critical' },
  { max: Infinity, label: 'Extremely poor', status: 'critical' },
];

export function scoreBand(score) {
  if (score == null || Number.isNaN(score)) {
    return { label: 'No data', color: '#64748b', status: 'none' };
  }
  const band = BANDS.find((b) => score <= b.max);
  return { label: band.label, color: STATUS[band.status], status: band.status };
}

export const MODES = [
  { id: 'walking', label: 'Walk', icon: 'walk' },
  { id: 'jogging', label: 'Jog', icon: 'run' },
  { id: 'cycling', label: 'Cycle', icon: 'bike' },
  { id: 'driving', label: 'Drive', icon: 'car' },
];

export function modeLabel(type) {
  if (type === 'commute') return 'Drive';
  return MODES.find((m) => m.id === type)?.label ?? 'Walk';
}

export function formatScore(score) {
  return score == null ? '–' : Math.round(score).toString();
}

export function formatDistanceMeters(meters) {
  if (meters == null) return '–';
  return meters < 1000 ? `${Math.round(meters)} m` : `${(meters / 1000).toFixed(1)} km`;
}

export function formatDistanceKm(km) {
  return km == null ? '–' : formatDistanceMeters(km * 1000);
}

export function formatDuration(seconds) {
  if (seconds == null) return '–';
  const minutes = Math.max(1, Math.round(seconds / 60));
  if (minutes < 60) return `${minutes} min`;
  const h = Math.floor(minutes / 60);
  const m = minutes % 60;
  return m ? `${h} h ${m} min` : `${h} h`;
}

export function formatHour(hour) {
  if (hour == null) return '–';
  const suffix = hour < 12 ? 'AM' : 'PM';
  const h = hour % 12 === 0 ? 12 : hour % 12;
  return `${h} ${suffix}`;
}

/** "Fastest route", "12% less exposure than the fastest", etc. */
export function exposureText(route) {
  if (route.fastest) return 'Fastest route';
  const p = route.exposureSavingPercent ?? 0;
  if (p >= 0.5) return `${Math.round(p)}% less exposure than the fastest`;
  if (p <= -0.5) return `${Math.round(-p)}% more exposure than the fastest`;
  return 'Same exposure as the fastest';
}

const relative = new Intl.RelativeTimeFormat(undefined, { numeric: 'auto' });

export function formatRelative(value) {
  if (!value) return 'never';
  const diffSeconds = (new Date(value).getTime() - Date.now()) / 1000;
  const abs = Math.abs(diffSeconds);
  if (abs < 60) return 'just now';
  if (abs < 3600) return relative.format(Math.round(diffSeconds / 60), 'minute');
  if (abs < 86400) return relative.format(Math.round(diffSeconds / 3600), 'hour');
  return relative.format(Math.round(diffSeconds / 86400), 'day');
}

export function formatDateTime(value) {
  if (!value) return '–';
  return new Date(value).toLocaleString(undefined, {
    day: 'numeric',
    month: 'short',
    hour: 'numeric',
    minute: '2-digit',
  });
}

export function userTimeZone() {
  try {
    return Intl.DateTimeFormat().resolvedOptions().timeZone || 'UTC';
  } catch {
    return 'UTC';
  }
}

export const POLLUTANTS = [
  { key: 'pm25', label: 'PM2.5', pref: 'avoidPm25' },
  { key: 'pm10', label: 'PM10', pref: 'avoidPm10' },
  { key: 'no2', label: 'NO₂', pref: 'avoidNo2' },
  { key: 'ozone', label: 'Ozone', pref: 'avoidOzone' },
];

/**
 * WHO 2021 air-quality guidelines (µg/m³): 24-hour means, except ozone (8-hour).
 * Readings here are snapshots, so they are compared as a rough guide, not a verdict.
 */
export const WHO_GUIDELINE = { pm25: 15, pm10: 45, no2: 25, ozone: 100 };

/** "Within WHO guideline" or "2.4× WHO guideline", with the ratio for drawing a bar. */
export function whoComparison(key, value) {
  const limit = WHO_GUIDELINE[key];
  if (value == null || !limit) return null;
  const ratio = value / limit;
  if (ratio <= 1) return { ratio, over: false, text: 'Within WHO guideline', short: 'Within WHO' };
  const times = ratio < 10 ? ratio.toFixed(1).replace(/\.0$/, '') : Math.round(ratio).toString();
  return { ratio, over: true, text: `${times}× WHO guideline`, short: `${times}× WHO` };
}

/** Percent change from `base` to `value`, rounded; positive = lower than base. */
function percentLower(value, base) {
  if (value == null || base == null || base <= 0) return null;
  return Math.round(((base - value) / base) * 100);
}

/**
 * Plain-language trade-off against the fastest route:
 * { time: "+4 min" | "Same time" | null, pm25: "38% less PM2.5" | null }.
 */
export function tradeOff(route, fastest) {
  if (!fastest || route === fastest || route.fastest) return { time: null, pm25: null };
  const extra = Math.round(((route.travelTimeSeconds ?? 0) - (fastest.travelTimeSeconds ?? 0)) / 60);
  const time = extra >= 1 ? `+${formatDuration(extra * 60)}` : extra <= -1 ? `${formatDuration(-extra * 60)} quicker` : 'Same time';
  const p = percentLower(route.pollutionData?.pm25, fastest.pollutionData?.pm25);
  const pm25 = p == null || p === 0 ? null : p > 0 ? `${p}% less PM2.5` : `${-p}% more PM2.5`;
  return { time, pm25 };
}
