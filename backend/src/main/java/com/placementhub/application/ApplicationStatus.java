package com.placementhub.application;

/**
 * REJECTED = rejected by the company in a round.
 * INELIGIBLE = no longer meets the criteria after an admin edited the drive (reason in statusReason).
 * WITHDRAWN = final; the student cannot apply to the same drive again.
 */
public enum ApplicationStatus {
	APPLIED, SHORTLISTED, REJECTED, SELECTED, WITHDRAWN, INELIGIBLE
}
