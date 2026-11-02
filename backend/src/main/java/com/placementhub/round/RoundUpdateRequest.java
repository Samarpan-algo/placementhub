package com.placementhub.round;

import java.time.Instant;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Body for PUT /api/drives/{driveId}/rounds/{roundId}. Replaces name and scheduledAt.
 * roundNo is optional and may only repeat the current number: round numbers never change.
 */
public record RoundUpdateRequest(

		Integer roundNo,

		@NotBlank @Size(max = 100)
		String name,

		Instant scheduledAt) {
}
