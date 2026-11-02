import { useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router';
import api from '../../api/client.js';
import { fieldErrorsFrom, toApiError } from '../../api/errors.js';
import { useApi } from '../../api/useApi.js';
import EmptyState from '../../components/EmptyState.jsx';
import ErrorAlert from '../../components/ErrorAlert.jsx';
import Field from '../../components/Field.jsx';
import Loading from '../../components/Loading.jsx';
import StatusBadge from '../../components/StatusBadge.jsx';
import { BRANCHES } from '../../utils/branches.js';
import {
  DRIVE_FIELDS,
  driveToForm,
  EDITABLE_STATUSES,
  emptyDriveForm,
  formToDriveRequest,
  JOB_TYPES,
  validateDriveForm,
} from '../../utils/drives.js';

/** The success message for the drive page after an edit, including what the re-evaluation did. */
function savedMessage(saved) {
  const count = saved.applicationsMadeIneligible;
  // The backend re-checks pending applications only on an OPEN drive (and only if a criterion changed)
  if (saved.status !== 'OPEN' || count == null) {
    return 'Drive saved.';
  }
  if (count === 0) {
    return 'Drive saved. No pending application became ineligible.';
  }
  return (
    `Drive saved. ${count} pending application${count === 1 ? '' : 's'} no longer ` +
    `meet${count === 1 ? 's' : ''} the criteria and ${count === 1 ? 'is' : 'are'} now INELIGIBLE ` +
    '(the reason is shown on each application).'
  );
}

/**
 * The drive form, for both create (companies = the company options) and edit (drive = the saved drive).
 * onSaved(savedDrive) after the backend accepted it.
 */
function DriveForm({ drive, companies, onSaved, cancelTo }) {
  const isEdit = Boolean(drive);
  const [form, setForm] = useState(() => (isEdit ? driveToForm(drive) : emptyDriveForm()));
  const [fieldErrors, setFieldErrors] = useState({});
  const [error, setError] = useState(null);
  const [saving, setSaving] = useState(false);

  // DriveService: the job type can change only while DRAFT (it decides what counts as an offer)
  const jobTypeLocked = isEdit && drive.status !== 'DRAFT';

  function handleChange(event) {
    setForm({ ...form, [event.target.name]: event.target.value });
  }

  function toggleBranch(code) {
    const branches = form.branches.includes(code)
      ? form.branches.filter((b) => b !== code)
      : [...form.branches, code];
    setForm({ ...form, branches });
  }

  async function handleSubmit(event) {
    event.preventDefault();
    const errors = validateDriveForm(form, { requireCompany: !isEdit });
    setFieldErrors(errors);
    if (Object.keys(errors).length > 0) {
      setError({ message: 'Please fix the highlighted fields.', details: [] });
      return;
    }
    setError(null);
    setSaving(true);
    const body = formToDriveRequest(form, { includeCompany: !isEdit });
    try {
      const response = isEdit ? await api.put(`/api/drives/${drive.id}`, body) : await api.post('/api/drives', body);
      onSaved(response.data);
    } catch (err) {
      // 400: under the fields (anything unmatched stays on top); 409 (e.g. "Cannot save OPEN drive: ..."): on top
      const split = fieldErrorsFrom(toApiError(err), DRIVE_FIELDS);
      setFieldErrors(split.fieldErrors);
      setError(split.error);
      setSaving(false);
    }
  }

  return (
    <form className="form" onSubmit={handleSubmit} noValidate>
      <ErrorAlert error={error} />

      {isEdit ? (
        <p>
          <span className="muted">Company:</span> <strong>{drive.companyName}</strong>{' '}
          <span className="muted">(cannot be changed)</span>
        </p>
      ) : (
        <Field id="companyId" label="Company" error={fieldErrors.companyId}>
          <select id="companyId" name="companyId" value={form.companyId} onChange={handleChange}>
            <option value="">Choose a company…</option>
            {companies.map((company) => (
              <option key={company.id} value={company.id}>
                {company.name}
              </option>
            ))}
          </select>
        </Field>
      )}

      <div className="form-row">
        <Field id="roleTitle" label="Role title" error={fieldErrors.roleTitle}>
          <input id="roleTitle" name="roleTitle" value={form.roleTitle} onChange={handleChange} />
        </Field>
        <Field
          id="jobType"
          label="Job type"
          error={fieldErrors.jobType}
          hint={jobTypeLocked ? 'Can be changed only while the drive is a draft' : undefined}
        >
          <select id="jobType" name="jobType" value={form.jobType} onChange={handleChange} disabled={jobTypeLocked}>
            {JOB_TYPES.map((type) => (
              <option key={type.value} value={type.value}>
                {type.label}
              </option>
            ))}
          </select>
        </Field>
      </div>

      <div className="form-row">
        <Field id="ctcLpa" label="CTC (LPA)" error={fieldErrors.ctcLpa} hint="Lakhs per annum, e.g. 12.50">
          <input id="ctcLpa" name="ctcLpa" inputMode="decimal" value={form.ctcLpa} onChange={handleChange} />
        </Field>
        <Field id="location" label="Location (optional)" error={fieldErrors.location}>
          <input id="location" name="location" value={form.location} onChange={handleChange} />
        </Field>
      </div>

      <Field id="description" label="Description (optional)" error={fieldErrors.description}>
        <textarea id="description" name="description" rows={4} value={form.description} onChange={handleChange} />
      </Field>

      <fieldset className="fieldset">
        <legend>Eligibility criteria</legend>
        {isEdit && drive.status === 'OPEN' && (
          <div className="alert alert-warning">
            This drive is <strong>open</strong>. If you change the branches, minimum CGPA, maximum backlogs or
            graduation year, every pending application is checked again, and those that no longer qualify become{' '}
            <strong>Ineligible</strong> (with the reason).
          </div>
        )}
        <div className="form-row">
          <Field id="minCgpa" label="Minimum CGPA" error={fieldErrors.minCgpa} hint="0 to 10">
            <input id="minCgpa" name="minCgpa" inputMode="decimal" value={form.minCgpa} onChange={handleChange} />
          </Field>
          <Field id="maxBacklogs" label="Maximum active backlogs" error={fieldErrors.maxBacklogs} hint="0 to 50">
            <input
              id="maxBacklogs"
              name="maxBacklogs"
              inputMode="numeric"
              value={form.maxBacklogs}
              onChange={handleChange}
            />
          </Field>
        </div>
        <Field id="graduationYear" label="Graduation year" error={fieldErrors.graduationYear}>
          <input
            id="graduationYear"
            name="graduationYear"
            inputMode="numeric"
            value={form.graduationYear}
            onChange={handleChange}
          />
        </Field>
        <div className="field">
          <span className="field-label">Eligible branches</span>
          <div className="checkbox-group">
            {BRANCHES.map((branch) => (
              <label key={branch.code} className="checkbox">
                <input
                  type="checkbox"
                  checked={form.branches.includes(branch.code)}
                  onChange={() => toggleBranch(branch.code)}
                />
                {branch.code} ({branch.name})
              </label>
            ))}
          </div>
          {fieldErrors.branches ? (
            <span className="field-error">{fieldErrors.branches}</span>
          ) : (
            <span className="hint">At least one branch is needed before the drive can be opened.</span>
          )}
        </div>
      </fieldset>

      <div className="form-row">
        <Field
          id="registrationDeadline"
          label="Registration deadline (IST)"
          error={fieldErrors.registrationDeadline}
          hint="Indian time. Must be in the future to open the drive."
        >
          <input
            id="registrationDeadline"
            name="registrationDeadline"
            type="datetime-local"
            value={form.registrationDeadline}
            onChange={handleChange}
          />
        </Field>
        <Field id="driveDate" label="Drive date (optional)" error={fieldErrors.driveDate}>
          <input id="driveDate" name="driveDate" type="date" value={form.driveDate} onChange={handleChange} />
        </Field>
      </div>

      <div className="button-row">
        <Link to={cancelTo} className="btn btn-secondary">
          Cancel
        </Link>
        <button type="submit" className="btn btn-primary" disabled={saving}>
          {saving ? 'Saving…' : isEdit ? 'Save changes' : 'Create drive'}
        </button>
      </div>
    </form>
  );
}

function CreateDrive() {
  const navigate = useNavigate();
  // The backend caps a page at 100: enough companies for one college (a known limitation)
  const { data, loading, error, reload } = useApi('/api/companies?size=100');

  if (error) return <ErrorAlert error={error} onRetry={reload} />;
  if (loading && !data) return <Loading text="Loading companies…" />;
  if (data.content.length === 0) {
    return (
      <EmptyState title="Add a company first.">
        <Link to="/admin/companies">Go to Companies</Link>
      </EmptyState>
    );
  }

  return (
    <section className="card">
      <h1>New drive</h1>
      <p className="muted">The drive starts as a draft: students see it only after you open registration.</p>
      {data.totalElements > data.content.length && (
        <div className="alert alert-warning">Only the first {data.content.length} companies (A–Z) are listed.</div>
      )}
      <DriveForm
        companies={data.content}
        cancelTo="/admin/drives"
        onSaved={(saved) =>
          navigate(`/admin/drives/${saved.id}`, {
            state: { message: 'Drive created as a draft. Open registration when it is ready.' },
          })
        }
      />
    </section>
  );
}

function EditDrive({ id }) {
  const navigate = useNavigate();
  const { data: drive, loading, error, reload } = useApi(`/api/drives/${id}`);

  if (error) return <ErrorAlert error={error} onRetry={reload} />;
  if (loading && !drive) return <Loading text="Loading drive…" />;

  if (!EDITABLE_STATUSES.includes(drive.status)) {
    return (
      <section className="card">
        <h1>Edit drive</h1>
        <div className="alert alert-warning">
          This drive is <StatusBadge status={drive.status} />. Only draft or open drives can be edited.
        </div>
        <Link to={`/admin/drives/${drive.id}`}>Back to the drive</Link>
      </section>
    );
  }

  return (
    <section className="card">
      <div className="page-heading">
        <h1>Edit drive</h1>
        <StatusBadge status={drive.status} />
      </div>
      <DriveForm
        drive={drive}
        cancelTo={`/admin/drives/${drive.id}`}
        onSaved={(saved) => navigate(`/admin/drives/${saved.id}`, { state: { message: savedMessage(saved) } })}
      />
    </section>
  );
}

/** /admin/drives/new and /admin/drives/:id/edit. */
export default function DriveFormPage() {
  const { id } = useParams();
  return id ? <EditDrive id={id} /> : <CreateDrive />;
}
