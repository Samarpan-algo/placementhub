package com.placementhub.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.placementhub.drive.JobType;
import com.placementhub.round.RoundOutcome;

/**
 * One of the current student's applications with round-by-round tracking: the same fields as
 * ApplicationResponse, plus every round of the drive and the student's result in it (null = no result yet).
 */
public record ApplicationDetailResponse(
		Long id,
		Long driveId,
		String companyName,
		String roleTitle,
		JobType jobType,
		BigDecimal ctcLpa,
		ApplicationStatus status,
		String statusReason,
		Instant appliedAt,
		Instant registrationDeadline,
		List<Round> rounds) {

	public record Round(
			int roundNo,
			String name,
			Instant scheduledAt,
			RoundOutcome result,
			String remarks) {
	}

}
