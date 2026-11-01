package com.placementhub.common;

/** Thrown when a request is invalid in a way Bean Validation can't express. Mapped to 400. */
public class BadRequestException extends RuntimeException {

	public BadRequestException(String message) {
		super(message);
	}

}
