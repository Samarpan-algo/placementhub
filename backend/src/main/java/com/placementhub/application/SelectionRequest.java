package com.placementhub.application;

import java.util.List;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Body for POST /api/drives/{driveId}/selections. All or nothing: one invalid id and nobody is selected. */
public record SelectionRequest(

		@NotEmpty @Size(max = 200)
		List<@NotNull Long> applicationIds) {
}
