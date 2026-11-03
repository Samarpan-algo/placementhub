import { useState } from 'react';
import { Link, useParams } from 'react-router';
import api from '../../api/client.js';
import { fieldErrorsFrom, toApiError } from '../../api/errors.js';
import { useApi } from '../../api/useApi.js';
import ConfirmDialog from '../../components/ConfirmDialog.jsx';
import EmptyState from '../../components/EmptyState.jsx';
import ErrorAlert from '../../components/ErrorAlert.jsx';
import Field from '../../components/Field.jsx';
import Loading from '../../components/Loading.jsx';
import StatusBadge from '../../components/StatusBadge.jsx';
import { formatDateTime, isoToIstInput, istInputToIso } from '../../utils/format.js';
import { ROUND_EDITABLE_STATUSES, roundLabel } from '../../utils/rounds.js';

const FIELDS = ['name', 'scheduledAt'];

/** The same rules as RoundCreateRequest / RoundUpdateRequest (the backend checks again). */
function validate(name, scheduledAt) {
  const errors = {};
  if (!name) errors.name = 'Name is required';
  else if (name.length > 100) errors.name = 'At most 100 characters';
  if (scheduledAt && !istInputToIso(scheduledAt)) errors.scheduledAt = 'Choose a date and time';
  return errors;
}

/**
 * Add the next round (round = null) or edit one. Adding asks first, because rounds can't be deleted.
 * roundNo isn't typed: numbers must have no gaps, so nextRoundNo (from the backend's list) is the only valid one.
 */
function RoundForm({ driveId, round, nextRoundNo, onSaved, onFailed, onCancel }) {
  const [name, setName] = useState(round?.name ?? '');
  const [scheduledAt, setScheduledAt] = useState(isoToIstInput(round?.scheduledAt));
  const [fieldErrors, setFieldErrors] = useState({});
  const [error, setError] = useState(null);
  const [confirming, setConfirming] = useState(false);
  const [saving, setSaving] = useState(false);

  async function save() {
    setSaving(true);
    setError(null);
    const body = { name: name.trim(), scheduledAt: scheduledAt ? istInputToIso(scheduledAt) : null };
    try {
      const response = round
        ? await api.put(`/api/drives/${driveId}/rounds/${round.id}`, body)
        : await api.post(`/api/drives/${driveId}/rounds`, { roundNo: nextRoundNo, ...body });
      onSaved(response.data);
    } catch (err) {
      // 400: messages go under their fields; 409 (wrong drive status, number taken) and the rest: the top alert
      const split = fieldErrorsFrom(toApiError(err), FIELDS);
      setFieldErrors(split.fieldErrors);
      setError(split.error);
      setSaving(false);
      setConfirming(false);
      onFailed(); // someone else may have added a round: refresh the list and the next number
    }
  }

  function handleSubmit(event) {
    event.preventDefault();
    const errors = validate(name.trim(), scheduledAt);
    setFieldErrors(errors);
    if (Object.keys(errors).length > 0) {
      setError({ message: 'Please fix the highlighted fields.', details: [] });
      return;
    }
    setError(null);
    if (round) save();
    else setConfirming(true);
  }

  return (
    <section className="card">
      <h2>{round ? `Edit Round ${round.roundNo}` : `Add Round ${nextRoundNo}`}</h2>
      <form className="form" onSubmit={handleSubmit} noValidate>
        <ErrorAlert error={error} />
        <div className="form-row">
          <Field id="roundName" label="Name" error={fieldErrors.name}>
            <input
              id="roundName"
              placeholder="e.g. Technical interview"
              value={name}
              onChange={(e) => setName(e.target.value)}
            />
          </Field>
          <Field
            id="roundScheduledAt"
            label="Scheduled time (IST, optional)"
            error={fieldErrors.scheduledAt}
            hint="Indian time."
          >
            <input
              id="roundScheduledAt"
              type="datetime-local"
              value={scheduledAt}
              onChange={(e) => setScheduledAt(e.target.value)}
            />
          </Field>
        </div>
        <div className="button-row">
          <button type="button" className="btn btn-secondary" onClick={onCancel} disabled={saving}>
            Cancel
          </button>
          <button type="submit" className="btn btn-primary" disabled={saving}>
            {saving ? 'Saving…' : round ? 'Save changes' : 'Add round'}
          </button>
        </div>
      </form>

      <ConfirmDialog
        open={confirming}
        title={`Add Round ${nextRoundNo}?`}
        confirmLabel="Add round"
        busyLabel="Adding…"
        busy={saving}
        onConfirm={save}
        onCancel={() => setConfirming(false)}
      >
        <p>
          <strong>
            Round {nextRoundNo}: {name.trim()}
          </strong>
        </p>
        <p>Rounds cannot be deleted or renumbered. You can only change the name and time later.</p>
        <p>This becomes the drive's final round: from now on, students must clear it before they can be selected.</p>
      </ConfirmDialog>
    </section>
  );
}

