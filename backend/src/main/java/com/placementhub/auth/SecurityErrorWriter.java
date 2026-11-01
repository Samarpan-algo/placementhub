package com.placementhub.auth;

import java.io.IOException;
import java.time.Instant;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import com.placementhub.common.ErrorResponse;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import tools.jackson.databind.json.JsonMapper;

/**
 * Writes our standard error JSON from inside the security filter chain, where
 * GlobalExceptionHandler cannot help (the request never reaches a controller).
 */
@Component
public class SecurityErrorWriter {

	private final JsonMapper jsonMapper;

	public SecurityErrorWriter(JsonMapper jsonMapper) {
		this.jsonMapper = jsonMapper;
	}

	public void write(HttpServletRequest request, HttpServletResponse response, HttpStatus status, String message)
			throws IOException {
		ErrorResponse body = new ErrorResponse(Instant.now(), status.value(), status.getReasonPhrase(), message,
				request.getRequestURI());
		response.setStatus(status.value());
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.setCharacterEncoding("UTF-8");
		jsonMapper.writeValue(response.getOutputStream(), body);
	}

}
