package com.placementhub.audit;

/** What an audit row is about. Must match ck_audit_logs_entity_type in the DB (V3). */
public enum AuditEntityType {
	DRIVE,
	APPLICATION,
	/** entity_id = round_results.id */
	ROUND_RESULT,
	/** entity_id = placement_policies.id */
	POLICY
}
