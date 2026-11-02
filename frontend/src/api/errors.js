// Our backend's error JSON: {timestamp, status, error, message, path} plus an optional details[]
// (one line per invalid field on a 400, every reason on a 422). This turns any axios error into
// { status, message, details } that a page can show with <ErrorAlert />.

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
  return { status: response.status, message: `Request failed (HTTP ${response.status}).`, details: [] };
}
