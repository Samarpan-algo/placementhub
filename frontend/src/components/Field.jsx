/** A labelled form field: the label, the input you pass as children, then a hint or an error below it. */
export default function Field({ id, label, error, hint, children }) {
  return (
    <div className="field">
      <label htmlFor={id}>{label}</label>
      {children}
      {error ? <span className="field-error">{error}</span> : hint && <span className="hint">{hint}</span>}
    </div>
  );
}
