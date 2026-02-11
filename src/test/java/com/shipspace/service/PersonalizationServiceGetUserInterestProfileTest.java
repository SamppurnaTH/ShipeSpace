package com.shipspace.service;

import com.shipspace.model.UserInterestProfile;
import com.shipspace.repository.BehavioralSignalRepository;
import com.shipspace.repository.ContentScoreRepository;
import com.shipspace.repository.ProjectRepository;
import com.shipspace.repository.ShipRepository;
import com.shipspace.repository.UserInterestProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for PersonalizationService.getUserInterestProfile() method.
 * 
 * These tests verify:
 * - Profile retrieval from repository
 * - Empty profile creation for cold-start users
 * - Proper caching behavior
 * 
 * Requirements: 8.2
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PersonalizationService - getUserInterestProfile Tests")
class PersonalizationServiceGetUserInterestProfileTest {

    @Mock
    private UserInterestProfileRepository userInterestProfileRepository;

    @Mock
    private ContentScoreRepository contentScoreRepository;

    @Mock
    private ShipRepository shipRepository;

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private BehavioralSignalRepository behavioralSignalRepository;

    @Mock
    private FeedService feedService;

    @InjectMocks
    private PersonalizationService personalizationService;

    @Test
    @DisplayName("getUserInterestProfile should return existing profile when found")
    void testGetUserInterestProfile_ExistingProfile_ReturnsProfile() {
        // Arrange
        String userId = "user123";
        Map<String, Double> techTagScores = new HashMap<>();
        techTagScores.put("React", 0.8);
        techTagScores.put("TypeScript", 0.6);
        
        Set<String> followedBuilders = new HashSet<>();
        followedBuilders.add("builder456");
        
        UserInterestProfile existingProfile = UserInterestProfile.builder()
                .id("profile123")
                .userId(userId)
                .techTagScores(techTagScores)
                .followedBuilders(followedBuilders)
                .totalSignals(25)
                .lastSignalAt(Instant.now().minusSeconds(3600))
                .createdAt(Instant.now().minusSeconds(86400))
                .updatedAt(Instant.now().minusSeconds(3600))
                .build();

        when(userInterestProfileRepository.findByUserId(userId))
                .thenReturn(Optional.of(existingProfile));

        // Act
        UserInterestProfile result = personalizationService.getUserInterestProfile(userId);

        // Assert
        assertNotNull(result, "Profile should not be null");
        assertEquals(userId, result.getUserId(), "User ID should match");
        assertEquals(25, result.getTotalSignals(), "Total signals should match");
        assertEquals(2, result.getTechTagScores().size(), "Should have 2 tech tags");
        assertEquals(0.8, result.getTechTagScores().get("React"), 0.001, "React score should match");
        assertEquals(1, result.getFollowedBuilders().size(), "Should have 1 followed builder");
        assertTrue(result.getFollowedBuilders().contains("builder456"), "Should contain builder456");
        
        verify(userInterestProfileRepository).findByUserId(userId);
    }

    @Test
    @DisplayName("getUserInterestProfile should return empty profile for cold-start user")
    void testGetUserInterestProfile_NoProfile_ReturnsEmptyProfile() {
        // Arrange
        String userId = "newUser789";
        when(userInterestProfileRepository.findByUserId(userId))
                .thenReturn(Optional.empty());

        // Act
        UserInterestProfile result = personalizationService.getUserInterestProfile(userId);

        // Assert
        assertNotNull(result, "Profile should not be null");
        assertEquals(userId, result.getUserId(), "User ID should match");
        assertEquals(0, result.getTotalSignals(), "Total signals should be 0 for cold-start user");
        assertNotNull(result.getTechTagScores(), "Tech tag scores should not be null");
        assertTrue(result.getTechTagScores().isEmpty(), "Tech tag scores should be empty");
        assertNotNull(result.getFollowedBuilders(), "Followed builders should not be null");
        assertTrue(result.getFollowedBuilders().isEmpty(), "Followed builders should be empty");
        assertTrue(result.isColdStart(), "Should be identified as cold-start user");
        
        verify(userInterestProfileRepository).findByUserId(userId);
    }

