package com.placementhub.audit;

import java.time.Instant;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * One append-only audit row: who (actor) did what (action) to which entity, old → new, when.
 * Old and new values are JSON text, stored in JSONB columns. There are no setters: rows are never edited.
 */
@Entity
@Table(name = "audit_logs")
public class AuditLog {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Enumerated(EnumType.STRING)
	@Column(name = "entity_type", nullable = false, length = 30, updatable = false)
	private AuditEntityType entityType;

	@Column(name = "entity_id", nullable = false, updatable = false)
	private Long entityId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30, updatable = false)
	private AuditAction action;

	// A String with the JSON JDBC type: Hibernate sends the text as-is into the JSONB column
	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "old_value", columnDefinition = "jsonb", updatable = false)
	private String oldValue;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "new_value", columnDefinition = "jsonb", updatable = false)
	private String newValue;

	/** A plain id, not a relation: the audit log never needs to load the user. NULL = system action. */
	@Column(name = "actor_id", updatable = false)
	private Long actorId;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected AuditLog() {
		// for JPA
	}

	public AuditLog(AuditEntityType entityType, Long entityId, AuditAction action, String oldValue,
			String newValue, Long actorId, Instant createdAt) {
		this.entityType = entityType;
		this.entityId = entityId;
		this.action = action;
		this.oldValue = oldValue;
		this.newValue = newValue;
		this.actorId = actorId;
		this.createdAt = createdAt;
	}

	public Long getId() {
		return id;
	}

	public AuditEntityType getEntityType() {
		return entityType;
	}

	public Long getEntityId() {
		return entityId;
	}

	public AuditAction getAction() {
		return action;
	}

	public String getOldValue() {
		return oldValue;
	}

	public String getNewValue() {
		return newValue;
	}

	public Long getActorId() {
		return actorId;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

}
