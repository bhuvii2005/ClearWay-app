import { useEffect } from 'react';
import { MapContainer, TileLayer, useMap, useMapEvents } from 'react-leaflet';
import L from 'leaflet';
import { DEFAULT_CENTER } from '../lib/mapUtils';

/**
 * OpenStreetMap's standard tiles: free, no API key, attribution required.
 * They are darkened with a CSS filter (see .dark-tiles in index.css) to suit the app.
 */
export function BaseMap({ children, center = DEFAULT_CENTER, zoom = 12, className = 'h-full w-full', ...props }) {
  return (
    <MapContainer center={center} zoom={zoom} className={className} zoomControl={false} {...props}>
      <TileLayer
        attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
        url="https://tile.openstreetmap.org/{z}/{x}/{y}.png"
        className="dark-tiles"
        maxZoom={19}
      />
      <ZoomControl />
      {children}
    </MapContainer>
  );
}

function ZoomControl() {
  const map = useMap();
  useEffect(() => {
    const control = L.control.zoom({ position: 'bottomright' });
    control.addTo(map);
    return () => control.remove();
  }, [map]);
  return null;
}

/** Zooms the map to show every given [lat, lng] point. */
export function FitBounds({ points, padding = [48, 48], paddingTopLeft }) {
  const map = useMap();
  const key = points.length ? `${points.length}:${points[0]}:${points[points.length - 1]}` : '';
  useEffect(() => {
    if (!points.length) return;
    const bounds = L.latLngBounds(points);
    if (!bounds.isValid()) return;
    map.fitBounds(bounds, paddingTopLeft ? { paddingTopLeft, paddingBottomRight: padding } : { padding, maxZoom: 16 });
    // Only refit when the set of points changes, not on every render.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [key, map]);
  return null;
}

export function ClickHandler({ onClick }) {
  useMapEvents({ click: (e) => onClick(e.latlng) });
  return null;
}

/** Leaflet needs a size recalculation when its container changes size. */
export function InvalidateOnResize() {
  const map = useMap();
  useEffect(() => {
    const observer = new ResizeObserver(() => map.invalidateSize());
    observer.observe(map.getContainer());
    return () => observer.disconnect();
  }, [map]);
  return null;
}

