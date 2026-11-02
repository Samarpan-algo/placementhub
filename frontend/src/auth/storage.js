// Everything we keep in the browser, in one place. Plain functions (not React), because the axios
// interceptors in api/client.js run outside React and can't use hooks or context.

const TOKEN_KEY = 'placementhub.token';
const FLASH_KEY = 'placementhub.flash';

// localStorage survives tab closes and browser restarts (until the token's 2 hours run out).
// Trade-off: any script running on our page can read it (XSS). See docs/decisions.md #62.
export function getToken() {
  return localStorage.getItem(TOKEN_KEY);
}

export function saveToken(token) {
  localStorage.setItem(TOKEN_KEY, token);
}

export function clearToken() {
  localStorage.removeItem(TOKEN_KEY);
}

// A one-time message for the next page load (e.g. "Session expired") that survives the full reload
// the 401 handler does. sessionStorage is per tab and is cleared when the tab closes.
export function saveFlashMessage(message) {
  sessionStorage.setItem(FLASH_KEY, message);
}

export function peekFlashMessage() {
  return sessionStorage.getItem(FLASH_KEY);
}

export function clearFlashMessage() {
  sessionStorage.removeItem(FLASH_KEY);
}
