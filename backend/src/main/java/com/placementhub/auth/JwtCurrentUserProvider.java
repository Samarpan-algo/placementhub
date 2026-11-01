package com.placementhub.auth;

import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import com.placementhub.user.CurrentUserProvider;

/**
 * The current user is the "sub" claim of the verified JWT. By the time this runs, Spring's
 * bearer-token filter has already checked the signature, expiry and issuer, so the id can be trusted.
 */
@Component
public class JwtCurrentUserProvider implements CurrentUserProvider {

	@Override
	public Long currentUserId() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication instanceof JwtAuthenticationToken jwtAuth) {
			return Long.valueOf(jwtAuth.getToken().getSubject());
		}
		// Only reachable if an endpoint that needs a user was wrongly made public
		throw new AuthenticationCredentialsNotFoundException("No authenticated user");
	}

}
