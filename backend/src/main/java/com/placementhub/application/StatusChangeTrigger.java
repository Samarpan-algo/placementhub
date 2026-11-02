package com.placementhub.application;

/** Why an application's status changed. Stored in the audit row, so a dispute can be traced to its cause. */
public enum StatusChangeTrigger {
	/** The student applied. */
	APPLY,
	/** The student withdrew. */
	WITHDRAW,
	/** An admin recorded or corrected a round result. */
	ROUND_RESULT,
	/** An admin selected the application (an offer). */
	SELECTION,
	/** A placement policy blocks the application after the student received an offer elsewhere. */
	POLICY,
	/** The drive was completed while the application was still pending. */
	DRIVE_COMPLETED,
	/** An admin changed an OPEN drive's eligibility criteria and the student no longer meets them. */
	CRITERIA_CHANGE
}
