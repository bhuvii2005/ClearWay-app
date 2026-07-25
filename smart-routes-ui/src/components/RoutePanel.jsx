import React from 'react';

const RoutePanel = ({ waypoints, loading, calculateRoute, clearRoute, alternativeRoutes }) => {
    return (
        <>
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
                        {/* New PollutionData Breakdown from Phase 5 Task 11 */}
                        {route.pollutionData && (
                            <div className="mt-1 flex gap-2 text-[8px] uppercase font-bold text-slate-400 opacity-80">
                                <span>PM2.5: {route.pollutionData.pm25}</span>
                                <span>NO2: {route.pollutionData.no2}</span>
                                <span>O3: {route.pollutionData.ozone}</span>
                            </div>
                        )}
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
        </>
    );
};

export default RoutePanel;
