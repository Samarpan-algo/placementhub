-- V3: new audit row kinds and a longer application status reason (Milestone 7 Session B).
-- Never edit V1/V2: Flyway checksums them. Constraints are changed by dropping and re-adding.

-- ---------------------------------------------------------------------------
-- audit_logs: result-only edits (ROUND_RESULT / RESULT_CHANGED)
--             and policy changes (POLICY / POLICY_CHANGED)
-- ---------------------------------------------------------------------------
ALTER TABLE audit_logs
    DROP CONSTRAINT ck_audit_logs_entity_type,
    ADD CONSTRAINT ck_audit_logs_entity_type
        CHECK (entity_type IN ('DRIVE', 'APPLICATION', 'ROUND_RESULT', 'POLICY'));

ALTER TABLE audit_logs
    DROP CONSTRAINT ck_audit_logs_action,
    ADD CONSTRAINT ck_audit_logs_action
        CHECK (action IN ('CRITERIA_CHANGED', 'STATUS_CHANGED', 'RESULT_CHANGED', 'POLICY_CHANGED'));

-- ---------------------------------------------------------------------------
-- applications.status_reason: 300 -> 1000 characters.
-- Several joined policy or eligibility reasons could exceed 300. Widening a VARCHAR
-- in PostgreSQL is a metadata-only change (no table rewrite).
-- ---------------------------------------------------------------------------
ALTER TABLE applications ALTER COLUMN status_reason TYPE VARCHAR(1000);
