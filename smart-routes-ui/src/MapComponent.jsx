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
  const [alternativeRoutes, setAlternativeRoutes] = useState([]);
  const [loading, setLoading] = useState(false);

  // Authentication State
  const [user, setUser] = useState(null);
  const [authMode, setAuthMode] = useState('login'); // 'login' or 'signup'
  const [authEmail, setAuthEmail] = useState('');
  const [authPassword, setAuthPassword] = useState('');
  const [authName, setAuthName] = useState('');
  const [authPhone, setAuthPhone] = useState('');
  const [authError, setAuthError] = useState('');
  const [authLoading, setAuthLoading] = useState(false);
  const [preferencesSaved, setPreferencesSaved] = useState(false);

  // Example User ID (Fallback placeholder if not logged in)
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
      // Hit the TomTom alternatives endpoint
      const response = await axios.post('http://localhost:8080/api/v1/routes/alternatives', {
        userId: user ? user.id : MOCK_USER_ID,
        name: "My Custom Route",
        type: "walking",
        pathCoordinates: waypoints.map(wp => [wp[1], wp[0]]) // Lon, Lat for PostGIS
      });

      // Save the array of routes to draw them
      setAlternativeRoutes(response.data);
    } catch (error) {
      console.error("Error creating route", error);
      alert("Error: Backend must be running on port 8080 and configured properly!");
    } finally {
      setLoading(false);
    }
  };

  const calculateRouteWithUser = async (userId) => {
    if (waypoints.length < 2) return;
    setLoading(true);
    try {
      const response = await axios.post('http://localhost:8080/api/v1/routes/alternatives', {
        userId: userId,
        name: "My Custom Route",
        type: "walking",
        pathCoordinates: waypoints.map(wp => [wp[1], wp[0]])
      });
      setAlternativeRoutes(response.data);
    } catch (error) {
      console.error("Error creating route with user preferences", error);
    } finally {
      setLoading(false);
    }
  };

  const clearRoute = () => {
    setWaypoints([]);
    setAlternativeRoutes([]);
  };

  // Auth Action Handlers
  const handleLogin = async (e) => {
    e.preventDefault();
    setAuthLoading(true);
    setAuthError("");
    try {
      const response = await axios.post('http://localhost:8080/api/v1/auth/login', {
        email: authEmail,
        password: authPassword
      });
      setUser(response.data);
      setAuthPassword("");
      setAuthError("");
      
      // Auto-recalculate if user already plotted path before logging in
      if (waypoints.length >= 2) {
        calculateRouteWithUser(response.data.id);
      }
    } catch (error) {
      console.error("Login failed", error);
      setAuthError(error.response?.data || "Invalid email or password");
    } finally {
      setAuthLoading(false);
    }
  };

  const handleSignup = async (e) => {
    e.preventDefault();
    setAuthLoading(true);
    setAuthError("");
    try {
      const response = await axios.post('http://localhost:8080/api/v1/auth/signup', {
        name: authName,
        email: authEmail,
        password: authPassword,
        phone: authPhone
      });
      setUser(response.data);
      setAuthPassword("");
      setAuthName("");
      setAuthPhone("");
      setAuthError("");

      if (waypoints.length >= 2) {
        calculateRouteWithUser(response.data.id);
      }
    } catch (error) {
      console.error("Signup failed", error);
      setAuthError(error.response?.data || "Registration failed. Try again.");
    } finally {
      setAuthLoading(false);
    }
  };

  const handleLogout = () => {
    setUser(null);
    setAuthEmail('');
    setAlternativeRoutes([]);
  };

  const handlePreferenceChange = async (prefKey, value) => {
    if (!user) return;
    const updatedPreferences = {
      userId: user.id,
      avoidPm25: prefKey === 'avoidPm25' ? value : user.avoidPm25,
      avoidOzone: prefKey === 'avoidOzone' ? value : user.avoidOzone,
      avoidPm10: prefKey === 'avoidPm10' ? value : user.avoidPm10,
      avoidNo2: prefKey === 'avoidNo2' ? value : user.avoidNo2,
    };
    
    try {
      const response = await axios.post('http://localhost:8080/api/v1/auth/preferences', updatedPreferences);
      setUser(response.data);
      setPreferencesSaved(true);
      setTimeout(() => setPreferencesSaved(false), 2000);
      
      // Re-calculate the route on-the-fly to show the immediate effect of the preference!
      if (waypoints.length >= 2) {
        calculateRouteWithUser(response.data.id);
      }
    } catch (error) {
      console.error("Failed to update preferences", error);
    }
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

          {alternativeRoutes.map((route, idx) => {
            // route.pathCoordinates in [lon, lat], Leaflet needs [lat, lon]
            const latLngs = route.pathCoordinates.map(coord => [coord[1], coord[0]]);
            
            // Healthiest is index 0
            const isHealthiest = idx === 0;
            const color = isHealthiest ? "#10b981" : (idx === 1 ? "#94a3b8" : "#f43f5e");
            const weight = isHealthiest ? 6 : 4;
            const dashArray = isHealthiest ? null : "10, 10";
            
            return (
              <Polyline 
                key={idx}
                positions={latLngs} 
                color={color}
                weight={weight}
                dashArray={dashArray}
                className={isHealthiest ? "z-50 drop-shadow-lg animate-pulse" : "z-10 opacity-70"}
              />
            );
          })}
        </MapContainer>
        {/* Subtle Map Overlay Vignette */}
        <div className="absolute inset-0 pointer-events-none shadow-[inset_0_0_150px_rgba(15,23,42,0.9)] z-[400]" />
      </div>

      {/* Floating UI Overlay for Desktop / Bottom Sheet for Mobile */}
      <div className="absolute top-0 left-0 w-full lg:w-[400px] h-auto lg:h-full z-[500] p-4 lg:p-6 lg:pl-8 flex flex-col justify-end lg:justify-start pointer-events-none">
        
        <div className="pointer-events-auto flex flex-col gap-5 bg-slate-900/80 backdrop-blur-xl lg:bg-slate-900/90 p-5 lg:p-6 rounded-2xl lg:rounded-3xl shadow-2xl border border-slate-700/50 max-h-[92vh] overflow-y-auto">
          
          <div>
            <h1 className="text-4xl lg:text-5xl font-extrabold text-transparent bg-clip-text bg-gradient-to-r from-emerald-400 to-cyan-500 tracking-tight pb-1">
              ClearWay
            </h1>
            <p className="mt-1 text-slate-400 text-[12px] leading-relaxed">
              Discover the healthiest way to travel. We calculate real-time pollution exposure along your path.
            </p>
          </div>

          {/* User Session & Preference Panel */}
          <div className="bg-slate-800/60 rounded-xl p-4 border border-slate-600/30">
            {user ? (
              <div className="space-y-4">
                <div className="flex justify-between items-center">
                  <div>
                    <p className="text-[10px] text-slate-400">Logged in as</p>
                    <p className="font-bold text-emerald-400 text-sm">{user.name}</p>
                  </div>
                  <button 
                    onClick={handleLogout}
                    className="py-1 px-3 text-xs bg-slate-700 hover:bg-slate-600 text-slate-200 rounded-lg border border-slate-650 transition-colors"
                  >
                    Logout
                  </button>
                </div>

                {/* Preferences Section */}
                <div className="border-t border-slate-700/50 pt-3.5 space-y-2.5">
                  <h3 className="text-xs font-bold text-cyan-400 uppercase tracking-wider">Allergy Toggles</h3>
                  <p className="text-[11px] text-slate-400 leading-snug">
                    Penalizes route alternatives containing high amounts of selected triggers.
                  </p>
                  
                  <div className="grid grid-cols-2 gap-2 text-xs">
                    <label className="flex items-center gap-2 bg-slate-900/50 p-2 rounded-lg border border-slate-700/30 cursor-pointer hover:bg-slate-900/80 transition-colors">
                      <input 
                        type="checkbox" 
                        checked={user.avoidPm25} 
                        onChange={(e) => handlePreferenceChange('avoidPm25', e.target.checked)}
                        className="rounded border-slate-650 text-emerald-500 focus:ring-emerald-500 bg-slate-850 h-4 w-4" 
                      />
                      <span>PM2.5</span>
                    </label>

                    <label className="flex items-center gap-2 bg-slate-900/50 p-2 rounded-lg border border-slate-700/30 cursor-pointer hover:bg-slate-900/80 transition-colors">
                      <input 
                        type="checkbox" 
                        checked={user.avoidOzone} 
                        onChange={(e) => handlePreferenceChange('avoidOzone', e.target.checked)}
                        className="rounded border-slate-650 text-emerald-500 focus:ring-emerald-500 bg-slate-850 h-4 w-4" 
                      />
                      <span>Ozone</span>
                    </label>

                    <label className="flex items-center gap-2 bg-slate-900/50 p-2 rounded-lg border border-slate-700/30 cursor-pointer hover:bg-slate-900/80 transition-colors">
                      <input 
                        type="checkbox" 
                        checked={user.avoidPm10} 
                        onChange={(e) => handlePreferenceChange('avoidPm10', e.target.checked)}
                        className="rounded border-slate-650 text-emerald-500 focus:ring-emerald-500 bg-slate-850 h-4 w-4" 
                      />
                      <span>PM10</span>
                    </label>

                    <label className="flex items-center gap-2 bg-slate-900/50 p-2 rounded-lg border border-slate-700/30 cursor-pointer hover:bg-slate-900/80 transition-colors">
                      <input 
                        type="checkbox" 
                        checked={user.avoidNo2} 
                        onChange={(e) => handlePreferenceChange('avoidNo2', e.target.checked)}
                        className="rounded border-slate-650 text-emerald-500 focus:ring-emerald-500 bg-slate-850 h-4 w-4" 
                      />
                      <span>NO₂</span>
                    </label>
                  </div>
                  
                  {preferencesSaved && (
                    <div className="text-[10px] text-emerald-400 font-bold text-center animate-pulse pt-1">
                      ✓ Preferences updated & route prioritized
                    </div>
                  )}
                </div>
              </div>
            ) : (
              <div>
                <div className="flex border-b border-slate-700 mb-3 text-xs font-bold uppercase">
                  <button 
                    onClick={() => { setAuthMode('login'); setAuthError(''); }}
                    className={`flex-1 pb-2 border-b-2 text-center transition-all ${authMode === 'login' ? 'border-emerald-400 text-emerald-400' : 'border-transparent text-slate-450'}`}
                  >
                    Login
                  </button>
                  <button 
                    onClick={() => { setAuthMode('signup'); setAuthError(''); }}
                    className={`flex-1 pb-2 border-b-2 text-center transition-all ${authMode === 'signup' ? 'border-emerald-400 text-emerald-400' : 'border-transparent text-slate-450'}`}
                  >
                    Register
                  </button>
                </div>

                <form onSubmit={authMode === 'login' ? handleLogin : handleSignup} className="space-y-2.5">
                  {authMode === 'signup' && (
                    <>
                      <input 
                        type="text" 
                        placeholder="Full Name" 
                        required
                        value={authName}
                        onChange={(e) => setAuthName(e.target.value)}
                        className="w-full bg-slate-900 border border-slate-700 rounded-lg p-2 text-xs text-slate-200 focus:outline-none focus:border-emerald-500"
                      />
                      <input 
                        type="text" 
                        placeholder="Phone Number" 
                        value={authPhone}
                        onChange={(e) => setAuthPhone(e.target.value)}
                        className="w-full bg-slate-900 border border-slate-700 rounded-lg p-2 text-xs text-slate-200 focus:outline-none focus:border-emerald-500"
                      />
                    </>
                  )}
                  
                  <input 
                    type="email" 
                    placeholder="Email Address" 
                    required
                    value={authEmail}
                    onChange={(e) => setAuthEmail(e.target.value)}
                    className="w-full bg-slate-900 border border-slate-700 rounded-lg p-2 text-xs text-slate-200 focus:outline-none focus:border-emerald-500"
                  />
                  <input 
                    type="password" 
                    placeholder="Password" 
                    required
                    value={authPassword}
                    onChange={(e) => setAuthPassword(e.target.value)}
                    className="w-full bg-slate-900 border border-slate-700 rounded-lg p-2 text-xs text-slate-200 focus:outline-none focus:border-emerald-500"
                  />

                  {authError && (
                    <p className="text-[10px] text-rose-400 font-bold bg-rose-955/20 p-2 rounded border border-rose-900/30">
                      {authError}
                    </p>
                  )}

                  <button 
                    type="submit" 
                    disabled={authLoading}
                    className="w-full py-2 bg-gradient-to-r from-emerald-500 to-cyan-500 hover:from-emerald-400 hover:to-cyan-400 rounded-lg text-xs font-bold text-white transition-all shadow hover:shadow-cyan-500/20 active:scale-[0.98] disabled:opacity-50"
                  >
                    {authLoading ? 'Authenticating...' : (authMode === 'login' ? 'Login' : 'Create Account')}
                  </button>
                </form>
              </div>
            )}
          </div>

          {/* Plan Route Panel */}
          <div className="bg-slate-800/60 rounded-xl p-4 border border-slate-600/30">
            <h2 className="text-md font-semibold text-emerald-300 mb-3 flex items-center gap-2">
              <svg className="w-5 h-5 flex-shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M17.657 16.657L13.414 20.9a1.998 1.998 0 01-2.827 0l-4.244-4.243a8 8 0 1111.314 0z" /><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 11a3 3 0 11-6 0 3 3 0 016 0z" /></svg>
              Plan Your Route
            </h2>
            
            <ul className="text-xs text-slate-300 space-y-2.5 mb-4">
              <li className="flex items-center gap-3 bg-slate-900/50 p-2.5 rounded-lg border border-slate-700/50 transition-colors">
                <span className={`flex h-6 w-6 flex-shrink-0 items-center justify-center rounded-full text-xs font-bold shadow-inner ${waypoints.length >= 1 ? 'bg-emerald-500 text-slate-900 shadow-emerald-400/50' : 'bg-slate-700 text-slate-400'}`}>1</span>
                <div>
                  <p className="font-medium text-slate-200">Set Origin</p>
                  <p className="text-[10px] text-slate-400">Click the map to start</p>
                </div>
              </li>
              <li className="flex items-center gap-3 bg-slate-900/50 p-2.5 rounded-lg border border-slate-700/50 transition-colors">
                <span className={`flex h-6 w-6 flex-shrink-0 items-center justify-center rounded-full text-xs font-bold shadow-inner ${waypoints.length >= 2 ? 'bg-cyan-500 text-slate-900 shadow-cyan-400/50' : 'bg-slate-700 text-slate-400'}`}>2</span>
                <div>
                  <p className="font-medium text-slate-200">Set Destination</p>
                  <p className="text-[10px] text-slate-400">Click the map to end</p>
                </div>
              </li>
            </ul>

            <div className="flex flex-col gap-2">
              <button 
                onClick={calculateRoute} 
                disabled={waypoints.length < 2 || loading}
                className={`w-full py-3 px-4 rounded-xl font-bold transition-all duration-300 shadow-lg flex items-center justify-center gap-2 text-sm
                  ${waypoints.length < 2 || loading 
                    ? 'bg-slate-800 cursor-not-allowed text-slate-500 shadow-none border border-slate-750' 
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
                className="w-full py-2.5 px-4 rounded-xl font-semibold bg-transparent hover:bg-slate-800 text-slate-400 hover:text-slate-200 transition-colors border border-slate-700 hover:border-slate-650"
              >
                Reset Map
              </button>
            </div>
          </div>

          {/* Route Alternatives Score Result Box */}
          {alternativeRoutes.length > 0 && (
            <div className="p-4 rounded-xl bg-slate-800/80 border border-slate-700 mt-1 space-y-3 animate-in slide-in-from-bottom-4 fade-in duration-500">
              <h3 className="text-emerald-400 font-bold flex items-center gap-2 text-sm">
                <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z" /></svg>
                Route Alternatives
              </h3>
              <div className="space-y-2">
                {alternativeRoutes.map((route, idx) => (
                  <div key={idx} className={`p-3 rounded-lg flex justify-between items-center border ${idx === 0 ? 'bg-emerald-900/40 border-emerald-500/50 shadow-[0_0_15px_rgba(16,185,129,0.15)]' : 'bg-slate-900/50 border-slate-750'}`}>
                    <div>
                      <p className={`font-bold text-xs ${idx === 0 ? 'text-emerald-400' : 'text-slate-300'}`}>
                        {idx === 0 ? 'Healthiest Route' : `Alternative ${idx}`}
                      </p>
                      <p className="text-[10px] text-slate-450">
                        {Math.round(route.distanceMeters / 1000 * 10) / 10} km • {Math.round(route.travelTimeSeconds / 60)} mins
                      </p>
                    </div>
                    <div className="text-right flex flex-col items-end">
                      <p className={`text-xl font-black ${idx === 0 ? 'text-emerald-400' : 'text-slate-400'}`}>
                        {Math.round(route.pollutionScore)}
                      </p>
                      <p className="text-[9px] text-slate-500 uppercase tracking-widest font-bold">AQI</p>
                    </div>
                  </div>
                ))}
              </div>
            </div>
          )}

        </div>
      </div>
    </div>
  );
}

export default MapComponent;
