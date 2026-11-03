import { useCallback, useEffect, useMemo, useState } from 'react';
import { authApi, tokenStore, UNAUTHORIZED_EVENT } from '../lib/api';
import { AuthContext } from './contexts';

/**
 * Holds the signed-in user. The token lives in storage; the user profile is
 * always re-read from the server on load, so it can never be stale or forged.
 */
export default function AuthProvider({ children }) {
  const [token, setToken] = useState(() => tokenStore.get());
  const [user, setUser] = useState(null);
  const [status, setStatus] = useState(() => (tokenStore.get() ? 'loading' : 'anonymous'));

  const signOutLocally = useCallback(() => {
    tokenStore.clear();
    setToken(null);
    setUser(null);
    setStatus('anonymous');
  }, []);

  useEffect(() => {
    if (!tokenStore.get()) return;
    let cancelled = false;
    authApi
      .me()
      .then((me) => {
        if (cancelled) return;
        setUser(me);
        setStatus('authenticated');
      })
      .catch(() => {
        if (!cancelled) signOutLocally();
      });
    return () => {
      cancelled = true;
    };
  }, [signOutLocally]);

  useEffect(() => {
    window.addEventListener(UNAUTHORIZED_EVENT, signOutLocally);
    return () => window.removeEventListener(UNAUTHORIZED_EVENT, signOutLocally);
  }, [signOutLocally]);

  const acceptSession = useCallback((session) => {
    tokenStore.set(session.token);
    setToken(session.token);
    setUser(session.user);
    setStatus('authenticated');
  }, []);

  const value = useMemo(
    () => ({
      user,
      token,
      status,
      async login(email, password) {
        acceptSession(await authApi.login(email, password));
      },
      async signup(details) {
        acceptSession(await authApi.signup(details));
      },
      async changePassword(currentPassword, newPassword) {
        acceptSession(await authApi.changePassword(currentPassword, newPassword));
      },
      /** Signs out of every device: the server invalidates all existing tokens. */
      async logout() {
        try {
          await authApi.logout();
        } finally {
          signOutLocally();
        }
      },
      async deleteAccount() {
        await authApi.deleteAccount();
        signOutLocally();
      },
      setUser,
    }),
    [user, token, status, acceptSession, signOutLocally],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
