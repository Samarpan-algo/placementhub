import { useState } from 'react';
import { Link, useNavigate } from 'react-router';
import api from '../api/client.js';
import { toApiError } from '../api/errors.js';
import ErrorAlert from '../components/ErrorAlert.jsx';
import Field from '../components/Field.jsx';

// Must match the backend's Branch enum (common/Branch.java)
const BRANCHES = [
  { code: 'CSE', name: 'Computer Science' },
  { code: 'IT', name: 'Information Technology' },
  { code: 'ECE', name: 'Electronics & Communication' },
  { code: 'EEE', name: 'Electrical & Electronics' },
  { code: 'ME', name: 'Mechanical' },
  { code: 'CE', name: 'Civil' },
];

const EMPTY_FORM = {
  email: '',
  password: '',
  confirmPassword: '',
  fullName: '',
  rollNo: '',
  branch: '',
  graduationYear: '',
  cgpa: '',
  activeBacklogs: '0',
  phone: '',
};

const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
const PHONE_PATTERN = /^\+?[0-9]{10,14}$/; // same regex as RegisterRequest.phone
const ROLL_NO_PATTERN = /^[A-Z0-9]{5,20}$/; // same regex as RegisterRequest.rollNo (after uppercasing)
const CGPA_PATTERN = /^\d{1,2}(\.\d{1,2})?$/; // at most 2 decimals, like @Digits(integer = 2, fraction = 2)

// Passwords are kept exactly as typed: spaces can be part of a password, and the backend doesn't trim them
const UNTRIMMED_FIELDS = ['password', 'confirmPassword'];

function isWholeNumber(value) {
  return /^\d+$/.test(value);
}

/** A copy of the form with leading/trailing spaces removed from every field except the passwords. */
function trimValues(form) {
  const trimmed = {};
  for (const [name, value] of Object.entries(form)) {
    trimmed[name] = UNTRIMMED_FIELDS.includes(name) ? value : value.trim();
  }
  return trimmed;
}

/**
 * The same rules as the backend's RegisterRequest, checked before sending so the student gets instant
 * feedback. The backend checks again anyway: never trust the client.
 * Expects already-trimmed values (see trimValues). Returns { field: message }.
 */
function validate(form) {
  const errors = {};
  const { email } = form;

  if (!email) errors.email = 'Email is required';
  else if (!EMAIL_PATTERN.test(email) || email.length > 255) errors.email = 'Enter a valid email address';

  if (form.password.length < 8 || form.password.length > 72) {
    errors.password = 'Password must be 8 to 72 characters';
  } else if (!form.password.trim()) {
    errors.password = 'Password cannot be only spaces';
  }
  if (form.confirmPassword !== form.password) errors.confirmPassword = 'Passwords do not match';

  if (!form.fullName) errors.fullName = 'Full name is required';
  else if (form.fullName.length > 100) errors.fullName = 'At most 100 characters';

  if (!form.rollNo) errors.rollNo = 'Roll number is required';
  else if (!ROLL_NO_PATTERN.test(form.rollNo)) errors.rollNo = '5 to 20 letters or digits, e.g. 23CSE099';

  if (!form.branch) errors.branch = 'Choose your branch';

  const year = Number(form.graduationYear);
  if (!isWholeNumber(form.graduationYear) || year < 2000 || year > 2100) {
    errors.graduationYear = 'Enter a year between 2000 and 2100';
  }

  const cgpa = Number(form.cgpa);
  if (!CGPA_PATTERN.test(form.cgpa) || cgpa > 10) errors.cgpa = 'Enter a CGPA from 0 to 10, at most 2 decimals';

  const backlogs = Number(form.activeBacklogs);
  if (!isWholeNumber(form.activeBacklogs) || backlogs > 50) {
    errors.activeBacklogs = 'Enter a whole number from 0 to 50';
  }

  if (form.phone && !PHONE_PATTERN.test(form.phone)) errors.phone = '10 to 14 digits, optionally starting with +';

  return errors;
}

