import { useState } from 'react';
import api from '../../api/client.js';
import { FIX_FIELDS_MESSAGE, toApiError } from '../../api/errors.js';
import { openResume, resumeFileProblem } from '../../api/resume.js';
import { useApi } from '../../api/useApi.js';
import ErrorAlert from '../../components/ErrorAlert.jsx';
import Field from '../../components/Field.jsx';
import Loading from '../../components/Loading.jsx';
import { branchLabel } from '../../utils/branches.js';
import { formatDateTime } from '../../utils/format.js';
import { useFormErrors } from '../../utils/useFormErrors.js';

const PHONE_PATTERN = /^\+?[0-9]{10,14}$/; // same regex as StudentProfileUpdateRequest.phone

/** The same rules as StudentProfileUpdateRequest (the backend checks again). Expects trimmed values. */
function validate(form) {
  const errors = {};
  if (!form.fullName) errors.fullName = 'Full name is required';
  else if (form.fullName.length > 100) errors.fullName = 'At most 100 characters';
  if (form.phone && !PHONE_PATTERN.test(form.phone)) errors.phone = '10 to 14 digits, optionally starting with +';
  return errors;
}

/** Name and phone, the only fields a student may change. */
function EditForm({ profile, onSaved, onCancel }) {
  // Start from the saved values; `?? ''` because an input's value can't be null
  const [form, setForm] = useState({ fullName: profile.fullName, phone: profile.phone ?? '' });
  const { fieldErrors, setFieldErrors, error, setError, clearFieldError } = useFormErrors();
  const [saving, setSaving] = useState(false);

  function handleChange(event) {
    setForm({ ...form, [event.target.name]: event.target.value });
    clearFieldError(event.target.name);
  }

  async function handleSubmit(event) {
    event.preventDefault();
    const values = { fullName: form.fullName.trim(), phone: form.phone.trim() };
    setForm(values);
    const errors = validate(values);
    setFieldErrors(errors);
    if (Object.keys(errors).length > 0) {
      setError({ message: FIX_FIELDS_MESSAGE, details: [] });
      return;
    }
    setError(null);
    setSaving(true);
    try {
      // An empty phone is sent as null, which removes it
      await api.put('/api/students/me', { fullName: values.fullName, phone: values.phone || null });
      onSaved();
    } catch (err) {
      setError(toApiError(err));
      setSaving(false);
    }
  }

  return (
    <form className="form" onSubmit={handleSubmit} noValidate>
      <ErrorAlert error={error} />
      <Field id="fullName" label="Full name" error={fieldErrors.fullName}>
        <input id="fullName" name="fullName" autoComplete="name" value={form.fullName} onChange={handleChange} />
      </Field>
      <Field id="phone" label="Phone (optional)" error={fieldErrors.phone} hint="Leave empty to remove it">
        <input
          id="phone"
          name="phone"
          type="tel"
          autoComplete="tel"
          placeholder="+919876543210"
          value={form.phone}
          onChange={handleChange}
        />
      </Field>
      <div className="button-row">
        <button type="button" className="btn btn-secondary" onClick={onCancel} disabled={saving}>
          Cancel
        </button>
        <button type="submit" className="btn btn-primary" disabled={saving}>
          {saving ? 'Saving…' : 'Save changes'}
        </button>
      </div>
    </form>
  );
}

