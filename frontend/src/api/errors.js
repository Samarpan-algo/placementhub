// Our backend's error JSON: {timestamp, status, error, message, path} plus an optional details[]
// (one line per invalid field on a 400, every reason on a 422). This turns any axios error into
// { status, message, details } that a page can show with <ErrorAlert />.

/** The top alert while the problems are shown under their fields. useFormErrors clears it when they are fixed. */
export const FIX_FIELDS_MESSAGE = 'Please fix the highlighted fields.';

export function toApiError(error) {
  const response = error?.response;

  if (!response) {
    // The request never got an answer: backend down, no network, or blocked by CORS
    return { status: 0, message: 'Cannot reach the server. Check your connection and try again.', details: [] };
  }

  const body = response.data;
  if (body && typeof body.message === 'string') {
    const details = Array.isArray(body.details) ? body.details : [];
    // Validation errors repeat every field error in the message; the list already shows them
    const message =
      details.length > 0 && body.message.startsWith('Validation failed')
        ? 'Please fix the following:'
        : body.message;
    return { status: response.status, message, details };
  }

  // Not our JSON. In dev, the Vite proxy answers 502 with an empty body when the backend is down.
  if (response.status >= 500) {
    return { status: response.status, message: 'The server is not responding. Is the backend running?', details: [] };
  }
  // A 404 without our JSON came from the dev server itself, not the backend: the /api proxy isn't active
  if (response.status === 404) {
    return unexpectedResponseError(404);
  }
  return { status: response.status, message: `Request failed (HTTP ${response.status}).`, details: [] };
}

/** True for a JSON object or array (what our API returns), false for HTML, plain text or an empty body. */
export function isApiJson(data) {
  return data !== null && typeof data === 'object';
}

/** The answer didn't come from our backend (e.g. the dev server answered itself). See decisions.md #68. */
export function unexpectedResponseError(status) {
  return {
    status,
    message:
      `Unexpected answer from the server (HTTP ${status}, not the API's JSON). ` +
      'Is the backend running? In dev, restart "npm run dev" so the /api proxy is active.',
    details: [],
  };
}

/**
 * Splits a 400's detail lines ("minCgpa: must be less than or equal to 10.00") between the form's fields.
 * `fields` lists the form's field names. "branches[0]: ..." or "branches[].x: ..." counts as "branches".
 *
 * Returns { fieldErrors, error }: fieldErrors = { minCgpa: '...' } for the lines that matched a field, and
 * error = the same error with only the lines that did NOT match left in details. So every message is shown
 * once, either under its field or in the top <ErrorAlert />, and none is ever dropped.
 * Errors that aren't a 400 with details (409, 404, ...) come back unchanged, with no field errors.
 */
export function fieldErrorsFrom(apiError, fields) {
  if (!apiError || apiError.status !== 400 || apiError.details.length === 0) {
    return { fieldErrors: {}, error: apiError };
  }
  const fieldErrors = {};
  const unmatched = [];
  for (const line of apiError.details) {
    const colon = line.indexOf(': ');
    // "branches[0].x" -> "branches": everything before the first [ or .
    const field = colon > 0 ? line.slice(0, colon).split(/[[.]/)[0] : null;
    if (field && fields.includes(field)) {
      const text = line.slice(colon + 2);
      fieldErrors[field] = fieldErrors[field] ? `${fieldErrors[field]}; ${text}` : text;
    } else {
      unmatched.push(line);
    }
  }
  const message = unmatched.length > 0 ? apiError.message : FIX_FIELDS_MESSAGE;
  return { fieldErrors, error: { ...apiError, message, details: unmatched } };
}
