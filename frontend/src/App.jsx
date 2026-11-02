import { Navigate, Route, Routes } from 'react-router';
import { useAuth } from './auth/AuthContext.jsx';
import ProtectedRoute from './auth/ProtectedRoute.jsx';
import PublicOnlyRoute from './auth/PublicOnlyRoute.jsx';
import { homePathFor, ROLES } from './auth/roles.js';
import AppLayout from './components/AppLayout.jsx';
import LoginPage from './pages/LoginPage.jsx';
import RegisterPage from './pages/RegisterPage.jsx';
import AdminHomePage from './pages/admin/AdminHomePage.jsx';
import CompaniesPage from './pages/admin/CompaniesPage.jsx';
import ApplicationDetailPage from './pages/student/ApplicationDetailPage.jsx';
import DriveDetailPage from './pages/student/DriveDetailPage.jsx';
import DrivesPage from './pages/student/DrivesPage.jsx';
import MyApplicationsPage from './pages/student/MyApplicationsPage.jsx';
import ProfilePage from './pages/student/ProfilePage.jsx';
import StudentHomePage from './pages/student/StudentHomePage.jsx';

/** "/" and unknown URLs: logged in → your own home, otherwise → login. */
function HomeRedirect() {
  const { user } = useAuth();
  return <Navigate to={user ? homePathFor(user.role) : '/login'} replace />;
}

// <Routes> picks the first <Route> whose path matches the URL and renders its element.
// Nested <Route>s render inside the parent's <Outlet /> (see AppLayout), so both homes share the header.
export default function App() {
  return (
    <Routes>
      <Route path="/" element={<HomeRedirect />} />
      <Route
        path="/login"
        element={
          <PublicOnlyRoute>
            <LoginPage />
          </PublicOnlyRoute>
        }
      />
      <Route
        path="/register"
        element={
          <PublicOnlyRoute>
            <RegisterPage />
          </PublicOnlyRoute>
        }
      />

      <Route
        path="/student"
        element={
          <ProtectedRoute role={ROLES.STUDENT}>
            <AppLayout />
          </ProtectedRoute>
        }
      >
        <Route index element={<StudentHomePage />} />
        <Route path="drives" element={<DrivesPage />} />
        {/* :id is a URL parameter; the page reads it with useParams() */}
        <Route path="drives/:id" element={<DriveDetailPage />} />
        <Route path="applications" element={<MyApplicationsPage />} />
        <Route path="applications/:id" element={<ApplicationDetailPage />} />
        <Route path="profile" element={<ProfilePage />} />
        {/* any other /student/... URL → back to the dashboard */}
        <Route path="*" element={<Navigate to="/student" replace />} />
      </Route>

      <Route
        path="/admin"
        element={
          <ProtectedRoute role={ROLES.ADMIN}>
            <AppLayout />
          </ProtectedRoute>
        }
      >
        <Route index element={<AdminHomePage />} />
        <Route path="companies" element={<CompaniesPage />} />
        <Route path="*" element={<Navigate to="/admin" replace />} />
      </Route>

      <Route path="*" element={<HomeRedirect />} />
    </Routes>
  );
}