export default function RegisterPage() {
  const navigate = useNavigate();
  // One state object for the whole form, plus one for the error under each field
  const [form, setForm] = useState(EMPTY_FORM);
  const [fieldErrors, setFieldErrors] = useState({});
  const [error, setError] = useState(null);
  const [submitting, setSubmitting] = useState(false);

  // Every input has a name matching a key in `form`, so one handler serves them all.
  // We build a NEW object ({...form}) instead of changing the old one: React re-renders when state is replaced.
  function handleChange(event) {
    const { name, value } = event.target;
    // Roll numbers are stored uppercase (the backend uppercases too), so show them that way while typing
    setForm({ ...form, [name]: name === 'rollNo' ? value.toUpperCase() : value });
  }

  async function handleSubmit(event) {
    event.preventDefault(); // stop the browser's own form submit (a full page reload)
    // Trim once, then validate and send the same values. Showing them trimmed tells the student what we used.
    const values = trimValues(form);
    setForm(values);
    const errors = validate(values);
    setFieldErrors(errors);
    if (Object.keys(errors).length > 0) {
      setError({ message: 'Please fix the highlighted fields.', details: [] });
      return;
    }

    setError(null);
    setSubmitting(true);
    try {
      await api.post('/api/auth/register', {
        email: values.email,
        password: values.password,
        fullName: values.fullName,
        rollNo: values.rollNo,
        branch: values.branch,
        graduationYear: Number(values.graduationYear),
        cgpa: Number(values.cgpa),
        activeBacklogs: Number(values.activeBacklogs),
        phone: values.phone || null,
      });
      navigate('/login', { state: { message: 'Registered, please log in.', email: values.email } });
    } catch (err) {
      // e.g. 409 "An account with this email already exists", or 400 with one detail per field
      setError(toApiError(err));
      setSubmitting(false);
    }
  }

  return (
    <div className="page">
      <div className="card auth-card wide">
        <h1>Student registration</h1>
        <p className="muted">Your academic details can&apos;t be changed after registering, so check them carefully.</p>

        <form className="form" onSubmit={handleSubmit} noValidate>
          <ErrorAlert error={error} />

          <Field id="email" label="Email" error={fieldErrors.email}>
            <input id="email" name="email" type="email" autoComplete="email" value={form.email} onChange={handleChange} />
          </Field>

          <div className="form-row">
            <Field id="password" label="Password" error={fieldErrors.password} hint="8 to 72 characters">
              <input
                id="password"
                name="password"
                type="password"
                autoComplete="new-password"
                value={form.password}
                onChange={handleChange}
              />
            </Field>
            <Field id="confirmPassword" label="Confirm password" error={fieldErrors.confirmPassword}>
              <input
                id="confirmPassword"
                name="confirmPassword"
                type="password"
                autoComplete="new-password"
                value={form.confirmPassword}
                onChange={handleChange}
              />
            </Field>
          </div>

          <Field id="fullName" label="Full name" error={fieldErrors.fullName}>
            <input id="fullName" name="fullName" autoComplete="name" value={form.fullName} onChange={handleChange} />
          </Field>

          <div className="form-row">
            <Field id="rollNo" label="Roll number" error={fieldErrors.rollNo} hint="Letters and digits, e.g. 23CSE099">
              <input id="rollNo" name="rollNo" value={form.rollNo} onChange={handleChange} />
            </Field>
            <Field id="branch" label="Branch" error={fieldErrors.branch}>
              <select id="branch" name="branch" value={form.branch} onChange={handleChange}>
                <option value="">Select…</option>
                {BRANCHES.map((branch) => (
                  <option key={branch.code} value={branch.code}>
                    {branch.code} ({branch.name})
                  </option>
                ))}
              </select>
            </Field>
          </div>

          <div className="form-row">
            <Field id="graduationYear" label="Graduation year" error={fieldErrors.graduationYear}>
              <input
                id="graduationYear"
                name="graduationYear"
                inputMode="numeric"
                placeholder="2027"
                value={form.graduationYear}
                onChange={handleChange}
              />
            </Field>
            <Field id="cgpa" label="CGPA" error={fieldErrors.cgpa} hint="0 to 10">
              <input
                id="cgpa"
                name="cgpa"
                inputMode="decimal"
                placeholder="8.25"
                value={form.cgpa}
                onChange={handleChange}
              />
            </Field>
          </div>

          <div className="form-row">
            <Field id="activeBacklogs" label="Active backlogs" error={fieldErrors.activeBacklogs}>
              <input
                id="activeBacklogs"
                name="activeBacklogs"
                inputMode="numeric"
                value={form.activeBacklogs}
                onChange={handleChange}
              />
            </Field>
            <Field id="phone" label="Phone (optional)" error={fieldErrors.phone}>
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
          </div>

          <button type="submit" className="btn btn-primary" disabled={submitting}>
            {submitting ? 'Registering…' : 'Register'}
          </button>
        </form>
        <p className="muted">
          Already registered? <Link to="/login">Log in</Link>
        </p>
      </div>
    </div>
  );
}
