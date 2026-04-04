package com.shipspace.service;

import com.shipspace.dto.GitHubAccessTokenResponse;
import com.shipspace.dto.GitHubUserResponse;
import com.shipspace.exception.GitHubOAuthException;
import com.shipspace.model.User;
import com.shipspace.repository.UserRepository;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Instant;
import java.util.Optional;

/**
 * Service for handling GitHub OAuth authentication flow.
 * Validates OAuth codes with GitHub API and manages user data retrieval and mapping.
 * 
 * This service implements Requirements 1.1 and 1.2:
 * - Validates GitHub OAuth codes with GitHub API
 * - Creates or retrieves user documents in MongoDB
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GitHubOAuthService {

    private final UserRepository userRepository;
    private final WebClient.Builder webClientBuilder;

    @Value("${github.oauth.client-id}")
    private String clientId;

    @Value("${github.oauth.client-secret}")
    private String clientSecret;

    @Value("${github.oauth.redirect-uri}")
    private String redirectUri;

    @Value("${github.oauth.token-url}")
    private String tokenUrl;

    @Value("${github.oauth.user-url}")
    private String userUrl;

    /**
     * Authenticates a user with GitHub OAuth code.
     * Protected by a circuit breaker — if GitHub API is down, the circuit opens
     * and subsequent calls fail fast with GitHubOAuthException instead of timing out.
     */
    @CircuitBreaker(name = "github-oauth", fallbackMethod = "githubAuthFallback")
    @Retry(name = "external-api")
    public User authenticateWithGitHub(String code) {
        log.debug("Starting GitHub OAuth authentication with code: {}", code);

        try {
            // Step 1: Exchange OAuth code for access token
            String accessToken = exchangeCodeForToken(code);
            log.debug("Successfully obtained access token from GitHub");

            // Step 2: Retrieve user data from GitHub
            GitHubUserResponse githubUser = fetchGitHubUserData(accessToken);
            log.debug("Successfully retrieved GitHub user data for user: {}", githubUser.getLogin());

            // Step 3: Create or update user in database
            User user = createOrUpdateUser(githubUser);
            log.info("User authenticated successfully: {} (GitHub ID: {})", user.getUsername(), user.getGithubId());

            return user;

        } catch (WebClientResponseException e) {
            log.error("GitHub API request failed with status {}: {}", e.getStatusCode(), e.getMessage());
            throw new GitHubOAuthException("Failed to authenticate with GitHub: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("Unexpected error during GitHub OAuth authentication", e);
            throw new GitHubOAuthException("GitHub OAuth authentication failed: " + e.getMessage(), e);
        }
    }

    /**
     * Exchanges the GitHub OAuth authorization code for an access token.
     *
     * @param code the OAuth authorization code
     * @return the access token string
     * @throws GitHubOAuthException if token exchange fails
     */
    private String exchangeCodeForToken(String code) {
        log.debug("Exchanging OAuth code for access token");

        // Prepare request parameters
        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("client_id", clientId);
        formData.add("client_secret", clientSecret);
        formData.add("code", code);
        formData.add("redirect_uri", redirectUri);

        try {
            // Make POST request to GitHub token endpoint
            GitHubAccessTokenResponse response = webClientBuilder.build()
                    .post()
                    .uri(tokenUrl)
                    .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                    .body(BodyInserters.fromFormData(formData))
                    .retrieve()
                    .bodyToMono(GitHubAccessTokenResponse.class)
                    .block();

            if (response == null || response.getAccessToken() == null) {
                throw new GitHubOAuthException("GitHub token exchange returned null response");
            }

            return response.getAccessToken();

        } catch (WebClientResponseException e) {
            log.error("Failed to exchange OAuth code for token: {}", e.getMessage());
            throw new GitHubOAuthException("Failed to exchange OAuth code for access token", e);
        }
    }

    /**
     * Fetches user data from GitHub API using the access token.
     *
     * @param accessToken the GitHub access token
     * @return GitHubUserResponse containing user data
     * @throws GitHubOAuthException if user data retrieval fails
     */
    private GitHubUserResponse fetchGitHubUserData(String accessToken) {
        log.debug("Fetching user data from GitHub API");

        try {
            GitHubUserResponse response = webClientBuilder.build()
                    .get()
                    .uri(userUrl)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                    .retrieve()
                    .bodyToMono(GitHubUserResponse.class)
                    .block();

            if (response == null) {
                throw new GitHubOAuthException("GitHub user data request returned null response");
            }

            return response;

        } catch (WebClientResponseException e) {
            log.error("Failed to fetch GitHub user data: {}", e.getMessage());
            throw new GitHubOAuthException("Failed to retrieve user data from GitHub", e);
        }
    }

    /**
     * Creates a new user or updates an existing user based on GitHub data.
     * Maps GitHub user information to the User entity and persists to MongoDB.
     *
     * @param githubUser the GitHub user data
     * @return the created or updated User entity
     */
    private User createOrUpdateUser(GitHubUserResponse githubUser) {
        String githubId = String.valueOf(githubUser.getId());
        log.debug("Creating or updating user with GitHub ID: {}", githubId);

        // Check if user already exists
        Optional<User> existingUser = userRepository.findByGithubId(githubId);

        if (existingUser.isPresent()) {
            // Update existing user
            User user = existingUser.get();
            log.debug("Updating existing user: {}", user.getUsername());
            
            updateUserFromGitHub(user, githubUser);
            user.setUpdatedAt(Instant.now());
            
            return userRepository.save(user);
        } else {
            // Create new user
            log.debug("Creating new user from GitHub data");
            
            User newUser = mapGitHubUserToUser(githubUser);
            newUser.setCreatedAt(Instant.now());
            newUser.setUpdatedAt(Instant.now());
            
            return userRepository.save(newUser);
        }
    }

    /**
     * Maps GitHub user data to a new User entity.
     *
     * @param githubUser the GitHub user data
     * @return a new User entity with mapped data
     */
    private User mapGitHubUserToUser(GitHubUserResponse githubUser) {
        return User.builder()
                .githubId(String.valueOf(githubUser.getId()))
                .username(githubUser.getLogin())
                .email(githubUser.getEmail())
                .avatarUrl(githubUser.getAvatarUrl())
                .bio(githubUser.getBio())
                .location(githubUser.getLocation())
                .websiteUrl(githubUser.getBlog())
                .followerCount(0)
                .followingCount(0)
                .projectCount(0)
                .build();
    }

    /**
     * Updates an existing user entity with fresh data from GitHub.
     *
     * @param user the existing user entity
     * @param githubUser the GitHub user data
     */
    private void updateUserFromGitHub(User user, GitHubUserResponse githubUser) {
        // Update fields that might have changed on GitHub
        user.setEmail(githubUser.getEmail());
        user.setAvatarUrl(githubUser.getAvatarUrl());
        
        // Only update bio, location, and website if they're not null from GitHub
        if (githubUser.getBio() != null) {
            user.setBio(githubUser.getBio());
        }
        if (githubUser.getLocation() != null) {
            user.setLocation(githubUser.getLocation());
        }
        if (githubUser.getBlog() != null) {
            user.setWebsiteUrl(githubUser.getBlog());
        }
    }

    /**
     * Retrieves a user by their GitHub ID.
     * Useful for validating existing users during authentication.
     *
     * @param githubId the GitHub user ID
     * @return Optional containing the user if found
     */
    public Optional<User> getUserByGithubId(String githubId) {
        return userRepository.findByGithubId(githubId);
    }

    /**
     * Circuit breaker fallback — GitHub API is unavailable.
     * Throws a clear exception so the client gets a 503-style error, not a timeout.
     */
    private User githubAuthFallback(String code, Throwable t) {
        log.error("GitHub OAuth circuit breaker open — GitHub API unavailable: {}", t.getMessage());
        throw new GitHubOAuthException(
                "GitHub authentication is temporarily unavailable. Please try again later.");
    }
}
