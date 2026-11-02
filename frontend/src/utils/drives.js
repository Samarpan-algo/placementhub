import { isoToIstInput, istInputToIso } from './format.js';

// Must match the backend enums (drive/DriveStatus.java, drive/JobType.java)
export const DRIVE_STATUSES = ['DRAFT', 'OPEN', 'CLOSED', 'COMPLETED'];
export const JOB_TYPES = [
  { value: 'FULL_TIME', label: 'Full-time' },
  { value: 'INTERNSHIP', label: 'Internship' },
];

/** The same as DriveService.EDITABLE_STATUSES: drives can be edited only before registration closes. */
export const EDITABLE_STATUSES = ['DRAFT', 'OPEN'];

/**
 * DriveService.ALLOWED_TRANSITIONS: each status has at most one next status, and each has its own endpoint
 * (POST /api/drives/{id}/open | close | complete). COMPLETED is final, so it has no entry.
 * The backend checks again (409 if the drive changed meanwhile, or can't be opened yet).
 */
export const NEXT_STATUS_ACTION = {
  DRAFT: {
    endpoint: 'open',
    label: 'Open registration',
    title: 'Open registration?',
    body: 'Students in the eligible branches will see this drive and can apply until the registration deadline.',
    done: 'Registration is now open.',
  },
  OPEN: {
    endpoint: 'close',
    label: 'Close registration',
    title: 'Close registration?',
    body: 'Students will no longer be able to apply or withdraw. A closed drive cannot be opened or edited again.',
    done: 'Registration is now closed.',
  },
  CLOSED: {
    endpoint: 'complete',
    label: 'Mark completed',
    title: 'Mark this drive completed?',
    body:
      'Every application that is still pending (Applied or Shortlisted) will be REJECTED automatically. ' +
      'Completed is final: this cannot be undone.',
    done: 'Drive marked completed. Pending applications were rejected.',
    danger: true,
  },
};

/** The form's field names, the same as the request's (used to place 400 errors under their fields). */
export const DRIVE_FIELDS = [
  'companyId',
  'roleTitle',
  'jobType',
  'ctcLpa',
  'location',
  'description',
  'minCgpa',
  'maxBacklogs',
  'graduationYear',
  'registrationDeadline',
  'driveDate',
  'branches',
];

/** Form state is all strings (that's what inputs hold), plus the branch codes as an array. */
export function emptyDriveForm() {
  return {
    companyId: '',
    roleTitle: '',
    jobType: 'FULL_TIME',
    ctcLpa: '',
    location: '',
    description: '',
    minCgpa: '',
    maxBacklogs: '0',
    graduationYear: String(new Date().getFullYear() + 1),
    registrationDeadline: '',
    driveDate: '',
    branches: [],
  };
}

/** A DriveResponse -> form values, for editing. */
export function driveToForm(drive) {
  return {
    companyId: String(drive.companyId),
    roleTitle: drive.roleTitle,
    jobType: drive.jobType,
    ctcLpa: String(drive.ctcLpa),
    location: drive.location ?? '',
    description: drive.description ?? '',
    minCgpa: String(drive.minCgpa),
    maxBacklogs: String(drive.maxBacklogs),
    graduationYear: String(drive.graduationYear),
    registrationDeadline: isoToIstInput(drive.registrationDeadline),
    driveDate: drive.driveDate ?? '',
    branches: [...drive.branches],
  };
}

/** Form values -> the request body. Create includes companyId; update can't change the company. */
export function formToDriveRequest(form, { includeCompany }) {
  const body = {
    roleTitle: form.roleTitle.trim(),
    jobType: form.jobType,
    ctcLpa: Number(form.ctcLpa),
    location: form.location.trim() || null,
    description: form.description.trim() || null,
    minCgpa: Number(form.minCgpa),
    maxBacklogs: Number(form.maxBacklogs),
    graduationYear: Number(form.graduationYear),
    registrationDeadline: istInputToIso(form.registrationDeadline), // IST input -> UTC instant
    driveDate: form.driveDate || null, // a plain date ("2026-12-03"): no timezone involved
    branches: form.branches,
  };
  return includeCompany ? { companyId: Number(form.companyId), ...body } : body;
}

/** A decimal with at most `decimals` digits after the point, e.g. "12" or "7.50". */
function isDecimal(value, decimals) {
  return new RegExp(`^\\d+(\\.\\d{1,${decimals}})?$`).test(value);
}

function isWholeNumber(value) {
  return /^\d+$/.test(value);
}

/**
 * The same rules as DriveCreateRequest / DriveUpdateRequest, plus "drive date not before the deadline"
 * (compared as IST days, like DriveService.checkDates). The backend checks everything again.
 */
export function validateDriveForm(form, { requireCompany }) {
  const errors = {};
  const roleTitle = form.roleTitle.trim();

  if (requireCompany && !form.companyId) errors.companyId = 'Choose a company';
  if (!roleTitle) errors.roleTitle = 'Role title is required';
  else if (roleTitle.length > 150) errors.roleTitle = 'At most 150 characters';
  if (!form.jobType) errors.jobType = 'Choose a job type';

  if (!isDecimal(form.ctcLpa, 2)) errors.ctcLpa = 'A number with at most 2 decimals, e.g. 12.50';
  else if (Number(form.ctcLpa) < 0.01 || Number(form.ctcLpa) > 9999.99) errors.ctcLpa = 'Between 0.01 and 9999.99';

  if (form.location.trim().length > 150) errors.location = 'At most 150 characters';
  if (form.description.trim().length > 5000) errors.description = 'At most 5000 characters';

  if (!isDecimal(form.minCgpa, 2)) errors.minCgpa = 'A number with at most 2 decimals, e.g. 7.50';
  else if (Number(form.minCgpa) > 10) errors.minCgpa = 'Between 0 and 10';

  if (!isWholeNumber(form.maxBacklogs) || Number(form.maxBacklogs) > 50) errors.maxBacklogs = 'A whole number, 0 to 50';

  if (!isWholeNumber(form.graduationYear) || Number(form.graduationYear) < 2000 || Number(form.graduationYear) > 2100) {
    errors.graduationYear = 'A year between 2000 and 2100';
  }

  if (!istInputToIso(form.registrationDeadline)) errors.registrationDeadline = 'Choose a date and time';
  // "YYYY-MM-DD" strings compare correctly as text; the deadline's first 10 characters are its IST day
  else if (form.driveDate && form.driveDate < form.registrationDeadline.slice(0, 10)) {
    errors.driveDate = 'Cannot be before the registration deadline';
  }

  return errors;
}
