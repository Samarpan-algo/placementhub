import { useEffect, useState } from 'react';
import { Link } from 'react-router';
import api from '../../api/client.js';
import { toApiError } from '../../api/errors.js';
import ErrorAlert from '../../components/ErrorAlert.jsx';

/** Admin home: a one-line stats summary and links to the admin pages (rounds, policies, audit and stats come later). */
export default function AdminHomePage() {
  const [stats, setStats] = useState(null);
  const [error, setError] = useState(null);

  // Same pattern as the student home: one admin-only call, once, when the page appears
  useEffect(() => {
    let ignore = false;
    api
      .get('/api/stats')
      .then((response) => {
        if (!ignore) setStats(response.data);
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
      <h1>TPO admin home</h1>
      <ErrorAlert error={error} />
      {!stats && !error && <p className="muted">Loading stats…</p>}
      {stats && (
        <p>
          {stats.placedStudents} of {stats.totalStudents} students placed ({stats.placementPercentage}%).
        </p>
      )}
      <p>
        Manage <Link to="/admin/companies">companies</Link> and <Link to="/admin/drives">drives</Link>.
      </p>
      <p className="muted">Rounds, results, policies, the audit log and stats are coming next.</p>
    </div>
  );
}
