import { Navigate } from 'react-router';
import { useAuth } from './AuthContext.jsx';
import { homePathFor } from './roles.js';

/** For /login and /register: a user who is already logged in goes to their own home instead. */
export default function PublicOnlyRoute({ children }) {
  const { user } = useAuth();

  if (user) {
    return <Navigate to={homePathFor(user.role)} replace />;
  }
  return children;
}
