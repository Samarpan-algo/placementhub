package com.placementhub.audit;

import java.time.Instant;

import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.placementhub.common.PageResponse;

/** Admin: the audit log (TPO_ADMIN via /api/audit/** in SecurityConfig). */
@RestController
@RequestMapping("/api/audit")
public class AuditController {

	private final AuditQueryService auditQueryService;

	public AuditController(AuditQueryService auditQueryService) {
		this.auditQueryService = auditQueryService;
	}

	/** from/to are ISO-8601 instants, e.g. 2026-11-02T00:00:00Z (from inclusive, to exclusive). */
	@GetMapping
	public PageResponse<AuditLogResponse> search(
			@RequestParam(required = false) AuditEntityType entityType,
			@RequestParam(required = false) Long entityId,
			@RequestParam(required = false) Instant from,
			@RequestParam(required = false) Instant to,
			@PageableDefault(size = 20) Pageable pageable) {
		return auditQueryService.search(entityType, entityId, from, to, pageable);
	}

}
