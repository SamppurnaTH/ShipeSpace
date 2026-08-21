package com.shipspace.controller;

import com.shipspace.dto.AuthRequest;
import com.shipspace.dto.AuthResponse;
import com.shipspace.dto.CurrentUserResponse;
import com.shipspace.dto.RefreshTokenRequest;
import com.shipspace.exception.GitHubOAuthException;
import com.shipspace.exception.InvalidTokenException;
import com.shipspace.security.OAuthStateService;
import com.shipspace.service.AuthService;
import com.shipspace.service.RateLimitingService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/**
 * Unit tests for AuthController.
 * Tests authentication endpoints including GitHub OAuth, token refresh, and current user retrieval.
 * 
 * Requirements tested: 1.1, 1.3, 1.4, 1.5
 */
@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private AuthService authService;

    @Mock
    private Authentication authentication;

    @Mock
    private UserDetails userDetails;

    @Mock
    private RateLimitingService rateLimitingService;

    @Mock
    private OAuthStateService oAuthStateService;

    @Mock
    private HttpServletRequest httpRequest;

    @Mock
    private HttpServletResponse httpResponse;

    @InjectMocks
    private AuthController authController;

    @Test
    void getOAuthState_GeneratesServerStateToken() {
        // Arrange
        when(oAuthStateService.generateState()).thenReturn("server-generated-state");

        // Act
        ResponseEntity<Map<String, String>> response =
                authController.getOAuthState(httpRequest, httpResponse);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody()).containsEntry("state", "server-generated-state");

        verify(oAuthStateService).generateState();
        verifyNoInteractions(authService);
    }

    @Test
    void authenticateWithGitHub_ValidCode_ReturnsAuthResponse() {
        // Arrange
        String oauthCode = "valid-oauth-code";
        AuthRequest request = AuthRequest.builder()
                .code(oauthCode)
                .state("valid-state")
                .build();

        AuthResponse expectedResponse = AuthResponse.builder()
                .accessToken("jwt-access-token")
                .refreshToken("jwt-refresh-token")
                .tokenType("Bearer")
                .expiresIn(3600000L)
                .user(AuthResponse.UserInfo.builder()
                        .id("user-id")
                        .username("testuser")
                        .email("test@example.com")
                        .avatarUrl("https://github.com/avatar.png")
                        .build())
                .build();

        when(oAuthStateService.validateAndConsume("valid-state")).thenReturn(true);
        when(authService.authenticateWithGitHub(oauthCode)).thenReturn(expectedResponse);

        // Act
        ResponseEntity<AuthResponse> response = authController.authenticateWithGitHub(request, httpRequest, httpResponse);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getAccessToken()).isEqualTo("jwt-access-token");
        assertThat(response.getBody().getRefreshToken()).isEqualTo("jwt-refresh-token");
        assertThat(response.getBody().getTokenType()).isEqualTo("Bearer");
        assertThat(response.getBody().getExpiresIn()).isEqualTo(3600000L);
        assertThat(response.getBody().getUser().getId()).isEqualTo("user-id");
        assertThat(response.getBody().getUser().getUsername()).isEqualTo("testuser");
        assertThat(response.getBody().getUser().getEmail()).isEqualTo("test@example.com");
        assertThat(response.getBody().getUser().getAvatarUrl()).isEqualTo("https://github.com/avatar.png");

        verify(authService).authenticateWithGitHub(oauthCode);
    }

    @Test
    void authenticateWithGitHub_InvalidState_RejectsBeforeGitHubExchange() {
        // Arrange
        AuthRequest request = AuthRequest.builder()
                .code("valid-oauth-code")
                .state("invalid-state")
                .build();

        when(oAuthStateService.validateAndConsume("invalid-state")).thenReturn(false);

        // Act & Assert
        assertThatThrownBy(() -> authController.authenticateWithGitHub(request, httpRequest, httpResponse))
                .isInstanceOf(GitHubOAuthException.class)
                .hasMessage("Invalid or expired OAuth state token. Please restart the login flow.");

        verify(oAuthStateService).validateAndConsume("invalid-state");
        verifyNoInteractions(authService);
    }

    @Test
    void authenticateWithGitHub_BlankState_RejectsBeforeGitHubExchange() {
        // Arrange
        AuthRequest request = AuthRequest.builder()
                .code("valid-oauth-code")
                .state(" ")
                .build();

        when(oAuthStateService.validateAndConsume(" ")).thenReturn(false);

        // Act & Assert
        assertThatThrownBy(() -> authController.authenticateWithGitHub(request, httpRequest, httpResponse))
                .isInstanceOf(GitHubOAuthException.class)
                .hasMessage("Invalid or expired OAuth state token. Please restart the login flow.");

        verify(oAuthStateService).validateAndConsume(" ");
        verifyNoInteractions(authService);
    }

    @Test
    void authenticateWithGitHub_InvalidCode_ThrowsException() {
        // Arrange
        String invalidCode = "invalid-oauth-code";
        AuthRequest request = AuthRequest.builder()
                .code(invalidCode)
                .state("valid-state")
                .build();

        when(oAuthStateService.validateAndConsume("valid-state")).thenReturn(true);
        when(authService.authenticateWithGitHub(invalidCode))
                .thenThrow(new GitHubOAuthException("Invalid OAuth code"));

        // Act & Assert
        assertThatThrownBy(() -> authController.authenticateWithGitHub(request, httpRequest, httpResponse))
                .isInstanceOf(GitHubOAuthException.class)
                .hasMessage("Invalid OAuth code");

        verify(authService).authenticateWithGitHub(invalidCode);
    }

    @Test
    void refreshToken_ValidRefreshToken_ReturnsNewAuthResponse() {
        // Arrange
        String validRefreshToken = "valid-refresh-token";
        RefreshTokenRequest request = RefreshTokenRequest.builder()
                .refreshToken(validRefreshToken)
                .build();

        AuthResponse expectedResponse = AuthResponse.builder()
                .accessToken("new-jwt-access-token")
                .refreshToken("new-jwt-refresh-token")
                .tokenType("Bearer")
                .expiresIn(3600000L)
                .user(AuthResponse.UserInfo.builder()
                        .id("user-id")
                        .username("testuser")
                        .email("test@example.com")
                        .avatarUrl("https://github.com/avatar.png")
                        .build())
                .build();

        when(authService.extractUserId(validRefreshToken)).thenReturn("user-id");
        when(authService.refreshToken(validRefreshToken)).thenReturn(expectedResponse);

        // Act
        ResponseEntity<AuthResponse> response = authController.refreshToken(request, httpRequest, httpResponse);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getAccessToken()).isEqualTo("new-jwt-access-token");
        assertThat(response.getBody().getRefreshToken()).isEqualTo("new-jwt-refresh-token");
        assertThat(response.getBody().getTokenType()).isEqualTo("Bearer");
        assertThat(response.getBody().getUser().getUsername()).isEqualTo("testuser");

        verify(authService).refreshToken(validRefreshToken);
    }

    @Test
    void refreshToken_InvalidRefreshToken_ThrowsException() {
        // Arrange
        String invalidRefreshToken = "invalid-refresh-token";
        RefreshTokenRequest request = RefreshTokenRequest.builder()
                .refreshToken(invalidRefreshToken)
                .build();

        when(authService.extractUserId(invalidRefreshToken)).thenReturn("user-id");
        when(authService.refreshToken(invalidRefreshToken))
                .thenThrow(new InvalidTokenException("Invalid or expired refresh token"));

        // Act & Assert
        assertThatThrownBy(() -> authController.refreshToken(request, httpRequest, httpResponse))
                .isInstanceOf(InvalidTokenException.class)
                .hasMessage("Invalid or expired refresh token");

        verify(authService).refreshToken(invalidRefreshToken);
    }

    @Test
    void getCurrentUser_ValidAuthentication_ReturnsUserInfo() {
        // Arrange
        String userId = "user-id";
        when(authentication.getPrincipal()).thenReturn(userDetails);
        when(userDetails.getUsername()).thenReturn(userId);
        CurrentUserResponse expectedResponse = CurrentUserResponse.builder()
                .id(userId)
                .username("testuser")
                .email("test@example.com")
                .followerCount(10)
                .followingCount(5)
                .projectCount(3)
                .build();
        when(authService.getCurrentUserResponse(userId)).thenReturn(expectedResponse);

        // Act
        ResponseEntity<CurrentUserResponse> response = authController.getCurrentUser(authentication);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getId()).isEqualTo(userId);
        assertThat(response.getBody().getUsername()).isEqualTo("testuser");
        assertThat(response.getBody().getEmail()).isEqualTo("test@example.com");
        assertThat(response.getBody().getFollowerCount()).isEqualTo(10);
        assertThat(response.getBody().getFollowingCount()).isEqualTo(5);
        assertThat(response.getBody().getProjectCount()).isEqualTo(3);

        verify(authService).getCurrentUserResponse(userId);
    }

    @Test
    void getCurrentUser_UserNotFound_ThrowsException() {
        // Arrange
        String userId = "non-existent-user-id";
        when(authentication.getPrincipal()).thenReturn(userDetails);
        when(userDetails.getUsername()).thenReturn(userId);
        when(authService.getCurrentUserResponse(userId))
                .thenThrow(new InvalidTokenException("User not found"));

        // Act & Assert
        assertThatThrownBy(() -> authController.getCurrentUser(authentication))
                .isInstanceOf(InvalidTokenException.class)
                .hasMessage("User not found");

        verify(authService).getCurrentUserResponse(userId);
    }
}
