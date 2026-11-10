package com.placementhub.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

import javax.crypto.SecretKey;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class JwtConfigTest {

	@Test
	void secretShorterThan32BytesStopsTheApp() {
		assertThatThrownBy(() -> JwtConfig.toSigningKey("too-short-31-bytes-xxxxxxxxxxxx"))
				.isInstanceOf(IllegalStateException.class)
				.hasMessage("JWT_SECRET must be at least 32 bytes (256 bits) for HS256; got 31");
	}

	@Test
	void secretOfExactly32BytesIsAccepted() {
		SecretKey key = JwtConfig.toSigningKey("exactly-32-bytes-long-secret-xxx");

		assertThat(key.getEncoded()).hasSize(32);
		assertThat(key.getAlgorithm()).isEqualTo("HmacSHA256");
	}

	@Test
	void missingSecretFallsBackToTheDevOnlyDefault() {
		SecretKey key = new JwtConfig().jwtSigningKey(new JwtProperties("", Duration.ofHours(2), "placementhub"), true);

		assertThat(key.getEncoded()).isEqualTo(JwtConfig.DEV_ONLY_SECRET.getBytes());
	}

	// ---------- the prod profile: allow-dev-secret = false ----------

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = "   ")
	void inProductionAMissingSecretStopsTheApp(String secret) {
		JwtProperties properties = new JwtProperties(secret, Duration.ofHours(2), "placementhub");

		assertThatThrownBy(() -> new JwtConfig().jwtSigningKey(properties, false))
				.isInstanceOf(IllegalStateException.class)
				.hasMessage(JwtConfig.MISSING_SECRET_MESSAGE);
	}

	@Test
	void inProductionAShortSecretStillStopsTheApp() {
		JwtProperties properties = new JwtProperties("too-short", Duration.ofHours(2), "placementhub");

		assertThatThrownBy(() -> new JwtConfig().jwtSigningKey(properties, false))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageStartingWith("JWT_SECRET must be at least 32 bytes");
	}

	@Test
	void inProductionARealSecretIsUsedAsIs() {
		String secret = "a-real-production-secret-of-48-bytes-xxxxxxxxxxx";

		SecretKey key = new JwtConfig().jwtSigningKey(new JwtProperties(secret, Duration.ofHours(2), "placementhub"),
				false);

		assertThat(key.getEncoded()).isEqualTo(secret.getBytes());
	}

}
