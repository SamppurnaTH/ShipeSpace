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
     */
    public boolean isBlacklisted(String tokenId) {
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(BLACKLIST_PREFIX + tokenId));
        } catch (Exception e) {
            // Fail closed — if Redis is down, reject the token to be safe
            log.error("Redis unavailable during blacklist check for token {}: {}", tokenId, e.getMessage());
            return true;
        }
    }
}
