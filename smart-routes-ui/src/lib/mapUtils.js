import { scoreBand } from './format';

/**
 * Free vector map styles from OpenFreeMap (no key, no usage limits), one per app theme,
 * plus the colours that outline routes so they stand out on that map.
 */
export const MAP_THEMES = {
  light: {
    style: 'https://tiles.openfreemap.org/styles/positron',
    casing: '#ffffff',
    alt: '#7b8a9c',
    altOpacity: 0.7,
  },
  dark: {
    style: 'https://tiles.openfreemap.org/styles/dark',
    casing: '#070b12',
    alt: '#94a3b8',
    altOpacity: 0.55,
  },
};

export const mapTheme = (theme) => MAP_THEMES[theme] ?? MAP_THEMES.light;

export const DEFAULT_VIEW = { longitude: 77.209, latitude: 28.6139, zoom: 12 }; // New Delhi

const TRANSPARENT = 'rgba(0,0,0,0)';

/** [[west, south], [east, north]] around [lng, lat] points, or null when there are none. */
export function boundsOf(coords) {
  if (!coords?.length) return null;
  let west = Infinity, south = Infinity, east = -Infinity, north = -Infinity;
  for (const [lng, lat] of coords) {
    if (lng < west) west = lng;
    if (lng > east) east = lng;
    if (lat < south) south = lat;
    if (lat > north) north = lat;
  }
  return [[west, south], [east, north]];
}

/** Share of the route's length (0 to 1) reached at each coordinate. */
function progressAlong(coords) {
  const out = [0];
  let total = 0;
  for (let i = 1; i < coords.length; i++) {
    const [x1, y1] = coords[i - 1];
    const [x2, y2] = coords[i];
    const dx = (x2 - x1) * Math.cos((((y1 + y2) / 2) * Math.PI) / 180);
    total += Math.hypot(dx, y2 - y1);
    out.push(total);
  }
  return total > 0 ? out.map((d) => d / total) : out.map((_, i) => i / Math.max(1, coords.length - 1));
}

/**
 * Colour stops along the route from its air samples ([lng, lat, aqi], start to end).
 * Each sample is placed at the matching path point; colours blend between samples.
 */
export function airStops(coords, samples, fallbackScore) {
  if (!samples?.length || coords.length < 2) {
    const color = scoreBand(fallbackScore).color;
    return [[0, color], [1, color]];
  }
  const progress = progressAlong(coords);
  const stops = [];
  let from = 0;
  for (const [lng, lat, aqi] of samples) {
    let best = from, bestDist = Infinity;
    for (let i = from; i < coords.length; i++) {
      const d = Math.abs(coords[i][0] - lng) + Math.abs(coords[i][1] - lat);
      if (d < bestDist) { bestDist = d; best = i; }
      if (d === 0) break;
    }
    from = best;
    stops.push([progress[best], scoreBand(aqi).color]);
  }
  stops[0][0] = 0;
  stops[stops.length - 1][0] = 1;
  return stops;
}

/**
 * A MapLibre line-gradient expression for the stops, drawn only up to `reveal` (0 to 1)
 * so the route can animate onto the map.
 */
export function gradientExpression(stops, reveal = 1) {
  const expr = ['interpolate', ['linear'], ['line-progress']];
  let last = -1;
  const add = (at, color) => {
    const pos = Math.min(1, Math.max(at, last + 1e-6));
    if (pos <= last || pos > 1) return;
    expr.push(pos, color);
    last = pos;
  };
  for (let i = 0; i < stops.length; i++) {
    const [at, color] = stops[i];
    if (at <= reveal) {
      add(at, color);
      continue;
    }
    // Colour at the reveal point, blended from the previous stop, then cut off.
    const [prevAt, prevColor] = stops[i - 1];
    add(reveal, (reveal - prevAt) / (at - prevAt) < 0.5 ? prevColor : color);
    break;
  }
  if (reveal < 1) {
    add(reveal + 1e-6, TRANSPARENT);
    add(1, TRANSPARENT);
  }
  if (expr.length === 5) expr.push(1, expr[4]); // interpolate needs two stops
  return expr;
}

export const prefersReducedMotion = () =>
  typeof window !== 'undefined' && window.matchMedia?.('(prefers-reduced-motion: reduce)').matches;
