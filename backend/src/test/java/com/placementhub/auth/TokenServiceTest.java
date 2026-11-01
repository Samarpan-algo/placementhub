package com.placementhub.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;

import com.placementhub.config.JwtProperties;
import com.placementhub.user.Role;

/** Real HS256 encoder and decoder: no mocks, so signature and expiry checks are genuinely exercised. */
class TokenServiceTest {

	private static final String SECRET = "test-secret-that-is-definitely-at-least-32-bytes-long";
	private static final Instant NOW = Instant.parse("2026-11-01T10:00:00Z");
	private static final AuthUser ADMIN = new AuthUser(1L, "admin@placementhub.dev", Role.TPO_ADMIN, "hash");

	private TokenService tokenServiceAt(Instant now) {
		return new TokenService(new JwtTestSupport(SECRET).encoder(),
				new JwtProperties(SECRET, Duration.ofHours(2), JwtTestSupport.ISSUER), Clock.fixed(now, ZoneOffset.UTC));
	}

	@Test
	void tokenCarriesSubjectRoleEmailIssuerAndTwoHourExpiry() {
		TokenService.IssuedToken token = tokenServiceAt(NOW).issue(ADMIN);

		Jwt jwt = JwtTestSupport.decoderAt(SECRET, NOW.plusSeconds(1)).decode(token.value());

		assertThat(jwt.getSubject()).isEqualTo("1");
		assertThat(jwt.getClaimAsString("role")).isEqualTo("TPO_ADMIN");
		assertThat(jwt.getClaimAsString("email")).isEqualTo("admin@placementhub.dev");
		// getIssuer() converts to URL; our issuer is a plain name, so read the raw claim
		assertThat(jwt.getClaimAsString("iss")).isEqualTo("placementhub");
		assertThat(jwt.getIssuedAt()).isEqualTo(NOW);
		assertThat(jwt.getExpiresAt()).isEqualTo(NOW.plus(Duration.ofHours(2)));
		assertThat(jwt.getHeaders()).containsEntry("alg", "HS256");
		assertThat(token.expiresInSeconds()).isEqualTo(7200);
	}

	@Test
	void tokenSignedWithAnotherSecretIsRejected() {
		String token = tokenServiceAt(NOW).issue(ADMIN).value();

		assertThatThrownBy(() -> JwtTestSupport.decoderAt("a-completely-different-secret-of-32-plus-bytes!!", NOW)
				.decode(token))
				.isInstanceOf(JwtException.class);
	}

	@Test
	void tamperedPayloadIsRejected() {
		String token = tokenServiceAt(NOW).issue(ADMIN).value();
		String[] parts = token.split("\\.");
		// Swap in a payload claiming a different subject; the signature no longer matches
		String forgedPayload = java.util.Base64.getUrlEncoder().withoutPadding()
				.encodeToString("{\"sub\":\"999\",\"role\":\"TPO_ADMIN\",\"iss\":\"placementhub\"}".getBytes());
		String forged = parts[0] + "." + forgedPayload + "." + parts[2];

		assertThatThrownBy(() -> JwtTestSupport.decoderAt(SECRET, NOW).decode(forged))
				.isInstanceOf(JwtException.class);
	}

	@Test
	void expiredTokenIsRejected() {
		String token = tokenServiceAt(NOW).issue(ADMIN).value();

		// 2 hours + more than the 60s clock skew later
		assertThatThrownBy(() -> JwtTestSupport.decoderAt(SECRET, NOW.plus(Duration.ofHours(2)).plusSeconds(61))
				.decode(token))
				.isInstanceOf(JwtException.class)
				.hasMessageContaining("expired");
	}

	@Test
	void tokenIsStillValidJustBeforeExpiry() {
		String token = tokenServiceAt(NOW).issue(ADMIN).value();

		Jwt jwt = JwtTestSupport.decoderAt(SECRET, NOW.plus(Duration.ofHours(2)).minusSeconds(1)).decode(token);

		assertThat(jwt.getSubject()).isEqualTo("1");
	}

}
