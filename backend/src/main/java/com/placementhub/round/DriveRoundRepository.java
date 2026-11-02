package com.placementhub.round;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DriveRoundRepository extends JpaRepository<DriveRound, Long> {

	List<DriveRound> findByDriveIdOrderByRoundNoAsc(Long driveId);

	long countByDriveId(Long driveId);

	/** The drive is part of the query: a round of another drive is simply "not found". */
	Optional<DriveRound> findByIdAndDriveId(Long id, Long driveId);

	Optional<DriveRound> findByDriveIdAndRoundNo(Long driveId, Short roundNo);

	/** The highest round number of the drive, or null if it has no rounds yet. */
	@Query("SELECT MAX(r.roundNo) FROM DriveRound r WHERE r.drive.id = :driveId")
	Short findMaxRoundNo(@Param("driveId") Long driveId);

}
