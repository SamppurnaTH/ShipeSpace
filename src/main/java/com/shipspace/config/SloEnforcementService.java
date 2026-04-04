package com.shipspace.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * SLO enforcement service — closed-loop adaptive control system.
 *
 * SIX PROPERTIES:
 * 1. HYBRID DETECTION     — streak OR violation-rate triggers degradation
 * 2. SCOPED DEGRADATION   — feed / engagement / system are independent
 * 3. METRIC-BASED RECOVERY — streak + rate must both be healthy to recover
 * 4. NONLINEAR CONTROL    — exponential drop, linear restore
 * 5. PROGRESSIVE RATE LIMITING — 1.0 → 0.70 → 0.50 → 0.33 (no cliff)
 * 6. PERSISTENT STATE + WARM START — Redis-backed, cautious cold start
 *
 * DEGRADATION LADDER:
 *   L0 NORMAL                — full features,          rate 1.00x
 *   L1 PERSONALIZATION_OFF   — feed p95 > 300ms,       rate 0.70x
 *   L2 BEHAVIORAL_WRITES_OFF — feed p95 > 500ms,       rate 0.50x
 *   L3 ASYNC_REDUCED         — engagement p95 > 200ms, rate 0.50x
 *   L4 LOAD_SHEDDING_ACTIVE  — error rate > 1%,        rate 0.33x
 *
 * HYBRID DETECTION:
 *   Degrade if: trailing streak >= DEGRADE_STREAK(3)
 *            OR window violation rate >= DEGRADE_RATE(0.40)
 *   Recover if: trailing healthy >= RECOVER_STREAK(9)
 *           AND window violation rate < RECOVER_RATE(0.10)
 *
 * WARM START:
 *   On restart, state is loaded from Redis.
 *   If Redis unavailable, starts at 50% concurrency and ramps linearly
 *   over WARM_START_CYCLES(6) evaluation cycles (~60s) to full capacity.
 */
@Slf4j
@Service
public class SloEnforcementService {

    public enum DegradationLevel {
        NORMAL(0), PERSONALIZATION_DISABLED(1),
        BEHAVIORAL_WRITES_SKIPPED(2), ASYNC_REDUCED(3), LOAD_SHEDDING_ACTIVE(4);
        public final int level;
        DegradationLevel(int l) { this.level = l; }
    }

    // ── Detection window ──────────────────────────────────────────────────────

    private static final class DetectionWindow {
        private final Deque<Boolean> buf = new ArrayDeque<>();
        private final int cap;
        DetectionWindow(int cap) { this.cap = cap; }

        synchronized void add(boolean violation) {
            buf.addLast(violation);
            while (buf.size() > cap) buf.removeFirst();
        }
        synchronized int trailingViolations() {
            int n = 0; Boolean[] a = buf.toArray(new Boolean[0]);
            for (int i = a.length - 1; i >= 0; i--) { if (a[i]) n++; else break; }
            return n;
        }
        synchronized int trailingHealthy() {
            int n = 0; Boolean[] a = buf.toArray(new Boolean[0]);
            for (int i = a.length - 1; i >= 0; i--) { if (!a[i]) n++; else break; }
            return n;
        }
        synchronized double violationRate() {
            if (buf.isEmpty()) return 0.0;
            return buf.stream().filter(v -> v).count() / (double) buf.size();
        }
        synchronized int size() { return buf.size(); }
    }

    // ── Scoped state ──────────────────────────────────────────────────────────

    private final DetectionWindow feedWindow   = new DetectionWindow(12);
    private final DetectionWindow engageWindow = new DetectionWindow(12);
    private final DetectionWindow errorWindow  = new DetectionWindow(12);

    private final AtomicReference<DegradationLevel> feedDeg   = new AtomicReference<>(DegradationLevel.NORMAL);
    private final AtomicReference<DegradationLevel> engageDeg = new AtomicReference<>(DegradationLevel.NORMAL);
    private final AtomicReference<DegradationLevel> systemDeg = new AtomicReference<>(DegradationLevel.NORMAL);

