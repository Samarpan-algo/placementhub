import { createContext, useContext, useState } from 'react';
import api from '../api/client.js';
import { clearToken, getToken, saveToken } from './storage.js';
import { userFromToken } from './jwt.js';

// Context = a value that any component below <AuthProvider> can read with useAuth(),
// without passing it down through props at every level.
const AuthContext = createContext(null);

/** On page load: a stored token that is unreadable or already expired counts as logged out. */
function loadSession() {
  const token = getToken();
  if (!token) {
    return null;
  }
  try {
    const user = userFromToken(token);
    if (user.expiresAt <= Date.now()) {
      clearToken();
      return null;
    }
    return { token, user };
  } catch {
    clearToken();
    return null;
  }
}

export function AuthProvider({ children }) {
  // useState(loadSession) runs loadSession once, on the first render only.
  // When setSession is called, React re-renders everything that uses useAuth().
  const [session, setSession] = useState(loadSession);

  /** Calls the backend; on success stores the token and returns the user. Throws the axios error on failure. */
  async function login(email, password) {
    const response = await api.post('/api/auth/login', { email, password });
    const token = response.data.accessToken;
    const user = userFromToken(token);
    saveToken(token);
    setSession({ token, user });
    return user;
  }

  function logout() {
    clearToken();
    setSession(null);
  }

  const value = {
    user: session?.user ?? null,
    isLoggedIn: session !== null,
    login,
    logout,
  };
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

/** A custom hook: components call useAuth() to get { user, isLoggedIn, login, logout }. */
export function useAuth() {
  const context = useContext(AuthContext);
  if (context === null) {
    throw new Error('useAuth must be used inside <AuthProvider>');
  }
  return context;
}
