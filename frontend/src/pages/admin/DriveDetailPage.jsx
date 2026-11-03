import { useState } from 'react';
import { Link, useLocation, useParams } from 'react-router';
import api from '../../api/client.js';
import { toApiError } from '../../api/errors.js';
import { useApi } from '../../api/useApi.js';
import ConfirmDialog from '../../components/ConfirmDialog.jsx';
import ErrorAlert from '../../components/ErrorAlert.jsx';
import Loading from '../../components/Loading.jsx';
import StatusBadge from '../../components/StatusBadge.jsx';
import { branchLabel } from '../../utils/branches.js';
import { EDITABLE_STATUSES, NEXT_STATUS_ACTION } from '../../utils/drives.js';
import { formatCtc, formatDate, formatDateTime, formatJobType } from '../../utils/format.js';

function Fact({ label, children }) {
  return (
    <div>
      <dt>{label}</dt>
      <dd>{children}</dd>
    </div>
  );
}

/** One drive for the admin: its details and criteria, Edit, the next status change, and its applicants. */
export default function DriveDetailPage() {
  const { id } = useParams();
  const location = useLocation();
  const { data: drive, loading, error, reload } = useApi(`/api/drives/${id}`);

  // A message from the form page ("Drive saved. ..."), then the result of the last status change
  const [message, setMessage] = useState(() => location.state?.message ?? null);
  const [actionError, setActionError] = useState(null);
  const [confirming, setConfirming] = useState(false);
  const [changing, setChanging] = useState(false);

  if (error) return <ErrorAlert error={error} onRetry={reload} />;
  if (loading && !drive) return <Loading text="Loading drive…" />;

  const action = NEXT_STATUS_ACTION[drive.status]; // undefined for COMPLETED (final)
  const editable = EDITABLE_STATUSES.includes(drive.status);

  async function confirmStatusChange() {
    setChanging(true);
    setMessage(null);
    setActionError(null);
    try {
      await api.post(`/api/drives/${drive.id}/${action.endpoint}`);
      setMessage(action.done);
    } catch (err) {
      setActionError(toApiError(err)); // e.g. 409 "Cannot open drive: registration deadline must be in the future"
    } finally {
      setChanging(false);
      setConfirming(false);
      reload(); // either way, show the drive as the backend has it now
    }
  }

  return (
    <div className="stack">
      <section className="card">
        <p>
          <Link to="/admin/drives">← All drives</Link>
        </p>
        <div className="page-heading">
          <div>
            <h1>{drive.roleTitle}</h1>
            <p className="muted">
              {drive.companyName} · {formatJobType(drive.jobType)} · {drive.location || 'Location not given'}
            </p>
          </div>
          <StatusBadge status={drive.status} />
        </div>

        {message && <div className="alert alert-success">{message}</div>}
        <ErrorAlert error={actionError} />

        <div className="button-row button-row-start">
          {editable && (
            <Link to={`/admin/drives/${drive.id}/edit`} className="btn btn-secondary">
              Edit drive
            </Link>
          )}
          {action && (
            <button
              type="button"
              className={`btn ${action.danger ? 'btn-danger' : 'btn-primary'}`}
              onClick={() => setConfirming(true)}
              disabled={changing || loading}
            >
              {action.label}
            </button>
          )}
          <Link to={`/admin/drives/${drive.id}/applicants`} className="btn btn-secondary">
            View applicants
          </Link>
          <Link to={`/admin/drives/${drive.id}/rounds`} className="btn btn-secondary">
            Rounds
          </Link>
          {drive.status === 'CLOSED' && (
            <Link to={`/admin/drives/${drive.id}/selection`} className="btn btn-secondary">
              Selection
            </Link>
          )}
        </div>
        {!action && <p className="muted">Completed is the final status: no further changes are possible.</p>}

        <dl className="facts">
          <Fact label="CTC">{formatCtc(drive.ctcLpa)}</Fact>
          <Fact label="Registration deadline">{formatDateTime(drive.registrationDeadline)}</Fact>
          <Fact label="Drive date">{formatDate(drive.driveDate)}</Fact>
        </dl>
      </section>

      <section className="card">
        <h2>Eligibility criteria</h2>
        <dl className="facts">
          <Fact label="Minimum CGPA">{drive.minCgpa}</Fact>
          <Fact label="Maximum active backlogs">{drive.maxBacklogs}</Fact>
          <Fact label="Graduation year">{drive.graduationYear}</Fact>
        </dl>
        <p>
          <strong>Eligible branches: </strong>
          {drive.branches.length > 0 ? (
            drive.branches.map(branchLabel).join(', ')
          ) : (
            <span className="muted">none selected yet (needed before opening)</span>
          )}
        </p>
      </section>

      <section className="card">
        <h2>Description</h2>
        {drive.description ? (
          <p className="description">{drive.description}</p>
        ) : (
          <p className="muted">No description.</p>
        )}
        <p className="muted">
          Created {formatDateTime(drive.createdAt)} · Last updated {formatDateTime(drive.updatedAt)}
        </p>
      </section>

      {action && (
        <ConfirmDialog
          open={confirming}
          title={action.title}
          confirmLabel={action.label}
          busyLabel="Saving…"
          danger={action.danger}
          busy={changing}
          onConfirm={confirmStatusChange}
          onCancel={() => setConfirming(false)}
        >
          <p>
            <strong>
              {drive.roleTitle} at {drive.companyName}
            </strong>
          </p>
          <p>{action.body}</p>
        </ConfirmDialog>
      )}
    </div>
  );
}
