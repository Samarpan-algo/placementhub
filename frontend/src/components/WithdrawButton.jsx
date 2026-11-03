import { useState } from 'react';
import api from '../api/client.js';
import { toApiError } from '../api/errors.js';
import { canWithdraw } from '../utils/applications.js';
import ConfirmDialog from './ConfirmDialog.jsx';

/**
 * The whole withdraw flow in one place, used by My applications and the drive detail page:
 * the button (only when canWithdraw allows it), the "withdrawal is final" confirm dialog, and the request.
 *
 * application needs: id, roleTitle, companyName, status, driveStatus, registrationDeadline.
 * onDone({ message }) after a withdrawal, onDone({ error }) if the backend refused (e.g. a 409 because the
 * deadline passed while the page was open). Either way the parent should reload to show the real status.
 */
export default function WithdrawButton({ application, onDone, disabled = false, className = 'btn btn-secondary' }) {
  const [confirming, setConfirming] = useState(false);
  const [withdrawing, setWithdrawing] = useState(false);

  if (!canWithdraw(application)) {
    return null;
  }

  async function confirmWithdraw() {
    setWithdrawing(true);
    try {
      await api.post(`/api/students/me/applications/${application.id}/withdraw`);
      onDone({ message: `You withdrew from ${application.roleTitle} at ${application.companyName}.` });
    } catch (err) {
      onDone({ error: toApiError(err) });
    } finally {
      setWithdrawing(false);
      setConfirming(false);
    }
  }

  return (
    <>
      <button type="button" className={className} onClick={() => setConfirming(true)} disabled={disabled || withdrawing}>
        Withdraw
      </button>
      <ConfirmDialog
        open={confirming}
        title="Withdraw this application?"
        confirmLabel="Yes, withdraw"
        busyLabel="Withdrawing…"
        danger
        busy={withdrawing}
        onConfirm={confirmWithdraw}
        onCancel={() => setConfirming(false)}
      >
        <p>
          You are withdrawing from <strong>{application.roleTitle}</strong> at{' '}
          <strong>{application.companyName}</strong>. <strong>Withdrawal is final:</strong> you will not be able to
          apply to this drive again.
        </p>
      </ConfirmDialog>
    </>
  );
}
