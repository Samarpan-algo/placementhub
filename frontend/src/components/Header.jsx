import { NavLink, useNavigate } from 'react-router';
import { useAuth } from '../auth/AuthContext.jsx';
import { homePathFor, ROLES } from '../auth/roles.js';

// The links each role sees. `end` = active only on exactly that URL, so "Home" (/student)
// isn't highlighted on every /student/... page.
const NAV_LINKS = {
  [ROLES.STUDENT]: [
    { to: '/student', label: 'Home', end: true },
    { to: '/student/drives', label: 'Drives' },
    { to: '/student/applications', label: 'My applications' },
    { to: '/student/profile', label: 'Profile' },
  ],
  [ROLES.ADMIN]: [
    { to: '/admin', label: 'Home', end: true },
    { to: '/admin/companies', label: 'Companies' },
    { to: '/admin/drives', label: 'Drives' },
  ],
};

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
      <NavLink to={homePathFor(user.role)} className="header-brand">
        PlacementHub
      </NavLink>
      {/* NavLink is a <Link> that knows whether its URL is the current page: it adds the "active" class */}
      <nav className="header-nav">
        {(NAV_LINKS[user.role] ?? []).map((link) => (
          <NavLink key={link.to} to={link.to} end={link.end} className="nav-link">
            {link.label}
          </NavLink>
        ))}
      </nav>
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