    // ── Concurrency + rate limiting ───────────────────────────────────────────

    private final AtomicInteger concurrencyLimit;
    private final int maxConcurrency;
    private final int minConcurrency;

    private static final double[] RATE_STEPS = { 1.0, 0.70, 0.50, 0.50, 0.33 };
    private final AtomicReference<Double> rateLimitMultiplier = new AtomicReference<>(1.0);

    // ── Warm start ────────────────────────────────────────────────────────────

    private final AtomicInteger warmCyclesLeft;
    private final int warmStartCycles;

    // ── Persistence keys ──────────────────────────────────────────────────────

    private static final String K_FEED   = "slo:state:feed";
    private static final String K_ENGAGE = "slo:state:engagement";
    private static final String K_SYSTEM = "slo:state:system";
    private static final String K_CONCUR = "slo:state:concurrency";

    private final RedisTemplate<String, String> redisTemplate;

    // ── Thresholds ────────────────────────────────────────────────────────────

    private static final int    DEGRADE_STREAK = 3;
    private static final double DEGRADE_RATE   = 0.40;
    private static final int    RECOVER_STREAK = 9;
    private static final double RECOVER_RATE   = 0.10;

    private static final double FEED_L1_MS      = 300.0;
    private static final double FEED_L2_MS      = 500.0;
    private static final double FEED_RECOVER_MS = 200.0;
    private static final double ENG_L3_MS       = 200.0;
    private static final double ENG_RECOVER_MS  = 150.0;
    private static final double ERR_L4          = 0.01;
    private static final double ERR_RECOVER     = 0.005;

    // ── Metrics ───────────────────────────────────────────────────────────────

    private final Timer feedTimer;
    private final Timer engageTimer;
    private final Counter activatedCounter;
    private final Counter recoveredCounter;
    private final MeterRegistry registry;

    public SloEnforcementService(
            MeterRegistry registry,
            Timer trendingFeedTimer,
            Timer engagementOperationTimer,
            RedisTemplate<String, String> redisTemplate,
            @Value("${load-shedding.max-concurrent-requests:200}") int maxConcurrent,
            @Value("${slo.warm-start-cycles:6}") int warmStartCycles) {
        this.registry        = registry;
        this.feedTimer       = trendingFeedTimer;
        this.engageTimer     = engagementOperationTimer;
        this.redisTemplate   = redisTemplate;
        this.maxConcurrency  = maxConcurrent;
        this.minConcurrency  = Math.max(10, maxConcurrent / 10);
        this.warmStartCycles = warmStartCycles;
        this.warmCyclesLeft  = new AtomicInteger(warmStartCycles);
        this.concurrencyLimit = new AtomicInteger(maxConcurrent);

        this.activatedCounter = Counter.builder("shipspace.slo.degradation_activated").register(registry);
        this.recoveredCounter = Counter.builder("shipspace.slo.degradation_recovered").register(registry);

        registry.gauge("shipspace.slo.degradation_level",        this,         s -> s.getEffectiveLevel().level);
        registry.gauge("shipspace.slo.feed_degradation_level",   feedDeg,      r -> r.get().level);
        registry.gauge("shipspace.slo.engage_degradation_level", engageDeg,    r -> r.get().level);
        registry.gauge("shipspace.slo.system_degradation_level", systemDeg,    r -> r.get().level);
        registry.gauge("shipspace.slo.adaptive_concurrency",     concurrencyLimit, AtomicInteger::get);
        registry.gauge("shipspace.slo.rate_limit_multiplier",    rateLimitMultiplier, AtomicReference::get);
        registry.gauge("shipspace.slo.warm_start_remaining",     warmCyclesLeft, AtomicInteger::get);
    }

    // ── Startup: restore state from Redis ────────────────────────────────────

