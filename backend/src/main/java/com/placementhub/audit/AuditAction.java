package com.placementhub.audit;

/** What happened. Must match ck_audit_logs_action in the DB. */
public enum AuditAction {
	CRITERIA_CHANGED,
	STATUS_CHANGED
}
