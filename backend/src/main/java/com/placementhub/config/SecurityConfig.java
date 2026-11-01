package com.placementhub.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

import com.placementhub.auth.RestAccessDeniedHandler;
import com.placementhub.auth.RestAuthenticationEntryPoint;

/**
 * Stateless JWT security. Every request is authenticated by Spring's bearer-token filter
 * (signature + exp + iss checked by JwtDecoder); the "role" claim becomes ROLE_STUDENT / ROLE_TPO_ADMIN.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

	/** Must match the cost of the seeded demo hashes (V2__seed_demo_data.sql). */
	private static final int BCRYPT_COST = 10;

	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http, RestAuthenticationEntryPoint entryPoint,
			RestAccessDeniedHandler accessDeniedHandler) throws Exception {
		http
				// No cookies or sessions: the token travels in the Authorization header, which browsers
				// never attach automatically, so a forged cross-site request carries no credentials (no CSRF risk)
				.csrf(AbstractHttpConfigurer::disable)
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.httpBasic(AbstractHttpConfigurer::disable)
				.formLogin(AbstractHttpConfigurer::disable)
				.logout(AbstractHttpConfigurer::disable)
				.authorizeHttpRequests(auth -> auth
						// /error is Spring's internal error page; keeping it public avoids masking errors as 401
						.requestMatchers("/api/auth/**", "/api/health", "/error").permitAll()
						.requestMatchers("/api/students/me/**").hasRole("STUDENT")
						.requestMatchers("/api/companies/**", "/api/drives/**").hasRole("TPO_ADMIN")
						.anyRequest().authenticated())
				.oauth2ResourceServer(oauth2 -> oauth2
						.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
						.authenticationEntryPoint(entryPoint)
						.accessDeniedHandler(accessDeniedHandler))
				.exceptionHandling(exceptions -> exceptions
						.authenticationEntryPoint(entryPoint)
						.accessDeniedHandler(accessDeniedHandler));
		return http.build();
	}

	/** Maps the "role" claim (e.g. "STUDENT") to the authority ROLE_STUDENT used by hasRole("STUDENT"). */
	@Bean
	public JwtAuthenticationConverter jwtAuthenticationConverter() {
		JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
		authorities.setAuthoritiesClaimName("role");
		authorities.setAuthorityPrefix("ROLE_");
		JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
		converter.setJwtGrantedAuthoritiesConverter(authorities);
		return converter;
	}

	@Bean
	public PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder(BCRYPT_COST);
	}

}
