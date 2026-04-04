package com.shipspace.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;

/**
 * Actuator health indicator for the Multibase image storage service.
 *
 * Phase 8: The actuator /health endpoint previously had no visibility into
 * Multibase availability. If Multibase goes down, image uploads silently fail
 * with a 502 — operators have no alert signal.
 *
 * This indicator pings the Multibase health endpoint with a 3-second timeout.
 * It is included in the readiness probe group so Kubernetes/ECS can route
 * traffic away from instances that cannot reach Multibase.
 *
 * Add to application.yml readiness group:
 *   management.endpoint.health.group.readiness.include: mongo,redis,multibase
 */
@Slf4j
@Component("multibase")
@RequiredArgsConstructor
public class MultibaseHealthIndicator implements HealthIndicator {

    private final WebClient.Builder webClientBuilder;

    @Value("${multibase.api-url:http://localhost:5001}")
    private String multibaseApiUrl;

    @Value("${multibase.api-key:}")
    private String apiKey;

    @Override
    public Health health() {
        try {
            WebClient client = webClientBuilder.baseUrl(multibaseApiUrl).build();
            // Multibase exposes a /health endpoint — adjust path if different
            String status = client.get()
                    .uri("/health")
                    .header("X-API-Key", apiKey)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(3))
                    .block();

            return Health.up()
                    .withDetail("url", multibaseApiUrl)
                    .withDetail("response", status != null ? "ok" : "empty")
                    .build();

        } catch (Exception e) {
            log.warn("Multibase health check failed: {}", e.getMessage());
            return Health.down()
                    .withDetail("url", multibaseApiUrl)
                    .withDetail("error", e.getMessage())
                    .build();
        }
    }
}
