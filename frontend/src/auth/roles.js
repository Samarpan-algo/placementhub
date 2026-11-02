// Role names exactly as the backend sends them.
export const ROLES = {
  STUDENT: 'STUDENT',
  ADMIN: 'TPO_ADMIN',
};

/** Where each role lands after login, and where a wrong-role URL sends you back to. */
export function homePathFor(role) {
  return role === ROLES.ADMIN ? '/admin' : '/student';
}
