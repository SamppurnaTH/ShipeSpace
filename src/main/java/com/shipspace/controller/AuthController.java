package com.shipspace.controller;

import com.shipspace.dto.AuthRequest;
import com.shipspace.dto.AuthResponse;
import com.shipspace.dto.CurrentUserResponse;
import com.shipspace.dto.RefreshTokenRequest;
import com.shipspace.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for authentication endpoints.
 * Handles GitHub OAuth authentication, token refresh, and current user retrieval.
 * 
 * This controller implements Requirements 1.1, 1.3, 1.4, and 1.5:
 * - POST /api/auth/github - Exchange GitHub OAuth code for JWT tokens
 * - POST /api/auth/refresh - Refresh JWT access token
 * - GET /api/auth/me - Get current authenticated user information
 */
@Slf4j
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Authentication and authorization endpoints")
public class AuthController {

    private final AuthService authService;

    /**
     * Authenticates a user with GitHub OAuth code.
     * Validates the code with GitHub, creates/retrieves the user, and returns JWT tokens.
     *
     * @param request the authentication request containing the OAuth code
     * @return AuthResponse with access token, refresh token, and user information
     */
    @PostMapping("/github")
    @Operation(
        summary = "Authenticate with GitHub OAuth",
        description = "Exchange GitHub OAuth authorization code for JWT tokens"
    )
    public ResponseEntity<AuthResponse> authenticateWithGitHub(@Valid @RequestBody AuthRequest request) {
        log.info("Received GitHub OAuth authentication request");
        
        AuthResponse response = authService.authenticateWithGitHub(request.getCode());
        
        log.info("Successfully authenticated user: {}", response.getUser().getUsername());
        return ResponseEntity.ok(response);
    }

    /**
     * Refreshes the JWT access token using a valid refresh token.
     *
     * @param request the refresh token request
     * @return AuthResponse with new access token and refresh token
     */
    @PostMapping("/refresh")
    @Operation(
        summary = "Refresh JWT token",
        description = "Generate new access token using a valid refresh token"
    )
    public ResponseEntity<AuthResponse> refreshToken(@Valid @RequestBody RefreshTokenRequest request) {
        log.info("Received token refresh request");
        
        AuthResponse response = authService.refreshToken(request.getRefreshToken());
        
        log.info("Successfully refreshed token for user: {}", response.getUser().getUsername());
        return ResponseEntity.ok(response);
    }

    /**
     * Gets the current authenticated user's information.
     *
     * @param authentication the Spring Security authentication object
     * @return the current user's information
     */
    @GetMapping("/me")
    @Operation(
        summary = "Get current user",
        description = "Retrieve information about the currently authenticated user",
        security = @SecurityRequirement(name = "bearerAuth")
    )
    public ResponseEntity<CurrentUserResponse> getCurrentUser(Authentication authentication) {
        log.debug("Received request for current user information");
        
        // Extract user ID from authentication
        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
        String userId = userDetails.getUsername(); // We store userId in username field
        
        CurrentUserResponse user = authService.getCurrentUserResponse(userId);
        
        log.debug("Returning current user information for: {}", user.getUsername());
        return ResponseEntity.ok(user);
    }
}
