package com.placementhub.common;

import java.time.Instant;

/** The single error JSON shape returned by every endpoint. */
public record ErrorResponse(Instant timestamp, int status, String error, String message, String path) {
}
