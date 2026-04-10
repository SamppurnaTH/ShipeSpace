package com.shipspace.service;

import com.shipspace.dto.ShipResponse;
import com.shipspace.dto.UserProfileResponse;
import com.shipspace.model.Ship;
import com.shipspace.model.User;
import com.shipspace.util.DtoMapper;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.TextCriteria;
import org.springframework.data.mongodb.core.query.TextQuery;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Full-text search service for users and ships.
 *
 * PRIMARY PATH — MongoDB $text (indexed):
 *   Uses TextCriteria → { $text: { $search: "..." } }.
 *   Requires text indexes created by MongoIndexInitializer at startup.
 *   Matches whole words only — "reac" will NOT match "react".
 *
 * FALLBACK PATH — bounded $regex (when $text returns empty):
 *   Triggered only when the primary path returns zero results AND the query
 *   is short enough (≤ FALLBACK_MAX_QUERY_LEN chars) to be a partial word.
 *   Hard-capped at FALLBACK_LIMIT results to bound scan cost.
 *
 * Returns SearchResult<T> which carries the path taken (text | fallback | text-empty).
 * The controller exposes this as X-Search-Path when debug headers are enabled.
 */
@Service
@Slf4j
public class SearchService {

    /**
     * Carries search results and the execution path taken.
     * path values: "text" | "fallback" | "text-empty"
     */
    public record SearchResult<T>(List<T> items, String path) {
        public static final String PATH_TEXT     = "text";
        public static final String PATH_FALLBACK = "fallback";
        public static final String PATH_EMPTY    = "text-empty";
    }

    private final MongoTemplate mongoTemplate;
    private final DtoMapper dtoMapper;
    private final Timer userSearchTimer;
    private final Timer shipSearchTimer;
    private final io.micrometer.core.instrument.Counter userFallbackCounter;
    private final io.micrometer.core.instrument.Counter shipFallbackCounter;
    private final io.micrometer.core.instrument.Counter userFallbackSuccessCounter;
    private final io.micrometer.core.instrument.Counter shipFallbackSuccessCounter;

    private static final int  FALLBACK_LIMIT         = 5;
    private static final int  FALLBACK_MAX_QUERY_LEN = 30;
    private final long slowQueryMs;

    // ── Adaptive fallback circuit ─────────────────────────────────────────────
    //
    // State machine with hysteresis:
    //   ENABLED  → DISABLED when p90(window) > disableThresholdMs
    //   DISABLED → ENABLED  when p90(window) < enableThresholdMs  (lower than disable)
    //
    // Hysteresis prevents oscillation: the system won't flap between states
    // when latency hovers near a single threshold.
    //
    // Signal: p90 of a rolling window (not average).
    //   - Average is sensitive to outliers: 9 fast + 1 slow = looks fine
    //   - p90 is sensitive to clusters: 9 slow + 1 fast = correctly triggers
    //
    // All thresholds are configurable via application.yml.
    private final long   fallbackDisableThresholdMs;  // p90 above this → disable
    private final long   fallbackEnableThresholdMs;   // p90 below this → re-enable (must be < disable)
    private final int    fallbackWindowSize;           // rolling window depth

    private final java.util.Deque<Long> fallbackLatencyWindow =
            new java.util.concurrent.ConcurrentLinkedDeque<>();

    // Circuit state: CLOSED = fallback allowed, OPEN = fallback suppressed
    private volatile boolean fallbackCircuitOpen = false;
    // Epoch ms when circuit opened — used to detect "stuck open" condition
    private volatile long    circuitOpenSinceMs  = 0;
    // Micrometer gauge: circuit state (0=closed, 1=open)
    private final java.util.concurrent.atomic.AtomicInteger fallbackCircuitGauge;
    // Micrometer gauge: current p90 of fallback latency window (ms), -1 when window not full
    private final java.util.concurrent.atomic.AtomicLong fallbackP90Gauge;

