package com.placementhub.common;

import java.util.List;

/**
 * Thrown by services for invalid input that Bean Validation can't express on its own
 * (e.g. a field that is required only for one policy type). Mapped to 400.
 * Optional details list every problem (e.g. each bad item of a batch); null means "no details".
 */
public class BadRequestException extends RuntimeException {

	private final List<String> details;

	public BadRequestException(String message) {
		super(message);
		this.details = null;
	}

	public BadRequestException(String message, List<String> details) {
		super(message);
		this.details = List.copyOf(details);
	}

	public List<String> getDetails() {
		return details;
	}

}
