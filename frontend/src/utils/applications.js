import { isInFuture } from './format.js';

const WITHDRAWABLE = ['APPLIED', 'SHORTLISTED'];

/**
 * The same rule as the backend's withdraw check, used only to decide whether to SHOW the button:
 * APPLIED or SHORTLISTED, the drive still OPEN, and the registration deadline not passed yet.
 * The backend checks again and answers 409 if anything changed in the meantime.
 */
export function canWithdraw(application) {
  return (
    WITHDRAWABLE.includes(application.status) &&
    application.driveStatus === 'OPEN' &&
    isInFuture(application.registrationDeadline)
  );
}
