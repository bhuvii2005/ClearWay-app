/**
 * Place search and reverse lookup using Photon (photon.komoot.io), a free
 * OpenStreetMap geocoder that needs no API key and allows search-as-you-type.
 */
const PHOTON = 'https://photon.komoot.io';

function placeLabel(props) {
  const street = [props.housenumber, props.street].filter(Boolean).join(' ');
  const primary = props.name || street || props.city || props.state || 'Unnamed place';
  const secondary = [props.name ? street : null, props.district, props.city, props.state, props.country]
    .filter((part) => part && part !== primary);
  return { name: primary, detail: [...new Set(secondary)].slice(0, 3).join(', ') };
}

function toPlace(feature) {
  const [lng, lat] = feature.geometry.coordinates;
  const { name, detail } = placeLabel(feature.properties || {});
  return { lat, lng, name, detail };
}

export async function searchPlaces(query, { near, signal } = {}) {
  const params = new URLSearchParams({ q: query, limit: '6', lang: 'en' });
  if (near) {
    params.set('lat', near.lat.toFixed(4));
    params.set('lon', near.lng.toFixed(4));
  }
  const res = await fetch(`${PHOTON}/api/?${params}`, { signal });
  if (!res.ok) throw new Error('Place search failed');
  const json = await res.json();
  return (json.features || []).map(toPlace);
}

export async function reverseGeocode(lat, lng, { signal } = {}) {
  const params = new URLSearchParams({ lat: String(lat), lon: String(lng), lang: 'en' });
  const res = await fetch(`${PHOTON}/reverse?${params}`, { signal });
  if (!res.ok) return null;
  const json = await res.json();
  return json.features?.length ? toPlace(json.features[0]) : null;
}

export function coordinateLabel(lat, lng) {
  return `${lat.toFixed(4)}, ${lng.toFixed(4)}`;
}
