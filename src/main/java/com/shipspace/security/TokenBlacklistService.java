package com.shipspace.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;

/**
 * Redis-backed token blacklist for refresh token revocation.
 *
 * On logout or token rotation, the old refresh token is added here with a TTL
 * equal to its remaining validity. The JWT filter checks this before accepting
 * any token. Blacklisted tokens are automatically evicted by Redis TTL.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TokenBlacklistService {

    private static final String BLACKLIST_PREFIX = "token:blacklist:";

    private final RedisTemplate<String, String> redisTemplate;

    /**
     * Adds a token to the blacklist until its natural expiry.
     *
     * @param tokenId  a unique identifier for the token (jti claim or SHA-256 of the token)
     * @param expiry   the token's expiry date — used to calculate the Redis TTL
     */
    public void blacklist(String tokenId, Date expiry) {
        long ttlSeconds = Duration.between(Instant.now(), expiry.toInstant()).getSeconds();
        if (ttlSeconds <= 0) {
            // Already expired — no need to store
            return;
        }
        redisTemplate.opsForValue().set(
                BLACKLIST_PREFIX + tokenId,
                "revoked",
                Duration.ofSeconds(ttlSeconds)
        );
        log.debug("Token {} blacklisted for {} seconds", tokenId, ttlSeconds);
    }

    /**
     * Returns true if the token has been revoked.
     *
     * Failure strategy:
     * - Access token checks (short-lived, 24h): FAIL OPEN.
     *   A Redis outage should not log out every active user. The worst case is
     *   a revoked access token remaining valid for up to 24h — acceptable.
     * - Refresh token checks (long-lived, 7d): FAIL CLOSED.
     *   Refresh tokens are long-lived and used for re-authentication. Allowing
     *   a potentially revoked refresh token through is a higher security risk.
     *
     * @param tokenId  the jti claim of the token
     * @param isRefreshToken  true if this is a refresh token check
     */
    public boolean isBlacklisted(String tokenId, boolean isRefreshToken) {
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(BLACKLIST_PREFIX + tokenId));
        } catch (Exception e) {
            if (isRefreshToken) {
                // Fail closed for refresh tokens — higher security risk
                log.error("Redis unavailable during refresh token blacklist check for jti={}: {}",
                        tokenId, e.getMessage());
                return true;
            } else {
                // Fail open for access tokens — availability over security for short-lived tokens
                log.warn("Redis unavailable during access token blacklist check for jti={}, failing open: {}",
                        tokenId, e.getMessage());
                return false;
            }
        }
    }

    /**
     * Convenience overload — defaults to access token (fail-open) behavior.
     * Kept for backward compatibility with the JWT filter.
     */
    public boolean isBlacklisted(String tokenId) {
        return isBlacklisted(tokenId, false);
    }
}
