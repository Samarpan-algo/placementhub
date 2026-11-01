package com.placementhub.student;

import org.springframework.stereotype.Component;

@Component
public class StudentProfileMapper {

	public void applyUpdate(StudentProfile profile, StudentProfileUpdateRequest request) {
		profile.setFullName(request.fullName().trim());
		profile.setPhone(blankToNull(request.phone()));
	}

	public StudentProfileResponse toResponse(StudentProfile profile) {
		return new StudentProfileResponse(
				profile.getId(),
				profile.getUser().getEmail(),
				profile.getFullName(),
				profile.getRollNo(),
				profile.getBranch(),
				profile.getGraduationYear().intValue(),
				profile.getCgpa(),
				profile.getActiveBacklogs().intValue(),
				profile.getPhone(),
				profile.getResumeUrl(),
				profile.getUpdatedAt());
	}

	private static String blankToNull(String value) {
		return (value == null || value.isBlank()) ? null : value.trim();
	}

}
