import React, { useState } from 'react';
import { MapContainer, TileLayer, Marker, Polyline, useMapEvents, Popup } from 'react-leaflet';
import 'leaflet/dist/leaflet.css';
import L from 'leaflet';
import axios from 'axios';

// Fix for default Leaflet icons in React
delete L.Icon.Default.prototype._getIconUrl;
L.Icon.Default.mergeOptions({
  iconRetinaUrl: 'https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.7.1/images/marker-icon-2x.png',
  iconUrl: 'https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.7.1/images/marker-icon.png',
  shadowUrl: 'https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.7.1/images/marker-shadow.png',
});

function MapComponent() {
  const [waypoints, setWaypoints] = useState([]);
  const [routeLine, setRouteLine] = useState([]);
  const [score, setScore] = useState(null);
  const [loading, setLoading] = useState(false);

  // Example User ID (UUID placeholder)
  const MOCK_USER_ID = "00000000-0000-0000-0000-000000000001"; 

  const MapEvents = () => {
    useMapEvents({
      click(e) {
        if (waypoints.length < 2) {
          setWaypoints([...waypoints, [e.latlng.lat, e.latlng.lng]]);
        }
      },
    });
    return null;
  };

  const calculateRoute = async () => {
    if (waypoints.length < 2) return;
    setLoading(true);
    try {
      // Note: Ideally, OpenRouteService gives us the real street line geometry here.
      // For this MVP Frontend, we draw a straight line and submit to backend for score
      const response = await axios.post('http://localhost:8080/api/v1/routes', {
        userId: MOCK_USER_ID,
        name: "My Custom Route " + new Date().toLocaleTimeString(),
        type: "walking",
        pathCoordinates: waypoints.map(wp => [wp[1], wp[0]]) // Lon, Lat for PostGIS
      });

      setScore(response.data);
      setRouteLine(waypoints);
    } catch (error) {
      console.error("Error creating route", error);
      alert("Error: Backend must be running on port 8080 and configured properly!");
    } finally {
      setLoading(false);
    }
  };

  const clearRoute = () => {
    setWaypoints([]);
    setRouteLine([]);
    setScore(null);
  };

  return (
    <div className="relative h-screen w-full bg-slate-900 font-sans text-slate-100 overflow-hidden">
      
      {/* Map UI - Now takes full screen */}
      <div className="absolute inset-0 z-0">
        <MapContainer 
          center={[28.6139, 77.2090]} // Default to New Delhi, India
          zoom={12} 
          className="h-full w-full"
          zoomControl={false}
        >
          {/* Dark Mode Tiles from CartoDB */}
          <TileLayer
            attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors &copy; <a href="https://carto.com/attributions">CARTO</a>'
            url="https://{s}.basemaps.cartocdn.com/dark_all/{z}/{x}/{y}{r}.png"
          />
          <MapEvents />
          
          {waypoints.map((pos, idx) => (
            <Marker key={idx} position={pos}>
              <Popup className="text-slate-900 font-bold">
                {idx === 0 ? 'Start Point' : 'Destination'}
              </Popup>
            </Marker>
          ))}

          {routeLine.length > 0 && (
            <Polyline 
              positions={routeLine} 
              color="#10b981" // emerald-500
              weight={5}
              dashArray="10, 10"
              className="animate-pulse"
            />
          )}
        </MapContainer>
        {/* Subtle Map Overlay Vignette */}
        <div className="absolute inset-0 pointer-events-none shadow-[inset_0_0_150px_rgba(15,23,42,0.9)] z-[400]" />
      </div>

      {/* Floating UI Overlay for Desktop / Bottom Sheet for Mobile */}
      <div className="absolute top-0 left-0 w-full lg:w-[400px] h-auto lg:h-full z-10 p-4 lg:p-6 lg:pl-8 flex flex-col justify-end lg:justify-start pointer-events-none">
        
        <div className="pointer-events-auto flex flex-col gap-6 bg-slate-900/80 backdrop-blur-xl lg:bg-slate-900/90 p-6 lg:p-8 rounded-2xl lg:rounded-3xl shadow-2xl border border-slate-700/50 max-h-[85vh] overflow-y-auto">
          
          <div>
            <h1 className="text-4xl lg:text-5xl font-extrabold text-transparent bg-clip-text bg-gradient-to-r from-emerald-400 to-cyan-500 tracking-tight pb-1">
              ClearWay
            </h1>
            <p className="mt-2 text-slate-400 text-sm leading-relaxed">
              Discover the healthiest way to travel. We calculate real-time pollution exposure along your path.
            </p>
          </div>

          <div className="bg-slate-800/60 rounded-xl p-5 border border-slate-600/30">
            <h2 className="text-lg font-semibold text-emerald-300 mb-4 flex items-center gap-2">
              <svg className="w-5 h-5 flex-shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M17.657 16.657L13.414 20.9a1.998 1.998 0 01-2.827 0l-4.244-4.243a8 8 0 1111.314 0z" /><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 11a3 3 0 11-6 0 3 3 0 016 0z" /></svg>
              Plan Your Route
            </h2>
            
            <ul className="text-sm text-slate-300 space-y-4 mb-6">
              <li className="flex items-center gap-3 bg-slate-900/50 p-3 rounded-lg border border-slate-700/50 transition-colors">
                <span className={`flex h-7 w-7 flex-shrink-0 items-center justify-center rounded-full text-xs font-bold shadow-inner ${waypoints.length >= 1 ? 'bg-emerald-500 text-slate-900 shadow-emerald-400/50' : 'bg-slate-700 text-slate-400'}`}>1</span>
                <div>
                  <p className="font-medium text-slate-200">Set Origin</p>
                  <p className="text-xs text-slate-400">Click the map to start</p>
                </div>
              </li>
              <li className="flex items-center gap-3 bg-slate-900/50 p-3 rounded-lg border border-slate-700/50 transition-colors">
                <span className={`flex h-7 w-7 flex-shrink-0 items-center justify-center rounded-full text-xs font-bold shadow-inner ${waypoints.length >= 2 ? 'bg-cyan-500 text-slate-900 shadow-cyan-400/50' : 'bg-slate-700 text-slate-400'}`}>2</span>
                <div>
                  <p className="font-medium text-slate-200">Set Destination</p>
                  <p className="text-xs text-slate-400">Click the map to end</p>
                </div>
              </li>
            </ul>

            <div className="flex flex-col gap-3">
              <button 
                onClick={calculateRoute} 
                disabled={waypoints.length < 2 || loading}
                className={`w-full py-3.5 px-4 rounded-xl font-bold transition-all duration-300 shadow-lg flex items-center justify-center gap-2
                  ${waypoints.length < 2 || loading 
                    ? 'bg-slate-800 cursor-not-allowed text-slate-500 shadow-none border border-slate-700' 
                    : 'bg-gradient-to-r from-emerald-500 to-cyan-500 hover:from-emerald-400 hover:to-cyan-400 text-white hover:shadow-cyan-500/25 active:scale-[0.98]'
                  }`}
              >
                {loading ? (
                  <>
                    <svg className="animate-spin h-5 w-5 text-white" fill="none" viewBox="0 0 24 24">
                      <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4"></circle>
                      <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
                    </svg>
                    Analyzing Air Quality...
                  </>
                ) : (
                  <>
                    <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M13 10V3L4 14h7v7l9-11h-7z" /></svg>
                    Calculate Exposure Score
                  </>
                )}
              </button>
              
              <button 
                onClick={clearRoute}
                className="w-full py-3 px-4 rounded-xl font-semibold bg-transparent hover:bg-slate-800 text-slate-400 hover:text-slate-200 transition-colors border border-slate-700 hover:border-slate-600"
              >
                Reset Map
              </button>
            </div>
          </div>

          {score && (
            <div className="p-5 rounded-xl bg-gradient-to-br from-emerald-900/60 to-slate-800/80 border border-emerald-500/30 animate-in slide-in-from-bottom-4 fade-in duration-500 shadow-[0_0_30px_rgba(16,185,129,0.15)] backdrop-blur-md">
              <h3 className="text-emerald-400 font-bold mb-3 flex items-center gap-2">
                <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z" /></svg>
                Analysis Complete
              </h3>
              <div className="bg-slate-900/50 p-4 rounded-lg border border-slate-700/50">
                <p className="text-slate-200 text-sm break-words whitespace-pre-wrap leading-relaxed font-mono font-medium">
                  {score}
                </p>
              </div>
            </div>
          )}

        </div>
      </div>
    </div>
  );
}

export default MapComponent;
