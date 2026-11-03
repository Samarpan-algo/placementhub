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
import { formatDateTime } from '../../utils/format.js';
import { ROUND_RESULTS, roundLabel, sheetPlacement, withStudentNames } from '../../utils/rounds.js';

const REMARKS_MAX = 500;

/**
 * One round's results sheet: every applicant who can get a result here, with a result select and remarks,
 * saved in ONE request. Only rows that differ from what is saved are sent. Edits live in `drafts`, apart from
 * the backend's data, so a refetch after a failed save keeps what the admin typed.
 */
export default function RoundResultsPage() {
  const { id, roundId } = useParams();
  const drive = useApi(`/api/drives/${id}`);
  const rounds = useApi(`/api/drives/${id}/rounds`);
  const applicants = useApi(`/api/drives/${id}/applications`, { allPages: true });

  const [drafts, setDrafts] = useState({}); // { applicationId: { result, remarks } }
  const [message, setMessage] = useState(null);
  const [saveError, setSaveError] = useState(null);
  const [showMissing, setShowMissing] = useState(false);
  const [confirming, setConfirming] = useState(false);
  const [saving, setSaving] = useState(false);

  function reloadAll() {
    drive.reload();
    rounds.reload();
    applicants.reload();
  }

  const loadError = drive.error ?? rounds.error ?? applicants.error;
  if (loadError) return <ErrorAlert error={loadError} onRetry={reloadAll} />;
  if (!drive.data || !rounds.data || !applicants.data) return <Loading text="Loading the results sheet…" />;

  const backLink = (
    <p>
      <Link to={`/admin/drives/${id}/rounds`}>← Back to rounds</Link>
    </p>
  );
  const round = rounds.data.find((r) => String(r.id) === roundId);
  if (!round) {
    return (
      <div>
        {backLink}
        <ErrorAlert error={{ message: `Round ${roundId} is not part of this drive.`, details: [] }} />
      </div>
    );
  }

  const heading = (
    <div>
      {backLink}
      <h1>Results for {roundLabel(round)}</h1>
      <p className="muted">
        {drive.data.roleTitle} at {drive.data.companyName} · <StatusBadge status={drive.data.status} /> · Scheduled{' '}
        {formatDateTime(round.scheduledAt)}
      </p>
    </div>
  );

  if (drive.data.status !== 'CLOSED') {
    return (
      <div className="stack">
        {heading}
        <div className="alert alert-warning">
          Round results can be recorded only while the drive is Closed. To see where each student stands, open the{' '}
          <Link to={`/admin/drives/${id}/applicants`}>applicants list</Link>.
        </div>
      </div>
    );
  }

  // Split the applicants, then work out each sheet row's current values (draft over saved) and what changed
  const rows = [];
  const notOnSheet = [];
  for (const applicant of applicants.data.content) {
    const placement = sheetPlacement(applicant, round);
    if (!placement.onSheet) {
      notOnSheet.push({ applicant, why: placement.why });
      continue;
    }
    const { saved } = placement;
    const draft = drafts[applicant.applicationId];
    const result = draft?.result ?? saved?.result ?? '';
    const remarks = draft?.remarks ?? saved?.remarks ?? '';
    const changed = result !== (saved?.result ?? '') || remarks.trim() !== (saved?.remarks ?? '');
    rows.push({ applicant, saved, result, remarks, changed });
  }
  const toSend = rows.filter((row) => row.changed && row.result);
  const missingResult = rows.filter((row) => row.changed && !row.result);
  const newCount = toSend.filter((row) => !row.saved).length;
  const correctionCount = toSend.length - newCount;

  function editRow(row, change) {
    setMessage(null);
    setDrafts({
      ...drafts,
      [row.applicant.applicationId]: { result: row.result, remarks: row.remarks, ...change },
    });
  }

  function handleSaveClick() {
    setMessage(null);
    setSaveError(null);
    if (missingResult.length > 0) {
      setShowMissing(true);
      return;
    }
    setShowMissing(false);
    setConfirming(true);
  }

  // Worked out on every render, so a name leaves the alert as soon as that row is fixed
  const missingError =
    showMissing && missingResult.length > 0
      ? {
          message: 'Choose a result for these students, or clear their remarks:',
          details: missingResult.map((row) => `${row.applicant.fullName} (${row.applicant.rollNo})`),
        }
      : null;

  async function confirmSave() {
    setSaving(true);
    setSaveError(null);
    const body = {
      results: toSend.map((row) => ({
        applicationId: row.applicant.applicationId,
        result: row.result,
        remarks: row.remarks.trim() || null,
      })),
    };
    try {
      const response = await api.put(`/api/drives/${id}/rounds/${round.id}/results`, body);
      const saved = response.data.results.length;
      setDrafts({});
      setMessage(`Saved ${saved} result${saved === 1 ? '' : 's'} for ${roundLabel(round)}. Statuses were updated.`);
    } catch (err) {
      // All or nothing: nothing was saved, so the drafts stay. 400/409 lines say "Application 12 ..."
      setSaveError(withStudentNames(toApiError(err), applicants.data.content));
    } finally {
      setSaving(false);
      setConfirming(false);
      reloadAll(); // either way, show the sheet as the backend has it now
    }
  }

  return (
    <div className="stack">
      {heading}
      {message && <div className="alert alert-success">{message}</div>}
      <ErrorAlert error={missingError ?? saveError} />

      {applicants.data.content.length === 0 && <EmptyState title="No one has applied to this drive." />}
      {applicants.data.content.length > 0 && rows.length === 0 && (
        <EmptyState title="No applicant can get a result in this round right now.">See the reasons below.</EmptyState>
      )}

      {rows.length > 0 && (
        <>
          <div className="table-wrap card">
            <table className="table">
              <thead>
                <tr>
                  <th>Roll no</th>
                  <th>Student</th>
                  <th>Status</th>
                  <th>Result</th>
                  <th>Remarks (optional)</th>
                </tr>
              </thead>
              <tbody>
                {rows.map((row) => (
                  <tr key={row.applicant.applicationId}>
                    <td className="nowrap">{row.applicant.rollNo}</td>
                    <td>
                      <strong>{row.applicant.fullName}</strong>
                      <div className="muted">{row.applicant.branch}</div>
                    </td>
                    <td>
                      <StatusBadge status={row.applicant.status} />
                    </td>
                    <td>
                      <select
                        aria-label={`Result for ${row.applicant.fullName}`}
                        value={row.result}
                        onChange={(e) => editRow(row, { result: e.target.value })}
                      >
                        {/* A saved result can be corrected but not removed: the backend has no delete */}
                        {!row.saved && <option value="">Choose…</option>}
                        {ROUND_RESULTS.map((option) => (
                          <option key={option.value} value={option.value}>
                            {option.label}
                          </option>
                        ))}
                      </select>
                      {row.changed && <div className="hint">Not saved yet</div>}
                    </td>
                    <td>
                      <input
                        type="text"
                        className="remarks-input"
                        aria-label={`Remarks for ${row.applicant.fullName}`}
                        maxLength={REMARKS_MAX}
                        value={row.remarks}
                        onChange={(e) => editRow(row, { remarks: e.target.value })}
                      />
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
              onClick={handleSaveClick}
              disabled={applicants.loading || saving || (toSend.length === 0 && missingResult.length === 0)}
            >
              Save {toSend.length > 0 ? `${toSend.length} ` : ''}result{toSend.length === 1 ? '' : 's'}
            </button>
          </div>
        </>
      )}

      <ApplicantReasonTable title="Not on this sheet" rows={notOnSheet} />

      <ConfirmDialog
        open={confirming}
        title={`Save results for ${roundLabel(round)}?`}
        confirmLabel="Save results"
        busyLabel="Saving…"
        busy={saving}
        onConfirm={confirmSave}
        onCancel={() => setConfirming(false)}
      >
        <p>
          {newCount} new result{newCount === 1 ? '' : 's'} and {correctionCount} correction
          {correctionCount === 1 ? '' : 's'} will be saved. It is all or nothing: one problem and nothing is saved.
        </p>
        <p>Each student's status is recalculated: Not cleared or Absent means Rejected, Cleared means Shortlisted.</p>
        <p>
          Students see these results. A result can be corrected only until the student has a result in a later round
          or is selected.
        </p>
      </ConfirmDialog>
    </div>
  );
}