    @PostConstruct
    public void restoreState() {
        try {
            DegradationLevel feed   = readLevel(K_FEED);
            DegradationLevel engage = readLevel(K_ENGAGE);
            DegradationLevel system = readLevel(K_SYSTEM);
            String concurStr = redisTemplate.opsForValue().get(K_CONCUR);

            if (feed != DegradationLevel.NORMAL || engage != DegradationLevel.NORMAL
                    || system != DegradationLevel.NORMAL) {
                feedDeg.set(feed);
                engageDeg.set(engage);
                systemDeg.set(system);
                syncRateLimitMultiplier();
                log.warn("Warm start: restored degradation state feed={} engage={} system={}",
                        feed, engage, system);
            }
            if (concurStr != null) {
                int restored = Integer.parseInt(concurStr);
                concurrencyLimit.set(Math.max(minConcurrency, Math.min(maxConcurrency, restored)));
                log.info("Warm start: restored concurrency limit={}", concurrencyLimit.get());
            }
            // State restored — no warm-up ramp needed
            warmCyclesLeft.set(0);
        } catch (Exception e) {
            // Redis unavailable — start cautious: 50% concurrency, ramp up over warm-start cycles
            log.warn("Warm start: Redis unavailable, starting at 50% concurrency and ramping up");
            concurrencyLimit.set(maxConcurrency / 2);
            warmCyclesLeft.set(warmStartCycles);
        }
    }

    // ── Public API ────────────────────────────────────────────────────────────

    public boolean isPersonalizationDisabled() {
        return feedDeg.get().level >= DegradationLevel.PERSONALIZATION_DISABLED.level;
    }
    public boolean isBehavioralWritesSkipped() {
        return feedDeg.get().level >= DegradationLevel.BEHAVIORAL_WRITES_SKIPPED.level;
    }
    public boolean isAsyncConcurrencyReduced() {
        return engageDeg.get().level >= DegradationLevel.ASYNC_REDUCED.level;
    }
    public boolean isLoadSheddingActive() {
        return systemDeg.get().level >= DegradationLevel.LOAD_SHEDDING_ACTIVE.level;
    }
    public int getAdaptiveConcurrencyLimit() { return concurrencyLimit.get(); }
    public double getRateLimitMultiplier()   { return rateLimitMultiplier.get(); }
    public DegradationLevel getEffectiveLevel() {
        return DegradationLevel.values()[
            Math.max(Math.max(feedDeg.get().level, engageDeg.get().level), systemDeg.get().level)];
    }

    // ── Control loop ──────────────────────────────────────────────────────────

    @Scheduled(fixedDelay = 10_000)
    public void evaluateSlos() {
        // Warm-start ramp: gradually increase concurrency if we started cautious
        if (warmCyclesLeft.get() > 0) {
            int step = Math.max(10, maxConcurrency / (warmStartCycles * 2));
            int cur  = concurrencyLimit.get();
            if (cur < maxConcurrency) {
                concurrencyLimit.compareAndSet(cur, Math.min(maxConcurrency, cur + step));
                log.info("Warm-start ramp: concurrency {} → {} ({} cycles left)",
                        cur, concurrencyLimit.get(), warmCyclesLeft.get());
            }
            warmCyclesLeft.decrementAndGet();
            return; // don't evaluate SLOs during warm-up — metrics are not representative
        }

        double feedP95  = p95ms(feedTimer);
        double engP95   = p95ms(engageTimer);
        double errRate  = errorRate();

        evaluateFeed(feedP95);
        evaluateEngagement(engP95);
        evaluateSystem(errRate);
    }

    // ── Scoped evaluators ─────────────────────────────────────────────────────

