package com.shipspace.service;

import com.shipspace.model.BehavioralSignal;
import com.shipspace.model.BehavioralSignal.ContentType;
import com.shipspace.model.BehavioralSignal.SignalType;
import com.shipspace.model.Project;
import com.shipspace.model.Ship;
import com.shipspace.model.UserInterestProfile;
import com.shipspace.repository.BehavioralSignalRepository;
import com.shipspace.repository.ProjectRepository;
import com.shipspace.repository.ShipRepository;
import com.shipspace.repository.UserInterestProfileRepository;
import net.jqwik.api.*;
import net.jqwik.api.constraints.AlphaChars;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.Size;
import net.jqwik.api.constraints.StringLength;
import net.jqwik.api.lifecycle.BeforeTry;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Property-based tests for interest profile updates from behavioral signals.
 * 
 * **Validates: Requirements 1.1, 1.2, 1.3**
 * 
 * These tests verify that behavioral signals (LIKE, COMMENT, VIEW) correctly
 * update user interest profiles by incrementing tech tag scores.
 */
class InterestProfileUpdatePropertyTest {

    private BehavioralTrackingService behavioralTrackingService;
    private BehavioralSignalRepository behavioralSignalRepository;
    private UserInterestProfileRepository userInterestProfileRepository;
    private ShipRepository shipRepository;
    private ProjectRepository projectRepository;

    @BeforeTry
    void setUp() {
        behavioralSignalRepository = mock(BehavioralSignalRepository.class);
        userInterestProfileRepository = mock(UserInterestProfileRepository.class);
        shipRepository = mock(ShipRepository.class);
        projectRepository = mock(ProjectRepository.class);
        
        behavioralTrackingService = new BehavioralTrackingService(
            behavioralSignalRepository,
            userInterestProfileRepository,
            shipRepository,
            projectRepository
        );
    }

