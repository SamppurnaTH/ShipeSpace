package com.shipspace.service;

import com.shipspace.dto.GitHubAccessTokenResponse;
import com.shipspace.dto.GitHubUserResponse;
import com.shipspace.exception.GitHubOAuthException;
import com.shipspace.model.User;
import com.shipspace.repository.UserRepository;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for GitHubOAuthService.
 * Tests OAuth code validation, user data retrieval, and user creation/update logic.
 */
@ExtendWith(MockitoExtension.class)
class GitHubOAuthServiceTest {

    @Mock
    private UserRepository userRepository;

    private GitHubOAuthService githubOAuthService;
    private MockWebServer mockWebServer;

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();

        WebClient.Builder webClientBuilder = WebClient.builder();
        githubOAuthService = new GitHubOAuthService(userRepository, webClientBuilder);

        // Set configuration values using reflection
        String baseUrl = mockWebServer.url("/").toString();
        ReflectionTestUtils.setField(githubOAuthService, "clientId", "test-client-id");
        ReflectionTestUtils.setField(githubOAuthService, "clientSecret", "test-client-secret");
        ReflectionTestUtils.setField(githubOAuthService, "redirectUri", "http://localhost:3000/callback");
        ReflectionTestUtils.setField(githubOAuthService, "tokenUrl", baseUrl + "login/oauth/access_token");
        ReflectionTestUtils.setField(githubOAuthService, "userUrl", baseUrl + "user");
        ReflectionTestUtils.setField(githubOAuthService, "adminGithubIds", "");
    }

    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    @Test
    void authenticateWithGitHub_NewUser_CreatesUserSuccessfully() {
        // Arrange
        String oauthCode = "test-oauth-code";
        String accessToken = "gho_test_access_token";
        
        // Mock GitHub token response
        mockWebServer.enqueue(new MockResponse()
                .setBody("{\"access_token\":\"" + accessToken + "\",\"token_type\":\"bearer\",\"scope\":\"read:user\"}")
                .addHeader("Content-Type", "application/json"));

        // Mock GitHub user response
        mockWebServer.enqueue(new MockResponse()
                .setBody("{\"id\":12345,\"login\":\"testuser\",\"email\":\"test@example.com\"," +
                        "\"avatar_url\":\"https://github.com/avatar.png\",\"bio\":\"Test bio\"," +
                        "\"location\":\"Test City\",\"blog\":\"https://test.com\"}")
                .addHeader("Content-Type", "application/json"));

        // Mock repository - user doesn't exist
        when(userRepository.findByGithubId("12345")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId("generated-id");
            return user;
        });

        // Act
        User result = githubOAuthService.authenticateWithGitHub(oauthCode);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getGithubId()).isEqualTo("12345");
        assertThat(result.getUsername()).isEqualTo("testuser");
        assertThat(result.getEmail()).isEqualTo("test@example.com");
        assertThat(result.getAvatarUrl()).isEqualTo("https://github.com/avatar.png");
        assertThat(result.getBio()).isEqualTo("Test bio");
        assertThat(result.getLocation()).isEqualTo("Test City");
        assertThat(result.getWebsiteUrl()).isEqualTo("https://test.com");
        assertThat(result.getFollowerCount()).isEqualTo(0);
        assertThat(result.getFollowingCount()).isEqualTo(0);
        assertThat(result.getProjectCount()).isEqualTo(0);
        assertThat(result.getRoles()).containsExactly("USER");

        verify(userRepository).findByGithubId("12345");
        verify(userRepository).save(any(User.class));
    }

    @Test
    void authenticateWithGitHub_ConfiguredAdminGithubId_AddsAdminRole() {
        // Arrange
        String oauthCode = "test-oauth-code";
        String accessToken = "gho_test_access_token";
        ReflectionTestUtils.setField(githubOAuthService, "adminGithubIds", "12345,99999");

        mockWebServer.enqueue(new MockResponse()
                .setBody("{\"access_token\":\"" + accessToken + "\",\"token_type\":\"bearer\",\"scope\":\"read:user\"}")
                .addHeader("Content-Type", "application/json"));

        mockWebServer.enqueue(new MockResponse()
                .setBody("{\"id\":12345,\"login\":\"adminuser\",\"email\":\"admin@example.com\"," +
                        "\"avatar_url\":\"https://github.com/admin.png\"}")
                .addHeader("Content-Type", "application/json"));

        when(userRepository.findByGithubId("12345")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId("generated-admin-id");
            return user;
        });

        // Act
        User result = githubOAuthService.authenticateWithGitHub(oauthCode);

        // Assert
        assertThat(result.getGithubId()).isEqualTo("12345");
        assertThat(result.getRoles()).containsExactlyInAnyOrder("USER", "ADMIN");

        verify(userRepository).findByGithubId("12345");
        verify(userRepository).save(any(User.class));
    }

    @Test
    void authenticateWithGitHub_AdminLikeUsernameWithoutConfiguredNumericId_DoesNotAddAdminRole() {
        // Arrange
        String oauthCode = "test-oauth-code";
        String accessToken = "gho_test_access_token";
        ReflectionTestUtils.setField(githubOAuthService, "adminGithubIds", "12345");

        mockWebServer.enqueue(new MockResponse()
                .setBody("{\"access_token\":\"" + accessToken + "\",\"token_type\":\"bearer\",\"scope\":\"read:user\"}")
                .addHeader("Content-Type", "application/json"));

        mockWebServer.enqueue(new MockResponse()
                .setBody("{\"id\":67890,\"login\":\"12345\",\"email\":\"normal@example.com\"," +
                        "\"avatar_url\":\"https://github.com/normal.png\"}")
                .addHeader("Content-Type", "application/json"));

        when(userRepository.findByGithubId("67890")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId("normal-user-id");
            return user;
        });

        // Act
        User result = githubOAuthService.authenticateWithGitHub(oauthCode);

        // Assert
        assertThat(result.getGithubId()).isEqualTo("67890");
        assertThat(result.getUsername()).isEqualTo("12345");
        assertThat(result.getRoles()).containsExactly("USER");
    }

    @Test
    void authenticateWithGitHub_ConfiguredAdminGithubId_TrimsWhitespaceAndKeepsUserRole() {
        // Arrange
        String oauthCode = "test-oauth-code";
        String accessToken = "gho_test_access_token";
        ReflectionTestUtils.setField(githubOAuthService, "adminGithubIds", " 11111 , 12345 , 99999 ");

        mockWebServer.enqueue(new MockResponse()
                .setBody("{\"access_token\":\"" + accessToken + "\",\"token_type\":\"bearer\",\"scope\":\"read:user\"}")
                .addHeader("Content-Type", "application/json"));

        mockWebServer.enqueue(new MockResponse()
                .setBody("{\"id\":12345,\"login\":\"adminuser\",\"email\":\"admin@example.com\"," +
                        "\"avatar_url\":\"https://github.com/admin.png\"}")
                .addHeader("Content-Type", "application/json"));

        when(userRepository.findByGithubId("12345")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId("generated-admin-id");
            return user;
        });

        // Act
        User result = githubOAuthService.authenticateWithGitHub(oauthCode);

        // Assert
        assertThat(result.getRoles()).containsExactlyInAnyOrder("USER", "ADMIN");
    }

    @Test
    void authenticateWithGitHub_ExistingUserAddedToAdminConfig_GainsAdminOnNextLogin() {
        // Arrange
        String oauthCode = "test-oauth-code";
        String accessToken = "gho_test_access_token";
        ReflectionTestUtils.setField(githubOAuthService, "adminGithubIds", "12345");

        mockWebServer.enqueue(new MockResponse()
                .setBody("{\"access_token\":\"" + accessToken + "\",\"token_type\":\"bearer\",\"scope\":\"read:user\"}")
                .addHeader("Content-Type", "application/json"));

        mockWebServer.enqueue(new MockResponse()
                .setBody("{\"id\":12345,\"login\":\"builder\",\"email\":\"builder@example.com\"," +
                        "\"avatar_url\":\"https://github.com/builder.png\"}")
                .addHeader("Content-Type", "application/json"));

        User existingUser = User.builder()
                .id("existing-id")
                .githubId("12345")
                .username("builder")
                .roles(java.util.Set.of("USER"))
                .build();
        when(userRepository.findByGithubId("12345")).thenReturn(Optional.of(existingUser));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        User result = githubOAuthService.authenticateWithGitHub(oauthCode);

        // Assert
        assertThat(result.getRoles()).containsExactlyInAnyOrder("USER", "ADMIN");
    }

    @Test
    void authenticateWithGitHub_ExistingAdminRemovedFromConfig_RetainsPersistedAdminRole() {
        // Arrange
        String oauthCode = "test-oauth-code";
        String accessToken = "gho_test_access_token";
        ReflectionTestUtils.setField(githubOAuthService, "adminGithubIds", "");

        mockWebServer.enqueue(new MockResponse()
                .setBody("{\"access_token\":\"" + accessToken + "\",\"token_type\":\"bearer\",\"scope\":\"read:user\"}")
                .addHeader("Content-Type", "application/json"));

        mockWebServer.enqueue(new MockResponse()
                .setBody("{\"id\":12345,\"login\":\"adminuser\",\"email\":\"admin@example.com\"," +
                        "\"avatar_url\":\"https://github.com/admin.png\"}")
                .addHeader("Content-Type", "application/json"));

        User existingUser = User.builder()
                .id("existing-admin-id")
                .githubId("12345")
                .username("adminuser")
                .roles(java.util.Set.of("USER", "ADMIN"))
                .build();
        when(userRepository.findByGithubId("12345")).thenReturn(Optional.of(existingUser));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        User result = githubOAuthService.authenticateWithGitHub(oauthCode);

        // Assert
        assertThat(result.getRoles()).containsExactlyInAnyOrder("USER", "ADMIN");
    }

    @Test
    void authenticateWithGitHub_ExistingUser_UpdatesUserSuccessfully() {
        // Arrange
        String oauthCode = "test-oauth-code";
        String accessToken = "gho_test_access_token";

        // Mock GitHub token response
        mockWebServer.enqueue(new MockResponse()
                .setBody("{\"access_token\":\"" + accessToken + "\",\"token_type\":\"bearer\",\"scope\":\"read:user\"}")
                .addHeader("Content-Type", "application/json"));

        // Mock GitHub user response with updated data
        mockWebServer.enqueue(new MockResponse()
                .setBody("{\"id\":12345,\"login\":\"testuser\",\"email\":\"newemail@example.com\"," +
                        "\"avatar_url\":\"https://github.com/newavatar.png\",\"bio\":\"Updated bio\"," +
                        "\"location\":\"New City\",\"blog\":\"https://newsite.com\"}")
                .addHeader("Content-Type", "application/json"));

        // Mock repository - user exists
        User existingUser = User.builder()
                .id("existing-id")
                .githubId("12345")
                .username("testuser")
                .email("old@example.com")
                .avatarUrl("https://github.com/oldavatar.png")
                .bio("Old bio")
                .followerCount(5)
                .followingCount(10)
                .projectCount(3)
                .build();

        when(userRepository.findByGithubId("12345")).thenReturn(Optional.of(existingUser));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        User result = githubOAuthService.authenticateWithGitHub(oauthCode);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo("existing-id");
        assertThat(result.getGithubId()).isEqualTo("12345");
        assertThat(result.getUsername()).isEqualTo("testuser");
        assertThat(result.getEmail()).isEqualTo("newemail@example.com"); // Updated
        assertThat(result.getAvatarUrl()).isEqualTo("https://github.com/newavatar.png"); // Updated
        assertThat(result.getBio()).isEqualTo("Updated bio"); // Updated
        assertThat(result.getLocation()).isEqualTo("New City"); // Updated
        assertThat(result.getWebsiteUrl()).isEqualTo("https://newsite.com"); // Updated
        assertThat(result.getFollowerCount()).isEqualTo(5); // Preserved
        assertThat(result.getFollowingCount()).isEqualTo(10); // Preserved
        assertThat(result.getProjectCount()).isEqualTo(3); // Preserved

        verify(userRepository).findByGithubId("12345");
        verify(userRepository).save(any(User.class));
    }

    @Test
    void authenticateWithGitHub_InvalidOAuthCode_ThrowsException() {
        // Arrange
        String invalidCode = "invalid-code";

        // Mock GitHub token response with error
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(401)
                .setBody("{\"error\":\"bad_verification_code\"}")
                .addHeader("Content-Type", "application/json"));

        // Act & Assert
        assertThatThrownBy(() -> githubOAuthService.authenticateWithGitHub(invalidCode))
                .isInstanceOf(GitHubOAuthException.class)
                .hasMessageContaining("GitHub OAuth authentication failed");

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void authenticateWithGitHub_GitHubUserApiFailure_ThrowsException() {
        // Arrange
        String oauthCode = "test-oauth-code";
        String accessToken = "gho_test_access_token";

        // Mock successful token response
        mockWebServer.enqueue(new MockResponse()
                .setBody("{\"access_token\":\"" + accessToken + "\",\"token_type\":\"bearer\",\"scope\":\"read:user\"}")
                .addHeader("Content-Type", "application/json"));

        // Mock failed user data response
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(403)
                .setBody("{\"message\":\"Forbidden\"}")
                .addHeader("Content-Type", "application/json"));

        // Act & Assert
        assertThatThrownBy(() -> githubOAuthService.authenticateWithGitHub(oauthCode))
                .isInstanceOf(GitHubOAuthException.class)
                .hasMessageContaining("GitHub OAuth authentication failed");

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void authenticateWithGitHub_NullAccessToken_ThrowsException() {
        // Arrange
        String oauthCode = "test-oauth-code";

        // Mock GitHub token response with null access_token
        mockWebServer.enqueue(new MockResponse()
                .setBody("{\"token_type\":\"bearer\",\"scope\":\"read:user\"}")
                .addHeader("Content-Type", "application/json"));

        // Act & Assert
        assertThatThrownBy(() -> githubOAuthService.authenticateWithGitHub(oauthCode))
                .isInstanceOf(GitHubOAuthException.class)
                .hasMessageContaining("GitHub token exchange returned null response");

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void authenticateWithGitHub_PartialGitHubData_CreatesUserWithAvailableData() {
        // Arrange
        String oauthCode = "test-oauth-code";
        String accessToken = "gho_test_access_token";

        // Mock GitHub token response
        mockWebServer.enqueue(new MockResponse()
                .setBody("{\"access_token\":\"" + accessToken + "\",\"token_type\":\"bearer\",\"scope\":\"read:user\"}")
                .addHeader("Content-Type", "application/json"));

        // Mock GitHub user response with minimal data (no bio, location, blog)
        mockWebServer.enqueue(new MockResponse()
                .setBody("{\"id\":12345,\"login\":\"testuser\",\"email\":\"test@example.com\"," +
                        "\"avatar_url\":\"https://github.com/avatar.png\"}")
                .addHeader("Content-Type", "application/json"));

        // Mock repository - user doesn't exist
        when(userRepository.findByGithubId("12345")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId("generated-id");
            return user;
        });

        // Act
        User result = githubOAuthService.authenticateWithGitHub(oauthCode);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getGithubId()).isEqualTo("12345");
        assertThat(result.getUsername()).isEqualTo("testuser");
        assertThat(result.getEmail()).isEqualTo("test@example.com");
        assertThat(result.getAvatarUrl()).isEqualTo("https://github.com/avatar.png");
        assertThat(result.getBio()).isNull();
        assertThat(result.getLocation()).isNull();
        assertThat(result.getWebsiteUrl()).isNull();

        verify(userRepository).save(any(User.class));
    }

    @Test
    void getUserByGithubId_UserExists_ReturnsUser() {
        // Arrange
        String githubId = "12345";
        User expectedUser = User.builder()
                .id("user-id")
                .githubId(githubId)
                .username("testuser")
                .build();

        when(userRepository.findByGithubId(githubId)).thenReturn(Optional.of(expectedUser));

        // Act
        Optional<User> result = githubOAuthService.getUserByGithubId(githubId);

        // Assert
        assertThat(result).isPresent();
        assertThat(result.get()).isEqualTo(expectedUser);
        verify(userRepository).findByGithubId(githubId);
    }

    @Test
    void getUserByGithubId_UserDoesNotExist_ReturnsEmpty() {
        // Arrange
        String githubId = "99999";
        when(userRepository.findByGithubId(githubId)).thenReturn(Optional.empty());

        // Act
        Optional<User> result = githubOAuthService.getUserByGithubId(githubId);

        // Assert
        assertThat(result).isEmpty();
        verify(userRepository).findByGithubId(githubId);
    }
}
