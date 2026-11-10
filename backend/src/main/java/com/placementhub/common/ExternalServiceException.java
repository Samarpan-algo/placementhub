package com.placementhub.common;

import org.springframework.http.HttpStatus;

/**
 * A service we depend on failed. Mapped to our usual error JSON with the given status (502 or 503).
 * The message goes to the client as it is, so it must never contain the other service's response.
 */
public class ExternalServiceException extends RuntimeException {

	private final HttpStatus status;

	private ExternalServiceException(HttpStatus status, String message) {
		super(message);
		this.status = status;
	}

	/** It answered, but with an error or something we can't use. */
	public static ExternalServiceException badGateway(String message) {
		return new ExternalServiceException(HttpStatus.BAD_GATEWAY, message);
	}

	/** It can't be reached, timed out, or isn't configured. */
	public static ExternalServiceException unavailable(String message) {
		return new ExternalServiceException(HttpStatus.SERVICE_UNAVAILABLE, message);
	}

	public HttpStatus getStatus() {
		return status;
	}

}
