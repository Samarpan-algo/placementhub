package com.placementhub.student;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface StudentProfileRepository extends JpaRepository<StudentProfile, Long> {

	/** Loads the user in the same query, because profile responses include the email. */
	@EntityGraph(attributePaths = "user")
	Optional<StudentProfile> findByUserId(Long userId);

	boolean existsByRollNo(String rollNo);

	/*
	 * Locking rule: the student's profile row is the lock for all of that student's applications.
	 * Every transaction that changes a student's application status (apply, withdraw, round results,
	 * selection, completing a drive) first takes SELECT ... FOR UPDATE on the student row, so policy
	 * checks always see the student's latest offers. Lock first, then load applications, or
	 * Hibernate would keep the stale copies it loaded before the lock.
	 */

	/** The current student's row, locked until the transaction ends. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("SELECT s FROM StudentProfile s WHERE s.user.id = :userId")
	Optional<StudentProfile> findByUserIdForUpdate(@Param("userId") Long userId);

	/** Several students locked at once. ORDER BY id: every transaction locks in the same order, so no deadlock. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("SELECT s FROM StudentProfile s WHERE s.id IN :ids ORDER BY s.id")
	List<StudentProfile> findAllByIdForUpdate(@Param("ids") Collection<Long> ids);

}
