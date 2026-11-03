import { useState } from 'react';
import { FIX_FIELDS_MESSAGE } from '../api/errors.js';

/**
 * A form's errors: the message under each field, and the alert on top. Without clearFieldError, a message would
 * stay until the next submit, even after the admin fixed the value. Call it from the field's onChange.
 * Only "fix the highlighted fields" goes away on its own, once no field has an error left. Any other alert
 * (e.g. a 409 conflict) isn't about one field, so it stays until the next submit.
 */
export function useFormErrors() {
  const [fieldErrors, setFieldErrors] = useState({});
  const [error, setError] = useState(null);

  function clearFieldError(name) {
    if (!(name in fieldErrors)) return;
    const remaining = { ...fieldErrors };
    delete remaining[name];
    setFieldErrors(remaining);
    if (Object.keys(remaining).length === 0 && error?.message === FIX_FIELDS_MESSAGE) {
      setError(null);
    }
  }

  return { fieldErrors, setFieldErrors, error, setError, clearFieldError };
}