/** One drive's rounds: the list, add and edit (OPEN or CLOSED drives), and links to each round's results sheet. */
export default function RoundsPage() {
  const { id } = useParams();
  const drive = useApi(`/api/drives/${id}`);
  const rounds = useApi(`/api/drives/${id}/rounds`);

  // null = no form; { round: null } = adding; { round } = editing that round
  const [editing, setEditing] = useState(null);
  const [message, setMessage] = useState(null);

  function openForm(round) {
    setMessage(null);
    setEditing({ round });
  }

  function reloadAll() {
    drive.reload();
    rounds.reload();
  }

  function handleSaved(saved) {
    setMessage(editing.round ? `Saved ${roundLabel(saved)}.` : `Added ${roundLabel(saved)}.`);
    setEditing(null);
    reloadAll(); // show the list as the backend has it now
  }

  const loadError = drive.error ?? rounds.error;
  if (loadError) return <ErrorAlert error={loadError} onRetry={reloadAll} />;
  if (!drive.data || !rounds.data) return <Loading text="Loading rounds…" />;

  const editable = ROUND_EDITABLE_STATUSES.includes(drive.data.status);
  const closed = drive.data.status === 'CLOSED';
  // Numbers have no gaps, and the backend returns the list in order, so the next one is the last + 1
  const nextRoundNo = rounds.data.length > 0 ? rounds.data[rounds.data.length - 1].roundNo + 1 : 1;

  return (
    <div className="stack">
      <div>
        <p>
          <Link to={`/admin/drives/${id}`}>← Back to the drive</Link>
        </p>
        <div className="page-heading">
          <div>
            <h1>Rounds</h1>
            <p className="muted">
              {drive.data.roleTitle} at {drive.data.companyName} · <StatusBadge status={drive.data.status} />
            </p>
          </div>
          <div className="button-row">
            {closed && (
              <Link to={`/admin/drives/${id}/selection`} className="btn btn-secondary">
                Selection
              </Link>
            )}
            {editable && !editing && (
              <button type="button" className="btn btn-primary" onClick={() => openForm(null)}>
                Add round
              </button>
            )}
          </div>
        </div>
      </div>

      {message && <div className="alert alert-success">{message}</div>}
      {!editable && (
        <div className="alert alert-warning">
          Rounds can be added or edited only while the drive is Open or Closed. This drive is{' '}
          {drive.data.status === 'DRAFT' ? 'still a draft' : 'completed'}.
        </div>
      )}
      {drive.data.status === 'OPEN' && (
        <div className="alert alert-warning">
          You can plan rounds now. Results can be entered once registration is closed.
        </div>
      )}

      {editing && editable && (
        <RoundForm
          // key: a different round (or "new") starts a fresh form instead of keeping the old values
          key={editing.round?.id ?? 'new'}
          driveId={id}
          round={editing.round}
          nextRoundNo={nextRoundNo}
          onSaved={handleSaved}
          onFailed={reloadAll}
          onCancel={() => setEditing(null)}
        />
      )}

      {rounds.data.length === 0 ? (
        <EmptyState title="This drive has no rounds yet.">
          {editable ? 'Add the first one with "Add round". ' : ''}
          Without rounds, any Applied or Shortlisted student can be selected directly.
        </EmptyState>
      ) : (
        <div className="table-wrap card">
          <table className="table">
            <thead>
              <tr>
                <th>No.</th>
                <th>Name</th>
                <th>Scheduled</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {rounds.data.map((round) => (
                <tr key={round.id}>
                  <td>{round.roundNo}</td>
                  <td>
                    <strong>{round.name}</strong>
                  </td>
                  <td>{formatDateTime(round.scheduledAt)}</td>
                  <td className="actions">
                    {closed && (
                      <Link to={`/admin/drives/${id}/rounds/${round.id}/results`} className="btn btn-primary btn-small">
                        Enter results
                      </Link>
                    )}{' '}
                    {editable && (
                      <button
                        type="button"
                        className="btn btn-secondary btn-small"
                        onClick={() => openForm(round)}
                        disabled={Boolean(editing)}
                      >
                        Edit
                      </button>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
