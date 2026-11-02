package com.placementhub.config;

import java.util.Arrays;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Which browser origins may call the API from another domain (the deployed frontend, Milestone 12).
 * In local dev the Vite proxy makes the browser think the API is same-origin, so CORS isn't used there.
 * SecurityConfig's .cors() picks up this bean by its name, "corsConfigurationSource".
 */
@Configuration
public class CorsConfig {

	@Bean
	public CorsConfigurationSource corsConfigurationSource(
			@Value("${app.cors.allowed-origins}") String allowedOrigins) {
		CorsConfiguration config = new CorsConfiguration();
		config.setAllowedOrigins(parseOrigins(allowedOrigins));
		config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
		config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
		// lets the frontend read the CSV export's file name
		config.setExposedHeaders(List.of("Content-Disposition"));
		// the token travels in a header, not a cookie, so no credentials mode is needed
		config.setAllowCredentials(false);
		config.setMaxAge(3600L);

		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/api/**", config);
		return source;
	}

	/** "https://a.app, https://b.app" -> [https://a.app, https://b.app]; blanks and trailing slashes dropped. */
	static List<String> parseOrigins(String allowedOrigins) {
		return Arrays.stream(allowedOrigins.split(","))
				.map(String::trim)
				.map(origin -> origin.endsWith("/") ? origin.substring(0, origin.length() - 1) : origin)
				.filter(origin -> !origin.isEmpty())
				.toList();
	}

}
