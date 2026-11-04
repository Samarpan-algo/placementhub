package com.placementhub.application;

import java.math.BigDecimal;
import java.time.Instant;

import com.placementhub.common.Branch;
import com.placementhub.round.RoundOutcome;

/** Admin view of one application to a drive: the student's details and their progress through the rounds. */
public record ApplicantResponse(
		Long applicationId,
		ApplicationStatus status,
		String statusReason,
		Instant appliedAt,
		Instant updatedAt,
		Long studentId,
		String fullName,
		String rollNo,
		String email,
		Branch branch,
		BigDecimal cgpa,
		int activeBacklogs,
		int graduationYear,
		String phone,
		boolean hasResume,
		Progress progress) {

	/**
	 * @param totalRounds the drive's rounds so far
	 * @param latestRound the highest-numbered round this application has a result in; null = no result yet
	 */
	public record Progress(int totalRounds, LatestRound latestRound) {
	}

	public record LatestRound(int roundNo, String name, RoundOutcome result, String remarks) {
	}

}
