package com.placementhub.auth;

import java.io.IOException;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/** 403 in our standard JSON: valid token, but the wrong role for this endpoint. */
@Component
public class RestAccessDeniedHandler implements AccessDeniedHandler {

	private final SecurityErrorWriter errorWriter;

	public RestAccessDeniedHandler(SecurityErrorWriter errorWriter) {
		this.errorWriter = errorWriter;
	}

	@Override
	public void handle(HttpServletRequest request, HttpServletResponse response,
			AccessDeniedException accessDeniedException) throws IOException {
		errorWriter.write(request, response, HttpStatus.FORBIDDEN,
				"You do not have permission to access this resource");
	}

}
