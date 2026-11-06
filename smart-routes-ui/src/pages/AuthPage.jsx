import { useState } from 'react';
import { Link } from 'react-router-dom';
import { motion } from 'motion/react';
import { useAuth } from '../context/contexts';
import { errorMessage } from '../lib/api';
import { Button, ErrorNote, TextField } from '../components/ui';
import { Logo } from '../components/Layout';
import Icon from '../components/Icon';
import ThemeSwitch from '../components/ThemeSwitch';

const POINTS = [
  { icon: 'route', title: 'Compare by the air', text: 'Every route ranked by what you will actually breathe.' },
  { icon: 'bell', title: 'Watch your commute', text: 'An alert when the air on a saved route turns poor.' },
  { icon: 'clock', title: 'Pick the cleanest hour', text: 'See when each trip is at its cleanest.' },
];

const ease = [0.22, 1, 0.36, 1];

export default function AuthPage({ mode }) {
  const isSignup = mode === 'signup';
  const { login, signup } = useAuth();
  const [form, setForm] = useState({ name: '', email: '', phone: '', password: '' });
  const [error, setError] = useState('');
  const [attempt, setAttempt] = useState(0);
  const [busy, setBusy] = useState(false);

  const update = (field) => (e) => setForm((f) => ({ ...f, [field]: e.target.value }));
  const fail = (message) => {
    setError(message);
    setAttempt((n) => n + 1);
  };

  async function submit(e) {
    e.preventDefault();
    setError('');
    if (isSignup && form.password.length < 8) {
      fail('Password must be at least 8 characters.');
      return;
    }
    setBusy(true);
    try {
      if (isSignup) {
        await signup({
          name: form.name.trim(),
          email: form.email.trim(),
          password: form.password,
          phone: form.phone.trim() || null,
        });
      } else {
        await login(form.email.trim(), form.password);
      }
    } catch (err) {
      fail(errorMessage(err, isSignup ? 'Could not create your account.' : 'Incorrect email or password.'));
      setBusy(false);
    }
  }

  return (
    <div className="grid min-h-dvh bg-canvas text-ink lg:grid-cols-[minmax(0,1.1fr)_minmax(440px,1fr)]">
      <aside className="relative hidden overflow-hidden bg-surface-2/60 p-12 lg:flex lg:flex-col lg:justify-between">
        <div
          className="pointer-events-none absolute inset-0 opacity-60"
          style={{ background: 'radial-gradient(60% 50% at 30% 20%, color-mix(in srgb, var(--color-accent) 14%, transparent), transparent 70%)' }}
          aria-hidden="true"
        />
        <Logo className="relative text-xl" />

        <div className="relative mx-auto w-full max-w-lg">
          <motion.h1
            initial={{ opacity: 0, y: 12 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.6, ease }}
            className="font-display text-[40px] font-bold leading-[1.08] tracking-tight"
          >
            The cleanest way there,
            <br />
            <span className="text-accent-ink">not just the fastest.</span>
          </motion.h1>
          <RouteIllustration />
          <ul className="mt-8 grid grid-cols-3 gap-4">
            {POINTS.map((p, i) => (
              <motion.li
                key={p.icon}
                initial={{ opacity: 0, y: 10 }}
                animate={{ opacity: 1, y: 0 }}
                transition={{ delay: 0.9 + i * 0.1, duration: 0.5, ease }}
              >
                <span className="flex h-9 w-9 items-center justify-center rounded-xl bg-surface text-accent-ink shadow-card">
                  <Icon name={p.icon} className="h-[18px] w-[18px]" />
                </span>
                <p className="mt-3 text-sm font-semibold text-ink">{p.title}</p>
                <p className="mt-0.5 text-[13px] leading-snug text-muted">{p.text}</p>
              </motion.li>
            ))}
          </ul>
        </div>

        <p className="relative text-xs text-faint">Air quality by Open-Meteo · Routing by TomTom · Maps by OpenFreeMap and OpenStreetMap</p>
      </aside>

      <main className="relative flex items-center justify-center px-5 py-12 sm:px-10">
        <div className="absolute right-4 top-4">
          <ThemeSwitch />
        </div>
        <motion.div
          initial={{ opacity: 0, y: 14 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.5, ease }}
          className="w-full max-w-[380px]"
        >
          <Logo className="mb-10 text-xl lg:hidden" />
          <h2 className="font-display text-[28px] font-bold leading-tight tracking-tight">
            {isSignup ? 'Create your account' : 'Welcome back'}
          </h2>
          <p className="mt-1.5 text-sm text-muted">
            {isSignup ? 'Start finding cleaner routes in under a minute.' : 'Sign in to plan routes and see your alerts.'}
          </p>

          <motion.form
            key={attempt}
            onSubmit={submit}
            animate={attempt ? { x: [0, -8, 8, -5, 5, 0] } : undefined}
            transition={{ duration: 0.4 }}
            className="mt-8 space-y-4"
          >
            {isSignup && (
              <TextField label="Name" required maxLength={100} autoComplete="name" value={form.name} onChange={update('name')} />
            )}
            <TextField label="Email" type="email" required autoComplete="email" value={form.email} onChange={update('email')} />
            {isSignup && (
              <TextField
                label="Phone (optional)"
                type="tel"
                maxLength={30}
                autoComplete="tel"
                value={form.phone}
                onChange={update('phone')}
              />
            )}
            <TextField
              label="Password"
              type="password"
              required
              minLength={isSignup ? 8 : undefined}
              autoComplete={isSignup ? 'new-password' : 'current-password'}
              hint={isSignup ? 'At least 8 characters.' : undefined}
              value={form.password}
              onChange={update('password')}
            />
            <ErrorNote>{error}</ErrorNote>
            <Button type="submit" loading={busy} className="h-12 w-full text-[15px]">
              {isSignup ? 'Create account' : 'Sign in'}
            </Button>
          </motion.form>

          <p className="mt-8 text-center text-sm text-muted">
            {isSignup ? 'Already have an account? ' : 'New to ClearWay? '}
            <Link to={isSignup ? '/login' : '/signup'} className="font-semibold text-accent-ink hover:text-accent-hover">
              {isSignup ? 'Sign in' : 'Create an account'}
            </Link>
          </p>
        </motion.div>
      </main>
    </div>
  );
}

