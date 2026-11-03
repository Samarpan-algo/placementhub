/** The "still loading" state of a page or section. */
export default function Loading({ text = 'Loading…' }) {
  return (
    <p className="loading muted" role="status">
      {text}
    </p>
  );
}
