package com.placementhub.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

import javax.crypto.SecretKey;

import org.junit.jupiter.api.Test;

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
		SecretKey key = new JwtConfig().jwtSigningKey(new JwtProperties("", Duration.ofHours(2), "placementhub"));

		assertThat(key.getEncoded()).isEqualTo(JwtConfig.DEV_ONLY_SECRET.getBytes());
	}

}
