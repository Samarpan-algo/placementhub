import { Navigate } from 'react-router';
import { useAuth } from './AuthContext.jsx';
import { homePathFor } from './roles.js';

/**
 * Wraps pages that need a logged-in user with a given role.
 * <Navigate> is a component that redirects as soon as it renders; "replace" swaps the history entry,
 * so the Back button doesn't bounce the user into the page they were just turned away from.
 * This only decides what to SHOW; the backend still checks the role on every API call (401/403).
 */
export default function ProtectedRoute({ role, children }) {
  const { user } = useAuth();

  if (!user) {
    return <Navigate to="/login" replace />;
  }
  if (user.role !== role) {
    return <Navigate to={homePathFor(user.role)} replace />;
  }
  return children;
}
