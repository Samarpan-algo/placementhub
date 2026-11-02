import { useNavigate } from 'react-router';
import { useAuth } from '../auth/AuthContext.jsx';
import { ROLES } from '../auth/roles.js';

export default function Header() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();

  function handleLogout() {
    logout();
    // "state" travels with the navigation; the login page reads it to show the message
    navigate('/login', { replace: true, state: { message: 'You have logged out.' } });
  }

  return (
    <header className="header">
      <span className="header-brand">PlacementHub</span>
      <div className="header-user">
        <span>
          {user.email} ({user.role === ROLES.ADMIN ? 'TPO admin' : 'Student'})
        </span>
        <button type="button" className="btn btn-secondary" onClick={handleLogout}>
          Logout
        </button>
      </div>
    </header>
  );
}
