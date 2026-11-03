// Labels for application statuses and drive statuses, exactly as the backend sends them.
// The CSS class (badge-applied, badge-open, ...) picks the color, see styles.css.
const LABELS = {
  APPLIED: 'Applied',
  SHORTLISTED: 'Shortlisted',
  SELECTED: 'Selected',
  REJECTED: 'Rejected',
  WITHDRAWN: 'Withdrawn',
  INELIGIBLE: 'Ineligible',
  DRAFT: 'Draft',
  OPEN: 'Open',
  CLOSED: 'Closed',
  COMPLETED: 'Completed',
  CLEARED: 'Cleared',
  NOT_CLEARED: 'Not cleared',
  ABSENT: 'Absent',
};

/** A small colored pill for a status, e.g. <StatusBadge status="SHORTLISTED" />. */
export default function StatusBadge({ status }) {
  return <span className={`badge badge-${status.toLowerCase()}`}>{LABELS[status] ?? status}</span>;
}
