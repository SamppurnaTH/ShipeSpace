package com.shipspace.service;

import com.shipspace.dto.AuthResponse;
import com.shipspace.dto.GitHubAccessTokenResponse;
import com.shipspace.dto.GitHubUserResponse;
import com.shipspace.exception.GitHubOAuthException;
import com.shipspace.exception.InvalidTokenException;
import com.shipspace.model.User;
import com.shipspace.repository.UserRepository;
import com.shipspace.security.TokenBlacklistService;
import com.shipspace.util.JwtUtil;
import io.micrometer.core.instrument.Counter;
import net.jqwik.api.*;
import net.jqwik.api.constraints.AlphaChars;
import net.jqwik.api.constraints.NumericChars;
import net.jqwik.api.constraints.StringLength;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import net.jqwik.api.lifecycle.BeforeTry;
import net.jqwik.api.lifecycle.AfterTry;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Property-based tests for authentication system.
 * 
 * **Validates: Requirements 1.1, 1.3, 1.4**
 * 
 * These tests verify the correctness properties of the authentication system
 * across a wide range of inputs using property-based testing with jqwik.
 */
class AuthenticationPropertyTest {

    private GitHubOAuthService githubOAuthService;
    private AuthService authService;
    private JwtUtil jwtUtil;
    private UserRepository userRepository;
    private MockWebServer mockWebServer;

    @BeforeTry
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();

        // Set up JwtUtil
        jwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(jwtUtil, "secret", "test-secret-key-for-jwt-token-generation-minimum-256-bits-required-for-hmac-sha");
        ReflectionTestUtils.setField(jwtUtil, "expiration", 86400000L); // 24 hours
        ReflectionTestUtils.setField(jwtUtil, "refreshExpiration", 604800000L); // 7 days

        // Set up mock repository
        userRepository = mock(UserRepository.class);

        // Set up GitHubOAuthService
        WebClient.Builder webClientBuilder = WebClient.builder();
        githubOAuthService = new GitHubOAuthService(userRepository, webClientBuilder);
        
        String baseUrl = mockWebServer.url("/").toString();
        ReflectionTestUtils.setField(githubOAuthService, "clientId", "test-client-id");
        ReflectionTestUtils.setField(githubOAuthService, "clientSecret", "test-client-secret");
        ReflectionTestUtils.setField(githubOAuthService, "redirectUri", "http://localhost:3000/callback");
        ReflectionTestUtils.setField(githubOAuthService, "tokenUrl", baseUrl + "login/oauth/access_token");
        ReflectionTestUtils.setField(githubOAuthService, "userUrl", baseUrl + "user");

