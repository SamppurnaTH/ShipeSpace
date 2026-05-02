package com.shipspace.service;

import com.shipspace.model.BehavioralSignal;
import com.shipspace.model.BehavioralSignal.ContentType;
import com.shipspace.model.BehavioralSignal.SignalType;
import com.shipspace.model.Project;
import com.shipspace.model.Ship;
import com.shipspace.repository.BehavioralSignalRepository;
import com.shipspace.repository.ProjectRepository;
import com.shipspace.repository.ShipRepository;
import com.shipspace.repository.UserInterestProfileRepository;
import net.jqwik.api.*;
import net.jqwik.api.constraints.AlphaChars;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.StringLength;
import net.jqwik.api.lifecycle.BeforeTry;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Property-based tests for behavioral signal persistence timing.
 * 
 * **Validates: Requirements 7.6**
 * 
 * These tests verify that behavioral signals are persisted to the database
 * within 1 second of occurrence, across all signal types and content types.
 */
class BehavioralSignalPersistencePropertyTest {

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
            projectRepository,
            mock(org.springframework.data.mongodb.core.MongoTemplate.class),
            mock(DistributedLockService.class),
            mock(InterestProfileService.class),
            mock(com.shipspace.config.ScheduledJobMetrics.class),
            mock(com.shipspace.config.SloEnforcementService.class)
        );
    }

    /**
     * Property: Signal persistence timing
     * 
     * For any signal type (LIKE, COMMENT, VIEW, FOLLOW, SKIP), the signal should be
     * persisted to the database within 1 second of the tracking method being called.
     * 
     * **Validates: Requirements 7.6**
     */
    @Property(tries = 100)
    @Label("Feature: personalized-feed-algorithm, Property: Signal persistence timing - LIKE signals")
    void likeSignalPersistedWithinOneSecond(
            @ForAll @StringLength(min = 1, max = 50) @AlphaChars String userId,
            @ForAll @StringLength(min = 1, max = 50) @AlphaChars String shipId) throws Exception {
        
        // Given: A ship exists with tech tags
        Ship ship = createMockShip(shipId, "project_" + shipId, "builder_" + shipId);
        when(shipRepository.findById(shipId)).thenReturn(Optional.of(ship));
        
        // Track when save is called
        CompletableFuture<Instant> saveTimeFuture = new CompletableFuture<>();
        when(behavioralSignalRepository.save(any(BehavioralSignal.class))).thenAnswer(invocation -> {
            saveTimeFuture.complete(Instant.now());
            return invocation.getArgument(0);
        });
        
        when(userInterestProfileRepository.findByUserId(anyString())).thenReturn(Optional.empty());
        when(userInterestProfileRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        // When: trackLike is called
        Instant startTime = Instant.now();
        behavioralTrackingService.trackLike(userId, shipId);
        
        // Then: Signal should be saved within 1 second
        Instant saveTime = saveTimeFuture.get(2, TimeUnit.SECONDS); // Wait up to 2 seconds for async operation
        Duration elapsed = Duration.between(startTime, saveTime);
        
        assertThat(elapsed.toMillis())
            .as("LIKE signal should be persisted within 1000ms")
            .isLessThanOrEqualTo(1000);
        
        // Verify the signal was saved with correct type
        verify(behavioralSignalRepository).save(argThat(signal ->
            signal.getUserId().equals(userId) &&
            signal.getContentId().equals(shipId) &&
            signal.getSignalType() == SignalType.LIKE &&
            signal.getContentType() == ContentType.SHIP
        ));
    }

    /**
     * Property: Signal persistence timing - COMMENT signals
     * 
     * **Validates: Requirements 7.6**
     */
    @Property(tries = 100)
    @Label("Feature: personalized-feed-algorithm, Property: Signal persistence timing - COMMENT signals")
    void commentSignalPersistedWithinOneSecond(
            @ForAll @StringLength(min = 1, max = 50) @AlphaChars String userId,
            @ForAll @StringLength(min = 1, max = 50) @AlphaChars String shipId) throws Exception {
        
        // Given: A ship exists with tech tags
        Ship ship = createMockShip(shipId, "project_" + shipId, "builder_" + shipId);
        when(shipRepository.findById(shipId)).thenReturn(Optional.of(ship));
        
        // Track when save is called
        CompletableFuture<Instant> saveTimeFuture = new CompletableFuture<>();
        when(behavioralSignalRepository.save(any(BehavioralSignal.class))).thenAnswer(invocation -> {
            saveTimeFuture.complete(Instant.now());
            return invocation.getArgument(0);
        });
        
        when(userInterestProfileRepository.findByUserId(anyString())).thenReturn(Optional.empty());
        when(userInterestProfileRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        // When: trackComment is called
        Instant startTime = Instant.now();
        behavioralTrackingService.trackComment(userId, shipId);
        
        // Then: Signal should be saved within 1 second
        Instant saveTime = saveTimeFuture.get(2, TimeUnit.SECONDS);
        Duration elapsed = Duration.between(startTime, saveTime);
        
        assertThat(elapsed.toMillis())
            .as("COMMENT signal should be persisted within 1000ms")
            .isLessThanOrEqualTo(1000);
        
        // Verify the signal was saved with correct type
        verify(behavioralSignalRepository).save(argThat(signal ->
            signal.getUserId().equals(userId) &&
            signal.getContentId().equals(shipId) &&
            signal.getSignalType() == SignalType.COMMENT &&
            signal.getContentType() == ContentType.SHIP
        ));
    }

    /**
     * Property: Signal persistence timing - VIEW signals
     * 
     * **Validates: Requirements 7.6**
     */
    @Property(tries = 100)
    @Label("Feature: personalized-feed-algorithm, Property: Signal persistence timing - VIEW signals")
    void viewSignalPersistedWithinOneSecond(
            @ForAll @StringLength(min = 1, max = 50) @AlphaChars String userId,
            @ForAll @StringLength(min = 1, max = 50) @AlphaChars String contentId,
            @ForAll @IntRange(min = 11, max = 300) int durationSeconds) throws Exception {
        
        // Given: A ship exists with tech tags
        Ship ship = createMockShip(contentId, "project_" + contentId, "builder_" + contentId);
        when(shipRepository.findById(contentId)).thenReturn(Optional.of(ship));
        
        Project project = createMockProject("project_" + contentId, "builder_" + contentId);
        when(projectRepository.findById(anyString())).thenReturn(Optional.of(project));
        
        // Track when save is called
        CompletableFuture<Instant> saveTimeFuture = new CompletableFuture<>();
        when(behavioralSignalRepository.save(any(BehavioralSignal.class))).thenAnswer(invocation -> {
            saveTimeFuture.complete(Instant.now());
            return invocation.getArgument(0);
        });
        
        when(userInterestProfileRepository.findByUserId(anyString())).thenReturn(Optional.empty());
        when(userInterestProfileRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        // When: trackView is called with duration > 10 seconds
        Instant startTime = Instant.now();
        behavioralTrackingService.trackView(userId, contentId, ContentType.SHIP, durationSeconds);
        
        // Then: Signal should be saved within 1 second
        Instant saveTime = saveTimeFuture.get(2, TimeUnit.SECONDS);
        Duration elapsed = Duration.between(startTime, saveTime);
        
        assertThat(elapsed.toMillis())
            .as("VIEW signal should be persisted within 1000ms")
            .isLessThanOrEqualTo(1000);
        
        // Verify the signal was saved with correct type and duration
        verify(behavioralSignalRepository).save(argThat(signal ->
            signal.getUserId().equals(userId) &&
            signal.getContentId().equals(contentId) &&
            signal.getSignalType() == SignalType.VIEW &&
            signal.getContentType() == ContentType.SHIP &&
            signal.getDurationSeconds() == durationSeconds
        ));
    }

    /**
     * Property: Signal persistence timing - FOLLOW signals
     * 
     * **Validates: Requirements 7.6**
     */
    @Property(tries = 100)
    @Label("Feature: personalized-feed-algorithm, Property: Signal persistence timing - FOLLOW signals")
    void followSignalPersistedWithinOneSecond(
            @ForAll @StringLength(min = 1, max = 50) @AlphaChars String userId,
            @ForAll @StringLength(min = 1, max = 50) @AlphaChars String builderId,
            @ForAll @StringLength(min = 1, max = 50) @AlphaChars String contentId) throws Exception {
        
        // Given: A ship exists with tech tags
        Ship ship = createMockShip(contentId, "project_" + contentId, builderId);
        when(shipRepository.findById(contentId)).thenReturn(Optional.of(ship));
        
        Project project = createMockProject("project_" + contentId, builderId);
        when(projectRepository.findById(anyString())).thenReturn(Optional.of(project));
        
        // Track when save is called
        CompletableFuture<Instant> saveTimeFuture = new CompletableFuture<>();
        when(behavioralSignalRepository.save(any(BehavioralSignal.class))).thenAnswer(invocation -> {
            saveTimeFuture.complete(Instant.now());
            return invocation.getArgument(0);
        });
        
        when(userInterestProfileRepository.findByUserId(anyString())).thenReturn(Optional.empty());
        when(userInterestProfileRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        // When: trackFollow is called
        Instant startTime = Instant.now();
        behavioralTrackingService.trackFollow(userId, builderId, contentId);
        
        // Then: Signal should be saved within 1 second
        Instant saveTime = saveTimeFuture.get(2, TimeUnit.SECONDS);
        Duration elapsed = Duration.between(startTime, saveTime);
        
        assertThat(elapsed.toMillis())
            .as("FOLLOW signal should be persisted within 1000ms")
            .isLessThanOrEqualTo(1000);
        
        // Verify the signal was saved with correct type and builder
        verify(behavioralSignalRepository).save(argThat(signal ->
            signal.getUserId().equals(userId) &&
            signal.getSignalType() == SignalType.FOLLOW &&
            signal.getBuilderId().equals(builderId)
        ));
    }

    /**
     * Property: Signal persistence timing - SKIP signals
     * 
     * **Validates: Requirements 7.6**
     */
    @Property(tries = 100)
    @Label("Feature: personalized-feed-algorithm, Property: Signal persistence timing - SKIP signals")
    void skipSignalPersistedWithinOneSecond(
            @ForAll @StringLength(min = 1, max = 50) @AlphaChars String userId,
            @ForAll @StringLength(min = 1, max = 50) @AlphaChars String contentId) throws Exception {
        
        // Given: A ship exists with tech tags
        Ship ship = createMockShip(contentId, "project_" + contentId, "builder_" + contentId);
        when(shipRepository.findById(contentId)).thenReturn(Optional.of(ship));
        
        Project project = createMockProject("project_" + contentId, "builder_" + contentId);
        when(projectRepository.findById(anyString())).thenReturn(Optional.of(project));
        
        // Track when save is called
        CompletableFuture<Instant> saveTimeFuture = new CompletableFuture<>();
        when(behavioralSignalRepository.save(any(BehavioralSignal.class))).thenAnswer(invocation -> {
            saveTimeFuture.complete(Instant.now());
            return invocation.getArgument(0);
        });
        
        when(userInterestProfileRepository.findByUserId(anyString())).thenReturn(Optional.empty());
        when(userInterestProfileRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        // When: trackSkip is called
        Instant startTime = Instant.now();
        behavioralTrackingService.trackSkip(userId, contentId);
        
        // Then: Signal should be saved within 1 second
        Instant saveTime = saveTimeFuture.get(2, TimeUnit.SECONDS);
        Duration elapsed = Duration.between(startTime, saveTime);
        
        assertThat(elapsed.toMillis())
            .as("SKIP signal should be persisted within 1000ms")
            .isLessThanOrEqualTo(1000);
        
        // Verify the signal was saved with correct type
        verify(behavioralSignalRepository).save(argThat(signal ->
            signal.getUserId().equals(userId) &&
            signal.getContentId().equals(contentId) &&
            signal.getSignalType() == SignalType.SKIP
        ));
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

    private Project createMockProject(String projectId, String userId) {
        Project project = new Project();
        project.setId(projectId);
        project.setUserId(userId);
        project.setName("Test Project " + projectId);
        project.setDescription("Test description");
        project.setCreatedAt(Instant.now());
        
        // Add some tech tags
        List<String> techTags = new ArrayList<>();
        techTags.add("Java");
        techTags.add("Spring");
        techTags.add("MongoDB");
        project.setTechTags(techTags);
        
        return project;
    }
}
