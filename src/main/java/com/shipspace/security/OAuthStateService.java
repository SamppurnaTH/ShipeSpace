package com.shipspace.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;

/**
 * Manages OAuth CSRF state tokens.
 *
 * Attack scenario: Without state validation, an attacker can craft a link that
 * triggers the OAuth callback with the attacker's code, binding the victim's
 * session to the attacker's GitHub account (account takeover).
 *
 * Fix: Generate a cryptographically random state token, store it in Redis with
 * a short TTL, and validate it on callback before exchanging the code.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OAuthStateService {

    private static final String KEY_PREFIX = "oauth:state:";
    private static final Duration STATE_TTL = Duration.ofMinutes(10);
    private static final int STATE_BYTES = 32;

    private final StringRedisTemplate redisTemplate;
    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * Generate a new state token and store it in Redis.
     * The client must include this value in the GitHub authorization URL
     * as the `state` query parameter.
     *
     * @return URL-safe base64-encoded state token
     */
    public String generateState() {
        byte[] bytes = new byte[STATE_BYTES];
        secureRandom.nextBytes(bytes);
        String state = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        redisTemplate.opsForValue().set(KEY_PREFIX + state, "1", STATE_TTL);
        log.debug("Generated OAuth state token (TTL={})", STATE_TTL);
        return state;
    }

    /**
     * Validate and consume a state token.
     * Tokens are single-use — deleted immediately after validation.
     *
     * @param state the state token from the OAuth callback
     * @return true if valid and not yet consumed
     */
    public boolean validateAndConsume(String state) {
        if (state == null || state.isBlank()) {
            log.warn("OAuth state validation failed: null or blank state");
            return false;
        }
        String key = KEY_PREFIX + state;
        Boolean deleted = redisTemplate.delete(key);
        boolean valid = Boolean.TRUE.equals(deleted);
        if (!valid) {
            log.warn("OAuth state validation failed: token not found or already consumed");
        }
        return valid;
    }
}
