import { useEffect, useState } from 'react';
import { Link, useLocation, useNavigate } from 'react-router';
import { toApiError } from '../api/errors.js';
import { useAuth } from '../auth/AuthContext.jsx';
import { clearFlashMessage, peekFlashMessage } from '../auth/storage.js';
import { homePathFor } from '../auth/roles.js';
import ErrorAlert from '../components/ErrorAlert.jsx';
import Field from '../components/Field.jsx';

export default function LoginPage() {
  const { login } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();

  // The success/info message comes either from the previous page (router state: "Registered…",
  // "You have logged out.") or from the 401 handler, which stored it before reloading the page.
  const [notice] = useState(() => location.state?.message ?? peekFlashMessage());
  // The email from registration, so the student doesn't have to type it again
  const [email, setEmail] = useState(location.state?.email ?? '');
  const [password, setPassword] = useState('');
  const [error, setError] = useState(null);
  const [submitting, setSubmitting] = useState(false);

  // useEffect runs after the page is on screen; [] = only once. The flash message is one-time only.
  useEffect(() => {
    clearFlashMessage();
  }, []);

  async function handleSubmit(event) {
    event.preventDefault(); // stop the browser's own form submit (a full page reload)
    if (!email.trim() || !password) {
      setError({ message: 'Enter your email and password.', details: [] });
      return;
    }
    setError(null);
    setSubmitting(true);
    try {
      const user = await login(email, password);
      navigate(homePathFor(user.role), { replace: true });
    } catch (err) {
      setError(toApiError(err));
      setSubmitting(false);
    }
  }

  return (
    <div className="page">
      <div className="card auth-card">
        <h1>Log in</h1>
        <form className="form" onSubmit={handleSubmit} noValidate>
          {notice && !error && <div className="alert alert-success">{notice}</div>}
          <ErrorAlert error={error} />

          <Field id="email" label="Email">
            {/* A "controlled" input: React state holds the value, onChange updates it on every keystroke */}
            <input
              id="email"
              type="email"
              autoComplete="username"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
            />
          </Field>
          <Field id="password" label="Password">
            <input
              id="password"
              type="password"
              autoComplete="current-password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
            />
          </Field>

          <button type="submit" className="btn btn-primary" disabled={submitting}>
            {submitting ? 'Logging in…' : 'Log in'}
          </button>
        </form>
        <p className="muted">
          New student? <Link to="/register">Create an account</Link>
        </p>
      </div>
    </div>
  );
}