/** Upload, replace and view the student's resume. Each action has its own busy state and error. */
function ResumeSection({ hasResume, updatedAt, onUploaded }) {
  const [file, setFile] = useState(null);
  const [inputKey, setInputKey] = useState(0); // changing the key empties the file input after an upload
  const [uploading, setUploading] = useState(false);
  const [opening, setOpening] = useState(false);
  const [error, setError] = useState(null);
  const [message, setMessage] = useState(null);

  function handleFileChange(event) {
    const chosen = event.target.files[0] ?? null;
    setMessage(null);
    const problem = chosen && resumeFileProblem(chosen);
    setError(problem ? { message: problem, details: [] } : null);
    setFile(problem ? null : chosen);
  }

  async function handleUpload(event) {
    event.preventDefault();
    setUploading(true);
    setError(null);
    setMessage(null);
    try {
      const form = new FormData();
      form.append('file', file);
      await api.post('/api/students/me/resume', form); // axios sets the multipart Content-Type itself
      setFile(null);
      setInputKey((key) => key + 1);
      setMessage(hasResume ? 'Resume replaced.' : 'Resume uploaded.');
      onUploaded();
    } catch (err) {
      setError(toApiError(err));
    } finally {
      setUploading(false);
    }
  }

  async function handleView() {
    setOpening(true);
    setError(null);
    setMessage(null);
    try {
      await openResume('/api/students/me/resume');
    } catch (apiError) {
      setError(apiError);
    } finally {
      setOpening(false);
    }
  }

  return (
    <section className="card">
      <h2>Resume</h2>
      {message && <div className="alert alert-success">{message}</div>}
      <ErrorAlert error={error} />

      {hasResume ? (
        <p>
          <button type="button" className="btn btn-secondary" onClick={handleView} disabled={opening || uploading}>
            {opening ? 'Opening…' : 'View my resume'}
          </button>
        </p>
      ) : (
        <p className="muted">No resume uploaded yet.</p>
      )}

      <form className="form" onSubmit={handleUpload} noValidate>
        <Field id="resumeFile" label={hasResume ? 'Replace it with a new PDF' : 'Upload a PDF'} hint="PDF only, at most 2 MB">
          <input
            key={inputKey}
            id="resumeFile"
            type="file"
            accept="application/pdf,.pdf"
            onChange={handleFileChange}
            disabled={uploading}
          />
        </Field>
        <div className="button-row">
          <button type="submit" className="btn btn-primary" disabled={!file || uploading}>
            {uploading ? 'Uploading…' : hasResume ? 'Replace resume' : 'Upload resume'}
          </button>
        </div>
      </form>
      <p className="muted">Profile last updated: {formatDateTime(updatedAt)}</p>
    </section>
  );
}

function Row({ label, children }) {
  return (
    <div className="review-row">
      <dt>{label}</dt>
      <dd>{children}</dd>
    </div>
  );
}

export default function ProfilePage() {
  const { data: profile, loading, error, reload } = useApi('/api/students/me');
  const [editing, setEditing] = useState(false);
  const [message, setMessage] = useState(null);

  if (error) return <ErrorAlert error={error} onRetry={reload} />;
  if (loading && !profile) return <Loading text="Loading your profile…" />;

  function handleSaved() {
    setEditing(false);
    setMessage('Profile saved.');
    reload();
  }

  return (
    <div className="stack">
      <section className="card">
        <div className="page-heading">
          <h1>My profile</h1>
          {!editing && (
            <button
              type="button"
              className="btn btn-secondary"
              onClick={() => {
                setMessage(null);
                setEditing(true);
              }}
            >
              Edit name and phone
            </button>
          )}
        </div>
        {message && <div className="alert alert-success">{message}</div>}

        {editing ? (
          <EditForm profile={profile} onSaved={handleSaved} onCancel={() => setEditing(false)} />
        ) : (
          <dl className="review-list">
            <Row label="Full name">{profile.fullName}</Row>
            <Row label="Phone">{profile.phone || <span className="muted">Not given</span>}</Row>
            <Row label="Email">{profile.email}</Row>
          </dl>
        )}
      </section>

      <section className="card">
        <h2>Academic details</h2>
        <div className="alert alert-warning">
          These were entered at registration and <strong>can&apos;t be changed</strong> here, because they decide
          which drives you are eligible for. If one is wrong, contact the placement office.
        </div>
        <dl className="review-list">
          <Row label="Roll number">{profile.rollNo}</Row>
          <Row label="Branch">{branchLabel(profile.branch)}</Row>
          <Row label="Graduation year">{profile.graduationYear}</Row>
          <Row label="CGPA">{profile.cgpa}</Row>
          <Row label="Active backlogs">{profile.activeBacklogs}</Row>
        </dl>
      </section>

      <ResumeSection hasResume={profile.hasResume} updatedAt={profile.updatedAt} onUploaded={reload} />
    </div>
  );
}
