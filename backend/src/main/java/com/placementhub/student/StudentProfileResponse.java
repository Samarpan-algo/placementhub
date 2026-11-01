package com.placementhub.student;

import java.math.BigDecimal;
import java.time.Instant;

import com.placementhub.common.Branch;

public record StudentProfileResponse(
		Long id,
		String email,
		String fullName,
		String rollNo,
		Branch branch,
		Integer graduationYear,
		BigDecimal cgpa,
		Integer activeBacklogs,
		String phone,
		String resumeUrl,
		Instant updatedAt) {
}