    /**
     * Property 1: Behavioral signals update interest profiles
     * 
     * For any user and any behavioral signal (like, comment, view), when the signal is processed,
     * all tech tags associated with the content should have their interest scores increased
     * in the user's interest profile.
     * 
     * **Validates: Requirements 1.1, 1.2, 1.3**
     */
    @Property(tries = 100)
    @Label("Feature: personalized-feed-algorithm, Property 1: Behavioral signals update interest profiles - LIKE")
    void likeSignalIncreasesAllTechTagScores(
            @ForAll @StringLength(min = 1, max = 50) @AlphaChars String userId,
            @ForAll @StringLength(min = 1, max = 50) @AlphaChars String shipId,
            @ForAll @Size(min = 1, max = 5) List<@StringLength(min = 1, max = 20) @AlphaChars String> techTags) throws Exception {
        
        // Given: A ship exists with specific tech tags
        Ship ship = createMockShip(shipId, "project_" + shipId, "builder_" + shipId);
        Project project = createMockProject("project_" + shipId, "builder_" + shipId, techTags);
        
        when(shipRepository.findById(shipId)).thenReturn(Optional.of(ship));
        when(projectRepository.findById("project_" + shipId)).thenReturn(Optional.of(project));
        
        // Given: User has an existing profile with initial scores
        Map<String, Double> initialScores = new HashMap<>();
        for (String tag : techTags) {
            initialScores.put(tag, 0.1); // Start with small initial scores
        }
        
        UserInterestProfile existingProfile = UserInterestProfile.builder()
                .userId(userId)
                .techTagScores(new HashMap<>(initialScores))
                .followedBuilders(new HashSet<>())
                .totalSignals(5)
                .lastSignalAt(Instant.now())
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        
        when(userInterestProfileRepository.findByUserId(userId)).thenReturn(Optional.of(existingProfile));
        
        // Capture the saved profile
        UserInterestProfile[] savedProfile = new UserInterestProfile[1];
        when(userInterestProfileRepository.save(any(UserInterestProfile.class))).thenAnswer(invocation -> {
            savedProfile[0] = invocation.getArgument(0);
            return savedProfile[0];
        });
        
        when(behavioralSignalRepository.save(any(BehavioralSignal.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When: User likes the ship
        behavioralTrackingService.trackLike(userId, shipId);
        
        // Wait for async processing
        Thread.sleep(200);
        
        // Then: All tech tags should have increased scores
        assertThat(savedProfile[0]).isNotNull();
        Map<String, Double> updatedScores = savedProfile[0].getTechTagScores();
        
        for (String tag : techTags) {
            assertThat(updatedScores).containsKey(tag);
            double initialScore = initialScores.get(tag);
            double updatedScore = updatedScores.get(tag);
            
            assertThat(updatedScore)
                .as("Tech tag '%s' score should increase after LIKE signal (weight 1.0)", tag)
                .isGreaterThan(initialScore);
            
            // LIKE signal has weight 1.0, so score should increase by 1.0
            assertThat(updatedScore)
                .as("Tech tag '%s' score should increase by 1.0 for LIKE signal", tag)
                .isEqualTo(Math.min(1.0, initialScore + 1.0));
        }
        
        // Verify profile metadata was updated
        assertThat(savedProfile[0].getTotalSignals())
            .as("Total signals should be incremented")
            .isEqualTo(6);
    }

    /**
     * Property 1: Behavioral signals update interest profiles - COMMENT
     * 
     * **Validates: Requirements 1.1, 1.2, 1.3**
     */
    @Property(tries = 100)
    @Label("Feature: personalized-feed-algorithm, Property 1: Behavioral signals update interest profiles - COMMENT")
    void commentSignalIncreasesAllTechTagScores(
            @ForAll @StringLength(min = 1, max = 50) @AlphaChars String userId,
            @ForAll @StringLength(min = 1, max = 50) @AlphaChars String shipId,
            @ForAll @Size(min = 1, max = 5) List<@StringLength(min = 1, max = 20) @AlphaChars String> techTags) throws Exception {
        
        // Given: A ship exists with specific tech tags
        Ship ship = createMockShip(shipId, "project_" + shipId, "builder_" + shipId);
        Project project = createMockProject("project_" + shipId, "builder_" + shipId, techTags);
        
        when(shipRepository.findById(shipId)).thenReturn(Optional.of(ship));
        when(projectRepository.findById("project_" + shipId)).thenReturn(Optional.of(project));
        
        // Given: User has an existing profile with initial scores
        Map<String, Double> initialScores = new HashMap<>();
        for (String tag : techTags) {
            initialScores.put(tag, 0.1); // Start with small initial scores
        }
        
        UserInterestProfile existingProfile = UserInterestProfile.builder()
                .userId(userId)
                .techTagScores(new HashMap<>(initialScores))
                .followedBuilders(new HashSet<>())
                .totalSignals(5)
                .lastSignalAt(Instant.now())
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        
        when(userInterestProfileRepository.findByUserId(userId)).thenReturn(Optional.of(existingProfile));
        
        // Capture the saved profile
        UserInterestProfile[] savedProfile = new UserInterestProfile[1];
        when(userInterestProfileRepository.save(any(UserInterestProfile.class))).thenAnswer(invocation -> {
            savedProfile[0] = invocation.getArgument(0);
            return savedProfile[0];
        });
        
        when(behavioralSignalRepository.save(any(BehavioralSignal.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When: User comments on the ship
        behavioralTrackingService.trackComment(userId, shipId);
        
        // Wait for async processing
        Thread.sleep(200);
        
        // Then: All tech tags should have increased scores
        assertThat(savedProfile[0]).isNotNull();
        Map<String, Double> updatedScores = savedProfile[0].getTechTagScores();
        
        for (String tag : techTags) {
            assertThat(updatedScores).containsKey(tag);
            double initialScore = initialScores.get(tag);
            double updatedScore = updatedScores.get(tag);
            
            assertThat(updatedScore)
                .as("Tech tag '%s' score should increase after COMMENT signal (weight 2.0)", tag)
                .isGreaterThan(initialScore);
            
            // COMMENT signal has weight 2.0, but scores are capped at 1.0
            assertThat(updatedScore)
                .as("Tech tag '%s' score should increase by 2.0 for COMMENT signal (capped at 1.0)", tag)
                .isEqualTo(Math.min(1.0, initialScore + 2.0));
        }
        
        // Verify profile metadata was updated
        assertThat(savedProfile[0].getTotalSignals())
            .as("Total signals should be incremented")
            .isEqualTo(6);
    }

    /**
     * Property 1: Behavioral signals update interest profiles - VIEW
     * 
     * **Validates: Requirements 1.1, 1.2, 1.3**
     */
    @Property(tries = 100)
    @Label("Feature: personalized-feed-algorithm, Property 1: Behavioral signals update interest profiles - VIEW")
    void viewSignalIncreasesAllTechTagScores(
            @ForAll @StringLength(min = 1, max = 50) @AlphaChars String userId,
            @ForAll @StringLength(min = 1, max = 50) @AlphaChars String contentId,
            @ForAll @Size(min = 1, max = 5) List<@StringLength(min = 1, max = 20) @AlphaChars String> techTags,
            @ForAll @IntRange(min = 11, max = 300) int durationSeconds) throws Exception {
        
        // Given: A ship exists with specific tech tags
        Ship ship = createMockShip(contentId, "project_" + contentId, "builder_" + contentId);
        Project project = createMockProject("project_" + contentId, "builder_" + contentId, techTags);
        
        when(shipRepository.findById(contentId)).thenReturn(Optional.of(ship));
        when(projectRepository.findById("project_" + contentId)).thenReturn(Optional.of(project));
        
        // Given: User has an existing profile with initial scores
        Map<String, Double> initialScores = new HashMap<>();
        for (String tag : techTags) {
            initialScores.put(tag, 0.2); // Start with small initial scores
        }
        
        UserInterestProfile existingProfile = UserInterestProfile.builder()
                .userId(userId)
                .techTagScores(new HashMap<>(initialScores))
                .followedBuilders(new HashSet<>())
                .totalSignals(5)
                .lastSignalAt(Instant.now())
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        
        when(userInterestProfileRepository.findByUserId(userId)).thenReturn(Optional.of(existingProfile));
        
        // Capture the saved profile
        UserInterestProfile[] savedProfile = new UserInterestProfile[1];
        when(userInterestProfileRepository.save(any(UserInterestProfile.class))).thenAnswer(invocation -> {
            savedProfile[0] = invocation.getArgument(0);
            return savedProfile[0];
        });
        
        when(behavioralSignalRepository.save(any(BehavioralSignal.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When: User views the content for more than 10 seconds
        behavioralTrackingService.trackView(userId, contentId, ContentType.SHIP, durationSeconds);
        
        // Wait for async processing
        Thread.sleep(200);
        
        // Then: All tech tags should have increased scores
        assertThat(savedProfile[0]).isNotNull();
        Map<String, Double> updatedScores = savedProfile[0].getTechTagScores();
        
        for (String tag : techTags) {
            assertThat(updatedScores).containsKey(tag);
            double initialScore = initialScores.get(tag);
            double updatedScore = updatedScores.get(tag);
            
            assertThat(updatedScore)
                .as("Tech tag '%s' score should increase after VIEW signal (weight 0.5)", tag)
                .isGreaterThan(initialScore);
            
            // VIEW signal has weight 0.5
            assertThat(updatedScore)
                .as("Tech tag '%s' score should increase by 0.5 for VIEW signal", tag)
                .isEqualTo(Math.min(1.0, initialScore + 0.5));
        }
        
        // Verify profile metadata was updated
        assertThat(savedProfile[0].getTotalSignals())
            .as("Total signals should be incremented")
            .isEqualTo(6);
    }

    /**
     * Property 1: Behavioral signals update interest profiles - New user (no existing profile)
     * 
     * Tests that signals work correctly for users without existing profiles.
     * 
     * **Validates: Requirements 1.1, 1.2, 1.3**
     */
    @Property(tries = 50)
    @Label("Feature: personalized-feed-algorithm, Property 1: Behavioral signals create and update new profiles")
    void signalCreatesProfileForNewUser(
            @ForAll @StringLength(min = 1, max = 50) @AlphaChars String userId,
            @ForAll @StringLength(min = 1, max = 50) @AlphaChars String shipId,
            @ForAll @Size(min = 1, max = 5) List<@StringLength(min = 1, max = 20) @AlphaChars String> techTags) throws Exception {
        
        // Given: A ship exists with specific tech tags
        Ship ship = createMockShip(shipId, "project_" + shipId, "builder_" + shipId);
        Project project = createMockProject("project_" + shipId, "builder_" + shipId, techTags);
        
        when(shipRepository.findById(shipId)).thenReturn(Optional.of(ship));
        when(projectRepository.findById("project_" + shipId)).thenReturn(Optional.of(project));
        
        // Given: User has NO existing profile
        when(userInterestProfileRepository.findByUserId(userId)).thenReturn(Optional.empty());
        
        // Capture the saved profile
        UserInterestProfile[] savedProfile = new UserInterestProfile[1];
        when(userInterestProfileRepository.save(any(UserInterestProfile.class))).thenAnswer(invocation -> {
            UserInterestProfile profile = invocation.getArgument(0);
            // Simulate database setting the ID
            if (profile.getId() == null) {
                profile.setId("generated_id_" + userId);
            }
            savedProfile[0] = profile;
            return profile;
        });
        
        when(behavioralSignalRepository.save(any(BehavioralSignal.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When: User likes the ship (first interaction)
        behavioralTrackingService.trackLike(userId, shipId);
        
        // Wait for async processing
        Thread.sleep(200);
        
        // Then: A new profile should be created with all tech tags
        assertThat(savedProfile[0]).isNotNull();
        assertThat(savedProfile[0].getUserId()).isEqualTo(userId);
        
        Map<String, Double> updatedScores = savedProfile[0].getTechTagScores();
        
        for (String tag : techTags) {
            assertThat(updatedScores).containsKey(tag);
            double score = updatedScores.get(tag);
            
            // For a new profile, score should be exactly the signal weight (1.0 for LIKE)
            assertThat(score)
                .as("Tech tag '%s' score should be 1.0 for new profile with LIKE signal", tag)
                .isEqualTo(1.0);
        }
        
        // Verify profile metadata
        assertThat(savedProfile[0].getTotalSignals())
            .as("Total signals should be 1 for new profile")
            .isEqualTo(1);
        
        assertThat(savedProfile[0].getLastSignalAt()).isNotNull();
        assertThat(savedProfile[0].getCreatedAt()).isNotNull();
        assertThat(savedProfile[0].getUpdatedAt()).isNotNull();
    }

    /**
     * Property 1: Behavioral signals update interest profiles - Multiple signals accumulate
     * 
     * Tests that multiple signals on the same content accumulate scores correctly.
     * 
     * **Validates: Requirements 1.1, 1.2, 1.3**
     */
    @Property(tries = 50)
    @Label("Feature: personalized-feed-algorithm, Property 1: Multiple signals accumulate tech tag scores")
    void multipleSignalsAccumulateScores(
            @ForAll @StringLength(min = 1, max = 50) @AlphaChars String userId,
            @ForAll @StringLength(min = 1, max = 50) @AlphaChars String shipId,
            @ForAll @Size(min = 1, max = 3) List<@StringLength(min = 1, max = 20) @AlphaChars String> techTags) throws Exception {
        
        // Given: A ship exists with specific tech tags
        Ship ship = createMockShip(shipId, "project_" + shipId, "builder_" + shipId);
        Project project = createMockProject("project_" + shipId, "builder_" + shipId, techTags);
        
        when(shipRepository.findById(shipId)).thenReturn(Optional.of(ship));
        when(projectRepository.findById("project_" + shipId)).thenReturn(Optional.of(project));
        
        // Given: User starts with an empty profile
        when(userInterestProfileRepository.findByUserId(userId)).thenReturn(Optional.empty());
        
        // Track all saved profiles
        List<UserInterestProfile> savedProfiles = new ArrayList<>();
        when(userInterestProfileRepository.save(any(UserInterestProfile.class))).thenAnswer(invocation -> {
            UserInterestProfile profile = invocation.getArgument(0);
            if (profile.getId() == null) {
                profile.setId("generated_id_" + userId);
            }
            savedProfiles.add(profile);
            // Return the profile for subsequent calls to findByUserId
            when(userInterestProfileRepository.findByUserId(userId)).thenReturn(Optional.of(profile));
            return profile;
        });
        
        when(behavioralSignalRepository.save(any(BehavioralSignal.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When: User performs multiple actions on the same ship
        behavioralTrackingService.trackLike(userId, shipId);
        Thread.sleep(150);
        
        behavioralTrackingService.trackComment(userId, shipId);
        Thread.sleep(150);
        
        behavioralTrackingService.trackView(userId, shipId, ContentType.SHIP, 15);
        Thread.sleep(150);
        
        // Then: Scores should accumulate across all signals
        // LIKE (1.0) + COMMENT (2.0) + VIEW (0.5) = 3.5, but capped at 1.0
        assertThat(savedProfiles).isNotEmpty();
        UserInterestProfile finalProfile = savedProfiles.get(savedProfiles.size() - 1);
        
        Map<String, Double> finalScores = finalProfile.getTechTagScores();
        
        for (String tag : techTags) {
            assertThat(finalScores).containsKey(tag);
            double finalScore = finalScores.get(tag);
            
            // Scores should be capped at 1.0
            assertThat(finalScore)
                .as("Tech tag '%s' score should be capped at 1.0 after multiple signals", tag)
                .isEqualTo(1.0);
        }
        
        // Verify total signals count
        assertThat(finalProfile.getTotalSignals())
            .as("Total signals should be 3 after three interactions")
            .isEqualTo(3);
    }

    // Helper methods to create mock objects

    private Ship createMockShip(String shipId, String projectId, String userId) {
        Ship ship = new Ship();
        ship.setId(shipId);
        ship.setProjectId(projectId);
        ship.setUserId(userId);
        ship.setContent("Test ship content for " + shipId);
        ship.setCreatedAt(Instant.now());
        ship.setLikeCount(0);
        ship.setCommentCount(0);
        return ship;
    }

    private Project createMockProject(String projectId, String userId, List<String> techTags) {
        Project project = new Project();
        project.setId(projectId);
        project.setUserId(userId);
        project.setName("Test Project " + projectId);
        project.setDescription("Test description");
        project.setCreatedAt(Instant.now());
        project.setTechTags(new ArrayList<>(techTags));
        return project;
    }
}
