import { useEffect, useRef } from 'react';

/**
 * A modal "Are you sure?" box built on the browser's own <dialog> element (no library needed).
 * The parent decides when it is open and what the buttons do:
 *   <ConfirmDialog open={...} title="..." confirmLabel="Withdraw" busy={sending}
 *                  onConfirm={...} onCancel={...}>message</ConfirmDialog>
 * While busy, both buttons are disabled and Escape does nothing, so the request can't be sent twice.
 */
export default function ConfirmDialog({
  open,
  title,
  children,
  confirmLabel = 'Confirm',
  busyLabel = 'Working…',
  danger = false,
  busy = false,
  onConfirm,
  onCancel,
}) {
  // A ref points at the real DOM element, because showModal()/close() are methods on it, not props
  const dialogRef = useRef(null);

  // Keep the browser dialog in step with the `open` prop
  useEffect(() => {
    const dialog = dialogRef.current;
    if (open && !dialog.open) {
      dialog.showModal(); // modal: the rest of the page can't be clicked, focus moves into the dialog
    } else if (!open && dialog.open) {
      dialog.close();
    }
  }, [open]);

  // Escape fires a "cancel" event. preventDefault stops the browser closing it on its own,
  // so the parent's `open` state stays the single source of truth.
  function handleCancel(event) {
    event.preventDefault();
    if (!busy) onCancel();
  }

  return (
    <dialog ref={dialogRef} className="dialog" onCancel={handleCancel} aria-labelledby="confirm-dialog-title">
      <h2 id="confirm-dialog-title">{title}</h2>
      <div className="dialog-body">{children}</div>
      <div className="button-row">
        <button type="button" className="btn btn-secondary" onClick={onCancel} disabled={busy}>
          Cancel
        </button>
        <button
          type="button"
          className={`btn ${danger ? 'btn-danger' : 'btn-primary'}`}
          onClick={onConfirm}
          disabled={busy}
        >
          {busy ? busyLabel : confirmLabel}
        </button>
      </div>
    </dialog>
  );
}
