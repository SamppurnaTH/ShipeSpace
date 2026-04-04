package com.shipspace.controller;

import com.shipspace.dto.AuthRequest;
import com.shipspace.dto.AuthResponse;
import com.shipspace.dto.CurrentUserResponse;
import com.shipspace.dto.RefreshTokenRequest;
import com.shipspace.exception.GitHubOAuthException;
import com.shipspace.exception.TooManyRequestsException;
import com.shipspace.security.OAuthStateService;
import com.shipspace.service.AuthService;
import com.shipspace.service.RateLimitingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST controller for authentication endpoints.
 */
@Slf4j
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Authentication and authorization endpoints")
public class AuthController {

    private final AuthService authService;
    private final RateLimitingService rateLimitingService;
    private final OAuthStateService oAuthStateService;

    // 10 auth attempts per minute per IP — prevents brute-force
    private static final int AUTH_RATE_LIMIT = 10;
    private static final int AUTH_WINDOW_SECS = 60;

    /**
     * Issues a new CSRF state token for the OAuth flow.
     * The client must include this token in the GitHub authorization URL
     * and send it back in the /github callback request body.
     */
    @GetMapping("/state")
    @Operation(summary = "Get OAuth state token",
            description = "Generate a CSRF state token to include in the GitHub OAuth authorization URL")
    public ResponseEntity<Map<String, String>> getOAuthState(
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        String clientIp = getClientIp(httpRequest);
        if (rateLimitingService.isRateLimited(clientIp, "auth:state", AUTH_RATE_LIMIT, AUTH_WINDOW_SECS, httpResponse)) {
            throw new TooManyRequestsException("Too many state requests. Please try again later.");
        }
        String state = oAuthStateService.generateState();
        return ResponseEntity.ok(Map.of("state", state));
    }

    @PostMapping("/github")
    @Operation(summary = "Authenticate with GitHub OAuth",
            description = "Exchange GitHub OAuth authorization code for JWT tokens. Requires a valid state token from GET /auth/state.")
    public ResponseEntity<AuthResponse> authenticateWithGitHub(
            @Valid @RequestBody AuthRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        String clientIp = getClientIp(httpRequest);
        if (rateLimitingService.isRateLimited(clientIp, "auth:github", AUTH_RATE_LIMIT, AUTH_WINDOW_SECS, httpResponse)) {
            throw new TooManyRequestsException("Too many authentication attempts. Please try again later.");
        }

        // Validate and consume the CSRF state token — prevents OAuth CSRF attacks
        if (!oAuthStateService.validateAndConsume(request.getState())) {
            throw new GitHubOAuthException("Invalid or expired OAuth state token. Please restart the login flow.");
        }

        log.info("Received GitHub OAuth authentication request from {}", clientIp);
        AuthResponse response = authService.authenticateWithGitHub(request.getCode());
        log.info("Successfully authenticated user: {}", response.getUser().getUsername());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/refresh")
    @Operation(summary = "Refresh JWT token",
            description = "Generate new access token using a valid refresh token. Old refresh token is immediately revoked.")
    public ResponseEntity<AuthResponse> refreshToken(
            @Valid @RequestBody RefreshTokenRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        String clientIp = getClientIp(httpRequest);
        // IP-based limit: prevents distributed brute-force from many users on same IP
        if (rateLimitingService.isRateLimited(clientIp, "auth:refresh", AUTH_RATE_LIMIT, AUTH_WINDOW_SECS, httpResponse)) {
            throw new TooManyRequestsException("Too many refresh attempts. Please try again later.");
        }
        // Phase 1 fix: also apply per-token-subject rate limiting to prevent a single
        // compromised refresh token from being replayed in a tight loop.
        // We extract the userId from the token WITHOUT validating it here — validation
        // happens inside authService.refreshToken(). This is safe because we only use
        // the userId as a rate-limit key, not for authorization.
        try {
            String userId = authService.extractUserId(request.getRefreshToken());
            if (userId != null && rateLimitingService.isRateLimited(userId, "auth:refresh:user", 5, 60)) {
                throw new TooManyRequestsException("Too many refresh attempts for this account. Please try again later.");
            }
        } catch (TooManyRequestsException e) {
            throw e;
        } catch (Exception ignored) {
            // Malformed token — let authService.refreshToken() handle the error
        }
        log.info("Received token refresh request");
        AuthResponse response = authService.refreshToken(request.getRefreshToken());
        log.info("Successfully refreshed token for user: {}", response.getUser().getUsername());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/me")
    @Operation(summary = "Get current user",
            description = "Retrieve information about the currently authenticated user",
            security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<CurrentUserResponse> getCurrentUser(Authentication authentication) {
        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
        String userId = userDetails.getUsername();
        return ResponseEntity.ok(authService.getCurrentUserResponse(userId));
    }

    /**
     * Logs out the current user by revoking their access and refresh tokens.
     * Accepts an optional refresh token in the request body for full revocation.
     * After this call, both tokens are immediately invalid regardless of their expiry.
     */
    @PostMapping("/logout")
    @Operation(summary = "Logout",
            description = "Revoke the current access token and optionally the refresh token.",
            security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<Void> logout(
            Authentication authentication,
            @RequestHeader("Authorization") String authHeader,
            @RequestBody(required = false) RefreshTokenRequest refreshRequest) {

        String accessToken = null;
        if (StringUtils.hasText(authHeader) && authHeader.startsWith("Bearer ")) {
            accessToken = authHeader.substring(7);
        }

        String refreshToken = (refreshRequest != null) ? refreshRequest.getRefreshToken() : null;
        authService.logout(accessToken, refreshToken);

        log.info("User {} logged out", ((UserDetails) authentication.getPrincipal()).getUsername());
        return ResponseEntity.noContent().build();
    }

    /**
     * Extract client IP safely.
     * X-Forwarded-For is only trusted when the direct connection comes from
     * a private/loopback address (i.e., a trusted reverse proxy like nginx).
     * Direct connections from public IPs use remoteAddr directly.
     */
    private String getClientIp(HttpServletRequest request) {
        String remoteAddr = request.getRemoteAddr();
        boolean fromTrustedProxy = isTrustedProxy(remoteAddr);

        if (fromTrustedProxy) {
            String xff = request.getHeader("X-Forwarded-For");
            if (StringUtils.hasText(xff)) {
                // Take the first (leftmost) IP — the original client
                return xff.split(",")[0].trim();
            }
        }
        return remoteAddr;
    }

    private boolean isTrustedProxy(String remoteAddr) {
        if (remoteAddr == null) return false;
        // Trust loopback and RFC-1918 private ranges (nginx on same host or internal LB)
        return remoteAddr.equals("127.0.0.1")
                || remoteAddr.equals("::1")
                || remoteAddr.startsWith("10.")
                || remoteAddr.startsWith("172.16.")
                || remoteAddr.startsWith("172.17.")
                || remoteAddr.startsWith("172.18.")
                || remoteAddr.startsWith("172.19.")
                || remoteAddr.startsWith("172.2")
                || remoteAddr.startsWith("172.30.")
                || remoteAddr.startsWith("172.31.")
                || remoteAddr.startsWith("192.168.");
    }
}