    public SearchService(MongoTemplate mongoTemplate, DtoMapper dtoMapper, MeterRegistry registry,
            @Value("${search.slow-query-threshold-ms:100}") long slowQueryMs,
            @Value("${search.fallback.disable-threshold-ms:200}") long fallbackDisableThresholdMs,
            @Value("${search.fallback.enable-threshold-ms:120}") long fallbackEnableThresholdMs,
            @Value("${search.fallback.window-size:20}") int fallbackWindowSize) {
        this.mongoTemplate = mongoTemplate;
        this.dtoMapper = dtoMapper;

        // Clamp slow-query threshold to sane bounds
        final long MIN = 10, MAX = 10_000;
        if (slowQueryMs < MIN || slowQueryMs > MAX) {
            long clamped = Math.min(MAX, Math.max(MIN, slowQueryMs));
            log.warn("search.slow-query-threshold-ms={} outside [{},{}], clamping to {}ms",
                    slowQueryMs, MIN, MAX, clamped);
            this.slowQueryMs = clamped;
        } else {
            this.slowQueryMs = slowQueryMs;
        }

        // Validate hysteresis: enable threshold must be strictly less than disable threshold
        if (fallbackEnableThresholdMs >= fallbackDisableThresholdMs) {
            log.warn("search.fallback.enable-threshold-ms={} must be < disable-threshold-ms={}. " +
                    "Clamping enable threshold to {}ms.",
                    fallbackEnableThresholdMs, fallbackDisableThresholdMs,
                    fallbackDisableThresholdMs - 1);
            this.fallbackEnableThresholdMs = fallbackDisableThresholdMs - 1;
        } else {
            this.fallbackEnableThresholdMs = fallbackEnableThresholdMs;
        }
        this.fallbackDisableThresholdMs = fallbackDisableThresholdMs;
        this.fallbackWindowSize = Math.max(3, fallbackWindowSize); // minimum 3 for meaningful p90

        // Gauge: 0 = circuit closed (fallback allowed), 1 = circuit open (fallback suppressed)
        this.fallbackCircuitGauge = new java.util.concurrent.atomic.AtomicInteger(0);
        io.micrometer.core.instrument.Gauge.builder("shipspace.search.fallback.circuit",
                        fallbackCircuitGauge, java.util.concurrent.atomic.AtomicInteger::get)
                .description("Adaptive fallback circuit state: 0=closed (allowed), 1=open (suppressed)")
                .register(registry);

        // Gauge: current p90 of fallback latency window in ms (-1 = window not yet full)
        this.fallbackP90Gauge = new java.util.concurrent.atomic.AtomicLong(-1);
        io.micrometer.core.instrument.Gauge.builder("shipspace.search.fallback.p90_ms",
                        fallbackP90Gauge, java.util.concurrent.atomic.AtomicLong::get)
                .description("Current p90 of fallback latency window (ms). -1 = window not full yet.")
                .register(registry);

        // Gauge: seconds the circuit has been open (0 when closed)
        // Allows alerting on "circuit open too long" without log parsing.
        io.micrometer.core.instrument.Gauge.builder("shipspace.search.fallback.circuit_open_seconds",
                        this, SearchService::circuitOpenSeconds)
                .description("Seconds the fallback circuit has been open. 0 when closed.")
                .register(registry);

        this.userSearchTimer = Timer.builder("shipspace.search.users.duration_ms")
                .description("User search query duration")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(registry);
        this.shipSearchTimer = Timer.builder("shipspace.search.ships.duration_ms")
                .description("Ship search query duration")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(registry);
        this.userFallbackCounter = io.micrometer.core.instrument.Counter
                .builder("shipspace.search.fallback")
                .description("Searches that fell back to regex")
                .tag("entity", "user")
                .register(registry);
        this.shipFallbackCounter = io.micrometer.core.instrument.Counter
                .builder("shipspace.search.fallback")
                .description("Searches that fell back to regex")
                .tag("entity", "ship")
                .register(registry);
        // Fallback success counters — tracks whether fallback actually returned results.
        // Ratio: fallback_success / fallback_total = fallback success rate.
        // Day-7 rule: if fallback > 20% AND success rate < 60% → broken search, not just partial-word behavior.
        this.userFallbackSuccessCounter = io.micrometer.core.instrument.Counter
                .builder("shipspace.search.fallback.success")
                .description("Fallback searches that returned at least one result")
                .tag("entity", "user")
                .register(registry);
        this.shipFallbackSuccessCounter = io.micrometer.core.instrument.Counter
                .builder("shipspace.search.fallback.success")
                .description("Fallback searches that returned at least one result")
                .tag("entity", "ship")
                .register(registry);

        log.info("SearchService initialized: slowQueryThreshold={}ms " +
                "fallback.disableAt={}ms fallback.enableAt={}ms fallback.windowSize={}",
                this.slowQueryMs, this.fallbackDisableThresholdMs,
                this.fallbackEnableThresholdMs, this.fallbackWindowSize);
    }

