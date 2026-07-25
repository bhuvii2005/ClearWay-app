import React from 'react';

const PreferencesPanel = ({ user, handleLogout, handlePreferenceChange, preferencesSaved }) => {
    return (
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
    );
};

export default PreferencesPanel;
