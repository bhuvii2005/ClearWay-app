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
    return 'Too many attempts. Please wait a minute and try again.';
  }
  if (data && typeof data.message === 'string' && data.message) {
    return data.message;
  }
  return fallback;
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

export const alertsApi = {
  list: (limit = 50) => data(api.get('/api/v1/notifications', { params: { limit } })),
  unreadCount: () => data(api.get('/api/v1/notifications/unread-count')).then((d) => d.unread ?? 0),
  markRead: (id) => api.post(`/api/v1/notifications/${id}/read`),
  markAllRead: () => data(api.post('/api/v1/notifications/read-all')),
};

export default api;
