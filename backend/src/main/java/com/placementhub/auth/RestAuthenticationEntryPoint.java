package com.placementhub.auth;

import java.io.IOException;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/** 401 in our standard JSON: no token, or a token that is invalid, tampered or expired. */
@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

	private final SecurityErrorWriter errorWriter;

	public RestAuthenticationEntryPoint(SecurityErrorWriter errorWriter) {
		this.errorWriter = errorWriter;
	}

	@Override
	public void commence(HttpServletRequest request, HttpServletResponse response,
			AuthenticationException authException) throws IOException {
		boolean badToken = authException instanceof InvalidBearerTokenException;
		// RFC 6750: tell the client which scheme to use
		response.setHeader(HttpHeaders.WWW_AUTHENTICATE, badToken ? "Bearer error=\"invalid_token\"" : "Bearer");
		errorWriter.write(request, response, HttpStatus.UNAUTHORIZED,
				badToken ? "Invalid or expired token" : "Authentication required");
	}

}
