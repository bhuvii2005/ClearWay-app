import L from 'leaflet';

export const DEFAULT_CENTER = [28.6139, 77.209]; // New Delhi

/** Start (A) and end (B) markers drawn as inline SVG, so no image files are needed. */
export function pinIcon(kind) {
  const isStart = kind === 'origin';
  const color = isStart ? '#34d399' : '#38bdf8';
  return L.divIcon({
    className: 'map-pin',
    iconSize: [30, 38],
    iconAnchor: [15, 36],
    html: `<svg width="30" height="38" viewBox="0 0 30 38" aria-hidden="true">
      <path d="M15 37s13-12.2 13-22A13 13 0 0 0 2 15c0 9.8 13 22 13 22Z" fill="${color}" stroke="#020617" stroke-width="2"/>
      <text x="15" y="20" text-anchor="middle" font-family="system-ui,sans-serif" font-size="13" font-weight="800" fill="#020617">${isStart ? 'A' : 'B'}</text>
    </svg>`,
  });
}

/** API coordinates are [lng, lat]; Leaflet wants [lat, lng]. */
export const toLatLngs = (coords) => (coords || []).map(([lng, lat]) => [lat, lng]);
