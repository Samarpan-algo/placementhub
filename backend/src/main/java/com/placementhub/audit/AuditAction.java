package com.placementhub.audit;

/** What happened. Must match ck_audit_logs_action in the DB (V3). */
public enum AuditAction {
	CRITERIA_CHANGED,
	STATUS_CHANGED,
	/** A round result was recorded or corrected (result or remarks). */
	RESULT_CHANGED,
	/** A placement policy's settings changed. */
	POLICY_CHANGED
}