    // ── Public API ────────────────────────────────────────────────────────────

    public SearchResult<UserProfileResponse> searchUsers(String query, int limit) {
        return userSearchTimer.record(() -> {
            long start = System.currentTimeMillis();

            Query textQuery = TextQuery.queryText(
                    TextCriteria.forDefaultLanguage()
                            .caseSensitive(false)
                            .matchingAny(query.split("\\s+")))
                    .sortByScore()
                    .with(PageRequest.of(0, limit));

            List<User> users = mongoTemplate.find(textQuery, User.class);
            long elapsed = System.currentTimeMillis() - start;
            String path;

            if (users.isEmpty() && query.length() <= FALLBACK_MAX_QUERY_LEN && isFallbackAllowed()) {
                userFallbackCounter.increment();
                long fbStart = System.currentTimeMillis();
                users = regexFallbackUsers(query, FALLBACK_LIMIT);
                long fbElapsed = System.currentTimeMillis() - fbStart;
                recordFallbackLatency(fbElapsed);
                if (!users.isEmpty()) userFallbackSuccessCounter.increment();
                path = SearchResult.PATH_FALLBACK;
                logQuery("search:users", path, query, users.size(), System.currentTimeMillis() - start);
            } else {
                if (users.isEmpty() && !isFallbackAllowed()) {
                    log.debug("search:users fallback suppressed (circuit open) query='{}'", query);
                }
                path = users.isEmpty() ? SearchResult.PATH_EMPTY : SearchResult.PATH_TEXT;
                logQuery("search:users", path, query, users.size(), elapsed);
            }

            return new SearchResult<>(users.stream().map(this::toUserProfileResponse).toList(), path);
        });
    }

