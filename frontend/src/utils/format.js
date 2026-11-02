// Display helpers. The backend stores and sends times in UTC (e.g. "2026-11-15T14:59:09Z");
// students read them in Indian time, in the same style as the backend's messages:
// "15 Nov 2026, 8:29 PM IST" (see StudentTimeFormat.java).

const IST = 'Asia/Kolkata';

const DATE_TIME = new Intl.DateTimeFormat('en-GB', {
  timeZone: IST,
  day: 'numeric',
  month: 'short',
  year: 'numeric',
  hour: 'numeric',
  minute: '2-digit',
  hour12: true,
});

/** "15 Nov 2026, 8:29 PM IST" for an ISO instant; "—" for null. */
export function formatDateTime(iso) {
  if (!iso) return '—';
  // formatToParts gives each piece separately, so the result is the same in every browser
  const parts = Object.fromEntries(DATE_TIME.formatToParts(new Date(iso)).map((p) => [p.type, p.value]));
  return `${parts.day} ${parts.month} ${parts.year}, ${parts.hour}:${parts.minute} ${parts.dayPeriod.toUpperCase()} IST`;
}

const DATE_ONLY = new Intl.DateTimeFormat('en-GB', { timeZone: 'UTC', day: 'numeric', month: 'short', year: 'numeric' });

/**
 * "22 Nov 2026" for a date without a time, like driveDate ("2026-11-22"); "—" for null.
 * A date-only string is read as midnight UTC, so it is formatted in UTC to keep the same day.
 */
export function formatDate(isoDate) {
  if (!isoDate) return '—';
  return DATE_ONLY.format(new Date(isoDate));
}

/** "12.00 LPA" (lakhs per annum); "—" for null. */
export function formatCtc(lpa) {
  return lpa == null ? '—' : `${Number(lpa).toFixed(2)} LPA`;
}

/** "Full-time" / "Internship". */
export function formatJobType(jobType) {
  return jobType === 'INTERNSHIP' ? 'Internship' : 'Full-time';
}

/** True if the instant is still in the future (e.g. a registration deadline). */
export function isInFuture(iso) {
  return new Date(iso).getTime() > Date.now();
}
