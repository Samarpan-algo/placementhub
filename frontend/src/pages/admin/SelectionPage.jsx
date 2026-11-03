import { useState } from 'react';
import { Link, useParams } from 'react-router';
import api from '../../api/client.js';
import { toApiError } from '../../api/errors.js';
import { useApi } from '../../api/useApi.js';
import ApplicantReasonTable from '../../components/ApplicantReasonTable.jsx';
import ConfirmDialog from '../../components/ConfirmDialog.jsx';
import EmptyState from '../../components/EmptyState.jsx';
import ErrorAlert from '../../components/ErrorAlert.jsx';
import Loading from '../../components/Loading.jsx';
import StatusBadge from '../../components/StatusBadge.jsx';
import { selectionBlock, withStudentNames } from '../../utils/rounds.js';

/** What the last selection did: who was selected and which other applications policies made INELIGIBLE. */
function SelectionOutcome({ outcome }) {
  return (
    <>
      <div className="alert alert-success">
        Selected: {outcome.selected.map((s) => `${s.studentName} (${s.rollNo})`).join(', ')}.
      </div>
      {outcome.madeIneligible.length > 0 && (
        <div className="alert alert-warning">
          Because of placement policies, these other applications became Ineligible:
          <ul>
            {outcome.madeIneligible.map((item) => (
              <li key={item.applicationId}>
                {item.studentName}: {item.roleTitle} at {item.companyName}. {item.reason}
              </li>
            ))}
          </ul>
        </div>
      )}
    </>
  );
}

/**
 * Final selection for a CLOSED drive: tick who can be selected (the same rules as SelectionService) and send
 * them in one request. Placement policies are checked only by the backend, which answers 409 with the reason.
 */
export default function SelectionPage() {
  const { id } = useParams();
  const drive = useApi(`/api/drives/${id}`);
  const applicants = useApi(`/api/drives/${id}/applications`, { allPages: true });

  const [ticked, setTicked] = useState([]); // applicationIds
  const [outcome, setOutcome] = useState(null);
  const [saveError, setSaveError] = useState(null);
  const [confirming, setConfirming] = useState(false);
  const [saving, setSaving] = useState(false);

  function reloadAll() {
    drive.reload();
    applicants.reload();
  }

  const loadError = drive.error ?? applicants.error;
  if (loadError) return <ErrorAlert error={loadError} onRetry={reloadAll} />;
  if (!drive.data || !applicants.data) return <Loading text="Loading applicants…" />;

  const fullTime = drive.data.jobType === 'FULL_TIME';
  const heading = (
    <div>
      <p>
        <Link to={`/admin/drives/${id}`}>← Back to the drive</Link>
      </p>
      <h1>Selection</h1>
      <p className="muted">
        {drive.data.roleTitle} at {drive.data.companyName} · <StatusBadge status={drive.data.status} />
      </p>
    </div>
  );

  if (drive.data.status !== 'CLOSED') {
    return (
      <div className="stack">
        {heading}
        <div className="alert alert-warning">
          Selections can be made only while the drive is Closed. To see where each student stands, open the{' '}
          <Link to={`/admin/drives/${id}/applicants`}>applicants list</Link>.
        </div>
      </div>
    );
  }

  const selectable = [];
  const notSelectable = [];
  for (const applicant of applicants.data.content) {
    const why = selectionBlock(applicant);
    if (why) notSelectable.push({ applicant, why });
    else selectable.push(applicant);
  }
  // Only students still selectable after a refetch count, even if they were ticked earlier
  const chosen = selectable.filter((a) => ticked.includes(a.applicationId));

  function toggle(applicationId) {
    setOutcome(null);
    setTicked(
      ticked.includes(applicationId) ? ticked.filter((t) => t !== applicationId) : [...ticked, applicationId],
    );
  }

  async function confirmSelect() {
    setSaving(true);
    setSaveError(null);
    setOutcome(null);
    try {
      const response = await api.post(`/api/drives/${id}/selections`, {
        applicationIds: chosen.map((a) => a.applicationId),
      });
      setOutcome(response.data);
      setTicked([]);
    } catch (err) {
      setSaveError(withStudentNames(toApiError(err), applicants.data.content));
    } finally {
      setSaving(false);
      setConfirming(false);
      reloadAll(); // either way, show the list as the backend has it now
    }
  }

  return (
    <div className="stack">
      {heading}
      {outcome && <SelectionOutcome outcome={outcome} />}
      <ErrorAlert error={saveError} />

      {applicants.data.content.length === 0 && <EmptyState title="No one has applied to this drive." />}
      {applicants.data.content.length > 0 && selectable.length === 0 && (
        <EmptyState title="No applicant can be selected right now.">See the reasons below.</EmptyState>
      )}

      {selectable.length > 0 && (
        <>
          <div className="table-wrap card">
            <table className="table">
              <thead>
                <tr>
                  <th>Select</th>
                  <th>Roll no</th>
                  <th>Student</th>
                  <th>CGPA</th>
                  <th>Status</th>
                </tr>
              </thead>
              <tbody>
                {selectable.map((applicant) => (
                  <tr key={applicant.applicationId}>
                    <td>
                      <input
                        type="checkbox"
                        aria-label={`Select ${applicant.fullName}`}
                        checked={ticked.includes(applicant.applicationId)}
                        onChange={() => toggle(applicant.applicationId)}
                      />
                    </td>
                    <td className="nowrap">{applicant.rollNo}</td>
                    <td>
                      <strong>{applicant.fullName}</strong>
                      <div className="muted">{applicant.branch}</div>
                    </td>
                    <td>{Number(applicant.cgpa).toFixed(2)}</td>
                    <td>
                      <StatusBadge status={applicant.status} />
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <div className="button-row">
            <button
              type="button"
              className="btn btn-primary"
              onClick={() => {
                setSaveError(null);
                setConfirming(true);
              }}
              disabled={chosen.length === 0 || applicants.loading || saving}
            >
              Select {chosen.length > 0 ? `${chosen.length} ` : ''}student{chosen.length === 1 ? '' : 's'}
            </button>
          </div>
        </>
      )}

      <ApplicantReasonTable title="Cannot be selected" rows={notSelectable} />

      <ConfirmDialog
        open={confirming}
        title={`Select ${chosen.length} student${chosen.length === 1 ? '' : 's'}?`}
        confirmLabel="Select"
        busyLabel="Selecting…"
        danger
        busy={saving}
        onConfirm={confirmSelect}
        onCancel={() => setConfirming(false)}
      >
        <ul className="confirm-list">
          {chosen.map((a) => (
            <li key={a.applicationId}>
              {a.fullName} ({a.rollNo})
            </li>
          ))}
        </ul>
        <p>
          <strong>There is no undo.</strong> Selected is final: it cannot be changed back.
        </p>
        {fullTime ? (
          <>
            <p>This is a full-time drive, so each selection becomes an offer that placement policies count.</p>
            <p>
              Because of policies, these students' other pending full-time applications may become Ineligible. You will
              see the list afterwards.
            </p>
          </>
        ) : (
          <p>This is an internship, so it doesn't count as an offer and placement policies are not checked.</p>
        )}
        <p>It is all or nothing: if any student is blocked, nobody is selected.</p>
      </ConfirmDialog>
    </div>
  );
}
