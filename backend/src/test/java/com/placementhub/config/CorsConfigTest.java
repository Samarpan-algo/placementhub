package com.placementhub.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CorsConfigTest {

	@Test
	void parsesCommaSeparatedOriginsTrimmingSpacesAndTrailingSlashes() {
		assertThat(CorsConfig.parseOrigins(" https://a.app/ ,https://b.app,, "))
				.containsExactly("https://a.app", "https://b.app");
	}

	@Test
	void singleOriginStaysAsIs() {
		assertThat(CorsConfig.parseOrigins("http://localhost:5173")).containsExactly("http://localhost:5173");
	}

}
