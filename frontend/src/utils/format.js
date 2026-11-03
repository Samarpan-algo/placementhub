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

// ---------- <input type="datetime-local"> in IST ----------
// The input holds a wall-clock time with no zone, e.g. "2026-11-20T18:00". Admins type Indian time, so:
//   input -> API: add India's fixed offset (+05:30, no daylight saving) and convert to a UTC instant.
//   API -> input: show the UTC instant's wall-clock time in Asia/Kolkata.
// Both work the same whatever timezone the admin's laptop is set to.

const INPUT_PARTS = new Intl.DateTimeFormat('en-GB', {
  timeZone: IST,
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
  hour: '2-digit',
  minute: '2-digit',
  hourCycle: 'h23', // 00-23, so midnight is "00", not "24"
});

/** "2026-11-20T12:30:00Z" -> "2026-11-20T18:00" (IST) for a datetime-local input; "" for null. */
export function isoToIstInput(iso) {
  if (!iso) return '';
  const p = Object.fromEntries(INPUT_PARTS.formatToParts(new Date(iso)).map((part) => [part.type, part.value]));
  return `${p.year}-${p.month}-${p.day}T${p.hour}:${p.minute}`;
}

/** "2026-11-20T18:00" (IST) -> "2026-11-20T12:30:00.000Z" for the API; null if it isn't a complete date-time. */
export function istInputToIso(value) {
  if (!/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}$/.test(value)) return null;
  const date = new Date(`${value}:00+05:30`);
  return Number.isNaN(date.getTime()) ? null : date.toISOString();
}

// ---------- whole IST days (<input type="date">, e.g. the audit log's filters) ----------

const DAY_MS = 24 * 60 * 60 * 1000;

/** "2026-11-04" -> "2026-11-03T18:30:00.000Z": the instant that IST day starts; null if it isn't a date. */
export function istDayStartIso(day) {
  return istInputToIso(`${day}T00:00`);
}

/**
 * "2026-11-04" -> "2026-11-04T18:30:00.000Z": the instant the NEXT IST day starts, for an EXCLUSIVE end, so
 * the whole of 4 Nov is included. India has no daylight saving, so every IST day is exactly 24 hours.
 */
export function istDayEndExclusiveIso(day) {
  const start = istDayStartIso(day);
  return start && new Date(new Date(start).getTime() + DAY_MS).toISOString();
}
