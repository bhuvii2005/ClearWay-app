import { Suspense } from 'react';
import { NavLink, Outlet, useLocation } from 'react-router-dom';
import Icon from './Icon';
import { PageLoader } from './ui';
import ThemeSwitch from './ThemeSwitch';
import { useAlerts, useAuth } from '../context/contexts';

const NAV = [
  { to: '/', label: 'Plan', icon: 'map', end: true },
  { to: '/routes', label: 'My routes', icon: 'route' },
  { to: '/alerts', label: 'Alerts', icon: 'bell', badge: true },
  { to: '/account', label: 'Account', icon: 'user' },
];

/** The ClearWay mark: a route winding through clear air, from start (ink) to destination (haze). */
export function LogoMark({ className = 'h-7 w-7' }) {
  return (
    <svg viewBox="0 0 32 32" className={className} aria-hidden="true">
      <circle cx="16" cy="16" r="15" className="fill-accent/15" />
      <path d="M7 21c4-1 5-9 9-10s6 5 9 4" fill="none" strokeWidth="3" strokeLinecap="round" className="stroke-accent" />
      <circle cx="7" cy="21" r="2.6" className="fill-ink" />
      <circle cx="25" cy="15" r="2.6" className="fill-haze" />
    </svg>
  );
}

/** Mark plus "ClearWay". `wordClassName` can hide the word on narrow screens. */
export function Logo({ className = '', wordClassName = '' }) {
  return (
    <span className={`inline-flex items-center gap-2 font-display text-lg font-extrabold tracking-tight text-ink ${className}`}>
      <LogoMark />
      <span className={wordClassName}>ClearWay</span>
    </span>
  );
}

export default function Layout() {
  const { user } = useAuth();
  const { unread, connected } = useAlerts();
  const { pathname } = useLocation();
  const fullBleed = pathname === '/';

  return (
    <div className="flex h-dvh flex-col bg-canvas text-ink">
      <header className="z-[1100] flex h-14 shrink-0 items-center justify-between gap-3 border-b border-line bg-surface/90 px-4 backdrop-blur">
        <NavLink to="/" aria-label="ClearWay home">
          <Logo wordClassName="hidden sm:inline" />
        </NavLink>

        <nav aria-label="Main" className="flex items-center gap-1">
          {NAV.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              end={item.end}
              className={({ isActive }) =>
                `relative flex h-10 items-center gap-2 rounded-lg px-2.5 text-sm font-medium transition-colors sm:px-3 ${
                  isActive ? 'bg-surface-2 text-ink' : 'text-muted hover:bg-surface hover:text-ink'
                }`
              }
            >
              <Icon name={item.icon} className="h-5 w-5" />
              <span className="hidden md:inline">{item.label}</span>
              {item.badge && unread > 0 && (
                <span className="absolute right-0.5 top-0.5 min-w-[18px] rounded-full bg-danger-solid px-1 text-center text-[10px] font-bold leading-[18px] text-white md:static md:ml-0.5">
                  {unread > 99 ? '99+' : unread}
                  <span className="sr-only"> unread alerts</span>
                </span>
              )}
            </NavLink>
          ))}
        </nav>

        <div className="flex items-center gap-3">
          <div className="hidden items-center gap-2 text-xs text-faint lg:flex" title={connected ? 'Live alerts connected' : 'Live alerts reconnecting'}>
            <span className={`h-2 w-2 rounded-full ${connected ? 'bg-good' : 'bg-line-strong'}`} aria-hidden="true" />
            <span className="max-w-40 truncate">{user?.name}</span>
          </div>
          <ThemeSwitch />
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
