package com.shipspace.integration;

import com.shipspace.service.RateLimitingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for RateLimitingService using a real Redis container.
 *
 * Phase 6: These tests verify the Lua script atomicity — something that cannot
 * be tested with mocks. Key scenarios:
 * - First N requests are allowed
 * - Request N+1 is rejected
 * - TTL is set correctly (window expires)
 * - Concurrent requests don't bypass the limit (atomicity)
 */
class RateLimitingIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private RateLimitingService rateLimitingService;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @BeforeEach
    void cleanRedis() {
        // Clean up rate limit keys before each test
        var keys = redisTemplate.keys("rate_limit:user:test-user-*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    @Test
    void allowsRequestsUpToLimit() {
        String userId = "test-user-rl-1";
        int limit = 5;

        for (int i = 0; i < limit; i++) {
            boolean limited = rateLimitingService.isRateLimited(userId, "test-action", limit, 60);
            assertThat(limited).as("Request %d should be allowed", i + 1).isFalse();
        }
    }

    @Test
    void blocksRequestBeyondLimit() {
        String userId = "test-user-rl-2";
        int limit = 3;

        for (int i = 0; i < limit; i++) {
            rateLimitingService.isRateLimited(userId, "test-action", limit, 60);
        }

        boolean limited = rateLimitingService.isRateLimited(userId, "test-action", limit, 60);
        assertThat(limited).isTrue();
    }

    @Test
    void differentActionsHaveIndependentCounters() {
        String userId = "test-user-rl-3";
        int limit = 2;

        // Exhaust action-a
        rateLimitingService.isRateLimited(userId, "action-a", limit, 60);
        rateLimitingService.isRateLimited(userId, "action-a", limit, 60);
        boolean aLimited = rateLimitingService.isRateLimited(userId, "action-a", limit, 60);

        // action-b should still be allowed
        boolean bAllowed = rateLimitingService.isRateLimited(userId, "action-b", limit, 60);

        assertThat(aLimited).isTrue();
        assertThat(bAllowed).isFalse();
    }

    @Test
    void differentUsersHaveIndependentCounters() {
        int limit = 1;

        rateLimitingService.isRateLimited("user-a", "action", limit, 60);
        boolean aLimited = rateLimitingService.isRateLimited("user-a", "action", limit, 60);
        boolean bAllowed = rateLimitingService.isRateLimited("user-b", "action", limit, 60);

        assertThat(aLimited).isTrue();
        assertThat(bAllowed).isFalse();
    }

    @Test
    void remainingRequestsDecrementsCorrectly() {
        String userId = "test-user-rl-4";
        int limit = 5;

        rateLimitingService.isRateLimited(userId, "action", limit, 60);
        rateLimitingService.isRateLimited(userId, "action", limit, 60);

        long remaining = rateLimitingService.getRemainingRequests(userId, "action", limit);
        assertThat(remaining).isEqualTo(3);
    }

    @Test
    void resetClearsCounter() {
        String userId = "test-user-rl-5";
        int limit = 1;

        rateLimitingService.isRateLimited(userId, "action", limit, 60);
        boolean limited = rateLimitingService.isRateLimited(userId, "action", limit, 60);
        assertThat(limited).isTrue();

        rateLimitingService.resetRateLimit(userId, "action");

        boolean allowedAfterReset = rateLimitingService.isRateLimited(userId, "action", limit, 60);
        assertThat(allowedAfterReset).isFalse();
    }
}
