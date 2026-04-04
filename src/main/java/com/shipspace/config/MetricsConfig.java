package com.shipspace.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Custom Prometheus metrics for business-level observability.
 *
 * Phase 8: The existing setup only exported Spring Boot auto-metrics
 * (JVM, HTTP, DB pool). These beans add domain-specific counters and
 * timers that answer real operational questions:
 *   - How many ships are being created per minute?
 *   - How many auth failures are we seeing?
 *   - How long does feed generation take?
 *   - How many rate-limit rejections per action?
 *
 * All metrics are tagged with `application=shipspace-mvp` via application.yml.
 * Scraped by Prometheus at /actuator/prometheus.
 */
@Configuration
public class MetricsConfig {

    // ── Auth ──────────────────────────────────────────────────────────────────

    @Bean
    public Counter authSuccessCounter(MeterRegistry registry) {
        return Counter.builder("shipspace.auth.success")
                .description("Successful GitHub OAuth authentications")
                .register(registry);
    }

    @Bean
    public Counter authFailureCounter(MeterRegistry registry) {
        return Counter.builder("shipspace.auth.failure")
                .description("Failed GitHub OAuth authentications")
                .register(registry);
    }

    @Bean
    public Counter tokenRefreshCounter(MeterRegistry registry) {
        return Counter.builder("shipspace.auth.token_refresh")
                .description("JWT token refresh operations")
                .register(registry);
    }

    // ── Content ───────────────────────────────────────────────────────────────

    @Bean
    public Counter shipCreatedCounter(MeterRegistry registry) {
        return Counter.builder("shipspace.ships.created")
                .description("Total ships created")
                .register(registry);
    }

    @Bean
    public Counter projectCreatedCounter(MeterRegistry registry) {
        return Counter.builder("shipspace.projects.created")
                .description("Total projects created")
                .register(registry);
    }

    // ── Engagement ────────────────────────────────────────────────────────────

    @Bean
    public Counter likeCounter(MeterRegistry registry) {
        return Counter.builder("shipspace.engagement.likes")
                .description("Total like actions (add + remove)")
                .tag("action", "like")
                .register(registry);
    }

    @Bean
    public Counter commentCounter(MeterRegistry registry) {
        return Counter.builder("shipspace.engagement.comments")
                .description("Total comments created")
                .register(registry);
    }

    // ── Feed ──────────────────────────────────────────────────────────────────

    @Bean
    public Timer feedGenerationTimer(MeterRegistry registry) {
        return Timer.builder("shipspace.feed.generation_time")
                .description("Time to generate a personalized feed page")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(registry);
    }

    @Bean
    public Counter coldStartFeedCounter(MeterRegistry registry) {
        return Counter.builder("shipspace.feed.cold_start")
                .description("Feed requests served as cold-start (trending fallback)")
                .register(registry);
    }

    // ── Rate limiting ─────────────────────────────────────────────────────────

    @Bean
    public Counter rateLimitRejectionCounter(MeterRegistry registry) {
        return Counter.builder("shipspace.rate_limit.rejections")
                .description("Total rate-limit rejections")
                .register(registry);
    }

    // ── Scoring ───────────────────────────────────────────────────────────────

    @Bean
    public Counter scoreEventAppliedCounter(MeterRegistry registry) {
        return Counter.builder("shipspace.score.events_applied")
                .description("Score events successfully applied")
                .register(registry);
    }

    @Bean
    public Counter scoreEventRejectedCounter(MeterRegistry registry) {
        return Counter.builder("shipspace.score.events_rejected")
                .description("Score events rejected (duplicate or cap exceeded)")
                .register(registry);
    }
}
