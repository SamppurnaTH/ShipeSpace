package com.shipspace.config;

import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;

/**
 * Verifies MongoDB text indexes are present AND efficient at startup.
 *
 * Runs explain("executionStats") on both search collections after
 * MongoIndexInitializer has created indexes. Checks:
 *
 *   1. winningPlan stage — must be TEXT or IXSCAN, not COLLSCAN
 *   2. Scan efficiency   — docsExamined / nReturned ratio
 *   3. Key efficiency    — totalKeysExamined (index traversal cost)
 *   4. Combined judgment — efficiency ratio is only flagged as poor when
 *      execution time also exceeds SLOW_EXEC_MS. A high ratio on a fast
 *      query (e.g. empty collection) is noise, not a problem.
 *
 * Startup behavior:
 *   COLLSCAN detected → logs ERROR, marks health DOWN (readiness fails)
 *   Poor efficiency + slow → logs WARN, health stays UP (degraded, not broken)
 *   Index OK → logs INFO, health UP
 *
 * Retry logic:
 *   On COLLSCAN, retries up to MAX_RETRIES times with RETRY_DELAY_MS between
 *   attempts. This handles the case where indexes are still being built after
 *   a fresh deployment. Only fails readiness after all retries are exhausted.
 *
 * Excluded from "test" profile — unit tests don't need a real MongoDB.
 */
@Slf4j
@Component("searchIndex")
@Profile("!test")
public class SearchIndexVerifier implements HealthIndicator {

    private final MongoTemplate mongoTemplate;

    // Efficiency: ratio above which we warn — but only when execution is also slow
    private static final double EFFICIENCY_WARN_RATIO  = 100.0;
    // Combined judgment: only warn on poor ratio if query also took this long
    private static final long   SLOW_EXEC_MS           = 50;
    // Retry config: exponential backoff — initial 2s, doubles each attempt, max 3 attempts
    // Total max wait: 2s + 4s = 6s before giving up. Configurable via application.yml.
    private final int    maxRetries;
    private final long   retryInitialDelayMs;
    private final double retryBackoffMultiplier;

    private volatile Health lastHealth = Health.unknown()
            .withDetail("status", "not yet verified").build();

    public SearchIndexVerifier(MongoTemplate mongoTemplate,
            @org.springframework.beans.factory.annotation.Value("${search.index-verify.max-retries:3}") int maxRetries,
            @org.springframework.beans.factory.annotation.Value("${search.index-verify.retry-initial-delay-ms:2000}") long retryInitialDelayMs,
            @org.springframework.beans.factory.annotation.Value("${search.index-verify.retry-backoff-multiplier:2.0}") double retryBackoffMultiplier) {
        this.mongoTemplate = mongoTemplate;
        this.maxRetries = maxRetries;
        this.retryInitialDelayMs = retryInitialDelayMs;
        this.retryBackoffMultiplier = retryBackoffMultiplier;
        log.info("SearchIndexVerifier configured: maxRetries={} initialDelay={}ms backoffMultiplier={}",
                maxRetries, retryInitialDelayMs, retryBackoffMultiplier);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void verifySearchIndexes() {
        log.info("Verifying search index usage and efficiency via explain()...");
        boolean shipsOk = verifyWithRetry("ships");
        boolean usersOk = verifyWithRetry("users");

        if (shipsOk && usersOk) {
            lastHealth = Health.up()
                    .withDetail("ships", "index OK")
                    .withDetail("users", "index OK")
                    .build();
        } else {
            lastHealth = Health.down()
                    .withDetail("ships", shipsOk ? "OK" : "COLLSCAN — index missing")
                    .withDetail("users", usersOk ? "OK" : "COLLSCAN — index missing")
                    .withDetail("action",
                            "Run db.<collection>.getIndexes() to verify. " +
                            "Restart the application to trigger MongoIndexInitializer.")
                    .build();
            log.error(
                "Search index verification FAILED after {} retries. " +
                "Search queries are running as full collection scans. " +
                "Readiness probe will report DOWN until indexes are restored and app restarts.",
                maxRetries);
        }
    }

    @Override
    public Health health() {
        return lastHealth;
    }

    // ── Retry wrapper ─────────────────────────────────────────────────────────

    /**
     * Retries index verification with exponential backoff.
     * Handles slow index builds after fresh deployment.
     * Delays: initialDelay, initialDelay*multiplier, initialDelay*multiplier^2, ...
     */
    private boolean verifyWithRetry(String collection) {
        long delay = retryInitialDelayMs;
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            boolean ok = verifyCollection(collection);
            if (ok) return true;

            if (attempt < maxRetries) {
                log.warn("Index verification failed for collection={} (attempt {}/{}). " +
                        "Retrying in {}ms (exponential backoff, multiplier={}).",
                        collection, attempt, maxRetries, delay, retryBackoffMultiplier);
                try {
                    Thread.sleep(delay);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return false;
                }
                delay = (long) (delay * retryBackoffMultiplier);
            }
        }
        return false;
    }

