package com.placementhub.common;

/** Thrown by services when a requested entity does not exist. Mapped to 404. */
public class ResourceNotFoundException extends RuntimeException {

	public ResourceNotFoundException(String message) {
		super(message);
	}

	public static ResourceNotFoundException of(String entity, Long id) {
		return new ResourceNotFoundException(entity + " with id " + id + " not found");
	}

}
