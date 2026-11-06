import { useEffect, useState } from 'react';
import { AnimatePresence, motion } from 'motion/react';
import { authApi, errorMessage } from '../lib/api';
import { POLLUTANTS } from '../lib/format';
import { useAuth, useToast } from '../context/contexts';
import { Button, ErrorNote, PageHeader, TextField, Toggle } from '../components/ui';
import Icon from '../components/Icon';

const POLLUTANT_NOTES = {
  avoidPm25: 'Fine particles from traffic and burning; the most harmful for lungs and heart.',
  avoidPm10: 'Coarser dust and pollen-sized particles.',
  avoidNo2: 'Mostly from vehicle exhaust; irritates airways and worsens asthma.',
  avoidOzone: 'Forms on sunny afternoons; can trigger breathing problems.',
};

const initials = (name = '') =>
  name
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((w) => w[0].toUpperCase())
    .join('') || '?';

export default function AccountPage() {
  const { user } = useAuth();
  return (
    <div className="mx-auto max-w-4xl px-4 py-8 sm:px-6 sm:py-10">
      <PageHeader title="Account" subtitle="Your profile, health settings and security." />
      <div className="mt-8 flex items-center gap-4 rounded-3xl bg-surface p-5 shadow-card ring-1 ring-hairline">
        <span className="grid h-14 w-14 shrink-0 place-items-center rounded-full bg-accent/15 font-display text-xl font-bold text-accent-ink">
          {initials(user.name)}
        </span>
        <div className="min-w-0">
          <p className="truncate text-lg font-semibold text-ink">{user.name}</p>
          <p className="truncate text-sm text-muted">{user.email}</p>
        </div>
      </div>
      <div className="mt-10 space-y-10">
        <ProfileSection />
        <SensitivitySection />
        <PasswordSection />
        <SessionSection />
      </div>
    </div>
  );
}

/** Settings row: title and description on the left, the controls in a card on the right. */
function Section({ title, description, children }) {
  return (
    <section className="grid gap-4 md:grid-cols-[220px_minmax(0,1fr)] md:gap-8">
      <div>
        <h2 className="text-base font-semibold text-ink">{title}</h2>
        {description && <p className="mt-1 text-sm leading-snug text-muted">{description}</p>}
      </div>
      <div className="rounded-3xl bg-surface p-5 shadow-card ring-1 ring-hairline sm:p-6">{children}</div>
    </section>
  );
}

/** A small "Saved" tick that appears after a save, then fades away. */
function SavedTick({ show, onDone }) {
  useEffect(() => {
    if (!show) return undefined;
    const t = setTimeout(onDone, 2000);
    return () => clearTimeout(t);
  }, [show, onDone]);
  return (
    <AnimatePresence>
      {show && (
        <motion.span
          initial={{ opacity: 0, x: -4 }}
          animate={{ opacity: 1, x: 0 }}
          exit={{ opacity: 0 }}
          className="inline-flex items-center gap-1 text-sm font-medium text-good"
          role="status"
        >
          <Icon name="check" className="h-4 w-4" /> Saved
        </motion.span>
      )}
    </AnimatePresence>
  );
}

function ProfileSection() {
  const { user, setUser } = useAuth();
  const [saved, setSaved] = useState(false);
  const [name, setName] = useState(user.name ?? '');
  const [phone, setPhone] = useState(user.phone ?? '');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const changed = name.trim() !== (user.name ?? '') || phone.trim() !== (user.phone ?? '');

  async function save(e) {
    e.preventDefault();
    setBusy(true);
    setError('');
    try {
      const updated = await authApi.updateProfile({ name: name.trim(), phone: phone.trim() });
      setUser(updated);
      setSaved(true);
    } catch (err) {
      setError(errorMessage(err, 'Could not update your profile.'));
    } finally {
      setBusy(false);
    }
  }

  return (
    <Section title="Profile" description="How you appear in ClearWay.">
      <form onSubmit={save} className="space-y-4">
        <div className="grid gap-4 sm:grid-cols-2">
          <TextField label="Name" required maxLength={100} value={name} onChange={(e) => setName(e.target.value)} />
          <TextField label="Phone" type="tel" maxLength={30} value={phone} onChange={(e) => setPhone(e.target.value)} />
        </div>
        <TextField label="Email" value={user.email} disabled hint="Your email is your sign-in and cannot be changed." />
        <ErrorNote>{error}</ErrorNote>
        <div className="flex items-center gap-3">
          <Button type="submit" loading={busy} disabled={!changed || !name.trim()}>
            Save changes
          </Button>
          <SavedTick show={saved} onDone={() => setSaved(false)} />
        </div>
      </form>
    </Section>
  );
}

