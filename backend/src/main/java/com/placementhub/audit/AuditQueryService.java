package com.placementhub.audit;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.placementhub.common.BadRequestException;
import com.placementhub.common.PageResponse;
import com.placementhub.user.User;
import com.placementhub.user.UserRepository;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Admin: reads the audit log, newest first, with optional filters. Two queries per page (+ the count):
 * the rows, then the actors' emails in one IN query (no N+1).
 */
@Service
public class AuditQueryService {

	/** Always newest first; id breaks ties between rows written in the same transaction. Client sort is ignored. */
	static final Sort NEWEST_FIRST = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));

	private final AuditLogRepository auditLogRepository;
	private final UserRepository userRepository;
	private final JsonMapper jsonMapper;

	public AuditQueryService(AuditLogRepository auditLogRepository, UserRepository userRepository,
			JsonMapper jsonMapper) {
		this.auditLogRepository = auditLogRepository;
		this.userRepository = userRepository;
		this.jsonMapper = jsonMapper;
	}

	/**
	 * @param from inclusive, or null
	 * @param to   exclusive, or null
	 */
	@Transactional(readOnly = true)
	public PageResponse<AuditLogResponse> search(AuditEntityType entityType, Long entityId, Instant from, Instant to,
			Pageable pageable) {
		if (entityId != null && entityType == null) {
			throw new BadRequestException("entityId needs entityType: ids are only unique within one entity type");
		}
		if (from != null && to != null && !from.isBefore(to)) {
			throw new BadRequestException("from must be before to");
		}
		Pageable newestFirst = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), NEWEST_FIRST);
		Page<AuditLog> page = auditLogRepository.search(entityType, entityId, from, to, newestFirst);

		Map<Long, String> emails = actorEmails(page.getContent());
		// a system action has no actor (and Map.of() rejects a null key)
		return PageResponse.from(page.map(row -> toResponse(row,
				row.getActorId() == null ? null : emails.get(row.getActorId()))));
	}

	private Map<Long, String> actorEmails(List<AuditLog> rows) {
		List<Long> actorIds = rows.stream().map(AuditLog::getActorId).filter(Objects::nonNull).distinct().toList();
		if (actorIds.isEmpty()) {
			return Map.of();
		}
		return userRepository.findAllById(actorIds).stream()
				.collect(Collectors.toMap(User::getId, User::getEmail, (a, b) -> a));
	}

	private AuditLogResponse toResponse(AuditLog row, String actorEmail) {
		return new AuditLogResponse(row.getId(), row.getEntityType(), row.getEntityId(), row.getAction(),
				json(row.getOldValue()), json(row.getNewValue()), row.getActorId(), actorEmail, row.getCreatedAt());
	}

	private JsonNode json(String value) {
		return value == null ? null : jsonMapper.readTree(value);
	}

}
