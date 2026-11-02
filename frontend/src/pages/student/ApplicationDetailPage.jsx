import { Link, useParams } from 'react-router';
import { useApi } from '../../api/useApi.js';
import EmptyState from '../../components/EmptyState.jsx';
import ErrorAlert from '../../components/ErrorAlert.jsx';
import Loading from '../../components/Loading.jsx';
import StatusBadge from '../../components/StatusBadge.jsx';
import { formatCtc, formatDateTime, formatJobType } from '../../utils/format.js';

/** One application with round-by-round tracking: every round of the drive and my result in it. */
export default function ApplicationDetailPage() {
  const { id } = useParams();
  const { data: application, loading, error, reload } = useApi(`/api/students/me/applications/${id}`);

  if (error) {
    return (
      <div className="stack">
        {/* Someone else's application is also a 404 (the backend doesn't reveal it exists) */}
        <ErrorAlert error={error} onRetry={error.status === 404 ? undefined : reload} />
        <Link to="/student/applications">← Back to my applications</Link>
      </div>
    );
  }
  if (loading && !application) return <Loading text="Loading application…" />;

  return (
    <div className="stack">
      <Link to="/student/applications">← Back to my applications</Link>

      <section className="card">
        <div className="page-heading">
          <div>
            <h1>{application.roleTitle}</h1>
            <p className="muted">
              {application.companyName} · {formatJobType(application.jobType)} · {formatCtc(application.ctcLpa)}
            </p>
          </div>
          <StatusBadge status={application.status} />
        </div>
        {application.statusReason && <div className="alert alert-warning">{application.statusReason}</div>}
        <dl className="facts">
          <div>
            <dt>Applied</dt>
            <dd>{formatDateTime(application.appliedAt)}</dd>
          </div>
          <div>
            <dt>Registration deadline</dt>
            <dd>{formatDateTime(application.registrationDeadline)}</dd>
          </div>
          <div>
            <dt>Drive</dt>
            <dd>
              <StatusBadge status={application.driveStatus} />{' '}
              <Link to={`/student/drives/${application.driveId}`}>View drive</Link>
            </dd>
          </div>
        </dl>
      </section>

      <section className="card">
        <h2>Rounds</h2>
        {application.rounds.length === 0 ? (
          <EmptyState title="No rounds have been announced yet.">
            They will appear here once the placement office adds them.
          </EmptyState>
        ) : (
          <div className="table-wrap">
            <table className="table">
              <thead>
                <tr>
                  <th>Round</th>
                  <th>Scheduled</th>
                  <th>Result</th>
                  <th>Remarks</th>
                </tr>
              </thead>
              <tbody>
                {application.rounds.map((round) => (
                  <tr key={round.roundNo}>
                    <td>
                      {round.roundNo}. {round.name}
                    </td>
                    <td>{round.scheduledAt ? formatDateTime(round.scheduledAt) : 'Not scheduled'}</td>
                    <td>{round.result ? <StatusBadge status={round.result} /> : <span className="muted">Pending</span>}</td>
                    <td>{round.remarks || <span className="muted">—</span>}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>
    </div>
  );
}
