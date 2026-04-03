package com.shipspace.controller;

import com.shipspace.dto.AuthRequest;
import com.shipspace.dto.AuthResponse;
import com.shipspace.dto.CurrentUserResponse;
import com.shipspace.dto.RefreshTokenRequest;
import com.shipspace.exception.ForbiddenException;
import com.shipspace.service.AuthService;
import com.shipspace.service.RateLimitingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

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

    // 10 auth attempts per minute per IP — prevents brute-force
    private static final int AUTH_RATE_LIMIT = 10;
    private static final int AUTH_WINDOW_SECS = 60;

    @PostMapping("/github")
    @Operation(summary = "Authenticate with GitHub OAuth",
            description = "Exchange GitHub OAuth authorization code for JWT tokens")
    public ResponseEntity<AuthResponse> authenticateWithGitHub(
            @Valid @RequestBody AuthRequest request,
            HttpServletRequest httpRequest) {
        String clientIp = getClientIp(httpRequest);
        if (rateLimitingService.isRateLimited(clientIp, "auth:github", AUTH_RATE_LIMIT, AUTH_WINDOW_SECS)) {
            throw new ForbiddenException("Too many authentication attempts. Please try again later.");
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
            HttpServletRequest httpRequest) {
        String clientIp = getClientIp(httpRequest);
        if (rateLimitingService.isRateLimited(clientIp, "auth:refresh", AUTH_RATE_LIMIT, AUTH_WINDOW_SECS)) {
            throw new ForbiddenException("Too many refresh attempts. Please try again later.");
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

    private String getClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(xff)) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
