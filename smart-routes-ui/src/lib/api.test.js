// @vitest-environment jsdom
import { afterEach, describe, expect, it, vi } from 'vitest';
import api, { errorMessage, formatReset, nextPacificMidnight, tokenStore, UNAUTHORIZED_EVENT } from './api';

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

describe('Coach errors and the daily reset', () => {
  const failed = (status, data = {}, headers = {}) => ({ response: { status, data, headers } });

  it('429 with Retry-After says how many minutes', () => {
    expect(errorMessage(failed(429, {}, { 'retry-after': '61' }))).toBe('Too many requests. Try again in 2 minutes.');
    expect(errorMessage(failed(429, {}, { 'retry-after': '20' }))).toBe('Too many requests. Try again in 1 minute.');
    expect(errorMessage(failed(429))).toMatch(/wait a minute/);
  });

  it('Coach 503s are told apart by their code', () => {
    expect(errorMessage(failed(503, { code: 'quota_exhausted', message: 'x' }))).toMatch(/^Coach has reached today's free limit; it resets (at|tomorrow at) /);
    expect(errorMessage(failed(503, { code: 'budget' }))).toMatch(/today's free limit/);
    expect(errorMessage(failed(503, { code: 'time_budget' }))).toBe('That took too long. Please try again.');
    expect(errorMessage(failed(503, { code: 'provider_error' }))).toMatch(/^Coach is offline right now/);
    // Not a Coach error: the server's message as before.
    expect(errorMessage(failed(503, { message: 'Routing is down' }))).toBe('Routing is down');
  });

  it('the reset is the next midnight in US Pacific time, across daylight-saving changes', () => {
    // 06:00 PST on 11 Nov -> midnight PST (08:00 UTC) on 12 Nov.
    expect(nextPacificMidnight(new Date('2026-11-11T14:00:00Z')).toISOString()).toBe('2026-11-12T08:00:00.000Z');
    // 23:30 PST on 7 Mar -> midnight on 8 Mar, still PST.
    expect(nextPacificMidnight(new Date('2026-03-08T07:30:00Z')).toISOString()).toBe('2026-03-08T08:00:00.000Z');
    // 05:00 PDT on 8 Mar (clocks went forward) -> midnight PDT (07:00 UTC) on 9 Mar.
    expect(nextPacificMidnight(new Date('2026-03-08T12:00:00Z')).toISOString()).toBe('2026-03-09T07:00:00.000Z');
  });

  it('the reset time reads as today or tomorrow in local time', () => {
    const now = new Date(2026, 10, 11, 9, 0);
    expect(formatReset(new Date(2026, 10, 11, 13, 30), now)).toMatch(/^at /);
    expect(formatReset(new Date(2026, 10, 12, 1, 0), now)).toMatch(/^tomorrow at /);
  });
});
