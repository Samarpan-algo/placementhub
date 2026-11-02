import { useState } from 'react';
import { useSearchParams } from 'react-router';
import api from '../../api/client.js';
import { fieldErrorsFrom, toApiError } from '../../api/errors.js';
import { useApi } from '../../api/useApi.js';
import EmptyState from '../../components/EmptyState.jsx';
import ErrorAlert from '../../components/ErrorAlert.jsx';
import Field from '../../components/Field.jsx';
import Loading from '../../components/Loading.jsx';
import Pagination from '../../components/Pagination.jsx';
import { formatDateTime } from '../../utils/format.js';

const PAGE_SIZE = 10;
const FIELDS = ['name', 'website', 'description'];

/** True for an absolute URL like "https://nimbus.example" (the backend's @URL needs the http:// part too). */
function isUrl(value) {
  try {
    new URL(value);
    return true;
  } catch {
    return false;
  }
}

/** The same rules as CompanyRequest (the backend checks again). Expects trimmed values. */
function validate(form) {
  const errors = {};
  if (!form.name) errors.name = 'Name is required';
  else if (form.name.length > 150) errors.name = 'At most 150 characters';
  if (form.website.length > 255) errors.website = 'At most 255 characters';
  else if (form.website && !isUrl(form.website)) errors.website = 'Enter a full URL, e.g. https://example.com';
  if (form.description.length > 2000) errors.description = 'At most 2000 characters';
  return errors;
}

/** Create (company = null) or edit one company. onSaved(savedCompany) after the backend accepted it. */
function CompanyForm({ company, onSaved, onCancel }) {
  const [form, setForm] = useState({
    name: company?.name ?? '',
    website: company?.website ?? '',
    description: company?.description ?? '',
  });
  const [fieldErrors, setFieldErrors] = useState({});
  const [error, setError] = useState(null);
  const [saving, setSaving] = useState(false);

  function handleChange(event) {
    setForm({ ...form, [event.target.name]: event.target.value });
  }

  async function handleSubmit(event) {
    event.preventDefault();
    const values = { name: form.name.trim(), website: form.website.trim(), description: form.description.trim() };
    setForm(values);
    const errors = validate(values);
    setFieldErrors(errors);
    if (Object.keys(errors).length > 0) {
      setError({ message: 'Please fix the highlighted fields.', details: [] });
      return;
    }
    setError(null);
    setSaving(true);
    // Empty optional fields are sent as null, which clears them
    const body = { name: values.name, website: values.website || null, description: values.description || null };
    try {
      const response = company
        ? await api.put(`/api/companies/${company.id}`, body)
        : await api.post('/api/companies', body);
      onSaved(response.data);
    } catch (err) {
      // 400: messages go under their fields; 409 (duplicate name) and anything else: the top alert
      const split = fieldErrorsFrom(toApiError(err), FIELDS);
      setFieldErrors(split.fieldErrors);
      setError(split.error);
      setSaving(false);
    }
  }

  return (
    <section className="card">
      <h2>{company ? `Edit ${company.name}` : 'New company'}</h2>
      <form className="form" onSubmit={handleSubmit} noValidate>
        <ErrorAlert error={error} />
        <Field id="name" label="Name" error={fieldErrors.name}>
          <input id="name" name="name" value={form.name} onChange={handleChange} />
        </Field>
        <Field id="website" label="Website (optional)" error={fieldErrors.website}>
          <input
            id="website"
            name="website"
            type="url"
            placeholder="https://example.com"
            value={form.website}
            onChange={handleChange}
          />
        </Field>
        <Field id="description" label="Description (optional)" error={fieldErrors.description}>
          <textarea id="description" name="description" rows={3} value={form.description} onChange={handleChange} />
        </Field>
        <div className="button-row">
          <button type="button" className="btn btn-secondary" onClick={onCancel} disabled={saving}>
            Cancel
          </button>
          <button type="submit" className="btn btn-primary" disabled={saving}>
            {saving ? 'Saving…' : company ? 'Save changes' : 'Create company'}
          </button>
        </div>
      </form>
    </section>
  );
}

/** All companies A–Z, 10 per page (?page= in the URL), with one create/edit form above the table. */
export default function CompaniesPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const page = Math.max(Number.parseInt(searchParams.get('page') ?? '1', 10) || 1, 1) - 1;
  const { data, loading, error, reload } = useApi(`/api/companies?page=${page}&size=${PAGE_SIZE}`);

  // null = no form; { company: null } = creating; { company } = editing that company
  const [editing, setEditing] = useState(null);
  const [message, setMessage] = useState(null);

  function goToPage(next) {
    setSearchParams(next > 0 ? { page: String(next + 1) } : {});
  }

  function openForm(company) {
    setMessage(null);
    setEditing({ company });
  }

  function handleSaved(saved) {
    setMessage(editing.company ? `Saved ${saved.name}.` : `Created ${saved.name}.`);
    setEditing(null);
    reload(); // show the list as the backend has it now
  }

  return (
    <div className="stack">
      <div className="page-heading">
        <h1>Companies</h1>
        {!editing && (
          <button type="button" className="btn btn-primary" onClick={() => openForm(null)}>
            New company
          </button>
        )}
      </div>
      {message && <div className="alert alert-success">{message}</div>}

      {editing && (
        <CompanyForm
          // key: a different company (or "new") starts a fresh form instead of keeping the old values
          key={editing.company?.id ?? 'new'}
          company={editing.company}
          onSaved={handleSaved}
          onCancel={() => setEditing(null)}
        />
      )}

      {error && <ErrorAlert error={error} onRetry={reload} />}
      {!error && loading && !data && <Loading text="Loading companies…" />}

      {data && data.content.length === 0 && (
        <EmptyState title={data.totalElements > 0 ? 'There are no companies on this page.' : 'No companies yet.'}>
          {data.totalElements > 0 ? (
            <button type="button" className="btn btn-secondary" onClick={() => goToPage(0)}>
              Go to page 1
            </button>
          ) : (
            'Add the first one with "New company".'
          )}
        </EmptyState>
      )}

      {data && data.content.length > 0 && (
        <>
          <div className="table-wrap card">
            <table className="table">
              <thead>
                <tr>
                  <th>Name</th>
                  <th>Website</th>
                  <th>Description</th>
                  <th>Added</th>
                  <th />
                </tr>
              </thead>
              <tbody>
                {data.content.map((company) => (
                  <tr key={company.id}>
                    <td>
                      <strong>{company.name}</strong>
                    </td>
                    <td>
                      {company.website ? (
                        <a href={company.website} target="_blank" rel="noreferrer">
                          {company.website}
                        </a>
                      ) : (
                        <span className="muted">—</span>
                      )}
                    </td>
                    <td className="cell-text">{company.description || <span className="muted">—</span>}</td>
                    <td>{formatDateTime(company.createdAt)}</td>
                    <td className="actions">
                      <button
                        type="button"
                        className="btn btn-secondary btn-small"
                        onClick={() => openForm(company)}
                        disabled={Boolean(editing)}
                      >
                        Edit
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <Pagination page={data.page} totalPages={data.totalPages} disabled={loading} onChange={goToPage} />
        </>
      )}
    </div>
  );
}
