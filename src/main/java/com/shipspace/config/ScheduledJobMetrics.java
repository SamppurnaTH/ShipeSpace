package com.shipspace.config;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Tracks last-success timestamps for scheduled jobs as Prometheus gauges.
 *
 * F-06 fix: scheduled jobs previously failed silently — no metric, no alert.
 * Each job calls recordSuccess(jobName) on completion. Prometheus scrapes the
 * gauge. An alert fires if the value hasn't updated within 2× the job interval.
 *
 * Alert rule example (Prometheus):
 *   ALERT ScheduledJobStale
 *   IF (time() - shipspace_job_last_success_seconds) > 90000  # 25h for daily jobs
 *   FOR 5m
 *   LABELS { severity="warning" }
 *   ANNOTATIONS { summary="Job {{ $labels.job }} has not run successfully in 25h" }
 *
 * Usage in job classes:
 *   scheduledJobMetrics.recordSuccess("score-decay-job");
 */
@Slf4j
@Component
public class ScheduledJobMetrics {

    private final ConcurrentHashMap<String, AtomicLong> lastSuccessEpoch = new ConcurrentHashMap<>();
    private final MeterRegistry meterRegistry;

    public ScheduledJobMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        // Pre-register known jobs so the gauge exists from startup (value = 0 = never run)
        register("score-decay-job");
        register("counter-reconciliation");
        register("trending-recalculation");
        register("interest-profile-decay");
    }

    /**
     * Record a successful job completion. Updates the gauge to current epoch seconds.
     */
    public void recordSuccess(String jobName) {
        AtomicLong ts = lastSuccessEpoch.computeIfAbsent(jobName, k -> {
            register(k);
            return lastSuccessEpoch.get(k);
        });
        if (ts != null) {
            ts.set(Instant.now().getEpochSecond());
            log.debug("Recorded success for job '{}'", jobName);
        }
    }

    private void register(String jobName) {
        AtomicLong ts = new AtomicLong(0L);
        lastSuccessEpoch.put(jobName, ts);
        Gauge.builder("shipspace.job.last_success_seconds", ts, AtomicLong::doubleValue)
                .tag("job", jobName)
                .description("Unix epoch seconds of last successful job run (0 = never)")
                .register(meterRegistry);
    }
}