    private void evaluateFeed(double p95) {
        boolean violation = p95 > FEED_L1_MS;
        boolean healthy   = p95 < FEED_RECOVER_MS || p95 == 0.0;
        feedWindow.add(violation);

        DegradationLevel cur    = feedDeg.get();
        DegradationLevel target = p95 > FEED_L2_MS ? DegradationLevel.BEHAVIORAL_WRITES_SKIPPED
                                : p95 > FEED_L1_MS ? DegradationLevel.PERSONALIZATION_DISABLED
                                : DegradationLevel.NORMAL;

        // HYBRID: degrade on streak OR high violation rate
        if (target.level > cur.level
                && (feedWindow.trailingViolations() >= DEGRADE_STREAK
                    || feedWindow.violationRate() >= DEGRADE_RATE)) {
            if (feedDeg.compareAndSet(cur, target)) {
                log.error("[FEED] Degraded {} → {} (p95={}ms, rate={:.0%})",
                        cur, target, String.format("%.0f", p95),
                        feedWindow.violationRate());
                activatedCounter.increment();
                syncRateLimitMultiplier();
                persist(K_FEED, target);
            }
        // HYBRID: recover only when streak AND rate are both healthy
        } else if (healthy && cur != DegradationLevel.NORMAL
                && feedWindow.trailingHealthy() >= RECOVER_STREAK
                && feedWindow.violationRate() < RECOVER_RATE) {
            DegradationLevel prev = DegradationLevel.values()[cur.level - 1];
            if (feedDeg.compareAndSet(cur, prev)) {
                log.info("[FEED] Recovered {} → {} (p95={}ms)", cur, prev,
                        String.format("%.0f", p95));
                recoveredCounter.increment();
                syncRateLimitMultiplier();
                persist(K_FEED, prev);
            }
        }
    }

    private void evaluateEngagement(double p95) {
        boolean violation = p95 > ENG_L3_MS;
        boolean healthy   = p95 < ENG_RECOVER_MS || p95 == 0.0;
        engageWindow.add(violation);

        DegradationLevel cur = engageDeg.get();
        if (violation
                && (engageWindow.trailingViolations() >= DEGRADE_STREAK
                    || engageWindow.violationRate() >= DEGRADE_RATE)
                && cur == DegradationLevel.NORMAL) {
            if (engageDeg.compareAndSet(cur, DegradationLevel.ASYNC_REDUCED)) {
                log.error("[ENGAGE] Async reduced (p95={}ms, rate={:.0%})",
                        String.format("%.0f", p95), engageWindow.violationRate());
                halveAdaptiveConcurrency();
                activatedCounter.increment();
                persist(K_ENGAGE, DegradationLevel.ASYNC_REDUCED);
            }
        } else if (healthy && cur != DegradationLevel.NORMAL
                && engageWindow.trailingHealthy() >= RECOVER_STREAK
                && engageWindow.violationRate() < RECOVER_RATE) {
            if (engageDeg.compareAndSet(cur, DegradationLevel.NORMAL)) {
                log.info("[ENGAGE] Recovered (p95={}ms)", String.format("%.0f", p95));
                restoreAdaptiveConcurrency();
                recoveredCounter.increment();
                persist(K_ENGAGE, DegradationLevel.NORMAL);
            }
        }
    }

    private void evaluateSystem(double errRate) {
        boolean violation = errRate > ERR_L4;
        boolean healthy   = errRate < ERR_RECOVER;
        errorWindow.add(violation);

        DegradationLevel cur = systemDeg.get();
        if (violation
                && (errorWindow.trailingViolations() >= DEGRADE_STREAK
                    || errorWindow.violationRate() >= DEGRADE_RATE)
                && cur == DegradationLevel.NORMAL) {
            if (systemDeg.compareAndSet(cur, DegradationLevel.LOAD_SHEDDING_ACTIVE)) {
                log.error("[SYSTEM] Load shedding activated (err={:.2%})", errRate);
                rateLimitMultiplier.set(RATE_STEPS[DegradationLevel.LOAD_SHEDDING_ACTIVE.level]);
                halveAdaptiveConcurrency();
                activatedCounter.increment();
                persist(K_SYSTEM, DegradationLevel.LOAD_SHEDDING_ACTIVE);
            }
        } else if (healthy && cur != DegradationLevel.NORMAL
                && errorWindow.trailingHealthy() >= RECOVER_STREAK
                && errorWindow.violationRate() < RECOVER_RATE) {
            if (systemDeg.compareAndSet(cur, DegradationLevel.NORMAL)) {
                log.info("[SYSTEM] Load shedding deactivated (err={:.2%})", errRate);
                syncRateLimitMultiplier();
                restoreAdaptiveConcurrency();
                recoveredCounter.increment();
                persist(K_SYSTEM, DegradationLevel.NORMAL);
            }
        }
    }

