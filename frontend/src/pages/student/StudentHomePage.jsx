import { useEffect, useState } from 'react';
import api from '../../api/client.js';
import { toApiError } from '../../api/errors.js';
import ErrorAlert from '../../components/ErrorAlert.jsx';

/** Placeholder home (Milestone 9 adds drives, applications and profile editing). */
export default function StudentHomePage() {
  const [profile, setProfile] = useState(null);
  const [error, setError] = useState(null);

  // Load the profile once when the page appears. This also proves the token works end to end.
  useEffect(() => {
    let ignore = false; // set by the cleanup below if the page goes away before the answer arrives
    api
      .get('/api/students/me')
      .then((response) => {
        if (!ignore) setProfile(response.data);
      })
      .catch((err) => {
        if (!ignore) setError(toApiError(err));
      });
    return () => {
      ignore = true;
    };
  }, []);

  return (
    <div className="card">
      <h1>Student home</h1>
      <ErrorAlert error={error} />
      {!profile && !error && <p className="muted">Loading your profile…</p>}
      {profile && (
        <p>
          Welcome, <strong>{profile.fullName}</strong> ({profile.rollNo}, {profile.branch}, CGPA {profile.cgpa}).
        </p>
      )}
      <p className="muted">Drives, applications and your profile are coming in the next milestone.</p>
    </div>
  );
}
