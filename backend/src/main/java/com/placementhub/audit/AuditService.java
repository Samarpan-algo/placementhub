package com.placementhub.audit;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.placementhub.user.CurrentUserProvider;

import tools.jackson.databind.json.JsonMapper;

/**
 * Writes audit rows. The actor is always the user making the current request (from the JWT).
 * MANDATORY: the row must be written in the caller's transaction, so a change and its audit row
 * are committed together or rolled back together. Calling this outside a transaction is a bug and fails.
 */
@Service
public class AuditService {

	private final AuditLogRepository auditLogRepository;
	private final CurrentUserProvider currentUserProvider;
	private final JsonMapper jsonMapper;
	private final Clock clock;

	public AuditService(AuditLogRepository auditLogRepository, CurrentUserProvider currentUserProvider,
			JsonMapper jsonMapper, Clock clock) {
		this.auditLogRepository = auditLogRepository;
		this.currentUserProvider = currentUserProvider;
		this.jsonMapper = jsonMapper;
		this.clock = clock;
	}

	/**
	 * @param oldValue null when the entity is new (e.g. a fresh application)
	 * @param newValue what it looks like after the change
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public void record(AuditEntityType entityType, Long entityId, AuditAction action, Map<String, ?> oldValue,
			Map<String, ?> newValue) {
		auditLogRepository.save(new AuditLog(entityType, entityId, action, toJson(oldValue), toJson(newValue),
				currentUserProvider.currentUserId(), Instant.now(clock)));
	}

	private String toJson(Map<String, ?> value) {
		return value == null ? null : jsonMapper.writeValueAsString(value);
	}

}
