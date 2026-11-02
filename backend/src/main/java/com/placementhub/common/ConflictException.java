package com.placementhub.common;

import java.util.List;

/**
 * Thrown by services when a request breaks a business rule about existing data. Mapped to 409.
 * Optional details list every problem (e.g. each bad item of a batch); null means "no details".
 */
public class ConflictException extends RuntimeException {

	private final List<String> details;

	public ConflictException(String message) {
		super(message);
		this.details = null;
	}

	public ConflictException(String message, List<String> details) {
		super(message);
		this.details = List.copyOf(details);
	}

	public List<String> getDetails() {
		return details;
	}

}
