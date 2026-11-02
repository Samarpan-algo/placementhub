package com.placementhub.audit;

import java.time.Instant;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

	/**
	 * Optional filters: a null parameter means "no filter on this". from is inclusive, to exclusive.
	 * Uses idx_audit_logs_entity (type + id) and idx_audit_logs_created_at.
	 * CAST: for a null Instant the driver sends no type, and PostgreSQL fails with "could not determine
	 * data type of parameter" on "? IS NULL". The cast tells it the type. (Enum and Long nulls are fine.)
	 */
	@Query("""
			SELECT a FROM AuditLog a
			WHERE (:entityType IS NULL OR a.entityType = :entityType)
			  AND (:entityId IS NULL OR a.entityId = :entityId)
			  AND (CAST(:from AS Instant) IS NULL OR a.createdAt >= :from)
			  AND (CAST(:to AS Instant) IS NULL OR a.createdAt < :to)
			""")
	Page<AuditLog> search(@Param("entityType") AuditEntityType entityType, @Param("entityId") Long entityId,
			@Param("from") Instant from, @Param("to") Instant to, Pageable pageable);

}
