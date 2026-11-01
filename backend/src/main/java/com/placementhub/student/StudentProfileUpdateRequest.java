package com.placementhub.student;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * The ONLY profile fields a student may change. Academic fields (CGPA, backlogs, branch,
 * graduation year, roll no) are deliberately absent, so a request cannot change them;
 * unknown JSON fields such as "cgpa" are ignored.
 */
public record StudentProfileUpdateRequest(

		@NotBlank
		@Size(max = 100)
		String fullName,

		@Pattern(regexp = "^\\+?[0-9]{10,14}$", message = "must be 10 to 14 digits, optionally starting with +")
		String phone) {
}