    public SearchResult<ShipResponse> searchShips(String query, int limit) {
        return shipSearchTimer.record(() -> {
            long start = System.currentTimeMillis();

            Query textQuery = TextQuery.queryText(
                    TextCriteria.forDefaultLanguage()
                            .caseSensitive(false)
                            .matchingAny(query.split("\\s+")))
                    .with(PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "trendingScore")));

            List<Ship> ships = mongoTemplate.find(textQuery, Ship.class);
            long elapsed = System.currentTimeMillis() - start;
            String path;

            if (ships.isEmpty() && query.length() <= FALLBACK_MAX_QUERY_LEN && isFallbackAllowed()) {
                shipFallbackCounter.increment();
                long fbStart = System.currentTimeMillis();
                ships = regexFallbackShips(query, FALLBACK_LIMIT);
                long fbElapsed = System.currentTimeMillis() - fbStart;
                recordFallbackLatency(fbElapsed);
                if (!ships.isEmpty()) shipFallbackSuccessCounter.increment();
                path = SearchResult.PATH_FALLBACK;
                logQuery("search:ships", path, query, ships.size(), System.currentTimeMillis() - start);
            } else {
                if (ships.isEmpty() && !isFallbackAllowed()) {
                    log.debug("search:ships fallback suppressed (circuit open) query='{}'", query);
                }
                path = ships.isEmpty() ? SearchResult.PATH_EMPTY : SearchResult.PATH_TEXT;
                logQuery("search:ships", path, query, ships.size(), elapsed);
            }

            return new SearchResult<>(ships.stream().map(dtoMapper::toShipResponse).toList(), path);
        });
    }

    // ── Logging ───────────────────────────────────────────────────────────────

    private void logQuery(String op, String path, String query, int results, long elapsedMs) {
        if (elapsedMs >= slowQueryMs) {
            log.warn("SLOW_QUERY op={} path={} query='{}' results={} elapsed={}ms threshold={}ms",
                    op, path, query, results, elapsedMs, slowQueryMs);
        } else {
            log.debug("op={} path={} query='{}' results={} elapsed={}ms",
                    op, path, query, results, elapsedMs);
        }
    }

    // ── Adaptive fallback circuit ─────────────────────────────────────────────

    /**
     * Returns true if fallback is currently allowed (circuit closed).
     * Checks p90 of the window against the re-enable threshold.
     * Only transitions OPEN→CLOSED when p90 drops below enableThreshold,
     * which is strictly lower than the disableThreshold — this is hysteresis.
     */
    private boolean isFallbackAllowed() {
        if (!fallbackCircuitOpen) return true;

        // Re-evaluate: can we close the circuit?
        double p90 = computeP90();
        if (p90 >= 0 && p90 < fallbackEnableThresholdMs) {
            fallbackCircuitOpen = false;
            circuitOpenSinceMs  = 0;
            fallbackCircuitGauge.set(0);
            fallbackLatencyWindow.clear();
            fallbackP90Gauge.set(-1);
            log.info("Adaptive fallback circuit CLOSED: p90={}ms dropped below re-enable threshold={}ms",
                    String.format("%.1f", p90), fallbackEnableThresholdMs);
        }
        return !fallbackCircuitOpen;
    }

    /**
     * Records a fallback execution latency and evaluates whether to open the circuit.
     * Uses p90 of the rolling window — resistant to single outliers,
     * sensitive to sustained clusters of slow queries.
     */
    private void recordFallbackLatency(long elapsedMs) {
        fallbackLatencyWindow.addLast(elapsedMs);
        while (fallbackLatencyWindow.size() > fallbackWindowSize) {
            fallbackLatencyWindow.pollFirst();
        }
        // Need a full window before making decisions — avoids false positives on startup
        if (fallbackLatencyWindow.size() < fallbackWindowSize) return;

        double p90 = computeP90();
        if (p90 < 0) return;

        // Update the p90 gauge so Prometheus/Grafana can track it continuously
        fallbackP90Gauge.set((long) p90);

        if (!fallbackCircuitOpen && p90 > fallbackDisableThresholdMs) {
            fallbackCircuitOpen = true;
            circuitOpenSinceMs  = System.currentTimeMillis();
            fallbackCircuitGauge.set(1);
            log.warn(
                "Adaptive fallback circuit OPEN: p90={}ms exceeds disable threshold={}ms. " +
                "Fallback suppressed until p90 drops below re-enable threshold={}ms. " +
                "Partial-word searches will return empty results.",
                String.format("%.1f", p90), fallbackDisableThresholdMs, fallbackEnableThresholdMs);
        }
    }

    /**
     * Returns how long the circuit has been open in seconds.
     * Returns 0 when the circuit is closed.
     * Used as a Prometheus gauge — enables "circuit open too long" alerting.
     */
    private double circuitOpenSeconds() {
        if (!fallbackCircuitOpen || circuitOpenSinceMs == 0) return 0;
        return (System.currentTimeMillis() - circuitOpenSinceMs) / 1000.0;
    }

    /**
     * Computes the p90 of the current latency window.
     * Returns -1 if the window is empty.
     *
     * p90 is chosen over average because:
     *   - Average: 9 fast (5ms) + 1 slow (500ms) = 54ms avg → looks fine, hides the spike
     *   - p90:     9 fast (5ms) + 1 slow (500ms) = 5ms p90  → correctly shows fast baseline
     *   - p90:     9 slow (300ms) + 1 fast (5ms) = 270ms p90 → correctly triggers circuit
     */
    private double computeP90() {
        if (fallbackLatencyWindow.isEmpty()) return -1;
        long[] sorted = fallbackLatencyWindow.stream()
                .mapToLong(Long::longValue).sorted().toArray();
        int idx = (int) Math.ceil(0.9 * sorted.length) - 1;
        return sorted[Math.max(0, Math.min(idx, sorted.length - 1))];
    }

    // ── Regex fallback ────────────────────────────────────────────────────────

    private List<User> regexFallbackUsers(String query, int limit) {
        Query q = new Query(Criteria.where("username").regex("^" + Pattern.quote(query), "i"))
                .with(PageRequest.of(0, limit));
        return mongoTemplate.find(q, User.class);
    }

    private List<Ship> regexFallbackShips(String query, int limit) {
        Query q = new Query(Criteria.where("content").regex(Pattern.quote(query), "i"))
                .with(PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "trendingScore")));
        return mongoTemplate.find(q, Ship.class);
    }

    // ── DTO mapping ───────────────────────────────────────────────────────────

    private UserProfileResponse toUserProfileResponse(User user) {
        return UserProfileResponse.builder()
                .id(user.getId()).username(user.getUsername()).avatarUrl(user.getAvatarUrl())
                .bio(user.getBio()).location(user.getLocation()).websiteUrl(user.getWebsiteUrl())
                .followerCount(user.getFollowerCount()).followingCount(user.getFollowingCount())
                .projectCount(user.getProjectCount()).createdAt(user.getCreatedAt())
                .build();
    }
}
