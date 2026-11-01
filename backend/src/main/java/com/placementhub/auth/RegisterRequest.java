package com.placementhub.auth;

import java.math.BigDecimal;
import java.util.Locale;

import com.placementhub.common.Branch;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Public student self-registration. There is deliberately no "role" field: every account
 * created here is a STUDENT. Academic fields are self-reported now and read-only afterwards.
 */
public record RegisterRequest(

		@NotBlank @Email @Size(max = 255)
		String email,

		// BCrypt only uses the first 72 bytes, so longer passwords are rejected rather than silently cut
		@NotBlank @Size(min = 8, max = 72)
		String password,

		@NotBlank @Size(max = 100)
		String fullName,

		@NotBlank @Size(max = 30)
		String rollNo,

		@NotNull
		Branch branch,

		@NotNull @Min(2000) @Max(2100)
		Integer graduationYear,

		@NotNull @DecimalMin("0.00") @DecimalMax("10.00") @Digits(integer = 2, fraction = 2)
		BigDecimal cgpa,

		@NotNull @Min(0) @Max(50)
		Integer activeBacklogs,

		@Pattern(regexp = "^\\+?[0-9]{10,14}$", message = "must be 10 to 14 digits, optionally starting with +")
		String phone) {

	/** Normalise before validation runs: emails are stored lowercase, roll numbers uppercase. */
	public RegisterRequest {
		email = email == null ? null : email.trim().toLowerCase(Locale.ROOT);
		rollNo = rollNo == null ? null : rollNo.trim().toUpperCase(Locale.ROOT);
		fullName = fullName == null ? null : fullName.trim();
		phone = (phone == null || phone.isBlank()) ? null : phone.trim();
	}

}