    // ── Nonlinear concurrency control ─────────────────────────────────────────

    private void halveAdaptiveConcurrency() {
        int cur = concurrencyLimit.get();
        int next = Math.max(minConcurrency, cur / 2);
        if (concurrencyLimit.compareAndSet(cur, next)) {
            log.warn("Concurrency halved: {} → {}", cur, next);
            persist(K_CONCUR, String.valueOf(next));
        }
    }

    private void restoreAdaptiveConcurrency() {
        int step = Math.max(10, maxConcurrency / 20); // 5% per cycle
        int cur  = concurrencyLimit.get();
        if (cur < maxConcurrency) {
            int next = Math.min(maxConcurrency, cur + step);
            concurrencyLimit.compareAndSet(cur, next);
            log.info("Concurrency restored: {} → {}", cur, next);
            persist(K_CONCUR, String.valueOf(next));
        }
    }

    // ── Progressive rate limit sync ───────────────────────────────────────────

    /** Sets multiplier to the step matching the highest active degradation level. */
    private void syncRateLimitMultiplier() {
        int maxLevel = getEffectiveLevel().level;
        rateLimitMultiplier.set(RATE_STEPS[maxLevel]);
    }

    // ── Persistence helpers ───────────────────────────────────────────────────

    private void persist(String key, DegradationLevel level) {
        persist(key, String.valueOf(level.level));
    }

    private void persist(String key, String value) {
        try {
            redisTemplate.opsForValue().set(key, value, Duration.ofHours(2));
        } catch (Exception e) {
            log.warn("Failed to persist SLO state key={}: {}", key, e.getMessage());
        }
    }

    private DegradationLevel readLevel(String key) {
        try {
            String val = redisTemplate.opsForValue().get(key);
            if (val == null) return DegradationLevel.NORMAL;
            int lvl = Integer.parseInt(val);
            return DegradationLevel.values()[Math.min(lvl, DegradationLevel.values().length - 1)];
        } catch (Exception e) {
            return DegradationLevel.NORMAL;
        }
    }

    // ── Metric helpers ────────────────────────────────────────────────────────

    private double p95ms(Timer t) {
        if (t == null) return 0.0;
        try {
            // takeSnapshot() returns a HistogramSnapshot with percentile values
            var snapshot = t.takeSnapshot();
            for (var pv : snapshot.percentileValues()) {
                if (Math.abs(pv.percentile() - 0.95) < 0.001) {
                    double v = pv.value(java.util.concurrent.TimeUnit.MILLISECONDS);
                    return Double.isNaN(v) ? 0.0 : v;
                }
            }
            return 0.0;
        } catch (Exception e) {
            return 0.0;
        }
    }

    private double errorRate() {
        try {
            var all  = registry.find("http.server.requests").timers();
            var errs = registry.find("http.server.requests").tag("status", "500").timers();
            if (all.isEmpty()) return 0.0;
            double t = all.stream().mapToDouble(Timer::count).sum();
            double e = errs.stream().mapToDouble(Timer::count).sum();
            return t > 0 ? e / t : 0.0;
        } catch (Exception ex) { return 0.0; }
    }
}