// The two routes in the illustration: a fast one through a haze of traffic, a clean one past a park.
const FAST = 'M70 250 C 150 250, 190 150, 260 150 S 380 120, 430 70';
const CLEAN = 'M70 250 C 110 300, 200 300, 250 260 S 330 180, 360 150 S 410 90, 430 70';

/** A small animated map: the clean route draws itself, a dot travels it, and the trade-off pops up. */
function RouteIllustration() {
  return (
    <div className="relative mt-8 overflow-hidden rounded-3xl bg-surface shadow-float">
      <svg viewBox="0 0 500 330" className="block w-full" aria-hidden="true">
        {/* streets */}
        <g className="stroke-line" strokeWidth="10" strokeLinecap="round" fill="none">
          <path d="M0 110 H500" />
          <path d="M0 205 H500" />
          <path d="M120 0 V330" />
          <path d="M320 0 V330" />
          <path d="M0 300 L500 20" strokeWidth="6" />
        </g>
        {/* park and haze */}
        <rect x="180" y="225" width="120" height="80" rx="22" className="fill-good/15" />
        <circle cx="240" cy="150" r="62" className="fill-haze/20" />
        <circle cx="240" cy="150" r="34" className="fill-haze/20" />

        <motion.path
          d={FAST}
          fill="none"
          strokeWidth="5"
          strokeLinecap="round"
          strokeDasharray="2 10"
          className="stroke-faint"
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          transition={{ duration: 0.8, ease }}
        />
        <motion.path
          d={CLEAN}
          fill="none"
          strokeWidth="11"
          strokeLinecap="round"
          className="stroke-surface"
          initial={{ pathLength: 0 }}
          animate={{ pathLength: 1 }}
          transition={{ delay: 0.4, duration: 1.4, ease }}
        />
        <motion.path
          d={CLEAN}
          fill="none"
          strokeWidth="6"
          strokeLinecap="round"
          className="stroke-accent"
          initial={{ pathLength: 0 }}
          animate={{ pathLength: 1 }}
          transition={{ delay: 0.4, duration: 1.4, ease }}
        />
        <circle r="6" className="route-runner fill-surface stroke-accent" strokeWidth="3" style={{ offsetPath: `path('${CLEAN}')` }} />

        <circle cx="70" cy="250" r="9" className="fill-accent stroke-surface" strokeWidth="3" />
        <circle cx="430" cy="70" r="9" className="fill-haze stroke-surface" strokeWidth="3" />
      </svg>

      <motion.div
        initial={{ opacity: 0, y: 8, scale: 0.95 }}
        animate={{ opacity: 1, y: 0, scale: 1 }}
        transition={{ delay: 1.6, type: 'spring', stiffness: 300, damping: 22 }}
        className="absolute bottom-4 right-4 rounded-2xl bg-surface/95 px-3.5 py-2.5 shadow-pop backdrop-blur"
      >
        <p className="flex items-center gap-1.5 text-sm font-semibold text-good">
          <Icon name="leaf" className="h-4 w-4" /> 38% less PM2.5
        </p>
        <p className="mt-0.5 text-xs text-muted">+3 min vs the fastest route</p>
      </motion.div>
      <motion.span
        initial={{ opacity: 0 }}
        animate={{ opacity: 1 }}
        transition={{ delay: 1.2 }}
        className="absolute left-[40%] top-[30%] rounded-full bg-surface/90 px-2 py-0.5 text-[11px] font-medium text-warning shadow-card"
      >
        Traffic haze
      </motion.span>
    </div>
  );
}
