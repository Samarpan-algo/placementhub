package com.placementhub.config;

import java.nio.charset.StandardCharsets;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/**
 * HS256 signing key plus Spring Security's JwtEncoder (login) and JwtDecoder (every request).
 * No third-party JWT library and no hand-written parsing: Spring verifies signature, exp and iss.
 */
@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class JwtConfig {

	private static final Logger log = LoggerFactory.getLogger(JwtConfig.class);

	/** DEV ONLY. Used when JWT_SECRET is not set. Milestone 12 must set a real secret. */
	static final String DEV_ONLY_SECRET = "dev-only-insecure-jwt-secret-CHANGE-ME-before-deploying-0123456789";

	static final String MISSING_SECRET_MESSAGE = "JWT_SECRET is required in production (the prod profile) and must be "
			+ "at least 32 bytes. See docs/deploy.md for how to generate one";

	/** HS256 needs a key of at least 256 bits. */
	static final int MIN_SECRET_BYTES = 32;

	@Bean
	public SecretKey jwtSigningKey(JwtProperties properties,
			@Value("${app.jwt.allow-dev-secret:true}") boolean allowDevSecret) {
		String secret = properties.secret();
		if (secret == null || secret.isBlank()) {
			if (!allowDevSecret) {
				throw new IllegalStateException(MISSING_SECRET_MESSAGE);
			}
			log.warn("""

					********************************************************************************
					*  WARNING: JWT_SECRET is not set. Using the DEV-ONLY default signing secret.   *
					*  Anyone who reads the source code can forge tokens with it.                   *
					*  Set JWT_SECRET (at least 32 bytes, e.g. `openssl rand -base64 48`)            *
					*  before deploying. See docs/decisions.md (Milestone 12).                      *
					********************************************************************************""");
			secret = DEV_ONLY_SECRET;
		}
		return toSigningKey(secret);
	}

	/** Fails fast at startup if the secret is too short to be a safe HS256 key. */
	static SecretKey toSigningKey(String secret) {
		byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
		if (bytes.length < MIN_SECRET_BYTES) {
			throw new IllegalStateException("JWT_SECRET must be at least " + MIN_SECRET_BYTES
					+ " bytes (256 bits) for HS256; got " + bytes.length);
		}
		return new SecretKeySpec(bytes, "HmacSHA256");
	}

	@Bean
	public JwtEncoder jwtEncoder(SecretKey jwtSigningKey) {
		return NimbusJwtEncoder.withSecretKey(jwtSigningKey).algorithm(MacAlgorithm.HS256).build();
	}

	@Bean
	public JwtDecoder jwtDecoder(SecretKey jwtSigningKey, JwtProperties properties) {
		NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(jwtSigningKey)
				.macAlgorithm(MacAlgorithm.HS256)
				.build();
		// Default validators check exp/nbf (60s clock skew); we also require our own issuer
		decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(properties.issuer()));
		return decoder;
	}

}
