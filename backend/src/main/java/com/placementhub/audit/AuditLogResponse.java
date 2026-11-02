package com.placementhub.audit;

import java.time.Instant;

import tools.jackson.databind.JsonNode;

/**
 * One audit row for the admin view. oldValue/newValue are real JSON objects in the response
 * (not JSON-in-a-string). actorEmail is null for a system action (actorId null).
 */
public record AuditLogResponse(
		Long id,
		AuditEntityType entityType,
		Long entityId,
		AuditAction action,
		JsonNode oldValue,
		JsonNode newValue,
		Long actorId,
		String actorEmail,
		Instant createdAt) {
}
