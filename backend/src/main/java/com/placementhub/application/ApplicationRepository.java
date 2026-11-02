package com.placementhub.application;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.placementhub.common.Branch;
import com.placementhub.policy.Offer;

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

	/**
	 * One query: every SELECTED application of the student, with company name, CTC and job type.
	 * Internship selections are included; PolicyEngine keeps only FULL_TIME ones as offers.
	 */
	@Query("""
			SELECT new com.placementhub.policy.Offer(c.name, d.ctcLpa, d.jobType)
			FROM Application a JOIN a.drive d JOIN d.company c
			WHERE a.student.id = :studentId
			  AND a.status = com.placementhub.application.ApplicationStatus.SELECTED
			ORDER BY a.id
			""")
	List<Offer> findSelectedOffers(@Param("studentId") Long studentId);

	// ---------- admin: applicants list and CSV export ----------

	/**
	 * A drive's applicants with optional filters (null = no filter). The student and their user (email)
	 * are JOINed into the same query: both are many-to-one, so this is safe with paging and avoids N+1.
	 * With an unpaged Pageable, Spring Data skips the count query.
	 */
	@EntityGraph(attributePaths = { "student", "student.user" })
	@Query("""
			SELECT a FROM Application a
			WHERE a.drive.id = :driveId
			  AND (:status IS NULL OR a.status = :status)
			  AND (:branch IS NULL OR a.student.branch = :branch)
			  AND (:minCgpa IS NULL OR a.student.cgpa >= :minCgpa)
			""")
	Page<Application> searchApplicants(@Param("driveId") Long driveId, @Param("status") ApplicationStatus status,
			@Param("branch") Branch branch, @Param("minCgpa") BigDecimal minCgpa, Pageable pageable);

	// ---------- admin: rounds, selection, completion ----------

	/**
	 * The distinct students behind the given applications of one drive. A plain id query, so no
	 * Application entity is loaded yet: callers lock these students first, then load the applications fresh.
	 */
	@Query("""
			SELECT DISTINCT a.student.id FROM Application a
			WHERE a.id IN :ids AND a.drive.id = :driveId
			""")
	List<Long> findStudentIdsByIdInAndDriveId(@Param("ids") Collection<Long> ids, @Param("driveId") Long driveId);

	/** Only applications of this drive: ids from another drive are simply missing from the result. */
	@EntityGraph(attributePaths = "student")
	List<Application> findByIdInAndDriveId(Collection<Long> ids, Long driveId);

	/** The distinct students with an application in one of the given statuses on the drive. */
	@Query("""
			SELECT DISTINCT a.student.id FROM Application a
			WHERE a.drive.id = :driveId AND a.status IN :statuses
			""")
	List<Long> findStudentIdsByDriveIdAndStatusIn(@Param("driveId") Long driveId,
			@Param("statuses") Collection<ApplicationStatus> statuses);

	List<Application> findByDriveIdAndStatusIn(Long driveId, Collection<ApplicationStatus> statuses);

	/**
	 * A student's other pending FULL_TIME applications (APPLIED/SHORTLISTED), with drive and company,
	 * to re-check placement policies after the student receives an offer.
	 */
	@Query("""
			SELECT a FROM Application a JOIN FETCH a.drive d JOIN FETCH d.company
			WHERE a.student.id = :studentId
			  AND a.id <> :excludedId
			  AND a.status IN (com.placementhub.application.ApplicationStatus.APPLIED,
			                   com.placementhub.application.ApplicationStatus.SHORTLISTED)
			  AND d.jobType = com.placementhub.drive.JobType.FULL_TIME
			ORDER BY a.id
			""")
	List<Application> findPendingFullTimeByStudentId(@Param("studentId") Long studentId,
			@Param("excludedId") Long excludedId);

}
