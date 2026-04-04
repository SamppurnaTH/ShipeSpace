package com.shipspace.integration;

import com.shipspace.security.TokenBlacklistService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for TokenBlacklistService against a real Redis container.
 *
 * Phase 6: Verifies that blacklisted tokens are correctly stored and retrieved,
 * and that already-expired tokens are not stored (TTL <= 0 guard).
 */
class TokenBlacklistIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private TokenBlacklistService tokenBlacklistService;

    @Test
    void blacklistedToken_isDetected() {
        String jti = "test-jti-" + System.nanoTime();
        Date expiry = new Date(System.currentTimeMillis() + 60_000); // 1 minute from now

        tokenBlacklistService.blacklist(jti, expiry);

        assertThat(tokenBlacklistService.isBlacklisted(jti)).isTrue();
    }

    @Test
    void nonBlacklistedToken_isNotDetected() {
        String jti = "non-blacklisted-" + System.nanoTime();
        assertThat(tokenBlacklistService.isBlacklisted(jti)).isFalse();
    }

    @Test
    void alreadyExpiredToken_isNotStored() {
        String jti = "expired-jti-" + System.nanoTime();
        Date expiry = new Date(System.currentTimeMillis() - 1000); // already expired

        tokenBlacklistService.blacklist(jti, expiry);

        // Should not be stored since TTL would be <= 0
        assertThat(tokenBlacklistService.isBlacklisted(jti)).isFalse();
    }

    @Test
    void refreshToken_failsClosedOnBlacklist() {
        String jti = "refresh-jti-" + System.nanoTime();
        Date expiry = new Date(System.currentTimeMillis() + 60_000);

        tokenBlacklistService.blacklist(jti, expiry);

        assertThat(tokenBlacklistService.isBlacklisted(jti, true)).isTrue();
    }
}
