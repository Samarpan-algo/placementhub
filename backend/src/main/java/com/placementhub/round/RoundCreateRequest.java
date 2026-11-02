package com.placementhub.round;

import java.time.Instant;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Body for POST /api/drives/{driveId}/rounds. roundNo must be the drive's next number (1, 2, 3…). */
public record RoundCreateRequest(

		@NotNull @Min(1) @Max(20)
		Integer roundNo,

		@NotBlank @Size(max = 100)
		String name,

		Instant scheduledAt) {
}
