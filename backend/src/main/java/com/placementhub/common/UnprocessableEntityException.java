package com.placementhub.common;

import java.util.List;

/**
 * The request is well-formed, but business rules about the caller say no (e.g. not eligible).
 * Mapped to 422, with every reason in the error's "details" array.
 */
public class UnprocessableEntityException extends RuntimeException {

	private final List<String> details;

	public UnprocessableEntityException(String message, List<String> details) {
		super(message);
		this.details = List.copyOf(details);
	}

	public List<String> getDetails() {
		return details;
	}

}
