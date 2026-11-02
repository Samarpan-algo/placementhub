package com.placementhub.drive;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.placementhub.common.Branch;

/**
 * @param applicationsMadeIneligible only in the PUT response: how many pending applications the edit made
 *                                   INELIGIBLE (0 if none). Left out (null) everywhere else.
 */
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
		Instant updatedAt,
		@JsonInclude(JsonInclude.Include.NON_NULL) Integer applicationsMadeIneligible) {

	public DriveResponse withApplicationsMadeIneligible(int count) {
		return new DriveResponse(id, companyId, companyName, roleTitle, jobType, ctcLpa, location, description,
				minCgpa, maxBacklogs, graduationYear, registrationDeadline, driveDate, status, branches, createdAt,
				updatedAt, count);
	}

}
