package com.placementhub.audit;

/** What an audit row is about. Must match ck_audit_logs_entity_type in the DB. */
public enum AuditEntityType {
	DRIVE,
	APPLICATION
}
