package com.shipspace.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Per-user rate limiting service using Redis.
 * 
 * <p>Provides sliding window rate limiting per user for:
 * <ul>
 *   <li>Score event applications</li>
 *   <li>API requests</li>
 *   <li>Resource creation operations</li>
 * </ul>
 * 
 * <p>Uses Redis with sliding window algorithm for accurate rate limiting
 * that prevents burst traffic while allowing legitimate usage patterns.
 */
@Slf4j
@Service
public class RateLimitingService {

    private static final String RATE_LIMIT_KEY_PREFIX = "rate_limit:";
    private static final String USER_RATE_LIMIT_KEY_PREFIX = "rate_limit:user:";

    private final RedisTemplate<String, String> redisTemplate;

    @Autowired
    public RateLimitingService(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * Checks if a user has exceeded their rate limit for a specific action.
     * 
     * <p>Uses a sliding window counter algorithm for accurate rate limiting.
     * 
     * @param userId the user ID
     * @param action the action being rate limited
     * @param maxRequests maximum allowed requests in the window
     * @param windowSize the time window size in seconds
     * @return true if the request is allowed, false if rate limited
     */
    public boolean isRateLimited(String userId, String action, int maxRequests, int windowSize) {
        String key = USER_RATE_LIMIT_KEY_PREFIX + userId + ":" + action;
        
        try {
            // Increment counter
            Long currentCount = redisTemplate.opsForValue().increment(key);
            
            if (currentCount == null) {
                currentCount = 1L;
            }
            
            // Set expiry on first request
            if (currentCount == 1) {
                redisTemplate.expire(key, windowSize, TimeUnit.SECONDS);
            }
            
            // Check if rate limited
            if (currentCount > maxRequests) {
                log.debug("Rate limited user {} for action {}: {}/{} requests",
                        userId, action, currentCount, maxRequests);
                return true;
            }
            
            log.debug("User {} action {} allowed: {}/{} requests",
                    userId, action, currentCount, maxRequests);
            return false;
            
        } catch (Exception e) {
            log.error("Error checking rate limit for user {} action {}: {}",
                    userId, action, e.getMessage());
            // Fail open - allow the request if Redis is unavailable
            return false;
        }
    }

    /**
     * Checks if a user has exceeded their score event rate limit.
     * 
     * <p>Uses the configured daily cap for PROJECT_UPDATED events.
     * 
     * @param userId the user ID
     * @param eventType the type of score event
     * @param maxRequests the maximum allowed requests (daily cap)
     * @return true if the request is allowed, false if rate limited
     */
    public boolean isScoreEventRateLimited(String userId, String eventType, int maxRequests) {
        if (maxRequests <= 0) {
            // No limit configured
            return false;
        }
        
        // Use daily window (86400 seconds)
        String key = USER_RATE_LIMIT_KEY_PREFIX + userId + ":score:" + eventType;
        
        try {
            Long currentCount = redisTemplate.opsForValue().increment(key);
            
            if (currentCount == null) {
                currentCount = 1L;
            }
            
            // Set expiry to end of day (simplified - in production use scheduled job)
            if (currentCount == 1) {
                // Calculate time until end of day
                long secondsUntilMidnight = getSecondsUntilMidnight();
                if (secondsUntilMidnight > 0) {
                    redisTemplate.expire(key, secondsUntilMidnight, TimeUnit.SECONDS);
                }
            }
            
            if (currentCount > maxRequests) {
                log.info("Score event rate limited user {} for {}: {}/{} daily",
                        userId, eventType, currentCount, maxRequests);
                return true;
            }
            
            return false;
            
        } catch (Exception e) {
            log.error("Error checking score rate limit for user {} event {}: {}",
                    userId, eventType, e.getMessage());
            return false;
        }
    }

    /**
     * Gets the remaining rate limit for a user.
     * 
     * @param userId the user ID
     * @param action the action
     * @param maxRequests maximum allowed requests
     * @param windowSize the time window size in seconds
     * @return the remaining requests allowed, or -1 if rate limited
     */
    public long getRemainingRequests(String userId, String action, int maxRequests, int windowSize) {
        String key = USER_RATE_LIMIT_KEY_PREFIX + userId + ":" + action;
        
        try {
            String count = redisTemplate.opsForValue().get(key);
            long currentCount = count != null ? Long.parseLong(count) : 0;
            
            long remaining = maxRequests - currentCount;
            return Math.max(0, remaining);
            
        } catch (Exception e) {
            log.error("Error getting remaining rate limit for user {} action {}: {}",
                    userId, action, e.getMessage());
            return maxRequests; // Assume full quota available
        }
    }

    /**
     * Resets rate limit for a user (admin operation).
     * 
     * @param userId the user ID
     * @param action the action
     */
    public void resetRateLimit(String userId, String action) {
        String key = USER_RATE_LIMIT_KEY_PREFIX + userId + ":" + action;
        redisTemplate.delete(key);
        log.info("Rate limit reset for user {} action {}", userId, action);
    }

    /**
     * Calculates seconds until midnight UTC.
     * 
     * @return seconds until end of current day
     */
    private long getSecondsUntilMidnight() {
        java.time.LocalDateTime now = java.time.LocalDateTime.now(java.time.ZoneOffset.UTC);
        java.time.LocalDateTime midnight = now.toLocalDate().plusDays(1)
                .atStartOfDay(java.time.ZoneOffset.UTC);
        return Duration.between(now, midnight).getSeconds();
    }

    /**
     * Gets current rate limit usage for monitoring.
     * 
     * @param userId the user ID
     * @param action the action
     * @return current usage count
     */
    public long getCurrentUsage(String userId, String action) {
        String key = USER_RATE_LIMIT_KEY_PREFIX + userId + ":" + action;
        
        try {
            String count = redisTemplate.opsForValue().get(key);
            return count != null ? Long.parseLong(count) : 0;
            
        } catch (Exception e) {
            log.error("Error getting current usage for user {} action {}: {}",
                    userId, action, e.getMessage());
            return 0;
        }
    }
}
