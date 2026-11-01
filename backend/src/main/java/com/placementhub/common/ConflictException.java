package com.placementhub.common;

/** Thrown by services when a request breaks a business rule about existing data. Mapped to 409. */
public class ConflictException extends RuntimeException {

	public ConflictException(String message) {
		super(message);
	}

}
