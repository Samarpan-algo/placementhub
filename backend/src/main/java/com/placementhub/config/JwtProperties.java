package com.placementhub.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * JWT settings from application.yml (app.jwt.*).
 * secret comes from the JWT_SECRET env var; when it is empty, JwtConfig falls back to a dev-only default.
 */
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(String secret, Duration expiry, String issuer) {
}
