package com.shipspace.service;

import com.shipspace.dto.UserProfileResponse;
import com.shipspace.dto.UserUpdateRequest;
import com.shipspace.exception.ResourceNotFoundException;
import com.shipspace.model.User;
import com.shipspace.repository.ProjectRepository;
import com.shipspace.repository.ShipRepository;
import com.shipspace.repository.UserRepository;
import net.jqwik.api.*;
import net.jqwik.api.constraints.AlphaChars;
import net.jqwik.api.constraints.StringLength;
import net.jqwik.api.lifecycle.BeforeTry;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Property-based tests for user profile management.
 * 
 * **Validates: Requirements 2.1, 2.3, 2.5**
 * 
 * These tests verify the correctness properties of the user profile system
 * across a wide range of inputs using property-based testing with jqwik.
 */
class UserProfilePropertyTest {

    private UserService userService;
    private UserRepository userRepository;
    private ProjectRepository projectRepository;
    private ShipRepository shipRepository;

    @BeforeTry
    void setUp() {
        userRepository = mock(UserRepository.class);
        projectRepository = mock(ProjectRepository.class);
        shipRepository = mock(ShipRepository.class);
        userService = new UserService(userRepository, projectRepository, shipRepository);
    }

    /**
     * Property 6: Profile data retrieval
     * 
     * For any valid JWT token, requesting user profile data should return 
     * the corresponding user's profile information from the database.
     * 
     * **Validates: Requirements 2.1**
     */
    @Property(tries = 100)
    @Label("Feature: shipspace-mvp, Property 6: Profile data retrieval")
    void profileDataRetrieval(
            @ForAll @StringLength(min = 1, max = 50) @AlphaChars String userId,
            @ForAll @StringLength(min = 3, max = 30) @AlphaChars String username,
            @ForAll @StringLength(min = 0, max = 500) String bio,
            @ForAll @StringLength(min = 0, max = 100) String location) {
        
        // Given: A user exists in the database
        User existingUser = createMockUser(userId, username, bio, location);
        when(userRepository.findById(userId)).thenReturn(Optional.of(existingUser));

        // When: Requesting the user's profile data
        UserProfileResponse profile = userService.getPublicProfile(userId);

        // Then: Should return the user's profile information
        assertThat(profile).isNotNull();
        assertThat(profile.getId()).isEqualTo(userId);
        assertThat(profile.getUsername()).isEqualTo(username);
        assertThat(profile.getBio()).isEqualTo(bio);
        assertThat(profile.getLocation()).isEqualTo(location);
        assertThat(profile.getFollowerCount()).isNotNull();
        assertThat(profile.getFollowingCount()).isNotNull();
        assertThat(profile.getProjectCount()).isNotNull();
        assertThat(profile.getCreatedAt()).isNotNull();
        
        // Verify repository was called
        verify(userRepository).findById(userId);
    }

    /**
     * Property 6 (variant): Profile retrieval returns consistent data
     * 
     * For any user, multiple profile retrievals should return the same data
     * (idempotency test).
     * 
     * **Validates: Requirements 2.1**
     */
    @Property(tries = 100)
    @Label("Feature: shipspace-mvp, Property 6: Profile retrieval consistency")
    void profileRetrievalConsistency(
            @ForAll @StringLength(min = 1, max = 50) @AlphaChars String userId,
            @ForAll @StringLength(min = 3, max = 30) @AlphaChars String username) {
        
        // Given: A user exists in the database
        User existingUser = createMockUser(userId, username, "Test bio", "Test location");
        when(userRepository.findById(userId)).thenReturn(Optional.of(existingUser));

        // When: Requesting the profile multiple times
        UserProfileResponse profile1 = userService.getPublicProfile(userId);
        UserProfileResponse profile2 = userService.getPublicProfile(userId);

        // Then: Both responses should be identical
        assertThat(profile1.getId()).isEqualTo(profile2.getId());
        assertThat(profile1.getUsername()).isEqualTo(profile2.getUsername());
        assertThat(profile1.getBio()).isEqualTo(profile2.getBio());
        assertThat(profile1.getLocation()).isEqualTo(profile2.getLocation());
        assertThat(profile1.getFollowerCount()).isEqualTo(profile2.getFollowerCount());
        assertThat(profile1.getFollowingCount()).isEqualTo(profile2.getFollowingCount());
        assertThat(profile1.getProjectCount()).isEqualTo(profile2.getProjectCount());
    }