        // Set up AuthService
        TokenBlacklistService tokenBlacklistService = mock(TokenBlacklistService.class);
        when(tokenBlacklistService.isBlacklisted(any())).thenReturn(false);
        Counter authSuccessCounter = mock(Counter.class);
        Counter authFailureCounter = mock(Counter.class);
        Counter tokenRefreshCounter = mock(Counter.class);
        authService = new AuthService(jwtUtil, userRepository, githubOAuthService,
                tokenBlacklistService, authSuccessCounter, authFailureCounter, tokenRefreshCounter);
        ReflectionTestUtils.setField(authService, "jwtExpiration", 86400000L);
    }

    @AfterTry
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    /**
     * Property 1: GitHub OAuth validation consistency
     * 
     * For any GitHub OAuth code, when sent to the authentication endpoint, 
     * the API should either successfully validate with GitHub and return user data, 
     * or reject with appropriate error response.
     * 
     * **Validates: Requirements 1.1**
     */
    @Property(tries = 100)
    @Label("Feature: shipspace-mvp, Property 1: GitHub OAuth validation consistency")
    void githubOAuthValidationConsistency(
            @ForAll @StringLength(min = 1, max = 100) @AlphaChars @NumericChars String oauthCode) {
        
        // Given: A GitHub OAuth code (valid or invalid)
        // We'll simulate both success and failure scenarios
        
        // Scenario 1: Valid OAuth code - should succeed
        mockSuccessfulGitHubResponse(oauthCode);
        
        User mockUser = createMockUser("12345", "testuser");
        when(userRepository.findByGithubId("12345")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenReturn(mockUser);

        // When: Authenticating with GitHub
        User result = githubOAuthService.authenticateWithGitHub(oauthCode);

        // Then: Should return a valid user
        assertThat(result).isNotNull();
        assertThat(result.getGithubId()).isNotNull();
        assertThat(result.getUsername()).isNotNull();
        
        // Scenario 2: Invalid OAuth code - should fail appropriately
        mockFailedGitHubResponse();
        
        // When: Authenticating with invalid code
        // Then: Should throw GitHubOAuthException
        assertThatThrownBy(() -> githubOAuthService.authenticateWithGitHub(oauthCode))
                .isInstanceOf(GitHubOAuthException.class);
    }

    /**
     * Property 3: JWT token generation
     * 
     * For any successful user authentication, the API should return a valid JWT token 
     * containing user information.
     * 
     * **Validates: Requirements 1.3**
     */
    @Property(tries = 100)
    @Label("Feature: shipspace-mvp, Property 3: JWT token generation")
    void jwtTokenGeneration(
            @ForAll @StringLength(min = 1, max = 50) @AlphaChars String userId,
            @ForAll @StringLength(min = 1, max = 50) @AlphaChars String username) {
        
        // Given: A successfully authenticated user
        User user = createMockUser(userId, username);
        when(userRepository.findByGithubId(userId)).thenReturn(Optional.of(user));

        // When: Generating authentication response
        mockSuccessfulGitHubResponse("test-code");
        when(userRepository.save(any(User.class))).thenReturn(user);
        
        AuthResponse authResponse = authService.authenticateWithGitHub("test-code");

        // Then: Should return valid JWT tokens
        assertThat(authResponse).isNotNull();
        assertThat(authResponse.getAccessToken()).isNotNull().isNotEmpty();
        assertThat(authResponse.getRefreshToken()).isNotNull().isNotEmpty();
        assertThat(authResponse.getTokenType()).isEqualTo("Bearer");
        assertThat(authResponse.getExpiresIn()).isPositive();
        
        // Verify token contains user information
        assertThat(authResponse.getUser()).isNotNull();
        assertThat(authResponse.getUser().getId()).isNotNull();
        assertThat(authResponse.getUser().getUsername()).isNotNull();
        
        // Verify the access token is valid and contains correct user ID
        String accessToken = authResponse.getAccessToken();
        assertThat(jwtUtil.validateToken(accessToken)).isTrue();
        assertThat(jwtUtil.extractUserId(accessToken)).isEqualTo(user.getId());
        assertThat(jwtUtil.extractUsername(accessToken)).isEqualTo(user.getUsername());
        
        // Verify the refresh token is valid
        String refreshToken = authResponse.getRefreshToken();
        assertThat(jwtUtil.validateRefreshToken(refreshToken)).isTrue();
        assertThat(jwtUtil.extractUserId(refreshToken)).isEqualTo(user.getId());
    }

    /**
     * Property 4: Invalid token rejection
     * 
     * For any invalid or expired JWT token, protected endpoints should return 
     * 401 Unauthorized response.
     * 
     * **Validates: Requirements 1.4**
     */
    @Property(tries = 100)
    @Label("Feature: shipspace-mvp, Property 4: Invalid token rejection")
    void invalidTokenRejection(
            @ForAll @StringLength(min = 1, max = 200) String invalidToken) {
        
        // Given: An invalid JWT token (random string, malformed, or expired)
        
        // When: Validating the token
        Boolean isValid = jwtUtil.validateToken(invalidToken);

        // Then: Should return false for invalid tokens
        assertThat(isValid).isFalse();
        
        // Additional test: Verify that using invalid token to get current user fails
        if (invalidToken.contains(".")) {
            // Only test extraction if it looks like it might be a JWT
            try {
                String userId = jwtUtil.extractUserId(invalidToken);
                // If we got a userId, verify that getCurrentUser throws exception
                when(userRepository.findById(userId)).thenReturn(Optional.empty());
                assertThatThrownBy(() -> authService.getCurrentUser(userId))
                        .isInstanceOf(InvalidTokenException.class);
            } catch (Exception e) {
                // Expected for malformed tokens - extraction itself should fail
                assertThat(e).isNotNull();
            }
        }
    }

    /**
     * Property: Token refresh generates new valid tokens
     * 
     * For any valid refresh token, the refresh endpoint should generate 
     * a new valid JWT token.
     * 
     * **Validates: Requirements 1.5**
     */
    @Property(tries = 100)
    @Label("Feature: shipspace-mvp, Property: Token refresh functionality")
    void tokenRefreshGeneratesNewValidTokens(
            @ForAll @StringLength(min = 1, max = 50) @AlphaChars String userId,
            @ForAll @StringLength(min = 1, max = 50) @AlphaChars String username) {
        
        // Given: A valid refresh token
        User user = createMockUser(userId, username);
        String refreshToken = jwtUtil.generateRefreshToken(user.getId());
        
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

        // When: Refreshing the token
        AuthResponse newAuthResponse = authService.refreshToken(refreshToken);

        // Then: Should return new valid tokens
        assertThat(newAuthResponse).isNotNull();
        assertThat(newAuthResponse.getAccessToken()).isNotNull().isNotEmpty();
        assertThat(newAuthResponse.getRefreshToken()).isNotNull().isNotEmpty();
        
        // Verify new access token is valid
        assertThat(jwtUtil.validateToken(newAuthResponse.getAccessToken())).isTrue();
        assertThat(jwtUtil.extractUserId(newAuthResponse.getAccessToken())).isEqualTo(user.getId());
        
        // Verify new refresh token is valid
        assertThat(jwtUtil.validateRefreshToken(newAuthResponse.getRefreshToken())).isTrue();
    }

    /**
     * Property: Invalid refresh token rejection
     * 
     * For any invalid refresh token (including access tokens used as refresh tokens),
     * the refresh endpoint should reject with InvalidTokenException.
     * 
     * **Validates: Requirements 1.5**
     */
    @Property(tries = 100)
    @Label("Feature: shipspace-mvp, Property: Invalid refresh token rejection")
    void invalidRefreshTokenRejection(
            @ForAll @StringLength(min = 1, max = 50) @AlphaChars String userId,
            @ForAll @StringLength(min = 1, max = 50) @AlphaChars String username) {
        
        // Given: An access token (not a refresh token)
        String accessToken = jwtUtil.generateToken(userId, username);

        // When: Attempting to refresh with an access token
        // Then: Should throw InvalidTokenException
        assertThatThrownBy(() -> authService.refreshToken(accessToken))
                .isInstanceOf(InvalidTokenException.class)
                .hasMessageContaining("Invalid or expired refresh token");
    }

    /**
     * Property: User document persistence after authentication
     * 
     * For any successful GitHub OAuth validation, a user document should exist 
     * in MongoDB after authentication completes.
     * 
     * **Validates: Requirements 1.2**
     */
    @Property(tries = 100)
    @Label("Feature: shipspace-mvp, Property: User document persistence")
    void userDocumentPersistenceAfterAuthentication(
            @ForAll @StringLength(min = 1, max = 100) @AlphaChars String oauthCode) {
        
        // Given: A valid GitHub OAuth code
        mockSuccessfulGitHubResponse(oauthCode);
        
        User mockUser = createMockUser("12345", "testuser");
        when(userRepository.findByGithubId("12345")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenReturn(mockUser);

        // When: Authenticating with GitHub
        User result = githubOAuthService.authenticateWithGitHub(oauthCode);

        // Then: User should be saved to repository
        verify(userRepository, atLeastOnce()).save(any(User.class));
        assertThat(result).isNotNull();
        assertThat(result.getId()).isNotNull();
        assertThat(result.getGithubId()).isEqualTo("12345");
        assertThat(result.getCreatedAt()).isNotNull();
    }

    // Helper methods

    private void mockSuccessfulGitHubResponse(String oauthCode) {
        // Mock GitHub token response
        mockWebServer.enqueue(new MockResponse()
                .setBody("{\"access_token\":\"gho_test_token\",\"token_type\":\"bearer\",\"scope\":\"read:user\"}")
                .addHeader("Content-Type", "application/json"));

        // Mock GitHub user response
        mockWebServer.enqueue(new MockResponse()
                .setBody("{\"id\":12345,\"login\":\"testuser\",\"email\":\"test@example.com\"," +
                        "\"avatar_url\":\"https://github.com/avatar.png\",\"bio\":\"Test bio\"," +
                        "\"location\":\"Test City\",\"blog\":\"https://test.com\"}")
                .addHeader("Content-Type", "application/json"));
    }

    private void mockFailedGitHubResponse() {
        // Mock GitHub token response with error
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(401)
                .setBody("{\"error\":\"bad_verification_code\"}")
                .addHeader("Content-Type", "application/json"));
    }

    private User createMockUser(String githubId, String username) {
        return User.builder()
                .id(githubId)
                .githubId(githubId)
                .username(username)
                .email(username + "@example.com")
                .avatarUrl("https://github.com/avatar.png")
                .bio("Test bio")
                .location("Test City")
                .websiteUrl("https://test.com")
                .followerCount(0)
                .followingCount(0)
                .projectCount(0)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }
}
