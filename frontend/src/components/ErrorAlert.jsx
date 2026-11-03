/**
 * Shows an error from toApiError(): the message, plus every detail line (if any). Renders nothing for null.
 * Pass onRetry to add a "Try again" button (used when a page failed to load).
 */
export default function ErrorAlert({ error, onRetry }) {
  if (!error) {
    return null;
  }
  return (
    <div className="alert alert-error" role="alert">
      {error.message}
      {error.details.length > 0 && (
        <ul>
          {/* key tells React which list item is which when the list changes */}
          {error.details.map((detail) => (
            <li key={detail}>{detail}</li>
          ))}
        </ul>
      )}
      {onRetry && (
        <div className="alert-actions">
          <button type="button" className="btn btn-secondary" onClick={onRetry}>
            Try again
          </button>
        </div>
      )}
    </div>
  );
}
