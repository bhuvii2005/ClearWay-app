import { useEffect, useMemo, useState } from 'react';
import Map, { Layer, Marker, NavigationControl, Source, useMap } from 'react-map-gl/maplibre';
import { setWorkerUrl } from 'maplibre-gl';
import workerUrl from 'maplibre-gl/dist/maplibre-gl-worker.mjs?url';
import { DEFAULT_VIEW, mapTheme, airStops, boundsOf, gradientExpression, prefersReducedMotion } from '../lib/mapUtils';
import { scoreBand } from '../lib/format';
import { useTheme } from '../context/contexts';

// MapLibre 6 runs tile decoding in a separate worker file; tell it where the build put that file.
setWorkerUrl(workerUrl);

/** Vector base map (MapLibre GL + OpenFreeMap) in the app's theme. Extra props go to react-map-gl's Map. */
export function BaseMap({ children, initialViewState = DEFAULT_VIEW, ...props }) {
  const { theme } = useTheme();
  return (
    <Map
      initialViewState={initialViewState}
      mapStyle={mapTheme(theme).style}
      style={{ width: '100%', height: '100%' }}
      attributionControl={{ compact: true }}
      dragRotate
      maxPitch={60}
      {...props}
    >
      <NavigationControl position="bottom-right" visualizePitch />
      {children}
    </Map>
  );
}

/** Flies the map to show every [lng, lat] point whenever the set of points changes. */
export function FitBounds({ coords, padding = 48 }) {
  const { current: map } = useMap();
  const bounds = boundsOf(coords);
  const key = bounds ? bounds.flat().map((n) => n.toFixed(5)).join(',') : '';
  const paddingKey = JSON.stringify(padding);
  useEffect(() => {
    if (!map || !bounds) return;
    const single = bounds[0][0] === bounds[1][0] && bounds[0][1] === bounds[1][1];
    const duration = prefersReducedMotion() ? 0 : 1100;
    if (single) {
      map.flyTo({ center: bounds[0], zoom: Math.max(map.getZoom(), 14), duration });
    } else {
      map.fitBounds(bounds, { padding, maxZoom: 16, duration, essential: true });
    }
    // Only move when the points or padding change, not on every render.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [map, key, paddingKey]);
  return null;
}

/** Start (A) or destination (B) pin. */
export function Pin({ kind, longitude, latitude, ...props }) {
  const isStart = kind === 'origin';
  return (
    <Marker
      longitude={longitude}
      latitude={latitude}
      anchor="bottom"
      onClick={(e) => e.originalEvent.stopPropagation()} // a click on a pin is not a click on the map
      {...props}
    >
      <div className={`cw-pin ${isStart ? 'cw-pin--start' : 'cw-pin--end'}`} title={props.title}>
        <span>{isStart ? 'A' : 'B'}</span>
      </div>
    </Marker>
  );
}

/**
 * The chosen route: a soft glow, an outline, and a line coloured by the air along it
 * (green = clean, red = poor). It draws itself from A to B when `animateKey` changes.
 */
export function AirRoute({ id = 'route', coords, samples, score, animateKey, width = 6 }) {
  const { current: map } = useMap();
  const { theme } = useTheme();
  const colors = mapTheme(theme);
  const stops = useMemo(() => airStops(coords, samples, score), [coords, samples, score]);
  const animate = !prefersReducedMotion();
  const data = useMemo(
    () => ({ type: 'Feature', properties: {}, geometry: { type: 'LineString', coordinates: coords } }),
    [coords],
  );
  const glowColor = scoreBand(score).color;
  const lineId = `${id}-line`;

  // How much of the route is drawn (0 to 1). Kept in state, so if the map style is swapped
  // (theme change) the re-added layer comes back at the right point.
  const [reveal, setReveal] = useState(animate ? 0 : 1);
  const [shown, setShown] = useState({ stops, animateKey });
  if (shown.stops !== stops || shown.animateKey !== animateKey) {
    setShown({ stops, animateKey });
    setReveal(animate ? 0 : 1);
  }

  useEffect(() => {
    if (!map || !animate) return;
    const gl = map.getMap();
    const duration = 1000;
    let start = null;
    let frame;
    const step = (now) => {
      if (!gl.getLayer(lineId)) {
        frame = requestAnimationFrame(step); // layer not added yet (style still loading)
        return;
      }
      start ??= now;
      const t = Math.min(1, (now - start) / duration);
      setReveal(1 - Math.pow(1 - t, 3)); // ease out
      if (t < 1) frame = requestAnimationFrame(step);
    };
    frame = requestAnimationFrame(step);
    return () => cancelAnimationFrame(frame);
  }, [map, stops, animateKey, animate, lineId]);

  const linePaint = useMemo(
    () => ({ 'line-width': width, 'line-gradient': gradientExpression(stops, reveal) }),
    [stops, width, reveal],
  );

  return (
    <Source id={id} type="geojson" data={data} lineMetrics>
      <Layer
        id={`${id}-glow`}
        type="line"
        layout={{ 'line-cap': 'round', 'line-join': 'round' }}
        paint={{ 'line-color': glowColor, 'line-width': width * 4, 'line-opacity': 0.2, 'line-blur': width * 2 }}
      />
      <Layer
        id={`${id}-casing`}
        type="line"
        layout={{ 'line-cap': 'round', 'line-join': 'round' }}
        paint={{ 'line-color': colors.casing, 'line-width': width + 4, 'line-opacity': 0.95 }}
      />
      <Layer id={lineId} type="line" layout={{ 'line-cap': 'round', 'line-join': 'round' }} paint={linePaint} />
    </Source>
  );
}

/** Routes that are not selected: quiet grey lines that can be clicked (layer id "alt-routes"). */
export function OtherRoutes({ routes, selected }) {
  const { theme } = useTheme();
  const colors = mapTheme(theme);
  const data = useMemo(
    () => ({
      type: 'FeatureCollection',
      features: routes
        .map((r, index) => ({
          type: 'Feature',
          properties: { index },
          geometry: { type: 'LineString', coordinates: r.pathCoordinates },
        }))
        .filter((f) => f.properties.index !== selected),
    }),
    [routes, selected],
  );
  return (
    <Source id="alt" type="geojson" data={data}>
      <Layer
        id="alt-routes-casing"
        type="line"
        layout={{ 'line-cap': 'round', 'line-join': 'round' }}
        paint={{ 'line-color': colors.casing, 'line-width': 9, 'line-opacity': 0.8 }}
      />
      <Layer
        id="alt-routes"
        type="line"
        layout={{ 'line-cap': 'round', 'line-join': 'round' }}
        paint={{ 'line-color': colors.alt, 'line-width': 5, 'line-opacity': colors.altOpacity }}
      />
    </Source>
  );
}
