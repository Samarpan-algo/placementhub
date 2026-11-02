import { Link } from 'react-router';
import { useApi } from '../../api/useApi.js';
import EmptyState from '../../components/EmptyState.jsx';
import ErrorAlert from '../../components/ErrorAlert.jsx';
import Loading from '../../components/Loading.jsx';
import StatusBadge from '../../components/StatusBadge.jsx';
import { branchLabel } from '../../utils/branches.js';

const STATUSES = ['APPLIED', 'SHORTLISTED', 'SELECTED', 'REJECTED', 'WITHDRAWN', 'INELIGIBLE'];

// The backend caps page size at 100. A student has far fewer applications than that in practice;
// if there are more, the page says the counts cover only the 100 most recent (see decisions.md #69).
const APPLICATIONS_URL = '/api/students/me/applications?size=100';

/** { APPLIED: 2, SHORTLISTED: 0, ... } for the given applications. */
function countByStatus(applications) {
  const counts = Object.fromEntries(STATUSES.map((status) => [status, 0]));
  for (const application of applications) {
    counts[application.status] += 1;
  }
  return counts;
}

function ProfileSummary() {
  const { data: profile, loading, error, reload } = useApi('/api/students/me');

  // Fields are shown only once the profile has loaded; a failed load shows the error, never empty values
  if (error) return <ErrorAlert error={error} onRetry={reload} />;
  if (loading && !profile) return <Loading text="Loading your profile…" />;

  return (
    <>
      <p>
        Welcome, <strong>{profile.fullName}</strong>.
      </p>
      <dl className="review-list">
        <div className="review-row">
          <dt>Roll number</dt>
          <dd>{profile.rollNo}</dd>
        </div>
        <div className="review-row">
          <dt>Branch</dt>
          <dd>{branchLabel(profile.branch)}</dd>
        </div>
        <div className="review-row">
          <dt>Graduation year</dt>
          <dd>{profile.graduationYear}</dd>
        </div>
        <div className="review-row">
          <dt>CGPA</dt>
          <dd>{profile.cgpa}</dd>
        </div>
        <div className="review-row">
          <dt>Active backlogs</dt>
          <dd>{profile.activeBacklogs}</dd>
        </div>
      </dl>
      <p>
        <Link to="/student/profile">View or edit your profile</Link>
      </p>
    </>
  );
}

function ApplicationCounts() {
  const { data, loading, error, reload } = useApi(APPLICATIONS_URL);

  if (error) return <ErrorAlert error={error} onRetry={reload} />;
  if (loading && !data) return <Loading text="Loading your applications…" />;

  if (data.totalElements === 0) {
    return (
      <EmptyState title="You haven't applied to any drive yet.">
        <Link to="/student/drives">Browse open drives</Link>
      </EmptyState>
    );
  }

  const counts = countByStatus(data.content);
  return (
    <>
      <div className="stat-grid">
        {STATUSES.map((status) => (
          <div key={status} className="stat-tile">
            <span className="stat-number">{counts[status]}</span>
            <StatusBadge status={status} />
          </div>
        ))}
      </div>
      <p className="muted">
        {data.totalElements} application{data.totalElements === 1 ? '' : 's'} in total.{' '}
        {data.totalElements > data.content.length && `Counts cover your ${data.content.length} most recent. `}
        <Link to="/student/applications">See all my applications</Link>
      </p>
    </>
  );
}

/**
 * The student dashboard. The two sections load independently, so if one fails the other still shows,
 * and each has its own "Try again".
 */
export default function StudentHomePage() {
  return (
    <div className="stack">
      <section className="card">
        <h1>My dashboard</h1>
        <ProfileSummary />
      </section>
      <section className="card">
        <h2>My applications by status</h2>
        <ApplicationCounts />
      </section>
    </div>
  );
}
