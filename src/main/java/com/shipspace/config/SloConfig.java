package com.shipspace.config;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.binder.MeterBinder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Service Level Objective (SLO) enforcement configuration.
 *
 * SLOs are hard numbers — not aspirations. They are enforced in two places:
 *   1. HERE: Micrometer SLO buckets emit a `_slo_violations` counter that
 *      Prometheus can alert on when the violation rate exceeds the error budget.
 *   2. observability/alerts.yml: Prometheus rules fire when SLOs are breached.
 *
 * ┌─────────────────────────────────────────────────────────────────────────┐
 * │  ShipSpace SLO Definitions                                              │
 * │                                                                         │
 * │  Endpoint Class        │ p95 target │ p99 target │ Error budget (30d)  │
 * │  ──────────────────────┼────────────┼────────────┼──────────────────── │
 * │  Engagement (like)     │  200ms     │  500ms     │  0.1% (43 min/mo)  │
 * │  Feed (trending)       │  300ms     │  800ms     │  0.5% (3.6 hr/mo)  │
 * │  Feed (personalized)   │  500ms     │  1500ms    │  0.5% (3.6 hr/mo)  │
 * │  Auth (GitHub OAuth)   │  1000ms    │  3000ms    │  1.0% (7.2 hr/mo)  │
 * │  Profile reads         │  150ms     │  400ms     │  0.1% (43 min/mo)  │
 * │                                                                         │
 * │  Availability SLO: 99.9% uptime (43 min downtime/month)                │
 * │                                                                         │
 * │  Error rate SLO: < 0.1% 5xx for engagement and profile endpoints       │
 * │                  < 0.5% 5xx for feed endpoints                         │
 * │                  < 1.0% 5xx for auth endpoints (GitHub dependency)     │
 * └─────────────────────────────────────────────────────────────────────────┘
 *
 * HOW SLO BUCKETS WORK:
 * Micrometer's serviceLevelObjectives() adds explicit histogram buckets at
 * the SLO boundary. This means Prometheus can compute the exact fraction of
 * requests that violated the SLO without approximation from adjacent buckets.
 *
 * Example: if p95 SLO is 200ms, we add a bucket at 200ms. Prometheus then
 * computes: violations = 1 - (requests_under_200ms / total_requests).
 * If violations > 0.05 (5%), the p95 SLO is breached.
 */
@Slf4j
@Configuration
public class SloConfig {

    // ── SLO boundary constants (used in both timers and alerts.yml) ───────────

    // Engagement: like/unlike — atomic MongoDB ops, should be fast
    public static final Duration ENGAGEMENT_P95_SLO  = Duration.ofMillis(200);
    public static final Duration ENGAGEMENT_P99_SLO  = Duration.ofMillis(500);

    // Feed: trending (cached) — cache hit should be very fast
    public static final Duration FEED_TRENDING_P95_SLO  = Duration.ofMillis(300);
    public static final Duration FEED_TRENDING_P99_SLO  = Duration.ofMillis(800);

    // Feed: personalized — involves MongoDB + interest profile lookup
    public static final Duration FEED_PERSONALIZED_P95_SLO  = Duration.ofMillis(500);
    public static final Duration FEED_PERSONALIZED_P99_SLO  = Duration.ofMillis(1500);

    // Auth: GitHub OAuth — external dependency, higher tolerance
    public static final Duration AUTH_P95_SLO  = Duration.ofMillis(1000);
    public static final Duration AUTH_P99_SLO  = Duration.ofMillis(3000);

    // Profile reads — simple MongoDB lookup, should be fast
    public static final Duration PROFILE_P95_SLO  = Duration.ofMillis(150);
    public static final Duration PROFILE_P99_SLO  = Duration.ofMillis(400);

    // ── SLO-instrumented timers ───────────────────────────────────────────────

    /**
     * Engagement operations timer with SLO buckets.
     * Tracks like/unlike latency. SLO: p95 < 200ms, p99 < 500ms.
     */
    @Bean
    public Timer engagementOperationTimer(MeterRegistry registry) {
        return Timer.builder("shipspace.slo.engagement")
                .description("Like/unlike operation latency — SLO: p95<200ms, p99<500ms")
                .publishPercentiles(0.50, 0.95, 0.99)
                .publishPercentileHistogram(true)
                .serviceLevelObjectives(ENGAGEMENT_P95_SLO, ENGAGEMENT_P99_SLO)
                .minimumExpectedValue(Duration.ofMillis(1))
                .maximumExpectedValue(Duration.ofSeconds(10))
                .register(registry);
    }

    /**
     * Trending feed timer with SLO buckets.
     * Cache hit should be < 50ms. Cache miss (MongoDB) should be < 300ms.
     * SLO: p95 < 300ms, p99 < 800ms.
     */
    @Bean
    public Timer trendingFeedTimer(MeterRegistry registry) {
        return Timer.builder("shipspace.slo.feed.trending")
                .description("Trending feed latency — SLO: p95<300ms, p99<800ms")
                .publishPercentiles(0.50, 0.95, 0.99)
                .publishPercentileHistogram(true)
                .serviceLevelObjectives(FEED_TRENDING_P95_SLO, FEED_TRENDING_P99_SLO)
                .minimumExpectedValue(Duration.ofMillis(1))
                .maximumExpectedValue(Duration.ofSeconds(10))
                .register(registry);
    }

    /**
     * Personalized feed timer with SLO buckets.
     * Involves interest profile lookup + candidate scoring.
     * SLO: p95 < 500ms, p99 < 1500ms.
     */
    @Bean
    public Timer personalizedFeedTimer(MeterRegistry registry) {
        return Timer.builder("shipspace.slo.feed.personalized")
                .description("Personalized feed latency — SLO: p95<500ms, p99<1500ms")
                .publishPercentiles(0.50, 0.95, 0.99)
                .publishPercentileHistogram(true)
                .serviceLevelObjectives(FEED_PERSONALIZED_P95_SLO, FEED_PERSONALIZED_P99_SLO)
                .minimumExpectedValue(Duration.ofMillis(1))
                .maximumExpectedValue(Duration.ofSeconds(30))
                .register(registry);
    }

    /**
     * Auth timer with SLO buckets.
     * GitHub OAuth is an external call — higher tolerance.
     * SLO: p95 < 1000ms, p99 < 3000ms.
     */
    @Bean
    public Timer authOperationTimer(MeterRegistry registry) {
        return Timer.builder("shipspace.slo.auth")
                .description("Auth operation latency — SLO: p95<1000ms, p99<3000ms")
                .publishPercentiles(0.50, 0.95, 0.99)
                .publishPercentileHistogram(true)
                .serviceLevelObjectives(AUTH_P95_SLO, AUTH_P99_SLO)
                .minimumExpectedValue(Duration.ofMillis(10))
                .maximumExpectedValue(Duration.ofSeconds(30))
                .register(registry);
    }

    /**
     * Profile read timer with SLO buckets.
     * Simple MongoDB lookup — should be very fast.
     * SLO: p95 < 150ms, p99 < 400ms.
     */
    @Bean
    public Timer profileReadTimer(MeterRegistry registry) {
        return Timer.builder("shipspace.slo.profile")
                .description("Profile read latency — SLO: p95<150ms, p99<400ms")
                .publishPercentiles(0.50, 0.95, 0.99)
                .publishPercentileHistogram(true)
                .serviceLevelObjectives(PROFILE_P95_SLO, PROFILE_P99_SLO)
                .minimumExpectedValue(Duration.ofMillis(1))
                .maximumExpectedValue(Duration.ofSeconds(5))
                .register(registry);
    }
}
