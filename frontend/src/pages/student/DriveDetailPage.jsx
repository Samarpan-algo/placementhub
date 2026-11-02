import { useState } from 'react';
import { Link, useParams } from 'react-router';
import api from '../../api/client.js';
import { toApiError } from '../../api/errors.js';
import { useApi } from '../../api/useApi.js';
import Eligibility from '../../components/Eligibility.jsx';
import ErrorAlert from '../../components/ErrorAlert.jsx';
import Loading from '../../components/Loading.jsx';
import StatusBadge from '../../components/StatusBadge.jsx';
import WithdrawButton from '../../components/WithdrawButton.jsx';
import { formatCtc, formatDate, formatDateTime, formatJobType } from '../../utils/format.js';

/**
 * The Apply / Withdraw area. Apply shows only when the drive is OPEN, the student is eligible and hasn't
 * applied; Withdraw only when My applications would show it (the same WithdrawButton and rules).
 * The backend checks everything again: a 422 lists every reason, a 409 says why (e.g. applied in another tab).
 */
function ApplySection({ item, onChanged }) {
  const { drive, eligible, applicationId, applicationStatus } = item;
  const [sending, setSending] = useState(false);
  const [error, setError] = useState(null);
  const [message, setMessage] = useState(null);

  async function handleApply() {
    setError(null);
    setMessage(null);
    setSending(true);
    try {
      await api.post('/api/students/me/applications', { driveId: drive.id });
      setMessage('Applied! You can follow it under My applications.');
    } catch (err) {
      setError(toApiError(err));
    } finally {
      setSending(false);
      // Load the drive again either way, so the page shows the real status and eligibility now
      onChanged();
    }
  }

  function handleWithdrawDone({ message: doneMessage, error: doneError }) {
    setMessage(doneMessage ?? null);
    setError(doneError ?? null);
    onChanged();
  }

  return (
    <section className="card">
      <h2>Your application</h2>
      {message && <div className="alert alert-success">{message}</div>}
      <ErrorAlert error={error} />

      {applicationStatus ? (
        <div className="application-row">
          <span>
            Status: <StatusBadge status={applicationStatus} />{' '}
            <Link to={`/student/applications/${applicationId}`}>View application and rounds</Link>
          </span>
          {/* The drive response has everything WithdrawButton needs, under slightly different names */}
          <WithdrawButton
            application={{
              id: applicationId,
              roleTitle: drive.roleTitle,
              companyName: drive.companyName,
              status: applicationStatus,
              driveStatus: drive.status,
              registrationDeadline: drive.registrationDeadline,
            }}
            onDone={handleWithdrawDone}
          />
        </div>
      ) : drive.status !== 'OPEN' ? (
        <p className="muted">This drive is no longer open for applications.</p>
      ) : eligible ? (
        <button type="button" className="btn btn-primary" onClick={handleApply} disabled={sending}>
          {sending ? 'Applying…' : 'Apply'}
        </button>
      ) : (
        <p className="muted">You can&apos;t apply to this drive. The reasons are listed above.</p>
      )}
    </section>
  );
}

export default function DriveDetailPage() {
  // useParams reads the :id part of the route /student/drives/:id (always a string)
  const { id } = useParams();
  const { data: item, loading, error, reload } = useApi(`/api/students/me/drives/${id}`);

  if (error) {
    return (
      <div className="stack">
        {/* A 404 won't fix itself, so no "Try again" for it */}
        <ErrorAlert error={error} onRetry={error.status === 404 ? undefined : reload} />
        <Link to="/student/drives">← Back to drives</Link>
      </div>
    );
  }
  if (loading && !item) return <Loading text="Loading drive…" />;

  const { drive } = item;
  return (
    <div className="stack">
      <Link to="/student/drives">← Back to drives</Link>

      <section className="card">
        <div className="page-heading">
          <div>
            <h1>{drive.roleTitle}</h1>
            <p className="muted">
              {drive.companyName} · {formatJobType(drive.jobType)}
            </p>
          </div>
          <StatusBadge status={drive.status} />
        </div>

        <dl className="facts">
          <div>
            <dt>CTC</dt>
            <dd>{formatCtc(drive.ctcLpa)}</dd>
          </div>
          <div>
            <dt>Location</dt>
            <dd>{drive.location || '—'}</dd>
          </div>
          <div>
            <dt>Apply by</dt>
            <dd>{formatDateTime(drive.registrationDeadline)}</dd>
          </div>
          <div>
            <dt>Drive date</dt>
            <dd>{formatDate(drive.driveDate)}</dd>
          </div>
        </dl>
        {drive.description && <p className="description">{drive.description}</p>}

        <h2>Criteria</h2>
        <dl className="facts">
          <div>
            <dt>Branches</dt>
            <dd>{drive.branches.join(', ')}</dd>
          </div>
          <div>
            <dt>Minimum CGPA</dt>
            <dd>{Number(drive.minCgpa).toFixed(2)}</dd>
          </div>
          <div>
            <dt>Max active backlogs</dt>
            <dd>{drive.maxBacklogs}</dd>
          </div>
          <div>
            <dt>Batch</dt>
            <dd>{drive.graduationYear}</dd>
          </div>
        </dl>
        <Eligibility eligible={item.eligible} reasons={item.reasons} />
      </section>

      {/* key: a different drive gets a fresh ApplySection, so an old success or error message never carries over */}
      <ApplySection key={drive.id} item={item} onChanged={reload} />
    </div>
  );
}
