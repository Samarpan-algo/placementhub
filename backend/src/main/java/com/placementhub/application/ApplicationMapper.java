package com.placementhub.application;

import org.springframework.stereotype.Component;

import com.placementhub.drive.Drive;

/** Call toResponse inside a transaction: drive and company are LAZY. */
@Component
public class ApplicationMapper {

	public ApplicationResponse toResponse(Application application) {
		Drive drive = application.getDrive();
		return new ApplicationResponse(
				application.getId(),
				drive.getId(),
				drive.getCompany().getName(),
				drive.getRoleTitle(),
				drive.getJobType(),
				drive.getCtcLpa(),
				application.getStatus(),
				application.getStatusReason(),
				application.getAppliedAt(),
				drive.getRegistrationDeadline());
	}

}
