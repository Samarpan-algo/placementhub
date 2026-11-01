package com.placementhub.drive;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import com.placementhub.common.Branch;

public record DriveResponse(
		Long id,
		Long companyId,
		String companyName,
		String roleTitle,
		JobType jobType,
		BigDecimal ctcLpa,
		String location,
		String description,
		BigDecimal minCgpa,
		Integer maxBacklogs,
		Integer graduationYear,
		Instant registrationDeadline,
		LocalDate driveDate,
		DriveStatus status,
		List<Branch> branches,
		Instant createdAt,
		Instant updatedAt) {
}
