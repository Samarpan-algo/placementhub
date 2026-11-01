package com.placementhub.drive;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;

import com.placementhub.common.Branch;

/** The editable drive fields shared by the create and update requests. */
public interface DriveFields {

	String roleTitle();

	JobType jobType();

	BigDecimal ctcLpa();

	String location();

	String description();

	BigDecimal minCgpa();

	Integer maxBacklogs();

	Integer graduationYear();

	Instant registrationDeadline();

	LocalDate driveDate();

	Set<Branch> branches();

}
