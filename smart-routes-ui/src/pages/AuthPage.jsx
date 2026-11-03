import { useState } from 'react';
import { Link } from 'react-router-dom';
import { useAuth } from '../context/contexts';
import { errorMessage } from '../lib/api';
import { Button, ErrorNote, TextField } from '../components/ui';
import { Logo } from '../components/Layout';
import Icon from '../components/Icon';

const POINTS = [
  { icon: 'route', text: 'Compare routes by the air you will actually breathe, not just by time.' },
  { icon: 'bell', text: 'Watch your regular routes and get an alert when the air turns poor.' },
  { icon: 'clock', text: 'See the cleanest hour of the day to make each trip.' },
];

export default function AuthPage({ mode }) {
  const isSignup = mode === 'signup';
  const { login, signup } = useAuth();
  const [form, setForm] = useState({ name: '', email: '', phone: '', password: '' });
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  const update = (field) => (e) => setForm((f) => ({ ...f, [field]: e.target.value }));

  async function submit(e) {
    e.preventDefault();
    setError('');
    if (isSignup && form.password.length < 8) {
      setError('Password must be at least 8 characters.');
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
      setError(errorMessage(err, isSignup ? 'Could not create your account.' : 'Incorrect email or password.'));
      setBusy(false);
    }
  }

  return (
    <div className="grid min-h-dvh bg-slate-950 text-slate-100 lg:grid-cols-[1fr_minmax(420px,520px)]">
      <aside className="relative hidden overflow-hidden border-r border-slate-800 bg-gradient-to-br from-emerald-950/60 via-slate-950 to-slate-950 p-12 lg:flex lg:flex-col lg:justify-between">
        <Logo className="text-xl" />
        <div className="max-w-md">
          <h1 className="text-4xl font-extrabold leading-tight tracking-tight">
            The cleanest way there, <span className="text-emerald-400">not just the fastest.</span>
          </h1>
          <ul className="mt-10 space-y-5">
            {POINTS.map((p) => (
              <li key={p.icon} className="flex gap-3 text-slate-300">
                <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg border border-emerald-500/30 bg-emerald-500/10 text-emerald-400">
                  <Icon name={p.icon} className="h-5 w-5" />
                </span>
                <span className="pt-1.5 text-[15px] leading-snug">{p.text}</span>
              </li>
            ))}
          </ul>
        </div>
        <p className="text-xs text-slate-500">Air-quality data: Open-Meteo · Routing: TomTom · Maps: OpenStreetMap</p>
      </aside>

      <div className="flex items-center justify-center px-4 py-12 sm:px-10">
        <div className="w-full max-w-sm">
          <Logo className="mb-10 text-xl lg:hidden" />
          <h2 className="text-2xl font-bold tracking-tight">{isSignup ? 'Create your account' : 'Welcome back'}</h2>
          <p className="mt-1.5 text-sm text-slate-400">
            {isSignup ? 'Start finding cleaner routes in under a minute.' : 'Sign in to plan routes and see your alerts.'}
          </p>

          <form onSubmit={submit} className="mt-8 space-y-4" noValidate={false}>
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
            <Button type="submit" loading={busy} className="w-full">
              {isSignup ? 'Create account' : 'Sign in'}
            </Button>
          </form>

          <p className="mt-6 text-center text-sm text-slate-400">
            {isSignup ? 'Already have an account? ' : 'New to ClearWay? '}
            <Link to={isSignup ? '/login' : '/signup'} className="font-semibold text-emerald-400 hover:text-emerald-300">
              {isSignup ? 'Sign in' : 'Create an account'}
            </Link>
          </p>
        </div>
      </div>
    </div>
  );
}
