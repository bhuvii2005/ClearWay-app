import axios from 'axios';

export const API_URL = import.meta.env.VITE_API_URL || 'http://localhost:8090';

const TOKEN_KEY = 'clearway.token';

/** Stores the login token. Wrapped in try/catch because storage can be blocked. */
export const tokenStore = {
  get() {
    try {
      return localStorage.getItem(TOKEN_KEY);
    } catch {
      return null;
    }
  },
  set(token) {
    try {
      localStorage.setItem(TOKEN_KEY, token);
    } catch {
      /* session will last until the tab closes */
    }
  },
  clear() {
    try {
      localStorage.removeItem(TOKEN_KEY);
      // Keys used by the old UI.
      localStorage.removeItem('authToken');
      localStorage.removeItem('authUser');
    } catch {
      /* nothing to clear */
    }
  },
};

/** Fired when the server rejects our token, so the app can return to the login page. */
export const UNAUTHORIZED_EVENT = 'clearway:unauthorized';

const api = axios.create({ baseURL: API_URL, timeout: 30000 });

api.interceptors.request.use((config) => {
  const token = tokenStore.get();
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

api.interceptors.response.use(
  (response) => response,
  (error) => {
    const sentToken = Boolean(error.config?.headers?.Authorization);
    if (error.response?.status === 401 && sentToken) {
      tokenStore.clear();
      window.dispatchEvent(new Event(UNAUTHORIZED_EVENT));
    }
    return Promise.reject(error);
  },
);

/** Turns an API error into a sentence a person can act on. */
export function errorMessage(error, fallback = 'Something went wrong. Please try again.') {
  if (!error?.response) {
    return 'Cannot reach the ClearWay server. Check that the backend is running.';
  }
  const { status, data } = error.response;
  if (status === 429) {
    const seconds = retryAfterSeconds(error.response);
    if (seconds != null) {
      const minutes = Math.max(1, Math.ceil(seconds / 60));
      return `Too many requests. Try again in ${minutes} ${minutes === 1 ? 'minute' : 'minutes'}.`;
    }
    return 'Too many attempts. Please wait a minute and try again.';
  }
  // Coach 503s say why in `code`; other services' 503s have no code and fall through to their message.
  if (status === 503 && data?.code) {
    switch (data.code) {
      case 'budget':
      case 'quota_exhausted':
        return `Coach has reached today's free limit; it resets ${formatReset(nextPacificMidnight(new Date()))}.`;
      case 'time_budget':
        return 'That took too long. Please try again.';
      default:
        return 'Coach is offline right now. Please try again later.';
    }
  }
  if (data && typeof data.message === 'string' && data.message) {
    return data.message;
  }
  return fallback;
}

/** Seconds from a Retry-After header (seconds or an HTTP date), or null. */
function retryAfterSeconds(response) {
  const headers = response.headers ?? {};
  const raw = typeof headers.get === 'function' ? headers.get('retry-after') : headers['retry-after'] ?? headers['Retry-After'];
  if (raw == null || raw === '') return null;
  const seconds = Number(raw);
  if (Number.isFinite(seconds)) return Math.max(0, seconds);
  const at = Date.parse(raw);
  return Number.isNaN(at) ? null : Math.max(0, (at - Date.now()) / 1000);
}

/** Offset of a time zone from UTC at an instant, in ms (positive east of UTC). */
function zoneOffsetMs(instant, timeZone) {
  const parts = Object.fromEntries(
    new Intl.DateTimeFormat('en-US', {
      timeZone,
      hourCycle: 'h23',
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit',
      second: '2-digit',
    })
      .formatToParts(instant)
      .map((p) => [p.type, p.value]),
  );
  const asUtc = Date.UTC(+parts.year, +parts.month - 1, +parts.day, +parts.hour, +parts.minute, +parts.second);
  return asUtc - Math.floor(instant.getTime() / 1000) * 1000;
}

/**
 * The next midnight in US Pacific time, when the Coach's daily allowance (ours and Gemini's
 * free tier) resets.
 */
export function nextPacificMidnight(now, timeZone = 'America/Los_Angeles') {
  const today = Object.fromEntries(
    new Intl.DateTimeFormat('en-US', { timeZone, year: 'numeric', month: 'numeric', day: 'numeric' })
      .formatToParts(now)
      .map((p) => [p.type, p.value]),
  );
  const wallClock = Date.UTC(+today.year, +today.month - 1, +today.day + 1);
  // Two passes settle the offset even when midnight falls on a daylight-saving change.
  let instant = wallClock - zoneOffsetMs(new Date(wallClock), timeZone);
  instant = wallClock - zoneOffsetMs(new Date(instant), timeZone);
  return new Date(instant);
}

/** "at 1:30 PM" today, or "tomorrow at 1:30 PM", in the browser's own time zone. */
export function formatReset(at, now = new Date()) {
  const time = at.toLocaleTimeString(undefined, { hour: 'numeric', minute: '2-digit' });
  const sameDay = at.toDateString() === now.toDateString();
  const tomorrow = new Date(now.getFullYear(), now.getMonth(), now.getDate() + 1).toDateString() === at.toDateString();
  if (sameDay) return `at ${time}`;
  if (tomorrow) return `tomorrow at ${time}`;
  return `on ${at.toLocaleDateString(undefined, { weekday: 'short', day: 'numeric', month: 'short' })} at ${time}`;
}

const data = (promise) => promise.then((res) => res.data);

export const authApi = {
  login: (email, password) => data(api.post('/api/v1/auth/login', { email, password })),
  signup: (body) => data(api.post('/api/v1/auth/signup', body)),
  me: () => data(api.get('/api/v1/auth/me')),
  updatePreferences: (prefs) => data(api.post('/api/v1/auth/preferences', prefs)),
  updateProfile: (body) => data(api.put('/api/v1/auth/profile', body)),
  changePassword: (currentPassword, newPassword) =>
    data(api.post('/api/v1/auth/password', { currentPassword, newPassword })),
  logout: () => api.post('/api/v1/auth/logout'),
  deleteAccount: () => api.delete('/api/v1/auth/account'),
};

export const routesApi = {
  alternatives: (type, pathCoordinates) =>
    data(api.post('/api/v1/routes/alternatives', { type, pathCoordinates })),
  create: (body) => data(api.post('/api/v1/routes', body)),
  list: () => data(api.get('/api/v1/routes')),
  get: (id) => data(api.get(`/api/v1/routes/${id}`)),
  update: (id, body) => data(api.patch(`/api/v1/routes/${id}`, body)),
  remove: (id) => api.delete(`/api/v1/routes/${id}`),
  history: (id, days, tz) => data(api.get(`/api/v1/routes/${id}/metrics`, { params: { days, tz } })),
};

/**
 * One Coach message is several AI calls. The server stops it after its total budget
 * (CLEARWAY_AI_COACH_TOTAL_TIMEOUT, 45 s by default) plus at most one tool that was already
 * running (a knowledge-base lookup: up to 20 s, usually about 1 s). This must stay above that,
 * so the browser never gives up before the server answers.
 */
export const COACH_TIMEOUT_MS = 75000;

export const coachApi = {
  chat: (conversationId, message, timeZone) =>
    data(api.post('/api/v1/coach/chat', { conversationId, message, timeZone }, { timeout: COACH_TIMEOUT_MS })),
};

export const alertsApi = {
  list: (limit = 50) => data(api.get('/api/v1/notifications', { params: { limit } })),
  unreadCount: () => data(api.get('/api/v1/notifications/unread-count')).then((d) => d.unread ?? 0),
  markRead: (id) => api.post(`/api/v1/notifications/${id}/read`),
  markAllRead: () => data(api.post('/api/v1/notifications/read-all')),
};

export default api;
