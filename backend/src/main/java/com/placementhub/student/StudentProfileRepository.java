package com.placementhub.student;

import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StudentProfileRepository extends JpaRepository<StudentProfile, Long> {

	/** Loads the user in the same query, because profile responses include the email. */
	@EntityGraph(attributePaths = "user")
	Optional<StudentProfile> findByUserId(Long userId);

	boolean existsByRollNo(String rollNo);

}
