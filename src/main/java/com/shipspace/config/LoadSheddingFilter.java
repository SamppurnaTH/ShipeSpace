package com.shipspace.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Active load shedding filter.
 *
 * WHY ACTIVE SHEDDING, NOT JUST CallerRunsPolicy:
 * CallerRunsPolicy is passive backpressure — it slows the system down but
 * still accepts every request. Under extreme overload, this causes:
 *   - HTTP threads blocked running async tasks
 *   - All requests slow down, not just the overflow
 *   - p99 latency spikes for ALL users, not just the excess ones
 *
 * Active load shedding is different:
 *   - Excess requests get an immediate 503 (fast fail)
 *   - In-flight requests complete at normal latency
 *   - p99 for accepted requests stays within SLO
 *   - The system degrades for some users, not all users
 *
 * STRATEGY:
 * Semaphore-based concurrency limiter. Max concurrent requests = configurable
 * (default: 200, matching Tomcat's default thread pool).
 *
 * Priority tiers — not all requests are equal:
 *   TIER 1 (never shed): health checks, auth endpoints
 *   TIER 2 (shed last):  read endpoints (feed, profile)
 *   TIER 3 (shed first): write endpoints under overload
 *
 * CONSISTENCY MODEL IMPACT:
 * Load shedding does NOT affect consistency guarantees.
 * A shed request returns 503 before touching MongoDB.
 * No partial writes, no counter drift from shedding.
 *
 * CONFIGURATION:
 *   load-shedding.enabled=true
 *   load-shedding.max-concurrent-requests=200
 *   load-shedding.shed-threshold=0.85  (shed writes when >85% capacity used)
 */
@Slf4j
@Component
public class LoadSheddingFilter extends OncePerRequestFilter {

    private final Semaphore semaphore;
    private final AtomicInteger activeRequests = new AtomicInteger(0);
    private final int maxConcurrent;
    private final double shedThreshold;
    private final boolean enabled;
    private final Counter shedCounter;

    public LoadSheddingFilter(
            @Value("${load-shedding.enabled:true}") boolean enabled,
            @Value("${load-shedding.max-concurrent-requests:200}") int maxConcurrent,
            @Value("${load-shedding.shed-threshold:0.85}") double shedThreshold,
            MeterRegistry registry) {
        this.enabled = enabled;
        this.maxConcurrent = maxConcurrent;
        this.shedThreshold = shedThreshold;
        this.semaphore = new Semaphore(maxConcurrent, true); // fair
        this.shedCounter = Counter.builder("shipspace.load_shedding.shed_requests")
                .description("Requests rejected by load shedding")
                .register(registry);
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        if (!enabled) {
            filterChain.doFilter(request, response);
            return;
        }

        String path = request.getRequestURI();

        // TIER 1: never shed — health checks and auth must always respond
        if (isNeverShed(path)) {
            filterChain.doFilter(request, response);
            return;
        }

        // Try to acquire a slot
        boolean acquired = semaphore.tryAcquire();

        if (!acquired) {
            // Hard limit reached — shed regardless of tier
            shed(response, path, "concurrency limit reached");
            return;
        }

        try {
            int active = activeRequests.incrementAndGet();
            double utilization = (double) active / maxConcurrent;

            // TIER 3: shed writes first when approaching capacity
            if (utilization > shedThreshold && isWriteEndpoint(request)) {
                semaphore.release();
                activeRequests.decrementAndGet();
                shed(response, path, String.format("write shed at %.0f%% capacity", utilization * 100));
                return;
            }

            filterChain.doFilter(request, response);
        } finally {
            semaphore.release();
            activeRequests.decrementAndGet();
        }
    }

    private void shed(HttpServletResponse response, String path, String reason)
            throws IOException {
        shedCounter.increment();
        log.warn("Load shedding: {} — {}", path, reason);

        response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
        response.setContentType("application/json");
        response.setHeader("Retry-After", "5");
        response.getWriter().write(
                "{\"code\":\"SERVICE_OVERLOADED\"," +
                "\"message\":\"Server is temporarily overloaded. Please retry in a few seconds.\"}"
        );
    }

    /**
     * Tier 1: endpoints that must always respond regardless of load.
     * Shedding health checks would cause the load balancer to remove the instance,
     * routing all traffic to other instances and making the problem worse.
     */
    private boolean isNeverShed(String path) {
        return path.contains("/health")
                || path.contains("/actuator")
                || path.contains("/auth/github")
                || path.contains("/auth/refresh")
                || path.contains("/auth/state");
    }

    /**
     * Tier 3: write endpoints are shed first under pressure.
     * Reads are more valuable to shed last — users can tolerate a slow feed
     * more than a failed like or comment.
     */
    private boolean isWriteEndpoint(HttpServletRequest request) {
        String method = request.getMethod();
        return "POST".equals(method) || "PUT".equals(method) || "DELETE".equals(method);
    }
}
