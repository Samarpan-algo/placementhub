import api from './client.js';
import { toApiError, unexpectedResponseError } from './errors.js';

/** "drive-5-applicants.csv" from 'attachment; filename="drive-5-applicants.csv"'; null if there is none. */
function fileNameFrom(contentDisposition) {
  if (!contentDisposition) return null;
  // filename*=UTF-8''... is the encoded form for non-ASCII names; prefer it when present
  const encoded = /filename\*=UTF-8''([^;]+)/i.exec(contentDisposition);
  if (encoded) return decodeURIComponent(encoded[1]);
  const plain = /filename="?([^";]+)"?/i.exec(contentDisposition);
  return plain ? plain[1] : null;
}

/**
 * With responseType 'blob', an error's body is a Blob too, not our JSON. Read it as text and parse it,
 * so toApiError() can show the backend's message (e.g. 404 "Drive not found: 99").
 */
async function blobErrorToApiError(error) {
  const body = error?.response?.data;
  if (body instanceof Blob) {
    const text = await body.text();
    try {
      error.response.data = JSON.parse(text);
    } catch {
      error.response.data = text; // not JSON: toApiError falls back to "Request failed (HTTP ...)"
    }
  }
  return toApiError(error);
}

/**
 * Downloads a file from the API (with the token, which a plain <a href> can't send) and saves it.
 * `expectedType` is checked against the response's Content-Type: anything else (e.g. the dev server's
 * index.html when the /api proxy isn't active) is reported as an error instead of saved.
 * Throws an ApiError ({ status, message, details }) on failure.
 */
export async function downloadFile(url, { expectedType, fallbackName }) {
  let response;
  try {
    response = await api.get(url, { responseType: 'blob' });
  } catch (err) {
    throw await blobErrorToApiError(err);
  }
  if (!String(response.headers['content-type'] ?? '').startsWith(expectedType)) {
    throw unexpectedResponseError(response.status);
  }

  // A temporary URL for the downloaded bytes, a hidden link with "download" to save them, then clean up
  const objectUrl = URL.createObjectURL(response.data);
  const link = document.createElement('a');
  link.href = objectUrl;
  link.download = fileNameFrom(response.headers['content-disposition']) ?? fallbackName;
  document.body.appendChild(link);
  link.click();
  link.remove();
  // Revoke a moment later, once the browser has started the download, so the bytes are freed
  setTimeout(() => URL.revokeObjectURL(objectUrl), 1000);
}