    @Test
    @DisplayName("getUserInterestProfile should handle profile with empty collections")
    void testGetUserInterestProfile_EmptyCollections_HandlesGracefully() {
        // Arrange
        String userId = "user456";
        UserInterestProfile profileWithEmptyCollections = UserInterestProfile.builder()
                .id("profile456")
                .userId(userId)
                .techTagScores(new HashMap<>())
                .followedBuilders(new HashSet<>())
                .totalSignals(5)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        when(userInterestProfileRepository.findByUserId(userId))
                .thenReturn(Optional.of(profileWithEmptyCollections));

        // Act
        UserInterestProfile result = personalizationService.getUserInterestProfile(userId);

        // Assert
        assertNotNull(result, "Profile should not be null");
        assertEquals(userId, result.getUserId(), "User ID should match");
        assertEquals(5, result.getTotalSignals(), "Total signals should match");
        assertTrue(result.getTechTagScores().isEmpty(), "Tech tag scores should be empty");
        assertTrue(result.getFollowedBuilders().isEmpty(), "Followed builders should be empty");
        assertTrue(result.isColdStart(), "Should be cold-start with only 5 signals");
        
        verify(userInterestProfileRepository).findByUserId(userId);
    }

    @Test
    @DisplayName("getUserInterestProfile should handle profile with many tech tags")
    void testGetUserInterestProfile_ManyTechTags_ReturnsAllTags() {
        // Arrange
        String userId = "powerUser999";
        Map<String, Double> manyTags = new HashMap<>();
        manyTags.put("React", 0.9);
        manyTags.put("TypeScript", 0.85);
        manyTags.put("Python", 0.8);
        manyTags.put("Java", 0.75);
        manyTags.put("Go", 0.7);
        manyTags.put("Rust", 0.65);
        manyTags.put("Docker", 0.6);
        manyTags.put("Kubernetes", 0.55);
        
        UserInterestProfile powerUserProfile = UserInterestProfile.builder()
                .id("profile999")
                .userId(userId)
                .techTagScores(manyTags)
                .followedBuilders(Set.of("builder1", "builder2", "builder3"))
                .totalSignals(150)
                .createdAt(Instant.now().minusSeconds(2592000)) // 30 days ago
                .updatedAt(Instant.now())
                .build();

        when(userInterestProfileRepository.findByUserId(userId))
                .thenReturn(Optional.of(powerUserProfile));

        // Act
        UserInterestProfile result = personalizationService.getUserInterestProfile(userId);

        // Assert
        assertNotNull(result, "Profile should not be null");
        assertEquals(userId, result.getUserId(), "User ID should match");
        assertEquals(150, result.getTotalSignals(), "Total signals should match");
        assertEquals(8, result.getTechTagScores().size(), "Should have 8 tech tags");
        assertEquals(3, result.getFollowedBuilders().size(), "Should have 3 followed builders");
        assertFalse(result.isColdStart(), "Should not be cold-start with 150 signals");
        
        verify(userInterestProfileRepository).findByUserId(userId);
    }

    @Test
    @DisplayName("getUserInterestProfile should return profile with null lastSignalAt")
    void testGetUserInterestProfile_NullLastSignalAt_HandlesGracefully() {
        // Arrange
        String userId = "user111";
        UserInterestProfile profileWithNullSignalAt = UserInterestProfile.builder()
                .id("profile111")
                .userId(userId)
                .techTagScores(Map.of("React", 0.5))
                .followedBuilders(Set.of())
                .totalSignals(3)
                .lastSignalAt(null) // Null last signal timestamp
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        when(userInterestProfileRepository.findByUserId(userId))
                .thenReturn(Optional.of(profileWithNullSignalAt));

        // Act
        UserInterestProfile result = personalizationService.getUserInterestProfile(userId);

        // Assert
        assertNotNull(result, "Profile should not be null");
        assertEquals(userId, result.getUserId(), "User ID should match");
        assertNull(result.getLastSignalAt(), "Last signal at should be null");
        
        verify(userInterestProfileRepository).findByUserId(userId);
    }
}
