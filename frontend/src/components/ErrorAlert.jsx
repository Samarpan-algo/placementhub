/** Shows an error from toApiError(): the message, plus every detail line (if any). Renders nothing for null. */
export default function ErrorAlert({ error }) {
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
    </div>
  );
}
