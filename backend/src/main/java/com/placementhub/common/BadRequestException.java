package com.placementhub.common;

/**
 * Thrown by services for invalid input that Bean Validation can't express on its own
 * (e.g. a field that is required only for one policy type). Mapped to 400.
 */
public class BadRequestException extends RuntimeException {

	public BadRequestException(String message) {
		super(message);
	}

}
