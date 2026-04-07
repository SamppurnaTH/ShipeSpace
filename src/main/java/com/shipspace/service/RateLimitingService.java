package com.shipspace.service;

import com.shipspace.config.SloEnforcementService;
import lombok.extern.slf4j.Slf4j;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;

/**
 * Per-user rate limiting service using Redis.
 *
 * Uses a Lua script for atomic increment + TTL set in a single round-trip.
 * This eliminates the race condition in the previous two-command approach
 * (INCR then EXPIRE) where the TTL could be lost between commands.
 *
 * Lua script logic:
 *   1. INCR the key
 *   2. If this is the first increment (count == 1), SET the TTL
 *   3. Return the current count
 * Both operations execute atomically inside Redis.
 */
@Slf4j
@Service
public class RateLimitingService {

    private static final String KEY_PREFIX = "rate_limit:user:";

    /**
     * Atomic increment + conditional TTL set.
     * KEYS[1] = key, ARGV[1] = ttl in seconds
     * Returns the count after increment.
     */
    private static final DefaultRedisScript<Long> INCREMENT_SCRIPT =
            new DefaultRedisScript<>(
                    "local count = redis.call('INCR', KEYS[1]) " +
                    "if count == 1 then " +
                    "  redis.call('EXPIRE', KEYS[1], ARGV[1]) " +
                    "end " +
                    "return count",
                    Long.class
            );

    private final RedisTemplate<String, String> redisTemplate;
    private final MeterRegistry meterRegistry;
    private final SloEnforcementService sloEnforcementService;

    public RateLimitingService(RedisTemplate<String, String> redisTemplate,
                                MeterRegistry meterRegistry,
                                SloEnforcementService sloEnforcementService) {
        this.redisTemplate = redisTemplate;
        this.meterRegistry = meterRegistry;
        this.sloEnforcementService = sloEnforcementService;
        // Pre-register the untagged counter so existing Prometheus queries still work
        meterRegistry.counter("shipspace.rate_limit.rejections");
    }

    /**
     * Checks if a user has exceeded their rate limit for a specific action.
     * Uses an atomic Lua script — no race between INCR and EXPIRE.
     *
     * @param userId      the user ID
     * @param action      the action being rate limited
     * @param maxRequests maximum allowed requests in the window
     * @param windowSecs  the time window size in seconds
     * @return true if rate limited (request should be rejected), false if allowed
     */
    public boolean isRateLimited(String userId, String action, int maxRequests, int windowSecs) {
        String key = KEY_PREFIX + userId + ":" + action;
        // Enforce tighter limits when system is under SLO pressure.
        // This is server-side enforcement — not advisory. Clients that ignore
        // Retry-After headers are rate-limited regardless.
        int effectiveMax = (int) Math.max(1, maxRequests * sloEnforcementService.getRateLimitMultiplier());
        try {
            Long count = redisTemplate.execute(
                    INCREMENT_SCRIPT,
                    Collections.singletonList(key),
                    String.valueOf(windowSecs)
            );
            if (count == null) count = 1L;

            if (count > effectiveMax) {
                log.debug("Rate limited user {} for action {}: {}/{} (multiplier={})",
                        userId, action, count, effectiveMax,
                        sloEnforcementService.getRateLimitMultiplier());
                meterRegistry.counter("shipspace.rate_limit.rejections",
                        "action", action).increment();
                return true;
            }
            return false;
        } catch (Exception e) {
            log.error("Rate limit check failed for user {} action {}: {}", userId, action, e.getMessage());
            return false;
        }
    }

    /**
     * Checks rate limit and writes X-RateLimit-* headers onto the response.
     * Returns true if the request should be rejected.
     */
    public boolean isRateLimited(String userId, String action, int maxRequests, int windowSecs,
                                  jakarta.servlet.http.HttpServletResponse response) {
        String key = KEY_PREFIX + userId + ":" + action;
        int effectiveMax = (int) Math.max(1, maxRequests * sloEnforcementService.getRateLimitMultiplier());
        try {
            Long count = redisTemplate.execute(
                    INCREMENT_SCRIPT,
                    Collections.singletonList(key),
                    String.valueOf(windowSecs)
            );
            if (count == null) count = 1L;

            long remaining = Math.max(0, effectiveMax - count);
            long resetEpoch = System.currentTimeMillis() / 1000 + windowSecs;

            response.setHeader("X-RateLimit-Limit", String.valueOf(effectiveMax));
            response.setHeader("X-RateLimit-Remaining", String.valueOf(remaining));
            response.setHeader("X-RateLimit-Reset", String.valueOf(resetEpoch));

            if (count > effectiveMax) {
                log.debug("Rate limited user {} for action {}: {}/{}", userId, action, count, effectiveMax);
                meterRegistry.counter("shipspace.rate_limit.rejections",
                        "action", action).increment();
                return true;
            }
            return false;
        } catch (Exception e) {
            log.error("Rate limit check failed for user {} action {}: {}", userId, action, e.getMessage());
            return false;
        }
    }

    /**
     * Checks if a user has exceeded their daily score event rate limit.
     * Window expires at midnight UTC.
     *
     * @param userId      the user ID
     * @param eventType   the score event type name
     * @param maxRequests the daily cap
     * @return true if rate limited, false if allowed
     */
    public boolean isScoreEventRateLimited(String userId, String eventType, int maxRequests) {
        if (maxRequests <= 0) return false;

        String key = KEY_PREFIX + userId + ":score:" + eventType;
        long secondsUntilMidnight = secondsUntilMidnightUtc();

        try {
            Long count = redisTemplate.execute(
                    INCREMENT_SCRIPT,
                    Collections.singletonList(key),
                    String.valueOf(secondsUntilMidnight)
            );
            if (count == null) count = 1L;

            if (count > maxRequests) {
                log.info("Score event rate limited user {} for {}: {}/{} daily", userId, eventType, count, maxRequests);
                return true;
            }
            return false;
        } catch (Exception e) {
            log.error("Score rate limit check failed for user {} event {}: {}", userId, eventType, e.getMessage());
            return false;
        }
    }

    /**
     * Returns remaining requests in the current window without incrementing.
     */
    public long getRemainingRequests(String userId, String action, int maxRequests) {
        String key = KEY_PREFIX + userId + ":" + action;
        try {
            String val = redisTemplate.opsForValue().get(key);
            long current = val != null ? Long.parseLong(val) : 0L;
            return Math.max(0, maxRequests - current);
        } catch (Exception e) {
            log.error("Failed to get remaining requests for user {} action {}: {}", userId, action, e.getMessage());
            return maxRequests;
        }
    }

    /** Admin operation — resets the rate limit counter for a user/action. */
    public void resetRateLimit(String userId, String action) {
        redisTemplate.delete(KEY_PREFIX + userId + ":" + action);
        log.info("Rate limit reset for user {} action {}", userId, action);
    }

    private long secondsUntilMidnightUtc() {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        LocalDateTime midnight = now.toLocalDate().plusDays(1).atStartOfDay();
        return Duration.between(now, midnight).getSeconds();
    }
}