    /**
     * Property 7: Profile update persistence
     * 
     * For any valid profile update request, the changes should be validated, 
     * stored in MongoDB, and retrievable in subsequent requests.
     * 
     * **Validates: Requirements 2.3**
     */
    @Property(tries = 100)
    @Label("Feature: shipspace-mvp, Property 7: Profile update persistence")
    void profileUpdatePersistence(
            @ForAll @StringLength(min = 1, max = 50) @AlphaChars String userId,
            @ForAll @StringLength(min = 3, max = 30) @AlphaChars String username,
            @ForAll @StringLength(min = 0, max = 500) String newBio,
            @ForAll @StringLength(min = 0, max = 100) String newLocation) {
        
        // Given: A user exists in the database
        User existingUser = createMockUser(userId, username, "Old bio", "Old location");
        when(userRepository.findById(userId)).thenReturn(Optional.of(existingUser));
        
        // Mock the save operation to return the updated user
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User savedUser = invocation.getArgument(0);
            savedUser.setUpdatedAt(Instant.now());
            return savedUser;
        });

        // When: Updating the user's profile
        UserUpdateRequest updateRequest = UserUpdateRequest.builder()
                .bio(newBio)
                .location(newLocation)
                .build();
        
        User updatedUser = userService.updateUserProfile(userId, updateRequest);

        // Then: The changes should be persisted
        assertThat(updatedUser).isNotNull();
        assertThat(updatedUser.getBio()).isEqualTo(newBio);
        assertThat(updatedUser.getLocation()).isEqualTo(newLocation);
        assertThat(updatedUser.getUpdatedAt()).isNotNull();
        
        // Verify save was called
        verify(userRepository).save(any(User.class));
        
        // Verify the user was retrieved first
        verify(userRepository).findById(userId);
    }

    /**
     * Property 7 (variant): Profile update only modifies provided fields
     * 
     * For any profile update request, only the fields provided in the request
     * should be modified, leaving other fields unchanged.
     * 
     * **Validates: Requirements 2.3**
     */
    @Property(tries = 100)
    @Label("Feature: shipspace-mvp, Property 7: Partial profile update")
    void partialProfileUpdate(
            @ForAll @StringLength(min = 1, max = 50) @AlphaChars String userId,
            @ForAll @StringLength(min = 3, max = 30) @AlphaChars String username,
            @ForAll @StringLength(min = 0, max = 500) String originalBio,
            @ForAll @StringLength(min = 0, max = 100) String originalLocation,
            @ForAll @StringLength(min = 0, max = 500) String newBio) {
        
        // Given: A user exists with original data
        User existingUser = createMockUser(userId, username, originalBio, originalLocation);
        when(userRepository.findById(userId)).thenReturn(Optional.of(existingUser));
        
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When: Updating only the bio (not location)
        UserUpdateRequest updateRequest = UserUpdateRequest.builder()
                .bio(newBio)
                .build();
        
        User updatedUser = userService.updateUserProfile(userId, updateRequest);

        // Then: Only bio should be updated, location should remain unchanged
        assertThat(updatedUser.getBio()).isEqualTo(newBio);
        assertThat(updatedUser.getLocation()).isEqualTo(originalLocation);
        assertThat(updatedUser.getUsername()).isEqualTo(username);
    }

    /**
     * Property 7 (variant): Profile update with email validation
     * 
     * For any profile update with email, the email should be stored correctly.
     * 
     * **Validates: Requirements 2.3**
     */
    @Property(tries = 100)
    @Label("Feature: shipspace-mvp, Property 7: Profile update with email")
    void profileUpdateWithEmail(
            @ForAll @StringLength(min = 1, max = 50) @AlphaChars String userId,
            @ForAll @StringLength(min = 3, max = 30) @AlphaChars String username,
            @ForAll @StringLength(min = 3, max = 20) @AlphaChars String emailPrefix) {
        
        // Given: A user exists in the database
        User existingUser = createMockUser(userId, username, "Bio", "Location");
        when(userRepository.findById(userId)).thenReturn(Optional.of(existingUser));
        
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When: Updating the user's email
        String newEmail = emailPrefix + "@example.com";
        UserUpdateRequest updateRequest = UserUpdateRequest.builder()
                .email(newEmail)
                .build();
        
        User updatedUser = userService.updateUserProfile(userId, updateRequest);

        // Then: The email should be updated
        assertThat(updatedUser.getEmail()).isEqualTo(newEmail);
        
        // Verify save was called
        verify(userRepository).save(any(User.class));
    }

    /**
     * Property 9: Non-existent user handling
     * 
     * For any non-existent user ID, profile requests should return 
     * 404 Not Found response.
     * 
     * **Validates: Requirements 2.5**
     */
    @Property(tries = 100)
    @Label("Feature: shipspace-mvp, Property 9: Non-existent user handling")
    void nonExistentUserHandling(
            @ForAll @StringLength(min = 1, max = 50) @AlphaChars String nonExistentUserId) {
        
        // Given: A user does not exist in the database
        when(userRepository.findById(nonExistentUserId)).thenReturn(Optional.empty());

        // When: Requesting a non-existent user's profile
        // Then: Should throw ResourceNotFoundException
        assertThatThrownBy(() -> userService.getPublicProfile(nonExistentUserId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("User not found");
        
        // Verify repository was called
        verify(userRepository).findById(nonExistentUserId);
    }

    /**
     * Property 9 (variant): Non-existent user handling for getUserById
     * 
     * For any non-existent user ID, getUserById should throw ResourceNotFoundException.
     * 
     * **Validates: Requirements 2.5**
     */
    @Property(tries = 100)
    @Label("Feature: shipspace-mvp, Property 9: Non-existent user in getUserById")
    void nonExistentUserInGetUserById(
            @ForAll @StringLength(min = 1, max = 50) @AlphaChars String nonExistentUserId) {
        
        // Given: A user does not exist in the database
        when(userRepository.findById(nonExistentUserId)).thenReturn(Optional.empty());

        // When: Calling getUserById with non-existent ID
        // Then: Should throw ResourceNotFoundException
        assertThatThrownBy(() -> userService.getUserById(nonExistentUserId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("User not found");
        
        verify(userRepository).findById(nonExistentUserId);
    }

    /**
     * Property 9 (variant): Non-existent user handling for profile updates
     * 
     * For any non-existent user ID, profile update requests should fail
     * with ResourceNotFoundException.
     * 
     * **Validates: Requirements 2.5**
     */
    @Property(tries = 100)
    @Label("Feature: shipspace-mvp, Property 9: Non-existent user in profile update")
    void nonExistentUserInProfileUpdate(
            @ForAll @StringLength(min = 1, max = 50) @AlphaChars String nonExistentUserId,
            @ForAll @StringLength(min = 0, max = 500) String newBio) {
        
        // Given: A user does not exist in the database
        when(userRepository.findById(nonExistentUserId)).thenReturn(Optional.empty());

        // When: Attempting to update a non-existent user's profile
        UserUpdateRequest updateRequest = UserUpdateRequest.builder()
                .bio(newBio)
                .build();

        // Then: Should throw ResourceNotFoundException
        assertThatThrownBy(() -> userService.updateUserProfile(nonExistentUserId, updateRequest))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("User not found");
        
        // Verify repository was called but save was not
        verify(userRepository).findById(nonExistentUserId);
        verify(userRepository, never()).save(any(User.class));
    }

    /**
     * Property: Profile data completeness
     * 
     * For any user profile retrieval, all required fields should be present
     * and properly formatted.
     * 
     * **Validates: Requirements 2.1, 2.4**
     */
    @Property(tries = 100)
    @Label("Feature: shipspace-mvp, Property: Profile data completeness")
    void profileDataCompleteness(
            @ForAll @StringLength(min = 1, max = 50) @AlphaChars String userId,
            @ForAll @StringLength(min = 3, max = 30) @AlphaChars String username) {
        
        // Given: A user exists with all fields populated
        User existingUser = User.builder()
                .id(userId)
                .githubId("gh_" + userId)
                .username(username)
                .email(username + "@example.com")
                .avatarUrl("https://github.com/avatar.png")
                .bio("Test bio")
                .location("Test City")
                .websiteUrl("https://test.com")
                .followerCount(10)
                .followingCount(5)
                .projectCount(3)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        
        when(userRepository.findById(userId)).thenReturn(Optional.of(existingUser));

        // When: Retrieving the public profile
        UserProfileResponse profile = userService.getPublicProfile(userId);

        // Then: All public fields should be present
        assertThat(profile.getId()).isNotNull().isEqualTo(userId);
        assertThat(profile.getUsername()).isNotNull().isEqualTo(username);
        assertThat(profile.getAvatarUrl()).isNotNull();
        assertThat(profile.getBio()).isNotNull();
        assertThat(profile.getLocation()).isNotNull();
        assertThat(profile.getWebsiteUrl()).isNotNull();
        assertThat(profile.getFollowerCount()).isNotNull().isEqualTo(10);
        assertThat(profile.getFollowingCount()).isNotNull().isEqualTo(5);
        assertThat(profile.getProjectCount()).isNotNull().isEqualTo(3);
        assertThat(profile.getCreatedAt()).isNotNull();
    }

    /**
     * Property: Profile update preserves immutable fields
     * 
     * For any profile update, immutable fields (username, githubId, createdAt)
     * should not be modified.
     * 
     * **Validates: Requirements 2.3**
     */
    @Property(tries = 100)
    @Label("Feature: shipspace-mvp, Property: Profile update preserves immutable fields")
    void profileUpdatePreservesImmutableFields(
            @ForAll @StringLength(min = 1, max = 50) @AlphaChars String userId,
            @ForAll @StringLength(min = 3, max = 30) @AlphaChars String username,
            @ForAll @StringLength(min = 0, max = 500) String newBio) {
        
        // Given: A user exists with specific immutable fields
        String originalGithubId = "gh_" + userId;
        Instant originalCreatedAt = Instant.now().minusSeconds(86400); // 1 day ago
        
        User existingUser = User.builder()
                .id(userId)
                .githubId(originalGithubId)
                .username(username)
                .email(username + "@example.com")
                .bio("Original bio")
                .createdAt(originalCreatedAt)
                .updatedAt(Instant.now())
                .build();
        
        when(userRepository.findById(userId)).thenReturn(Optional.of(existingUser));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When: Updating the profile
        UserUpdateRequest updateRequest = UserUpdateRequest.builder()
                .bio(newBio)
                .build();
        
        User updatedUser = userService.updateUserProfile(userId, updateRequest);

        // Then: Immutable fields should remain unchanged
        assertThat(updatedUser.getGithubId()).isEqualTo(originalGithubId);
        assertThat(updatedUser.getUsername()).isEqualTo(username);
        assertThat(updatedUser.getCreatedAt()).isEqualTo(originalCreatedAt);
        
        // But mutable fields should be updated
        assertThat(updatedUser.getBio()).isEqualTo(newBio);
        assertThat(updatedUser.getUpdatedAt()).isAfter(originalCreatedAt);
    }

    // Helper methods

    private User createMockUser(String userId, String username, String bio, String location) {
        return User.builder()
                .id(userId)
                .githubId("gh_" + userId)
                .username(username)
                .email(username + "@example.com")
                .avatarUrl("https://github.com/avatar.png")
                .bio(bio)
                .location(location)
                .websiteUrl("https://test.com")
                .followerCount(0)
                .followingCount(0)
                .projectCount(0)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }
}
