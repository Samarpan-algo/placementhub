package com.placementhub.application;

import java.math.BigDecimal;
import java.time.Instant;

import com.placementhub.drive.JobType;

/** One of the current student's applications, with the drive details a student needs to see. */
public record ApplicationResponse(
		Long id,
		Long driveId,
		String companyName,
		String roleTitle,
		JobType jobType,
		BigDecimal ctcLpa,
		ApplicationStatus status,
		String statusReason,
		Instant appliedAt,
		Instant registrationDeadline) {
}
