package com.placementhub.drive;

import org.springframework.stereotype.Component;

/** Hand-written mapping between Drive entities and DTOs. Call toResponse inside a transaction (LAZY fields). */
@Component
public class DriveMapper {

	public void applyFields(Drive drive, DriveFields fields) {
		drive.setRoleTitle(fields.roleTitle().trim());
		drive.setJobType(fields.jobType());
		drive.setCtcLpa(fields.ctcLpa());
		drive.setLocation(blankToNull(fields.location()));
		drive.setDescription(blankToNull(fields.description()));
		drive.setMinCgpa(fields.minCgpa());
		drive.setMaxBacklogs(fields.maxBacklogs().shortValue());
		drive.setGraduationYear(fields.graduationYear().shortValue());
		drive.setRegistrationDeadline(fields.registrationDeadline());
		drive.setDriveDate(fields.driveDate());
		drive.setBranches(fields.branches());
	}

	public DriveResponse toResponse(Drive drive) {
		return new DriveResponse(
				drive.getId(),
				drive.getCompany().getId(),
				drive.getCompany().getName(),
				drive.getRoleTitle(),
				drive.getJobType(),
				drive.getCtcLpa(),
				drive.getLocation(),
				drive.getDescription(),
				drive.getMinCgpa(),
				drive.getMaxBacklogs().intValue(),
				drive.getGraduationYear().intValue(),
				drive.getRegistrationDeadline(),
				drive.getDriveDate(),
				drive.getStatus(),
				drive.getBranches().stream().sorted().toList(),
				drive.getCreatedAt(),
				drive.getUpdatedAt(),
				null);
	}

	private static String blankToNull(String value) {
		return (value == null || value.isBlank()) ? null : value.trim();
	}

}
