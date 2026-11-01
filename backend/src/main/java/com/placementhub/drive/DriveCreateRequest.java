package com.placementhub.drive;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;

import com.placementhub.common.Branch;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Body for POST /api/drives. The new drive always starts as DRAFT. */
public record DriveCreateRequest(

		@NotNull
		Long companyId,

		@NotBlank @Size(max = 150)
		String roleTitle,

		@NotNull
		JobType jobType,

		@NotNull @DecimalMin(value = "0.01") @Digits(integer = 4, fraction = 2)
		BigDecimal ctcLpa,

		@Size(max = 150)
		String location,

		@Size(max = 5000)
		String description,

		@NotNull @DecimalMin("0.00") @DecimalMax("10.00") @Digits(integer = 2, fraction = 2)
		BigDecimal minCgpa,

		@NotNull @Min(0) @Max(50)
		Integer maxBacklogs,

		@NotNull @Min(2000) @Max(2100)
		Integer graduationYear,

		@NotNull
		Instant registrationDeadline,

		LocalDate driveDate,

		@NotNull
		Set<@NotNull Branch> branches) implements DriveFields {
}
