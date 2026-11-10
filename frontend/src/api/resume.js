import api from './client.js';
import { isApiJson, toApiError, unexpectedResponseError } from './errors.js';

/** The same limit as the backend, so a too-large file is refused before it is sent. */
export const MAX_RESUME_BYTES = 2 * 1024 * 1024;

/** Quick check before uploading; the backend checks again, including the file's first bytes. */
export function resumeFileProblem(file) {
  if (file.type !== 'application/pdf') return 'Only PDF files are allowed.';
  if (file.size > MAX_RESUME_BYTES) return 'The file is too large. The maximum size is 2 MB.';
  if (file.size === 0) return 'The file is empty.';
  return null;
}

/**
 * Asks the backend for a short-lived link and opens it in a new tab. The tab is opened BEFORE the request,
 * while we are still handling the click, because browsers block new tabs opened later.
 * Throws an ApiError ({ status, message, details }) on failure.
 */
export async function openResume(endpoint) {
  const tab = window.open('', '_blank');
  if (!tab) {
    throw { status: 0, message: 'Your browser blocked the new tab. Allow pop-ups for this site and try again.', details: [] };
  }
  tab.opener = null; // the opened page gets no access back to this one
  try {
    const response = await api.get(endpoint);
    if (!isApiJson(response.data) || typeof response.data.url !== 'string') {
      throw unexpectedResponseError(response.status);
    }
    tab.location.href = response.data.url;
  } catch (err) {
    tab.close();
    throw err.response || err.request ? toApiError(err) : err;
  }
}
