// Must match the backend's RoundOutcome enum
export const ROUND_RESULTS = [
  { value: 'CLEARED', label: 'Cleared' },
  { value: 'NOT_CLEARED', label: 'Not cleared' },
  { value: 'ABSENT', label: 'Absent' },
];

/** RoundService.ROUND_EDITABLE_STATUSES: rounds can be added or edited only while the drive is OPEN or CLOSED. */
export const ROUND_EDITABLE_STATUSES = ['OPEN', 'CLOSED'];

/** "Round 2: Technical" */
export function roundLabel(round) {
  return `Round ${round.roundNo}: ${round.name}`;
}

/** Why a SELECTED, WITHDRAWN, REJECTED or INELIGIBLE application stands where it is. */
function statusExplanation(applicant) {
  if (applicant.status === 'SELECTED') return 'Already selected.';
  if (applicant.status === 'WITHDRAWN') return 'Withdrawn by the student.';
  return applicant.statusReason ?? 'Its status does not allow this.';
}

/**
 * Where one applicant stands on one round's sheet, using the same rules as RoundResultService. The applicants
 * list only carries each student's LATEST result, but that is enough: the backend won't record a fail while a
 * later result exists, and won't record round N without CLEARED in round N-1, so all earlier rounds were cleared.
 *
 * Returns { onSheet: true, saved } (saved = this round's result, or null) or { onSheet: false, why }.
 * The backend checks everything again and answers 409 if anything changed in the meantime.
 */
export function sheetPlacement(applicant, round) {
  if (['SELECTED', 'WITHDRAWN', 'INELIGIBLE'].includes(applicant.status)) {
    return { onSheet: false, why: statusExplanation(applicant) };
  }
  const latest = applicant.progress.latestRound;
  if (latest && latest.roundNo > round.roundNo) {
    // Read-only: this round's saved remarks aren't visible, so an edit could overwrite them with a blank
    return {
      onSheet: false,
      why: `Already has a result in Round ${latest.roundNo}, so this round stands as Cleared.`,
    };
  }
  if (latest && latest.roundNo === round.roundNo) {
    return { onSheet: true, saved: { result: latest.result, remarks: latest.remarks ?? '' } };
  }
  if (round.roundNo === 1 || (latest?.roundNo === round.roundNo - 1 && latest.result === 'CLEARED')) {
    return { onSheet: true, saved: null };
  }
  return { onSheet: false, why: `Must have Cleared in Round ${round.roundNo - 1} first.` };
}

/**
 * The same rules as SelectionService: null if the applicant can be ticked, otherwise why not.
 * Placement policies aren't checked here: only the backend knows, and it answers 409 with the reason.
 */
export function selectionBlock(applicant) {
  if (!['APPLIED', 'SHORTLISTED'].includes(applicant.status)) {
    return statusExplanation(applicant);
  }
  const { totalRounds, latestRound } = applicant.progress;
  if (totalRounds > 0 && !(latestRound?.roundNo === totalRounds && latestRound.result === 'CLEARED')) {
    return `Has not cleared the final round (Round ${totalRounds}).`;
  }
  return null;
}

/**
 * A batch error's lines say "Application 12 ...". Adds the student's name and roll number to each line it can
 * match. A line it can't match stays exactly as the backend wrote it, so no line is ever dropped.
 */
export function withStudentNames(apiError, applicants) {
  const details = apiError.details.map((line) => {
    const match = /^Application (\d+)\b/.exec(line);
    const applicant = match && applicants.find((a) => a.applicationId === Number(match[1]));
    if (!applicant) return line;
    return `${match[0]} (${applicant.fullName}, ${applicant.rollNo})${line.slice(match[0].length)}`;
  });
  return { ...apiError, details };
}