    // ── Core verification ─────────────────────────────────────────────────────

    /**
     * Runs explain("executionStats") and evaluates index usage and efficiency.
     *
     * Efficiency judgment uses BOTH ratio AND execution time:
     *   - High ratio + slow query → WARN (real problem)
     *   - High ratio + fast query → INFO (empty collection or tiny dataset, not a problem)
     *   - Low ratio               → INFO (healthy)
     *
     * @return true if index is being used (TEXT or IXSCAN), false if COLLSCAN
     */
    private boolean verifyCollection(String collection) {
        try {
            Document result = mongoTemplate.getDb().runCommand(
                    new Document("explain",
                            new Document("find", collection)
                                    .append("filter", Document.parse("{ $text: { $search: \"test\" } }"))
                                    .append("limit", 1))
                            .append("verbosity", "executionStats"));

            String winningPlan    = extractWinningPlanStage(result);
            long   docsExamined   = extractLong(result, "executionStats", "totalDocsExamined");
            long   docsReturned   = extractLong(result, "executionStats", "nReturned");
            long   keysExamined   = extractLong(result, "executionStats", "totalKeysExamined");
            long   execTimeMs     = extractLong(result, "executionStats", "executionTimeMillis");

            boolean indexUsed = "IXSCAN".equals(winningPlan) || "TEXT".equals(winningPlan);

            if (!indexUsed) {
                log.error(
                    "COLLSCAN collection={} winningPlan={} docsExamined={} keysExamined={} " +
                    "nReturned={} execTimeMs={} — text index is NOT being used",
                    collection, winningPlan, docsExamined, keysExamined, docsReturned, execTimeMs);
                return false;
            }

            // Scan efficiency: docs examined per doc returned
            double docRatio = (docsReturned > 0)
                    ? (double) docsExamined / docsReturned
                    : (double) docsExamined;

            // Only flag poor efficiency when the query is also slow.
            // A high ratio on a fast query means the collection is tiny — not a problem.
            boolean poorEfficiency = docRatio > EFFICIENCY_WARN_RATIO && execTimeMs > SLOW_EXEC_MS;

            if (poorEfficiency) {
                log.warn(
                    "search index POOR EFFICIENCY collection={} winningPlan={} " +
                    "docsExamined={} keysExamined={} nReturned={} docRatio={} execTimeMs={}ms " +
                    "(ratio threshold={}, time threshold={}ms) — index exists but scan is expensive",
                    collection, winningPlan, docsExamined, keysExamined, docsReturned,
                    String.format("%.1f", docRatio), execTimeMs,
                    EFFICIENCY_WARN_RATIO, SLOW_EXEC_MS);
            } else {
                log.info(
                    "search index OK collection={} winningPlan={} docsExamined={} " +
                    "keysExamined={} nReturned={} docRatio={} execTimeMs={}ms",
                    collection, winningPlan, docsExamined, keysExamined, docsReturned,
                    String.format("%.1f", docRatio), execTimeMs);
            }
            return true;

        } catch (Exception e) {
            log.warn("Could not verify search index for collection={}: {} " +
                    "— search may be running without index support",
                    collection, e.getMessage());
            return true; // Unknown — don't fail health on transient errors
        }
    }

    // ── Plan extraction ───────────────────────────────────────────────────────

    private String extractWinningPlanStage(Document result) {
        try {
            Document queryPlanner = (Document) result.get("queryPlanner");
            if (queryPlanner == null) return "UNKNOWN";
            return findLeafStage((Document) queryPlanner.get("winningPlan"));
        } catch (Exception e) {
            return "UNKNOWN";
        }
    }

    private String findLeafStage(Document plan) {
        if (plan == null) return "UNKNOWN";
        Document inputStage = (Document) plan.get("inputStage");
        if (inputStage != null) return findLeafStage(inputStage);
        return plan.getString("stage");
    }

    private long extractLong(Document result, String section, String field) {
        try {
            Document sec = (Document) result.get(section);
            if (sec == null) return -1;
            Object val = sec.get(field);
            if (val instanceof Integer i) return i.longValue();
            if (val instanceof Long l)    return l;
            return -1;
        } catch (Exception e) {
            return -1;
        }
    }
}
