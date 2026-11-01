package com.placementhub.auth;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/** Builds the same HS256 encoder/decoder as JwtConfig, for unit tests. */
record JwtTestSupport(SecretKey key) {

	static final String ISSUER = "placementhub";

	JwtTestSupport(String secret) {
		this(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
	}

	JwtEncoder encoder() {
		return NimbusJwtEncoder.withSecretKey(key).algorithm(MacAlgorithm.HS256).build();
	}

	/** Decoder that checks exp/nbf against the real system clock (like production). */
	JwtDecoder decoder() {
		return decoderWithClock(Clock.systemUTC());
	}

	/** Decoder that believes "now" is the given instant, for tokens issued with a fixed Clock. */
	static JwtDecoder decoderAt(String secret, Instant now) {
		return new JwtTestSupport(secret).decoderWithClock(Clock.fixed(now, ZoneOffset.UTC));
	}

	private JwtDecoder decoderWithClock(Clock clock) {
		NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
		JwtTimestampValidator timestamps = new JwtTimestampValidator(Duration.ofSeconds(60));
		timestamps.setClock(clock);
		decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(timestamps, new JwtIssuerValidator(ISSUER)));
		return decoder;
	}

}