function SensitivitySection() {
  const { user, setUser } = useAuth();
  const { push } = useToast();
  const [saving, setSaving] = useState(null);

  async function toggle(pref, on) {
    setSaving(pref);
    const next = Object.fromEntries(POLLUTANTS.map((p) => [p.pref, p.pref === pref ? on : Boolean(user[p.pref])]));
    try {
      setUser(await authApi.updatePreferences(next));
    } catch (err) {
      push({ tone: 'error', title: 'Could not save your sensitivity', body: errorMessage(err) });
    } finally {
      setSaving(null);
    }
  }

  return (
    <Section title="Sensitivities" description="Pick what affects you. Routes with more of it rank lower and score worse.">
      <div className="-my-2 divide-y divide-line">
        {POLLUTANTS.map((p) => (
          <div key={p.pref} className="py-2.5">
            <Toggle
              checked={Boolean(user[p.pref])}
              disabled={saving !== null}
              onChange={(on) => toggle(p.pref, on)}
              label={p.label}
              description={POLLUTANT_NOTES[p.pref]}
            />
          </div>
        ))}
      </div>
    </Section>
  );
}

function PasswordSection() {
  const { changePassword } = useAuth();
  const { push } = useToast();
  const [form, setForm] = useState({ current: '', next: '', confirm: '' });
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const update = (field) => (e) => setForm((f) => ({ ...f, [field]: e.target.value }));

  async function submit(e) {
    e.preventDefault();
    setError('');
    if (form.next.length < 8) {
      setError('The new password must be at least 8 characters.');
      return;
    }
    if (form.next !== form.confirm) {
      setError('The new passwords do not match.');
      return;
    }
    setBusy(true);
    try {
      await changePassword(form.current, form.next);
      setForm({ current: '', next: '', confirm: '' });
      push({ tone: 'success', title: 'Password changed', body: 'Other devices have been signed out.' });
    } catch (err) {
      setError(errorMessage(err, 'Could not change your password.'));
    } finally {
      setBusy(false);
    }
  }

  return (
    <Section title="Password" description="Changing it signs you out on every other device.">
      <form onSubmit={submit} className="space-y-4">
        <TextField
          label="Current password"
          type="password"
          required
          autoComplete="current-password"
          value={form.current}
          onChange={update('current')}
        />
        <div className="grid gap-4 sm:grid-cols-2">
          <TextField
            label="New password"
            type="password"
            required
            minLength={8}
            autoComplete="new-password"
            hint="At least 8 characters."
            value={form.next}
            onChange={update('next')}
          />
          <TextField
            label="Confirm new password"
            type="password"
            required
            autoComplete="new-password"
            value={form.confirm}
            onChange={update('confirm')}
          />
        </div>
        <ErrorNote>{error}</ErrorNote>
        <Button type="submit" loading={busy} disabled={!form.current || !form.next}>
          Change password
        </Button>
      </form>
    </Section>
  );
}

function SessionSection() {
  const { logout, deleteAccount, user } = useAuth();
  const { push } = useToast();
  const [signingOut, setSigningOut] = useState(false);
  const [confirmText, setConfirmText] = useState('');
  const [deleting, setDeleting] = useState(false);
  const [showDelete, setShowDelete] = useState(false);

  async function signOut() {
    setSigningOut(true);
    await logout();
  }

  async function destroy(e) {
    e.preventDefault();
    setDeleting(true);
    try {
      await deleteAccount();
      push({ tone: 'info', title: 'Your account has been deleted' });
    } catch (err) {
      push({ tone: 'error', title: 'Could not delete your account', body: errorMessage(err) });
      setDeleting(false);
    }
  }

  return (
    <Section title="Session" description="Signing out ends your session on every device.">
      <Button variant="secondary" icon="logout" loading={signingOut} onClick={signOut}>
        Sign out everywhere
      </Button>

      <div className="mt-6 border-t border-line pt-5">
        <h3 className="text-sm font-semibold text-danger">Delete account</h3>
        <p className="mt-1 text-sm text-muted">Permanently removes your account, saved routes, their history and your alerts. This cannot be undone.</p>
        <AnimatePresence initial={false} mode="wait">
          {showDelete ? (
          <motion.form
            key="confirm"
            initial={{ opacity: 0, height: 0 }}
            animate={{ opacity: 1, height: 'auto' }}
            exit={{ opacity: 0, height: 0 }}
            onSubmit={destroy}
            className="space-y-3 overflow-hidden pt-4"
          >
            <TextField
              label={`Type DELETE to confirm deleting ${user.email}`}
              value={confirmText}
              autoComplete="off"
              onChange={(e) => setConfirmText(e.target.value)}
            />
            <div className="flex gap-2">
              <Button type="submit" variant="danger" loading={deleting} disabled={confirmText !== 'DELETE'}>
                Delete my account
              </Button>
              <Button
                variant="ghost"
                onClick={() => {
                  setShowDelete(false);
                  setConfirmText('');
                }}
              >
                Cancel
              </Button>
            </div>
          </motion.form>
        ) : (
          <motion.div key="ask" initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }}>
            <Button variant="dangerGhost" className="-ml-3 mt-3" onClick={() => setShowDelete(true)}>
              Delete account…
            </Button>
          </motion.div>
        )}
        </AnimatePresence>
      </div>
    </Section>
  );
}
