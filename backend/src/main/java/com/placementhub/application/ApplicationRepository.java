package com.placementhub.application;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ApplicationRepository extends JpaRepository<Application, Long> {

	Optional<Application> findByDriveIdAndStudentId(Long driveId, Long studentId);

	/**
	 * Ownership is part of the query: someone else's application is simply "not found".
	 * Drive and company are JOINed (many-to-one, so safe), because responses show them.
	 */
	@EntityGraph(attributePaths = { "drive", "drive.company" })
	Optional<Application> findByIdAndStudentId(Long id, Long studentId);

	@EntityGraph(attributePaths = { "drive", "drive.company" })
	Page<Application> findByStudentId(Long studentId, Pageable pageable);

	/** One query for the whole browse page: the student's status on each of the given drives. */
	@Query("""
			SELECT new com.placementhub.application.DriveApplicationStatus(a.drive.id, a.status)
			FROM Application a
			WHERE a.student.id = :studentId AND a.drive.id IN :driveIds
			""")
	List<DriveApplicationStatus> findStatuses(@Param("studentId") Long studentId,
			@Param("driveIds") Collection<Long> driveIds);

}
