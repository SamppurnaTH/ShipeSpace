package com.shipspace.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Arrays;

/**
 * Validates critical security configuration at startup.
 * Fails fast if insecure defaults are detected in non-development environments.
 */
@Slf4j
@Component
public class StartupSecurityValidator {

    private static final String INSECURE_JWT_SECRET = "your-secret-key-change-this-in-production-minimum-256-bits";
    private static final int MIN_JWT_SECRET_LENGTH = 32;

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Value("${github.oauth.client-id}")
    private String githubClientId;

    private final Environment environment;

    public StartupSecurityValidator(Environment environment) {
        this.environment = environment;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void validateSecurityConfig() {
        boolean isDev = Arrays.stream(environment.getActiveProfiles())
                .anyMatch(p -> p.equalsIgnoreCase("dev") || p.equalsIgnoreCase("test"));

        if (isDev) {
            log.warn("Running in dev/test profile — skipping strict security validation");
            return;
        }

        boolean failed = false;

        if (INSECURE_JWT_SECRET.equals(jwtSecret)) {
            log.error("SECURITY: JWT_SECRET is set to the default placeholder value. " +
                    "Set a strong random secret via the JWT_SECRET environment variable.");
            failed = true;
        }

        if (jwtSecret.length() < MIN_JWT_SECRET_LENGTH) {
            log.error("SECURITY: JWT_SECRET is too short ({} chars). Minimum is {} chars.",
                    jwtSecret.length(), MIN_JWT_SECRET_LENGTH);
            failed = true;
        }

        if ("your-github-client-id".equals(githubClientId)) {
            log.error("SECURITY: GITHUB_CLIENT_ID is set to the default placeholder value. " +
                    "Set the real value via the GITHUB_CLIENT_ID environment variable.");
            failed = true;
        }

        if (failed) {
            throw new IllegalStateException(
                    "Application startup aborted due to insecure configuration. " +
                    "Check logs above for details.");
        }

        log.info("Security configuration validated successfully.");
    }
}
