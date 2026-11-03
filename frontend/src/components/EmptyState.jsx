/** "Nothing here yet": a title, plus optional children (a hint or a link to what to do next). */
export default function EmptyState({ title, children }) {
  return (
    <div className="empty-state">
      <p className="empty-state-title">{title}</p>
      {children && <div className="muted">{children}</div>}
    </div>
  );
}
