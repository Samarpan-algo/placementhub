import axios from 'axios';
import { clearToken, getToken, saveFlashMessage } from '../auth/storage.js';

// One axios instance for the whole app. Pages call api.get('/api/...') / api.post(...).
// Empty base URL (the dev default) = same origin, so the Vite proxy forwards /api to the backend.
// In Milestone 12, VITE_API_BASE_URL points at the deployed backend instead.
const api = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '',
});

// Runs before every request: attach the token if we have one.
api.interceptors.request.use((config) => {
  const token = getToken();
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

// Runs after every response. A 401 on a request that carried a token means the token is expired or
// invalid: forget it and send the user to the login page. A 401 from /api/auth/login itself just means
// "wrong email or password", so the login page handles that one.
api.interceptors.response.use(
  (response) => response,
  (error) => {
    const sentToken = Boolean(error.config?.headers?.Authorization);
    const isAuthCall = error.config?.url?.startsWith('/api/auth/');
    if (error.response?.status === 401 && sentToken && !isAuthCall) {
      clearToken();
      saveFlashMessage('Session expired, please log in again.');
      // A full page load (not a React navigation): every bit of in-memory state starts fresh
      window.location.assign('/login');
    }
    return Promise.reject(error);
  },
);

export default api;
