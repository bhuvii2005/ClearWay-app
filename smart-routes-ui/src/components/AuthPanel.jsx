import React from 'react';

const AuthPanel = ({ 
    authMode, setAuthMode, 
    authName, setAuthName, 
    authEmail, setAuthEmail, 
    authPassword, setAuthPassword, 
    authPhone, setAuthPhone, 
    authError, setAuthError, 
    authLoading, 
    handleLogin, handleSignup 
}) => {
    return (
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
    );
};

export default AuthPanel;
