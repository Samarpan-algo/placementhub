package com.placementhub.round;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Body for PUT /api/drives/{driveId}/rounds/{roundId}/results: one round's results sheet.
 * All or nothing: if any item is invalid, nothing is saved.
 */
public record RoundResultsRequest(

		@NotEmpty @Size(max = 500)
		List<@Valid @NotNull Item> results) {

	public record Item(

			@NotNull
			Long applicationId,

			@NotNull
			RoundOutcome result,

			@Size(max = 500)
			String remarks) {
	}

}
