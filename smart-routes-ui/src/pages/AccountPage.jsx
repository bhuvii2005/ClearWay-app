import { useState } from 'react';
import { authApi, errorMessage } from '../lib/api';
import { POLLUTANTS } from '../lib/format';
import { useAuth, useToast } from '../context/contexts';
import { Button, Card, ErrorNote, TextField, Toggle } from '../components/ui';

const POLLUTANT_NOTES = {
  avoidPm25: 'Fine particles from traffic and burning; the most harmful for lungs and heart.',
  avoidPm10: 'Coarser dust and pollen-sized particles.',
  avoidNo2: 'Mostly from vehicle exhaust; irritates airways and worsens asthma.',
  avoidOzone: 'Forms on sunny afternoons; can trigger breathing problems.',
};

export default function AccountPage() {
  return (
    <div className="mx-auto max-w-3xl space-y-6 px-4 py-8 sm:px-6">
      <h1 className="text-2xl font-bold tracking-tight">Account</h1>
      <ProfileSection />
      <SensitivitySection />
      <PasswordSection />
      <SessionSection />
    </div>
  );
}

function SectionHeader({ title, children }) {
  return (
    <div className="mb-4">
      <h2 className="text-base font-semibold text-slate-100">{title}</h2>
      {children && <p className="mt-1 text-sm text-slate-400">{children}</p>}
    </div>
  );
}

function ProfileSection() {
  const { user, setUser } = useAuth();
  const { push } = useToast();
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
      push({ tone: 'success', title: 'Profile updated' });
    } catch (err) {
      setError(errorMessage(err, 'Could not update your profile.'));
    } finally {
      setBusy(false);
    }
  }

  return (
    <Card>
      <SectionHeader title="Profile" />
      <form onSubmit={save} className="space-y-4">
        <div className="grid gap-4 sm:grid-cols-2">
          <TextField label="Name" required maxLength={100} value={name} onChange={(e) => setName(e.target.value)} />
          <TextField label="Phone" type="tel" maxLength={30} value={phone} onChange={(e) => setPhone(e.target.value)} />
        </div>
        <TextField label="Email" value={user.email} disabled hint="Your email is your sign-in and cannot be changed." />
        <ErrorNote>{error}</ErrorNote>
        <Button type="submit" loading={busy} disabled={!changed || !name.trim()}>
          Save changes
        </Button>
      </form>
    </Card>
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
    <Card>
      <SectionHeader title="Pollutant sensitivities">
        Routes with more of the pollutants you pick here are ranked lower, and their scores count those pollutants more heavily.
      </SectionHeader>
      <div className="divide-y divide-slate-800">
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
    </Card>
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
    <Card>
      <SectionHeader title="Change password">Changing your password signs you out on every other device.</SectionHeader>
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
    </Card>
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
    <Card>
      <SectionHeader title="Sign out">Signing out ends your session on every device where you are signed in.</SectionHeader>
      <Button variant="secondary" icon="logout" loading={signingOut} onClick={signOut}>
        Sign out everywhere
      </Button>

      <div className="mt-6 border-t border-slate-800 pt-5">
        <h3 className="text-sm font-semibold text-rose-300">Delete account</h3>
        <p className="mt-1 text-sm text-slate-400">Permanently removes your account, saved routes, their history and your alerts. This cannot be undone.</p>
        {showDelete ? (
          <form onSubmit={destroy} className="mt-4 space-y-3">
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
          </form>
        ) : (
          <Button variant="dangerGhost" className="mt-3" onClick={() => setShowDelete(true)}>
            Delete account…
          </Button>
        )}
      </div>
    </Card>
  );
}
