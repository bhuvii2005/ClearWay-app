import { Suspense } from 'react';
import { NavLink, Outlet, useLocation } from 'react-router-dom';
import Icon from './Icon';
import { PageLoader } from './ui';
import { useAlerts, useAuth } from '../context/contexts';

const NAV = [
  { to: '/', label: 'Plan', icon: 'map', end: true },
  { to: '/routes', label: 'My routes', icon: 'route' },
  { to: '/alerts', label: 'Alerts', icon: 'bell', badge: true },
  { to: '/account', label: 'Account', icon: 'user' },
];

export function Logo({ className = '' }) {
  return (
    <span className={`inline-flex items-center gap-2 font-extrabold tracking-tight text-slate-100 ${className}`}>
      <span className="flex h-7 w-7 items-center justify-center rounded-lg bg-emerald-500 text-slate-950">
        <Icon name="leaf" className="h-4 w-4" strokeWidth={2.5} />
      </span>
      ClearWay
    </span>
  );
}

export default function Layout() {
  const { user } = useAuth();
  const { unread, connected } = useAlerts();
  const { pathname } = useLocation();
  const fullBleed = pathname === '/';

  return (
    <div className="flex h-dvh flex-col bg-slate-950 text-slate-100">
      <header className="z-[1100] flex h-14 shrink-0 items-center justify-between gap-3 border-b border-slate-800 bg-slate-950/95 px-4 backdrop-blur">
        <NavLink to="/" aria-label="ClearWay home">
          <Logo />
        </NavLink>

        <nav aria-label="Main" className="flex items-center gap-1">
          {NAV.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              end={item.end}
              className={({ isActive }) =>
                `relative flex h-10 items-center gap-2 rounded-lg px-2.5 text-sm font-medium transition-colors sm:px-3 ${
                  isActive ? 'bg-slate-800 text-slate-100' : 'text-slate-400 hover:bg-slate-900 hover:text-slate-200'
                }`
              }
            >
              <Icon name={item.icon} className="h-5 w-5" />
              <span className="hidden md:inline">{item.label}</span>
              {item.badge && unread > 0 && (
                <span className="absolute right-0.5 top-0.5 min-w-[18px] rounded-full bg-rose-500 px-1 text-center text-[10px] font-bold leading-[18px] text-white md:static md:ml-0.5">
                  {unread > 99 ? '99+' : unread}
                  <span className="sr-only"> unread alerts</span>
                </span>
              )}
            </NavLink>
          ))}
        </nav>

        <div className="hidden items-center gap-2 text-xs text-slate-500 lg:flex" title={connected ? 'Live alerts connected' : 'Live alerts reconnecting'}>
          <span className={`h-2 w-2 rounded-full ${connected ? 'bg-emerald-400' : 'bg-slate-600'}`} aria-hidden="true" />
          <span className="max-w-40 truncate">{user?.name}</span>
        </div>
      </header>

      <main className={`min-h-0 flex-1 ${fullBleed ? 'overflow-hidden' : 'overflow-y-auto'}`}>
        <Suspense fallback={<PageLoader />}>
          <Outlet />
        </Suspense>
      </main>
    </div>
  );
}
