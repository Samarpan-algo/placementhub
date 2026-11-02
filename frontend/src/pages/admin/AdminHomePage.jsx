import { useEffect, useState } from 'react';
import api from '../../api/client.js';
import { toApiError } from '../../api/errors.js';
import ErrorAlert from '../../components/ErrorAlert.jsx';

/** Placeholder home (Milestone 9 adds companies, drives, applicants, policies, audit and stats pages). */
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
      <p className="muted">Companies, drives, applicants and policies are coming in the next milestone.</p>
    </div>
  );
}
