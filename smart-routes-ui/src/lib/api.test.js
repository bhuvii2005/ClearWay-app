// @vitest-environment jsdom
import { afterEach, describe, expect, it, vi } from 'vitest';
import api, { errorMessage, tokenStore, UNAUTHORIZED_EVENT } from './api';

/** Replaces the network layer so requests resolve or fail with a chosen status. */
function respondWith(status, data = {}) {
  api.defaults.adapter = (config) => {
    const response = { status, data, headers: {}, config, statusText: '' };
    if (status >= 400) {
      const error = new Error(`HTTP ${status}`);
      error.config = config;
      error.response = response;
      return Promise.reject(error);
    }
    return Promise.resolve(response);
  };
}

afterEach(() => {
  tokenStore.clear();
  vi.restoreAllMocks();
});

describe('api client', () => {
  it('sends the stored token', async () => {
    tokenStore.set('abc');
    let seen;
    api.defaults.adapter = (config) => {
      seen = config.headers.Authorization;
      return Promise.resolve({ status: 200, data: {}, headers: {}, config });
    };
    await api.get('/api/v1/routes');
    expect(seen).toBe('Bearer abc');
  });

  it('signs out when the server rejects the token', async () => {
    tokenStore.set('revoked');
    const listener = vi.fn();
    window.addEventListener(UNAUTHORIZED_EVENT, listener);
    respondWith(401, { message: 'Unauthorized' });

    await expect(api.get('/api/v1/routes')).rejects.toThrow();
    expect(tokenStore.get()).toBeNull();
    expect(listener).toHaveBeenCalledTimes(1);
    window.removeEventListener(UNAUTHORIZED_EVENT, listener);
  });

  it('does not sign out on a failed login (no token sent)', async () => {
    const listener = vi.fn();
    window.addEventListener(UNAUTHORIZED_EVENT, listener);
    respondWith(401, { message: 'Invalid email or password' });

    await expect(api.post('/api/v1/auth/login', {})).rejects.toThrow();
    expect(listener).not.toHaveBeenCalled();
    window.removeEventListener(UNAUTHORIZED_EVENT, listener);
  });
});

describe('errorMessage', () => {
  it('uses the server message when there is one', () => {
    expect(errorMessage({ response: { status: 409, data: { message: 'Email already registered' } } })).toBe(
      'Email already registered',
    );
  });

  it('explains rate limiting and an unreachable server', () => {
    expect(errorMessage({ response: { status: 429, data: {} } })).toMatch(/wait a minute/);
    expect(errorMessage(new Error('Network Error'))).toMatch(/Cannot reach/);
  });

  it('falls back to the given sentence', () => {
    expect(errorMessage({ response: { status: 500, data: {} } }, 'Could not save.')).toBe('Could not save.');
  });
});
