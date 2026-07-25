import React, { useState, useEffect } from 'react';
import { MapContainer, TileLayer, Marker, Polyline, useMapEvents, Popup } from 'react-leaflet';
import 'leaflet/dist/leaflet.css';
import L from 'leaflet';
import api from './api';

import AuthPanel from './components/AuthPanel';
import PreferencesPanel from './components/PreferencesPanel';
import RoutePanel from './components/RoutePanel';

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

  // Authentication State with localStorage persistence
  const [user, setUser] = useState(() => {
    const savedUser = localStorage.getItem('authUser');
    return savedUser ? JSON.parse(savedUser) : null;
  });
  const [authMode, setAuthMode] = useState('login'); // 'login' or 'signup'
  const [authEmail, setAuthEmail] = useState('');
  const [authPassword, setAuthPassword] = useState('');
  const [authName, setAuthName] = useState('');
  const [authPhone, setAuthPhone] = useState('');
  const [authError, setAuthError] = useState('');
  const [authLoading, setAuthLoading] = useState(false);
  const [preferencesSaved, setPreferencesSaved] = useState(false);

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
      const response = await api.post('/api/v1/routes/alternatives', {
        userId: user ? user.id : null,
        name: "My Custom Route",
        type: "walking",
        pathCoordinates: waypoints.map(wp => [wp[1], wp[0]])
      });

      setAlternativeRoutes(response.data);
    } catch (error) {
      console.error("Error creating route", error);
      alert("Error calculating routes. Make sure you are logged in!");
    } finally {
      setLoading(false);
    }
  };

  const calculateRouteWithUser = async (userId) => {
    if (waypoints.length < 2) return;
    setLoading(true);
    try {
      const response = await api.post('/api/v1/routes/alternatives', {
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
      const response = await api.post('/api/v1/auth/login', {
        email: authEmail,
        password: authPassword
      });
      
      const { token, user: userData } = response.data;
      if (token) {
        localStorage.setItem('authToken', token);
        localStorage.setItem('authUser', JSON.stringify(userData));
      }
      setUser(userData);
      setAuthPassword("");
      setAuthError("");
      
      if (waypoints.length >= 2) {
        calculateRouteWithUser(userData.id);
      }
    } catch (error) {
      console.error("Login failed", error);
      setAuthError(error.response?.data?.message || error.response?.data || "Invalid email or password");
    } finally {
      setAuthLoading(false);
    }
  };

  const handleSignup = async (e) => {
    e.preventDefault();
    setAuthLoading(true);
    setAuthError("");
    try {
      const response = await api.post('/api/v1/auth/signup', {
        name: authName,
        email: authEmail,
        password: authPassword,
        phone: authPhone
      });
      
      const { token, user: userData } = response.data;
      if (token) {
        localStorage.setItem('authToken', token);
        localStorage.setItem('authUser', JSON.stringify(userData));
      }
      setUser(userData);
      setAuthPassword("");
      setAuthName("");
      setAuthPhone("");
      setAuthError("");

      if (waypoints.length >= 2) {
        calculateRouteWithUser(userData.id);
      }
    } catch (error) {
      console.error("Signup failed", error);
      setAuthError(error.response?.data?.message || error.response?.data || "Registration failed. Try again.");
    } finally {
      setAuthLoading(false);
    }
  };

  const handleLogout = () => {
    localStorage.removeItem('authToken');
    localStorage.removeItem('authUser');
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
      const response = await api.post('/api/v1/auth/preferences', updatedPreferences);
      const updatedUser = response.data;
      localStorage.setItem('authUser', JSON.stringify(updatedUser));
      setUser(updatedUser);
      setPreferencesSaved(true);
      setTimeout(() => setPreferencesSaved(false), 2000);
      
      if (waypoints.length >= 2) {
        calculateRouteWithUser(updatedUser.id);
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
              <PreferencesPanel 
                user={user} 
                handleLogout={handleLogout} 
                handlePreferenceChange={handlePreferenceChange} 
                preferencesSaved={preferencesSaved} 
              />
            ) : (
              <AuthPanel 
                authMode={authMode} setAuthMode={setAuthMode}
                authName={authName} setAuthName={setAuthName}
                authEmail={authEmail} setAuthEmail={setAuthEmail}
                authPassword={authPassword} setAuthPassword={setAuthPassword}
                authPhone={authPhone} setAuthPhone={setAuthPhone}
                authError={authError} setAuthError={setAuthError}
                authLoading={authLoading}
                handleLogin={handleLogin} handleSignup={handleSignup}
              />
            )}
          </div>

          <RoutePanel 
            waypoints={waypoints}
            loading={loading}
            calculateRoute={calculateRoute}
            clearRoute={clearRoute}
            alternativeRoutes={alternativeRoutes}
          />

        </div>
      </div>
    </div>
  );
}

export default MapComponent;
