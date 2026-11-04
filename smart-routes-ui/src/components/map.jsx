import { useEffect, useMemo } from 'react';
import Map, { Layer, Marker, NavigationControl, Source, useMap } from 'react-map-gl/maplibre';
import { setWorkerUrl } from 'maplibre-gl';
import workerUrl from 'maplibre-gl/dist/maplibre-gl-worker.mjs?url';
import { MAP_STYLE, DEFAULT_VIEW, airStops, boundsOf, gradientExpression, prefersReducedMotion } from '../lib/mapUtils';
import { scoreBand } from '../lib/format';

// MapLibre 6 runs tile decoding in a separate worker file; tell it where the build put that file.
setWorkerUrl(workerUrl);

/** Vector base map (MapLibre GL + OpenFreeMap). Extra props go to react-map-gl's Map. */
export function BaseMap({ children, initialViewState = DEFAULT_VIEW, ...props }) {
  return (
    <Map
      initialViewState={initialViewState}
      mapStyle={MAP_STYLE}
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
 * The chosen route: a soft glow, a dark casing, and a line coloured by the air along it
 * (green = clean, red = poor). It draws itself from A to B when `animateKey` changes.
 */
export function AirRoute({ id = 'route', coords, samples, score, animateKey, width = 6 }) {
  const { current: map } = useMap();
  const stops = useMemo(() => airStops(coords, samples, score), [coords, samples, score]);
  const animate = !prefersReducedMotion();
  const data = useMemo(
    () => ({ type: 'Feature', properties: {}, geometry: { type: 'LineString', coordinates: coords } }),
    [coords],
  );
  const glowColor = scoreBand(score).color;
  const lineId = `${id}-line`;

  // Paint is fixed per route so React re-renders never undo the animation below.
  const linePaint = useMemo(
    () => ({ 'line-width': width, 'line-gradient': gradientExpression(stops, animate ? 0 : 1) }),
    [stops, width, animate],
  );

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
      const eased = 1 - Math.pow(1 - t, 3);
      gl.setPaintProperty(lineId, 'line-gradient', gradientExpression(stops, eased));
      if (t < 1) frame = requestAnimationFrame(step);
    };
    frame = requestAnimationFrame(step);
    return () => cancelAnimationFrame(frame);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [map, stops, animateKey]);

  return (
    <Source id={id} type="geojson" data={data} lineMetrics>
      <Layer
        id={`${id}-glow`}
        type="line"
        layout={{ 'line-cap': 'round', 'line-join': 'round' }}
        paint={{ 'line-color': glowColor, 'line-width': width * 4, 'line-opacity': 0.18, 'line-blur': width * 2 }}
      />
      <Layer
        id={`${id}-casing`}
        type="line"
        layout={{ 'line-cap': 'round', 'line-join': 'round' }}
        paint={{ 'line-color': '#020617', 'line-width': width + 4, 'line-opacity': 0.9 }}
      />
      <Layer id={lineId} type="line" layout={{ 'line-cap': 'round', 'line-join': 'round' }} paint={linePaint} />
    </Source>
  );
}

/** Routes that are not selected: quiet grey lines that can be clicked (layer id "alt-routes"). */
export function OtherRoutes({ routes, selected }) {
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
        paint={{ 'line-color': '#020617', 'line-width': 9, 'line-opacity': 0.6 }}
      />
      <Layer
        id="alt-routes"
        type="line"
        layout={{ 'line-cap': 'round', 'line-join': 'round' }}
        paint={{ 'line-color': '#94a3b8', 'line-width': 5, 'line-opacity': 0.55 }}
      />
    </Source>
  );
}
