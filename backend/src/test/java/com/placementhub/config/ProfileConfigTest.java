package com.placementhub.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Properties;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;

/** Guards for the profile files: the demo seed must never be reachable from production. */
class ProfileConfigTest {

	private static Properties load(String file) {
		YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
		yaml.setResources(new ClassPathResource(file));
		return yaml.getObject();
	}

	@Test
	void productionReadsOnlyTheNormalMigrations() {
		assertThat(load("application-prod.yml").getProperty("spring.flyway.locations"))
				.isEqualTo("classpath:db/migration");
	}

	@Test
	void theBaseConfigDoesNotIncludeTheDemoSeed() {
		assertThat(load("application.yml").getProperty("spring.flyway.locations"))
				.isEqualTo("classpath:db/migration");
	}

	@Test
	void devAndTestAddTheDemoSeed() {
		assertThat(load("application-dev.yml").getProperty("spring.flyway.locations")).contains("classpath:db/demo");
		assertThat(load("application-test.yml").getProperty("spring.flyway.locations")).contains("classpath:db/demo");
	}

	@Test
	void productionHasNoJwtFallbackAndNoDefaultsForSecrets() {
		Properties prod = load("application-prod.yml");

		assertThat(prod.getProperty("app.jwt.allow-dev-secret")).isEqualTo("false");
		assertThat(prod.getProperty("spring.datasource.url")).isEqualTo("${DB_URL}");
		assertThat(prod.getProperty("app.cors.allowed-origins")).isEqualTo("${CORS_ALLOWED_ORIGINS}");
		assertThat(prod.getProperty("spring.datasource.hikari.data-source-properties.sslmode"))
				.isEqualTo("${DB_SSLMODE:require}");
	}

}
