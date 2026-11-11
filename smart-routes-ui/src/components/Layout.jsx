import { Suspense, useEffect, useRef, useState } from 'react';
import { NavLink, Outlet, useLocation, useNavigate } from 'react-router-dom';
import { AnimatePresence, motion } from 'motion/react';
import Icon from './Icon';
import { PageLoader } from './ui';
import ThemeSwitch from './ThemeSwitch';
import { useAlerts, useAuth } from '../context/contexts';

const NAV = [
  { to: '/', label: 'Plan', icon: 'map', end: true },
  { to: '/routes', label: 'My routes', icon: 'route' },
  { to: '/coach', label: 'Coach', icon: 'chat' },
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
  const { unread } = useAlerts();
  const { pathname } = useLocation();
  const fullBleed = pathname === '/';

  return (
    <div className="flex h-dvh flex-col bg-canvas text-ink">
      <header className="relative z-[1100] flex h-14 shrink-0 items-center justify-between gap-3 bg-surface/80 px-3 shadow-[0_1px_0_var(--color-hairline)] backdrop-blur-xl sm:px-4">
        <NavLink to="/" aria-label="ClearWay home" className="rounded-lg focus-visible:outline-2 focus-visible:outline-accent">
          <Logo wordClassName="hidden sm:inline" />
        </NavLink>

        <nav aria-label="Main" className="flex items-center gap-0.5 rounded-xl bg-surface-2/70 p-1">
          {NAV.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              end={item.end}
              className={({ isActive }) =>
                `relative flex h-8 items-center gap-1.5 rounded-lg px-2.5 text-[13px] font-medium transition-colors focus-visible:outline-2 focus-visible:outline-accent sm:px-3 ${
                  isActive ? 'text-ink' : 'text-muted hover:text-ink'
                }`
              }
            >
              {({ isActive }) => (
                <>
                  {isActive && (
                    <motion.span
                      layoutId="nav-pill"
                      className="absolute inset-0 rounded-lg bg-surface shadow-card"
                      transition={{ type: 'spring', stiffness: 500, damping: 38 }}
                    />
                  )}
                  <Icon name={item.icon} className="relative h-[18px] w-[18px]" />
                  <span className="relative hidden md:inline">{item.label}</span>
                  {item.badge && unread > 0 && (
                    <motion.span
                      key={unread}
                      initial={{ scale: 0.4 }}
                      animate={{ scale: 1 }}
                      className="absolute -right-0.5 -top-0.5 min-w-[17px] rounded-full bg-danger-solid px-1 text-center text-[10px] font-bold leading-[17px] text-white ring-2 ring-surface md:static md:ml-0.5 md:ring-0"
                    >
                      {unread > 99 ? '99+' : unread}
                      <span className="sr-only"> unread alerts</span>
                    </motion.span>
                  )}
                </>
              )}
            </NavLink>
          ))}
        </nav>

        <div className="flex items-center gap-2">
          <ThemeSwitch />
          <UserMenu />
        </div>
      </header>

      <main className={`min-h-0 flex-1 ${fullBleed ? 'overflow-hidden' : 'overflow-y-auto'}`}>
        <Suspense fallback={<PageLoader />}>
          <motion.div
            key={pathname}
            initial={{ opacity: 0, y: 6 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.28, ease: [0.22, 1, 0.36, 1] }}
            className="h-full"
          >
            <Outlet />
          </motion.div>
        </Suspense>
      </main>
    </div>
  );
}

const initials = (name = '') =>
  name
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((w) => w[0].toUpperCase())
    .join('') || '?';

/** Avatar button with a small menu: who is signed in, live-alert status, account and sign out. */
function UserMenu() {
  const { user, logout } = useAuth();
  const { connected } = useAlerts();
  const [open, setOpen] = useState(false);
  const ref = useRef(null);
  const navigate = useNavigate();

  useEffect(() => {
    if (!open) return undefined;
    const close = (e) => {
      if (e.type === 'keydown' ? e.key === 'Escape' : !ref.current?.contains(e.target)) setOpen(false);
    };
    document.addEventListener('pointerdown', close);
    document.addEventListener('keydown', close);
    return () => {
      document.removeEventListener('pointerdown', close);
      document.removeEventListener('keydown', close);
    };
  }, [open]);

  return (
    <div ref={ref} className="relative">
      <button
        type="button"
        aria-haspopup="menu"
        aria-expanded={open}
        aria-label="Account menu"
        onClick={() => setOpen((o) => !o)}
        className="relative grid h-9 w-9 place-items-center rounded-full bg-accent/15 text-[13px] font-semibold text-accent-ink transition-transform hover:scale-105 active:scale-95 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent"
      >
        {initials(user?.name)}
        <span
          className={`absolute bottom-0 right-0 h-2.5 w-2.5 rounded-full ring-2 ring-surface ${connected ? 'bg-good' : 'bg-line-strong'}`}
          aria-hidden="true"
        />
      </button>
      <AnimatePresence>
        {open && (
          <motion.div
            role="menu"
            initial={{ opacity: 0, scale: 0.96, y: -4 }}
            animate={{ opacity: 1, scale: 1, y: 0 }}
            exit={{ opacity: 0, scale: 0.96, y: -4, transition: { duration: 0.12 } }}
            style={{ transformOrigin: 'top right' }}
            className="absolute right-0 top-11 w-64 rounded-2xl bg-surface p-1.5 shadow-pop"
          >
            <div className="px-3 pb-2.5 pt-2">
              <p className="truncate text-sm font-semibold text-ink">{user?.name}</p>
              <p className="truncate text-xs text-muted">{user?.email}</p>
              <p className="mt-2 flex items-center gap-1.5 text-xs text-muted">
                <span className={`h-1.5 w-1.5 rounded-full ${connected ? 'bg-good' : 'bg-line-strong'}`} aria-hidden="true" />
                {connected ? 'Live alerts on' : 'Reconnecting live alerts…'}
              </p>
            </div>
            <div className="my-1 h-px bg-line" />
            <MenuItem icon="user" onClick={() => { setOpen(false); navigate('/account'); }}>
              Account settings
            </MenuItem>
            <MenuItem icon="logout" onClick={() => { setOpen(false); logout(); }}>
              Sign out
            </MenuItem>
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  );
}

function MenuItem({ icon, children, onClick }) {
  return (
    <button
      type="button"
      role="menuitem"
      onClick={onClick}
      className="flex w-full items-center gap-2.5 rounded-lg px-3 py-2 text-left text-sm text-ink-2 transition-colors hover:bg-surface-2 hover:text-ink"
    >
      <Icon name={icon} className="h-4 w-4 text-muted" />
      {children}
    </button>
  );
}
