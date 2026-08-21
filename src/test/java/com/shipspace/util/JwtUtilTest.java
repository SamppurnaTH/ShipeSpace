package com.shipspace.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for JwtUtil.
 */
class JwtUtilTest {

    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil();
        // Set test values using reflection
        ReflectionTestUtils.setField(jwtUtil, "secret", "test-secret-key-for-jwt-token-generation-minimum-256-bits-required");
        ReflectionTestUtils.setField(jwtUtil, "expiration", 86400000L); // 24 hours
        ReflectionTestUtils.setField(jwtUtil, "refreshExpiration", 604800000L); // 7 days
        jwtUtil.init();
    }

    @Test
    void generateToken_ValidUserIdAndUsername_ReturnsToken() {
        // Given
        String userId = "user123";
        String username = "testuser";

        // When
        String token = jwtUtil.generateToken(userId, username);

        // Then
        assertThat(token).isNotNull();
        assertThat(token).isNotEmpty();
    }

    @Test
    void extractUserId_ValidToken_ReturnsUserId() {
        // Given
        String userId = "user123";
        String username = "testuser";
        String token = jwtUtil.generateToken(userId, username);

        // When
        String extractedUserId = jwtUtil.extractUserId(token);

        // Then
        assertThat(extractedUserId).isEqualTo(userId);
    }

    @Test
    void extractUsername_ValidToken_ReturnsUsername() {
        // Given
        String userId = "user123";
        String username = "testuser";
        String token = jwtUtil.generateToken(userId, username);

        // When
        String extractedUsername = jwtUtil.extractUsername(token);

        // Then
        assertThat(extractedUsername).isEqualTo(username);
    }

    @Test
    void validateToken_ValidToken_ReturnsTrue() {
        // Given
        String userId = "user123";
        String username = "testuser";
        String token = jwtUtil.generateToken(userId, username);

        // When
        Boolean isValid = jwtUtil.validateToken(token);

        // Then
        assertThat(isValid).isTrue();
    }

    @Test
    void validateToken_InvalidToken_ReturnsFalse() {
        // Given
        String invalidToken = "invalid.token.here";

        // When
        Boolean isValid = jwtUtil.validateToken(invalidToken);

        // Then
        assertThat(isValid).isFalse();
    }

    @Test
    void validateToken_ExpiredToken_ReturnsFalse() {
        // Given
        JwtUtil expiringJwtUtil = newJwtUtil(-1000L, 604800000L,
                "expired-token-test-secret-key-minimum-256-bits-required");
        String token = expiringJwtUtil.generateToken("user123", "testuser");

        // When
        Boolean isValid = expiringJwtUtil.validateToken(token);
        Boolean isExpired = expiringJwtUtil.isTokenExpired(token);

        // Then
        assertThat(isValid).isFalse();
        assertThat(isExpired).isTrue();
    }

    @Test
    void init_ShortSecret_ThrowsStartupFailure() {
        // Given
        JwtUtil weakJwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(weakJwtUtil, "secret", "short");
        ReflectionTestUtils.setField(weakJwtUtil, "expiration", 86400000L);
        ReflectionTestUtils.setField(weakJwtUtil, "refreshExpiration", 604800000L);

        // When / Then
        assertThatThrownBy(weakJwtUtil::init)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT secret must be at least 32 bytes");
    }

    @Test
    void generateRefreshToken_ValidUserId_ReturnsToken() {
        // Given
        String userId = "user123";

        // When
        String refreshToken = jwtUtil.generateRefreshToken(userId);

        // Then
        assertThat(refreshToken).isNotNull();
        assertThat(refreshToken).isNotEmpty();
    }

    @Test
    void validateRefreshToken_ValidRefreshToken_ReturnsTrue() {
        // Given
        String userId = "user123";
        String refreshToken = jwtUtil.generateRefreshToken(userId);

        // When
        Boolean isValid = jwtUtil.validateRefreshToken(refreshToken);

        // Then
        assertThat(isValid).isTrue();
    }

    @Test
    void validateRefreshToken_AccessToken_ReturnsFalse() {
        // Given
        String userId = "user123";
        String username = "testuser";
        String accessToken = jwtUtil.generateToken(userId, username);

        // When
        Boolean isValid = jwtUtil.validateRefreshToken(accessToken);

        // Then
        assertThat(isValid).isFalse();
    }

    @Test
    void extractTokenType_AccessToken_ReturnsAccess() {
        // Given
        String userId = "user123";
        String username = "testuser";
        String token = jwtUtil.generateToken(userId, username);

        // When
        String tokenType = jwtUtil.extractTokenType(token);

        // Then
        assertThat(tokenType).isEqualTo("access");
    }

    @Test
    void extractTokenType_RefreshToken_ReturnsRefresh() {
        // Given
        String userId = "user123";
        String refreshToken = jwtUtil.generateRefreshToken(userId);

        // When
        String tokenType = jwtUtil.extractTokenType(refreshToken);

        // Then
        assertThat(tokenType).isEqualTo("refresh");
    }

    @Test
    void isTokenExpired_ValidToken_ReturnsFalse() {
        // Given
        String userId = "user123";
        String username = "testuser";
        String token = jwtUtil.generateToken(userId, username);

        // When
        Boolean isExpired = jwtUtil.isTokenExpired(token);

        // Then
        assertThat(isExpired).isFalse();
    }

    private JwtUtil newJwtUtil(long expiration, long refreshExpiration, String secret) {
        JwtUtil customJwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(customJwtUtil, "secret", secret);
        ReflectionTestUtils.setField(customJwtUtil, "expiration", expiration);
        ReflectionTestUtils.setField(customJwtUtil, "refreshExpiration", refreshExpiration);
        customJwtUtil.init();
        return customJwtUtil;
    }
}
