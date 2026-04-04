package com.shipspace.service;

import com.shipspace.dto.AuthResponse;
import com.shipspace.dto.CurrentUserResponse;
import com.shipspace.exception.InvalidTokenException;
import com.shipspace.model.User;
import com.shipspace.repository.UserRepository;
import com.shipspace.security.TokenBlacklistService;
import com.shipspace.util.JwtUtil;
import io.micrometer.core.instrument.Counter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Service for authentication operations.
 * Handles JWT token generation, validation, and refresh.
 * 
 * This service implements Requirements 1.3, 1.4, and 1.5:
 * - Generates JWT tokens after successful authentication
 * - Validates JWT tokens
 * - Refreshes JWT tokens
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;
    private final GitHubOAuthService gitHubOAuthService;
    private final TokenBlacklistService tokenBlacklistService;
    private final Counter authSuccessCounter;
    private final Counter authFailureCounter;
    private final Counter tokenRefreshCounter;

    @Value("${jwt.expiration}")
    private Long jwtExpiration;

    /**
     * Authenticates a user with GitHub OAuth code and generates JWT tokens.
     *
     * @param code the GitHub OAuth authorization code
     * @return AuthResponse containing access token, refresh token, and user info
     */
    public AuthResponse authenticateWithGitHub(String code) {
        log.debug("Authenticating user with GitHub OAuth code");
        try {
            User user = gitHubOAuthService.authenticateWithGitHub(code);
            authSuccessCounter.increment();
            return generateAuthResponse(user);
        } catch (Exception e) {
            authFailureCounter.increment();
            throw e;
        }
    }

    /**
     * Refreshes the JWT access token using a valid refresh token.
     * The old refresh token is immediately blacklisted (token rotation).
     */
    public AuthResponse refreshToken(String refreshToken) {
        log.debug("Refreshing JWT token");

        if (!jwtUtil.validateRefreshToken(refreshToken)) {
            throw new InvalidTokenException("Invalid or expired refresh token");
        }

        // Check blacklist — prevents replay of already-rotated tokens (fail-closed for refresh tokens)
        String jti = jwtUtil.extractJti(refreshToken);
        if (jti != null && tokenBlacklistService.isBlacklisted(jti, true)) {
            throw new InvalidTokenException("Refresh token has been revoked");
        }

        String userId = jwtUtil.extractUserId(refreshToken);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new InvalidTokenException("User not found"));

        // Blacklist the consumed refresh token (rotation — one-time use)
        if (jti != null) {
            tokenBlacklistService.blacklist(jti, jwtUtil.extractExpiration(refreshToken));
        }

        log.info("Successfully refreshed token for user: {}", user.getUsername());
        tokenRefreshCounter.increment();
        return generateAuthResponse(user);
    }

    /**
     * Logs out a user by blacklisting both their current access and refresh tokens.
     *
     * @param accessToken  the current access token (from Authorization header, without "Bearer ")
     * @param refreshToken the refresh token to revoke (optional — may be null)
     */
    public void logout(String accessToken, String refreshToken) {
        // Blacklist access token
        if (accessToken != null && jwtUtil.validateToken(accessToken)) {
            String jti = jwtUtil.extractJti(accessToken);
            if (jti != null) {
                tokenBlacklistService.blacklist(jti, jwtUtil.extractExpiration(accessToken));
                log.debug("Access token blacklisted on logout, jti={}", jti);
            }
        }
        // Blacklist refresh token if provided
        if (refreshToken != null && jwtUtil.validateRefreshToken(refreshToken)) {
            String jti = jwtUtil.extractJti(refreshToken);
            if (jti != null) {
                tokenBlacklistService.blacklist(jti, jwtUtil.extractExpiration(refreshToken));
                log.debug("Refresh token blacklisted on logout, jti={}", jti);
            }
        }
    }

    /**
     * Validates a JWT token.
     *
     * @param token the JWT token to validate
     * @return true if the token is valid, false otherwise
     */
    public Boolean validateToken(String token) {
        return jwtUtil.validateToken(token);
    }

    /**
     * Extracts the user ID from a JWT token.
     *
     * @param token the JWT token
     * @return the user ID
     */
    public String extractUserId(String token) {
        return jwtUtil.extractUserId(token);
    }

    /**
     * Gets the current authenticated user information.
     *
     * @param userId the user ID from the JWT token
     * @return the User entity
     * @throws InvalidTokenException if the user is not found
     */
    public User getCurrentUser(String userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> {
                    log.error("User not found for ID: {}", userId);
                    return new InvalidTokenException("User not found");
                });
    }

    /**
     * Gets the current authenticated user as CurrentUserResponse DTO.
     *
     * @param userId the user ID from the JWT token
     * @return the current user response DTO
     * @throws InvalidTokenException if the user is not found
     */
    public CurrentUserResponse getCurrentUserResponse(String userId) {
        User user = getCurrentUser(userId);
        return CurrentUserResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .avatarUrl(user.getAvatarUrl())
                .bio(user.getBio())
                .location(user.getLocation())
                .websiteUrl(user.getWebsiteUrl())
                .followerCount(user.getFollowerCount())
                .followingCount(user.getFollowingCount())
                .projectCount(user.getProjectCount())
                .shipScore(user.getShipScore())
                .level(user.getLevel())
                .rank(user.getRank())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }

    /**
     * Generates an AuthResponse with JWT tokens and user information.
     *
     * @param user the authenticated user
     * @return AuthResponse containing tokens and user info
     */
    private AuthResponse generateAuthResponse(User user) {
        String accessToken = jwtUtil.generateToken(user.getId(), user.getUsername());
        String refreshToken = jwtUtil.generateRefreshToken(user.getId());

        log.debug("Generated JWT tokens for user: {}", user.getUsername());

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(jwtExpiration)
                .user(AuthResponse.UserInfo.builder()
                        .id(user.getId())
                        .username(user.getUsername())
                        .email(user.getEmail())
                        .avatarUrl(user.getAvatarUrl())
                        .build())
                .build();
    }
}
