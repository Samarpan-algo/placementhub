package com.placementhub.common;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * The single error JSON shape returned by every endpoint: {timestamp, status, error, message, path}.
 * "details" is optional (e.g. every eligibility reason on a 422) and omitted from the JSON when null,
 * so existing errors look exactly the same.
 */
public record ErrorResponse(
		Instant timestamp,
		int status,
		String error,
		String message,
		@JsonInclude(JsonInclude.Include.NON_NULL) List<String> details,
		String path) {

	public ErrorResponse(Instant timestamp, int status, String error, String message, String path) {
		this(timestamp, status, error, message, null, path);
	}

}
