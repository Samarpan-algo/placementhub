package com.placementhub.common;

public record HealthResponse(String status, String database) {

	public static HealthResponse up() {
		return new HealthResponse("UP", "UP");
	}

	public static HealthResponse down() {
		return new HealthResponse("DOWN", "DOWN");
	}

}
