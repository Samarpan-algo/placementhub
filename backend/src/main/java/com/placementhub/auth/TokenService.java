package com.placementhub.auth;

import java.time.Clock;
import java.time.Instant;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import com.placementhub.config.JwtProperties;

/** Issues signed HS256 access tokens. Validation happens in Spring's JwtDecoder on every request. */
@Service
public class TokenService {

	public static final String ROLE_CLAIM = "role";
	public static final String EMAIL_CLAIM = "email";

	private final JwtEncoder jwtEncoder;
	private final JwtProperties properties;
	private final Clock clock;

	public TokenService(JwtEncoder jwtEncoder, JwtProperties properties, Clock clock) {
		this.jwtEncoder = jwtEncoder;
		this.properties = properties;
		this.clock = clock;
	}

	public IssuedToken issue(AuthUser user) {
		Instant now = Instant.now(clock);
		Instant expiresAt = now.plus(properties.expiry());
		JwtClaimsSet claims = JwtClaimsSet.builder()
				.issuer(properties.issuer())
				.subject(user.id().toString()) // "sub" must be a string; the app reads it as the user id
				.issuedAt(now)
				.expiresAt(expiresAt)
				.claim(ROLE_CLAIM, user.role().name())
				.claim(EMAIL_CLAIM, user.email())
				.build();
		JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
		String token = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
		return new IssuedToken(token, properties.expiry().toSeconds());
	}

	public record IssuedToken(String value, long expiresInSeconds) {
	}

}
