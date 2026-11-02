// A JWT is three base64url parts joined by dots: header.payload.signature.
// The payload is just JSON, readable by anyone; the signature is what makes it trustworthy, and only
// the backend checks it. So we read the payload here only to know what to SHOW (email, role); the
// backend still decides what the user may DO on every request.

/** Returns the token's payload (claims) as an object. Throws if the token isn't a readable JWT. */
export function decodeJwtPayload(token) {
  const payload = token.split('.')[1];
  if (!payload) {
    throw new Error('Not a JWT');
  }
  // base64url uses - and _ instead of + and /, and drops the trailing = padding; atob needs plain base64
  const base64 = payload.replace(/-/g, '+').replace(/_/g, '/');
  const padded = base64 + '='.repeat((4 - (base64.length % 4)) % 4);
  // atob returns one character per byte; TextDecoder turns those bytes into UTF-8 text
  const bytes = Uint8Array.from(atob(padded), (char) => char.charCodeAt(0));
  return JSON.parse(new TextDecoder().decode(bytes));
}

/** The user the frontend needs, from our backend's claims: sub = user id, email, role, exp (seconds). */
export function userFromToken(token) {
  const claims = decodeJwtPayload(token);
  if (!claims.sub || !claims.role || !claims.exp) {
    throw new Error('Token is missing claims');
  }
  return {
    id: Number(claims.sub),
    email: claims.email,
    role: claims.role,
    expiresAt: claims.exp * 1000, // milliseconds, like Date.now()
  };
}
