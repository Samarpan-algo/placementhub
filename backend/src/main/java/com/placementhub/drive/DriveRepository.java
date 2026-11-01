package com.placementhub.drive;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DriveRepository extends JpaRepository<Drive, Long> {

	long countByCompanyId(Long companyId);

	/** All drives in one status with their company JOINed; branches are batch-loaded (@BatchSize). */
	@EntityGraph(attributePaths = "company")
	List<Drive> findByStatusOrderByRegistrationDeadlineAsc(DriveStatus status);

	/**
	 * Paginated list with optional filters: a null parameter means "don't filter on this".
	 * The entity graph JOINs the company into the same query (safe with paging: many-to-one).
	 * Branches are a collection, so they are batch-loaded instead (see @BatchSize on Drive).
	 */
	@EntityGraph(attributePaths = "company")
	@Query("""
			SELECT d FROM Drive d
			WHERE (:status IS NULL OR d.status = :status)
			  AND (:companyId IS NULL OR d.company.id = :companyId)
			""")
	Page<Drive> search(@Param("status") DriveStatus status, @Param("companyId") Long companyId, Pageable pageable);

}
