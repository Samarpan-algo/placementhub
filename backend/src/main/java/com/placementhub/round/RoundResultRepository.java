package com.placementhub.round;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RoundResultRepository extends JpaRepository<RoundResult, Long> {

	/**
	 * One query: every result (in every round) of the given applications, with the round JOINed,
	 * because sequencing checks and status reasons need the round number and name.
	 */
	@Query("""
			SELECT r FROM RoundResult r JOIN FETCH r.round
			WHERE r.application.id IN :applicationIds
			""")
	List<RoundResult> findWithRoundByApplicationIdIn(@Param("applicationIds") Collection<Long> applicationIds);

	/** One application's results, for the student's round tracking. */
	@Query("SELECT r FROM RoundResult r WHERE r.application.id = :applicationId")
	List<RoundResult> findByApplicationId(@Param("applicationId") Long applicationId);

}
