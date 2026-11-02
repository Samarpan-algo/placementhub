/**
 * Previous / "Page 2 of 5" / Next for our PageResponse ({page, totalPages}, pages counted from 0).
 * Shows nothing when everything fits on one page. onChange(newPage) gets the 0-based page number.
 */
export default function Pagination({ page, totalPages, onChange, disabled = false }) {
  if (totalPages <= 1) {
    return null;
  }
  return (
    <nav className="pagination" aria-label="Pages">
      <button
        type="button"
        className="btn btn-secondary"
        onClick={() => onChange(page - 1)}
        disabled={disabled || page <= 0}
      >
        ← Previous
      </button>
      <span className="muted">
        Page {page + 1} of {totalPages}
      </span>
      <button
        type="button"
        className="btn btn-secondary"
        onClick={() => onChange(page + 1)}
        disabled={disabled || page >= totalPages - 1}
      >
        Next →
      </button>
    </nav>
  );
}
