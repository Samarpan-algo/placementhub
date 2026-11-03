import { formatDateTime } from './format.js';

// Must match the backend's AuditEntityType and AuditAction enums
export const ENTITY_TYPES = [
  { value: 'DRIVE', label: 'Drive' },
  { value: 'APPLICATION', label: 'Application' },
  { value: 'ROUND_RESULT', label: 'Round result' },
  { value: 'POLICY', label: 'Policy' },
];

const ACTION_LABELS = {
  CRITERIA_CHANGED: 'Drive edited',
  STATUS_CHANGED: 'Status changed',
  RESULT_CHANGED: 'Round result saved',
  POLICY_CHANGED: 'Policy changed',
};

const FIELD_LABELS = {
  active: 'Active',
  branches: 'Branches',
  ctcLpa: 'CTC (LPA)',
  ctcThresholdLpa: 'CTC threshold (LPA)',
  description: 'Description',
  driveDate: 'Drive date',
  graduationYear: 'Graduation year',
  jobType: 'Job type',
  location: 'Location',
  maxBacklogs: 'Maximum backlogs',
  maxOffers: 'Maximum offers',
  minCgpa: 'Minimum CGPA',
  reason: 'Reason',
  registrationDeadline: 'Registration deadline',
  remarks: 'Remarks',
  result: 'Result',
  roleTitle: 'Role title',
  status: 'Status',
};

// Facts written on one side only: they describe the change itself, so they aren't an "old → new" pair
const NOTE_LABELS = {
  trigger: 'Trigger',
  applicationsMadeIneligible: 'Applications made ineligible',
};

export function entityLabel(row) {
  const type = ENTITY_TYPES.find((t) => t.value === row.entityType);
  return `${type?.label ?? row.entityType} #${row.entityId}`;
}

export function actionLabel(action) {
  return ACTION_LABELS[action] ?? action;
}

/** One value as text: "—" for nothing, IST for an instant, "CSE, IT" for a list, Yes/No for a boolean. */
function formatValue(value) {
  if (value === null || value === undefined) return '—';
  if (Array.isArray(value)) return value.join(', ') || '—';
  if (typeof value === 'boolean') return value ? 'Yes' : 'No';
  if (typeof value === 'string' && /^\d{4}-\d{2}-\d{2}T.*Z$/.test(value)) return formatDateTime(value);
  return String(value);
}

/** What the row is about, from the JSON's fixed fields: "Application #12, Round 2" or "DREAM_OFFER". */
function contextOf(oldValue, newValue) {
  const value = newValue ?? oldValue ?? {};
  if (value.applicationId !== undefined) return `Application #${value.applicationId}, Round ${value.roundNo}`;
  if (value.type !== undefined) return value.type;
  return null;
}

/**
 * Turns an audit row's two JSON objects into a readable list. Returns
 * { context, changes: [{ field, from, to }], notes: [{ field, value }] }, with unchanged fields left out.
 * oldValue is null for something newly created, so its fields show as "— → value".
 */
export function describeChange(row) {
  const oldValue = row.oldValue ?? {};
  const newValue = row.newValue ?? {};
  const changes = [];
  const notes = [];
  for (const key of new Set([...Object.keys(oldValue), ...Object.keys(newValue)])) {
    if (NOTE_LABELS[key]) {
      notes.push({ field: NOTE_LABELS[key], value: formatValue(newValue[key] ?? oldValue[key]) });
    } else if (key !== 'applicationId' && key !== 'roundNo' && key !== 'type') {
      // Stringify, so that two equal lists compare equal; undefined and null both mean "no value"
      if (JSON.stringify(oldValue[key] ?? null) !== JSON.stringify(newValue[key] ?? null)) {
        changes.push({ field: FIELD_LABELS[key] ?? key, from: formatValue(oldValue[key]), to: formatValue(newValue[key]) });
      }
    }
  }
  return { context: contextOf(row.oldValue, row.newValue), changes, notes };
}
