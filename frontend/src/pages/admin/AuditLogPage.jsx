import { useState } from 'react';
import { useSearchParams } from 'react-router';
import { useApi } from '../../api/useApi.js';
import EmptyState from '../../components/EmptyState.jsx';
import ErrorAlert from '../../components/ErrorAlert.jsx';
import Field from '../../components/Field.jsx';
import Loading from '../../components/Loading.jsx';
import Pagination from '../../components/Pagination.jsx';
import { actionLabel, describeChange, ENTITY_TYPES, entityLabel } from '../../utils/audit.js';
import { formatDateTime, istDayEndExclusiveIso, istDayStartIso } from '../../utils/format.js';
import { useFormErrors } from '../../utils/useFormErrors.js';

const PAGE_SIZE = 20;

/**
 * The filters the backend supports. Applied with the button, not on every keystroke, so a half-typed id isn't
 * sent. The page gives it a key built from the URL's values, so "Clear filters" resets what's typed.
 */
function AuditFilters({ values, onApply, onClear }) {
  const [form, setForm] = useState(values);
  const { fieldErrors: errors, setFieldErrors: setErrors, clearFieldError } = useFormErrors();

  function handleChange(event) {
    const next = { ...form, [event.target.name]: event.target.value };
    if (!next.entityType) {
      next.entityId = ''; // the backend needs a type for an id: ids repeat across types
      clearFieldError('entityId');
    }
    setForm(next);
    clearFieldError(event.target.name);
    if (event.target.name === 'from') clearFieldError('to'); // that message is about both days
  }

  function handleSubmit(event) {
    event.preventDefault();
    const entityId = form.entityId.trim();
    const found = {};
    if (entityId && !/^[1-9]\d{0,17}$/.test(entityId)) found.entityId = 'A whole number, e.g. 12';
    // "YYYY-MM-DD" strings compare correctly as text. The same day twice is fine: it means that one whole day.
    if (form.from && form.to && form.from > form.to) found.to = 'Cannot be before the From day';
    setErrors(found);
    if (Object.keys(found).length === 0) onApply({ ...form, entityId });
  }

  const filtered = Object.values(values).some(Boolean);

  return (
    <form className="filter-bar" onSubmit={handleSubmit} noValidate>
      <Field id="entityType" label="Entity type">
        <select id="entityType" name="entityType" value={form.entityType} onChange={handleChange}>
          <option value="">All types</option>
          {ENTITY_TYPES.map((type) => (
            <option key={type.value} value={type.value}>
              {type.label}
            </option>
          ))}
        </select>
      </Field>
      <Field id="entityId" label="Entity ID" error={errors.entityId} hint={form.entityType ? null : 'Choose a type first'}>
        <input
          id="entityId"
          name="entityId"
          inputMode="numeric"
          value={form.entityId}
          onChange={handleChange}
          disabled={!form.entityType}
        />
      </Field>
      <Field id="from" label="From (IST day)">
        <input id="from" name="from" type="date" value={form.from} onChange={handleChange} />
      </Field>
      <Field id="to" label="To (IST day, included)" error={errors.to}>
        <input id="to" name="to" type="date" value={form.to} onChange={handleChange} />
      </Field>
      <button type="submit" className="btn btn-secondary">
        Apply
      </button>
      {filtered && (
        <button type="button" className="btn btn-secondary" onClick={onClear}>
          Clear filters
        </button>
      )}
    </form>
  );
}

/** The "old → new" list of one audit row. */
function Changes({ row }) {
  const { context, changes, notes } = describeChange(row);
  return (
    <>
      {context && <div className="muted">{context}</div>}
      {changes.length === 0 && notes.length === 0 && <span className="muted">No field changes</span>}
      {(changes.length > 0 || notes.length > 0) && (
        <ul className="change-list">
          {changes.map((change) => (
            <li key={change.field}>
              <strong>{change.field}:</strong> {change.from} → {change.to}
            </li>
          ))}
          {notes.map((note) => (
            <li key={note.field} className="muted">
              {note.field}: {note.value}
            </li>
          ))}
        </ul>
      )}
    </>
  );
}

/** The audit log, newest first (the backend's fixed order). Filters and page live in the URL. */
export default function AuditLogPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const values = {
    entityType: searchParams.get('entityType') ?? '',
    entityId: searchParams.get('entityId') ?? '',
    from: searchParams.get('from') ?? '',
    to: searchParams.get('to') ?? '',
  };
  const page = Math.max(Number.parseInt(searchParams.get('page') ?? '1', 10) || 1, 1) - 1;

  // The URL holds IST days; the backend wants instants. "to" is exclusive there, so it becomes the next day's start
  const query = new URLSearchParams({ page: String(page), size: String(PAGE_SIZE) });
  if (values.entityType) query.set('entityType', values.entityType);
  if (values.entityId) query.set('entityId', values.entityId);
  const fromIso = values.from && istDayStartIso(values.from);
  const toIso = values.to && istDayEndExclusiveIso(values.to);
  if (fromIso) query.set('from', fromIso);
  if (toIso) query.set('to', toIso);

  const { data, loading, error, reload } = useApi(`/api/audit?${query}`);

  function changeParams(next, nextPage) {
    const params = {};
    for (const [key, value] of Object.entries(next)) {
      if (value) params[key] = value;
    }
    if (nextPage > 0) params.page = String(nextPage + 1);
    setSearchParams(params);
  }

  const filtered = Object.values(values).some(Boolean);

  return (
    <div>
      <div className="page-heading">
        <h1>Audit log</h1>
      </div>
      <AuditFilters
        key={JSON.stringify(values)}
        values={values}
        onApply={(next) => changeParams(next, 0)}
        onClear={() => setSearchParams({})}
      />

      {/* e.g. a 400 for a bad entity type typed into the URL */}
      {error && <ErrorAlert error={error} onRetry={reload} />}
      {!error && loading && !data && <Loading text="Loading the audit log…" />}

      {data && data.content.length === 0 && (
        <EmptyState
          title={
            data.totalElements > 0
              ? 'There are no rows on this page.'
              : filtered
                ? 'No audit rows match these filters.'
                : 'Nothing has been recorded yet.'
          }
        >
          {data.totalElements > 0 && (
            <button type="button" className="btn btn-secondary" onClick={() => changeParams(values, 0)}>
              Go to page 1
            </button>
          )}
        </EmptyState>
      )}

      {data && data.content.length > 0 && (
        <>
          <p className="muted">
            {data.totalElements} row{data.totalElements === 1 ? '' : 's'}, newest first. Times are in IST.
          </p>
          <div className="table-wrap card">
            <table className="table">
              <thead>
                <tr>
                  <th>When</th>
                  <th>Who</th>
                  <th>What</th>
                  <th>Entity</th>
                  <th>Old → new</th>
                </tr>
              </thead>
              <tbody>
                {data.content.map((row) => (
                  <tr key={row.id}>
                    <td>{formatDateTime(row.createdAt)}</td>
                    {/* no actor means the system did it */}
                    <td>{row.actorEmail ?? <span className="muted">System</span>}</td>
                    <td>{actionLabel(row.action)}</td>
                    <td className="nowrap">{entityLabel(row)}</td>
                    <td className="cell-text">
                      <Changes row={row} />
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <Pagination
            page={data.page}
            totalPages={data.totalPages}
            disabled={loading}
            onChange={(next) => changeParams(values, next)}
          />
        </>
      )}
    </div>
  );
}
