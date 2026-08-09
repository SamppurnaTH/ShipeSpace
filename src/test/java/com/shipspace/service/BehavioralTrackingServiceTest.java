package com.shipspace.service;

import com.shipspace.model.BehavioralSignal;
import com.shipspace.model.BehavioralSignal.ContentType;
import com.shipspace.model.BehavioralSignal.SignalType;
import com.shipspace.model.Project;
import com.shipspace.model.Ship;
import com.shipspace.model.UserInterestProfile;
import com.shipspace.config.ScheduledJobMetrics;
import com.shipspace.config.SloEnforcementService;
import com.shipspace.repository.BehavioralSignalRepository;
import com.shipspace.repository.ProjectRepository;
import com.shipspace.repository.ShipRepository;
import com.shipspace.repository.UserInterestProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.time.Instant;
import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for BehavioralTrackingService.
 * 
 * These tests verify:
 * - Signal tracking methods create and persist signals correctly
 * - Signals are only tracked when content exists
 * - View signals are only tracked for durations > 10 seconds
 * - All signal types have correct weights and metadata
 * 
 * Requirements: 7.1, 7.2, 7.3, 7.4, 7.5, 7.6
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("BehavioralTrackingService - Signal Tracking Tests")
class BehavioralTrackingServiceTest {

    @Mock
    private BehavioralSignalRepository behavioralSignalRepository;

    @Mock
    private UserInterestProfileRepository userInterestProfileRepository;

    @Mock
    private ShipRepository shipRepository;

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private MongoTemplate mongoTemplate;

    @Mock
    private DistributedLockService lockService;

    @Mock
    private InterestProfileService interestProfileService;

    @Mock
    private ScheduledJobMetrics scheduledJobMetrics;

    @Mock
    private SloEnforcementService sloEnforcementService;

    @InjectMocks
    private BehavioralTrackingService behavioralTrackingService;

    private Ship testShip;
    private Project testProject;
    private Map<String, UserInterestProfile> profilesByUserId;

    @BeforeEach
    void setUp() {
        // Create test ship
        testShip = Ship.builder()
                .id("ship123")
                .userId("builder456")
                .projectId("project789")
                .content("Test ship content")
                .createdAt(Instant.now())
                .build();

        // Create test project
        testProject = Project.builder()
                .id("project789")
                .userId("builder456")
                .name("Test Project")
                .description("Test project description")
                .techTags(List.of("Java", "Spring", "MongoDB"))
                .createdAt(Instant.now())
                .build();

        profilesByUserId = new HashMap<>();
        lenient().when(interestProfileService.getOrCreatePersistedProfile(anyString()))
                .thenAnswer(invocation -> profilesByUserId.computeIfAbsent(
                        invocation.getArgument(0),
                        userId -> UserInterestProfile.builder()
                                .userId(userId)
                                .techTagScores(new HashMap<>())
                                .followedBuilders(new HashSet<>())
                                .totalSignals(0)
                                .createdAt(Instant.now())
                                .updatedAt(Instant.now())
                                .build()));
        lenient().when(userInterestProfileRepository.save(any(UserInterestProfile.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(lockService.runWithLock(anyString(), any(Duration.class), any(Runnable.class)))
                .thenAnswer(invocation -> {
                    Runnable task = invocation.getArgument(2);
                    task.run();
                    return true;
                });
    }

    @Test
    @DisplayName("trackLike should create and persist LIKE signal for valid ship")
    void testTrackLike_ValidShip() throws InterruptedException {
        // Arrange
        when(shipRepository.findById("ship123")).thenReturn(Optional.of(testShip));
        when(behavioralSignalRepository.save(any(BehavioralSignal.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        behavioralTrackingService.trackLike("user123", "ship123");
        
        // Give async method time to complete
        Thread.sleep(100);

        // Assert
        ArgumentCaptor<BehavioralSignal> signalCaptor = ArgumentCaptor.forClass(BehavioralSignal.class);
        verify(behavioralSignalRepository, atLeastOnce()).save(signalCaptor.capture());
        
        BehavioralSignal savedSignal = signalCaptor.getValue();
        assertEquals("user123", savedSignal.getUserId());
        assertEquals("ship123", savedSignal.getContentId());
        assertEquals(SignalType.LIKE, savedSignal.getSignalType());
        assertEquals(ContentType.SHIP, savedSignal.getContentType());
        assertEquals("builder456", savedSignal.getBuilderId());
        assertNotNull(savedSignal.getTimestamp());
    }

    @Test
    @DisplayName("trackLike should not persist signal when ship not found")
    void testTrackLike_ShipNotFound() throws InterruptedException {
        // Arrange
        when(shipRepository.findById("nonexistent")).thenReturn(Optional.empty());

        // Act
        behavioralTrackingService.trackLike("user123", "nonexistent");
        
        // Give async method time to complete
        Thread.sleep(100);

        // Assert
        verify(behavioralSignalRepository, never()).save(any(BehavioralSignal.class));
    }

    @Test
    @DisplayName("trackComment should create and persist COMMENT signal for valid ship")
    void testTrackComment_ValidShip() throws InterruptedException {
        // Arrange
        when(shipRepository.findById("ship123")).thenReturn(Optional.of(testShip));
        when(behavioralSignalRepository.save(any(BehavioralSignal.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        behavioralTrackingService.trackComment("user123", "ship123");
        
        // Give async method time to complete
        Thread.sleep(100);

        // Assert
        ArgumentCaptor<BehavioralSignal> signalCaptor = ArgumentCaptor.forClass(BehavioralSignal.class);
        verify(behavioralSignalRepository, atLeastOnce()).save(signalCaptor.capture());
        
        BehavioralSignal savedSignal = signalCaptor.getValue();
        assertEquals("user123", savedSignal.getUserId());
        assertEquals("ship123", savedSignal.getContentId());
        assertEquals(SignalType.COMMENT, savedSignal.getSignalType());
        assertEquals(ContentType.SHIP, savedSignal.getContentType());
        assertEquals("builder456", savedSignal.getBuilderId());
    }

    @Test
    @DisplayName("trackComment should not persist signal when ship not found")
    void testTrackComment_ShipNotFound() throws InterruptedException {
        // Arrange
        when(shipRepository.findById("nonexistent")).thenReturn(Optional.empty());

        // Act
        behavioralTrackingService.trackComment("user123", "nonexistent");
        
        // Give async method time to complete
        Thread.sleep(100);

        // Assert
        verify(behavioralSignalRepository, never()).save(any(BehavioralSignal.class));
    }

    @Test
    @DisplayName("trackView should create and persist VIEW signal for ship with duration > 10 seconds")
    void testTrackView_ValidShipLongDuration() throws InterruptedException {
        // Arrange
        when(shipRepository.findById("ship123")).thenReturn(Optional.of(testShip));
        when(behavioralSignalRepository.save(any(BehavioralSignal.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        behavioralTrackingService.trackView("user123", "ship123", ContentType.SHIP, 15);
        
        // Give async method time to complete
        Thread.sleep(100);

        // Assert
        ArgumentCaptor<BehavioralSignal> signalCaptor = ArgumentCaptor.forClass(BehavioralSignal.class);
        verify(behavioralSignalRepository, atLeastOnce()).save(signalCaptor.capture());
        
        BehavioralSignal savedSignal = signalCaptor.getValue();
        assertEquals("user123", savedSignal.getUserId());
        assertEquals("ship123", savedSignal.getContentId());
        assertEquals(SignalType.VIEW, savedSignal.getSignalType());
        assertEquals(ContentType.SHIP, savedSignal.getContentType());
        assertEquals(15, savedSignal.getDurationSeconds());
        assertEquals("builder456", savedSignal.getBuilderId());
    }

    @Test
    @DisplayName("trackView should NOT persist signal for duration <= 10 seconds")
    void testTrackView_ShortDuration() throws InterruptedException {
        // Act
        behavioralTrackingService.trackView("user123", "ship123", ContentType.SHIP, 5);
        
        // Give async method time to complete
        Thread.sleep(100);

        // Assert - signal should not be saved for short views
        verify(behavioralSignalRepository, never()).save(any(BehavioralSignal.class));
        verify(shipRepository, never()).findById(any());
    }

    @Test
    @DisplayName("trackView should handle project content type")
    void testTrackView_ValidProject() throws InterruptedException {
        // Arrange
        when(projectRepository.findById("project789")).thenReturn(Optional.of(testProject));
        when(behavioralSignalRepository.save(any(BehavioralSignal.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        behavioralTrackingService.trackView("user123", "project789", ContentType.PROJECT, 20);
        
        // Give async method time to complete
        Thread.sleep(100);

        // Assert
        ArgumentCaptor<BehavioralSignal> signalCaptor = ArgumentCaptor.forClass(BehavioralSignal.class);
        verify(behavioralSignalRepository, atLeastOnce()).save(signalCaptor.capture());
        
        BehavioralSignal savedSignal = signalCaptor.getValue();
        assertEquals("user123", savedSignal.getUserId());
        assertEquals("project789", savedSignal.getContentId());
        assertEquals(SignalType.VIEW, savedSignal.getSignalType());
        assertEquals(ContentType.PROJECT, savedSignal.getContentType());
        assertEquals(20, savedSignal.getDurationSeconds());
    }

    @Test
    @DisplayName("trackView should not persist signal when content not found")
    void testTrackView_ContentNotFound() throws InterruptedException {
        // Arrange
        when(shipRepository.findById("nonexistent")).thenReturn(Optional.empty());

        // Act
        behavioralTrackingService.trackView("user123", "nonexistent", ContentType.SHIP, 15);
        
        // Give async method time to complete
        Thread.sleep(100);

        // Assert
        verify(behavioralSignalRepository, never()).save(any(BehavioralSignal.class));
    }

    @Test
    @DisplayName("trackFollow should create and persist FOLLOW signal")
    void testTrackFollow_ValidBuilder() throws InterruptedException {
        // Arrange
        when(shipRepository.findById("ship123")).thenReturn(Optional.of(testShip));
        when(behavioralSignalRepository.save(any(BehavioralSignal.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        behavioralTrackingService.trackFollow("user123", "builder456", "ship123");
        
        // Give async method time to complete
        Thread.sleep(100);

        // Assert
        ArgumentCaptor<BehavioralSignal> signalCaptor = ArgumentCaptor.forClass(BehavioralSignal.class);
        verify(behavioralSignalRepository, atLeastOnce()).save(signalCaptor.capture());
        
        BehavioralSignal savedSignal = signalCaptor.getValue();
        assertEquals("user123", savedSignal.getUserId());
        assertEquals("ship123", savedSignal.getContentId());
        assertEquals(SignalType.FOLLOW, savedSignal.getSignalType());
        assertEquals("builder456", savedSignal.getBuilderId());
    }

    @Test
    @DisplayName("trackFollow should handle null contentId")
    void testTrackFollow_NullContentId() throws InterruptedException {
        // Arrange
        when(behavioralSignalRepository.save(any(BehavioralSignal.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        behavioralTrackingService.trackFollow("user123", "builder456", null);
        
        // Give async method time to complete
        Thread.sleep(100);

        // Assert
        ArgumentCaptor<BehavioralSignal> signalCaptor = ArgumentCaptor.forClass(BehavioralSignal.class);
        verify(behavioralSignalRepository, atLeastOnce()).save(signalCaptor.capture());
        
        BehavioralSignal savedSignal = signalCaptor.getValue();
        assertEquals("user123", savedSignal.getUserId());
        assertEquals(SignalType.FOLLOW, savedSignal.getSignalType());
        assertEquals("builder456", savedSignal.getBuilderId());
        assertNull(savedSignal.getContentId());
    }

    @Test
    @DisplayName("trackSkip should create and persist SKIP signal for ship")
    void testTrackSkip_ValidShip() throws InterruptedException {
        // Arrange
        when(shipRepository.findById("ship123")).thenReturn(Optional.of(testShip));
        when(behavioralSignalRepository.save(any(BehavioralSignal.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        behavioralTrackingService.trackSkip("user123", "ship123");
        
        // Give async method time to complete
        Thread.sleep(100);

        // Assert
        ArgumentCaptor<BehavioralSignal> signalCaptor = ArgumentCaptor.forClass(BehavioralSignal.class);
        verify(behavioralSignalRepository, atLeastOnce()).save(signalCaptor.capture());
        
        BehavioralSignal savedSignal = signalCaptor.getValue();
        assertEquals("user123", savedSignal.getUserId());
        assertEquals("ship123", savedSignal.getContentId());
        assertEquals(SignalType.SKIP, savedSignal.getSignalType());
        assertEquals(ContentType.SHIP, savedSignal.getContentType());
    }

    @Test
    @DisplayName("trackSkip should handle project content")
    void testTrackSkip_ValidProject() throws InterruptedException {
        // Arrange
        when(shipRepository.findById("project789")).thenReturn(Optional.empty());
        when(projectRepository.findById("project789")).thenReturn(Optional.of(testProject));
        when(behavioralSignalRepository.save(any(BehavioralSignal.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        behavioralTrackingService.trackSkip("user123", "project789");
        
        // Give async method time to complete
        Thread.sleep(100);

        // Assert
        ArgumentCaptor<BehavioralSignal> signalCaptor = ArgumentCaptor.forClass(BehavioralSignal.class);
        verify(behavioralSignalRepository, atLeastOnce()).save(signalCaptor.capture());
        
        BehavioralSignal savedSignal = signalCaptor.getValue();
        assertEquals("user123", savedSignal.getUserId());
        assertEquals("project789", savedSignal.getContentId());
        assertEquals(SignalType.SKIP, savedSignal.getSignalType());
        assertEquals(ContentType.PROJECT, savedSignal.getContentType());
    }

    @Test
    @DisplayName("trackSkip should not persist signal when content not found")
    void testTrackSkip_ContentNotFound() throws InterruptedException {
        // Arrange
        when(shipRepository.findById("nonexistent")).thenReturn(Optional.empty());
        when(projectRepository.findById("nonexistent")).thenReturn(Optional.empty());

        // Act
        behavioralTrackingService.trackSkip("user123", "nonexistent");
        
        // Give async method time to complete
        Thread.sleep(100);

        // Assert
        verify(behavioralSignalRepository, never()).save(any(BehavioralSignal.class));
    }

    @Test
    @DisplayName("Service should be instantiated with all dependencies")
    void testServiceInstantiation() {
        assertNotNull(behavioralTrackingService, "Service should be instantiated");
    }

    // ========== Tests for processSignal() and related methods ==========

    @Test
    @DisplayName("processSignal should update user interest profile with tech tag scores")
    void testProcessSignal_UpdatesTechTagScores() throws Exception {
        // Arrange
        when(shipRepository.findById("ship123")).thenReturn(Optional.of(testShip));
        when(projectRepository.findById("project789")).thenReturn(Optional.of(testProject));
        
        // Create an AtomicReference to hold the current profile state
        final UserInterestProfile[] currentProfile = {null};
        
        // Mock profile repository to maintain state across calls
        lenient().when(userInterestProfileRepository.findByUserId("user123")).thenAnswer(invocation -> 
            currentProfile[0] == null ? Optional.empty() : Optional.of(currentProfile[0])
        );
        when(userInterestProfileRepository.save(any())).thenAnswer(invocation -> {
            currentProfile[0] = invocation.getArgument(0);
            return currentProfile[0];
        });
        when(behavioralSignalRepository.save(any(BehavioralSignal.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        behavioralTrackingService.trackLike("user123", "ship123");
        
        // Give async method time to complete
        Thread.sleep(200);

        // Assert - verify profile was saved with updated tech tag scores
        ArgumentCaptor<com.shipspace.model.UserInterestProfile> profileCaptor = 
                ArgumentCaptor.forClass(com.shipspace.model.UserInterestProfile.class);
        verify(userInterestProfileRepository, atLeast(1)).save(profileCaptor.capture());
        
        com.shipspace.model.UserInterestProfile savedProfile = profileCaptor.getValue();
        assertNotNull(savedProfile);
        assertEquals("user123", savedProfile.getUserId());
        assertTrue(savedProfile.getTotalSignals() > 0, "Total signals should be incremented");
        assertNotNull(savedProfile.getLastSignalAt(), "Last signal timestamp should be set");
        
        // Verify tech tags from project were added to profile
        Map<String, Double> techTagScores = savedProfile.getTechTagScores();
        assertTrue(techTagScores.containsKey("Java"), "Should contain Java tag");
        assertTrue(techTagScores.containsKey("Spring"), "Should contain Spring tag");
        assertTrue(techTagScores.containsKey("MongoDB"), "Should contain MongoDB tag");
        
        // LIKE signal has weight 1.0, so each tag should have score 1.0
        assertEquals(1.0, techTagScores.get("Java"), 0.001);
        assertEquals(1.0, techTagScores.get("Spring"), 0.001);
        assertEquals(1.0, techTagScores.get("MongoDB"), 0.001);
    }

    @Test
    @DisplayName("processSignal should apply correct weights for different signal types")
    void testProcessSignal_AppliesCorrectWeights() throws Exception {
        // Arrange
        when(shipRepository.findById("ship123")).thenReturn(Optional.of(testShip));
        when(projectRepository.findById("project789")).thenReturn(Optional.of(testProject));

        // Create an AtomicReference to hold the current profile state
        final UserInterestProfile[] currentProfile = {null};

        // Mock profile repository to maintain state across calls
        lenient().when(userInterestProfileRepository.findByUserId("user123")).thenAnswer(invocation -> 
            currentProfile[0] == null ? Optional.empty() : Optional.of(currentProfile[0])
        );
        when(userInterestProfileRepository.save(any())).thenAnswer(invocation -> {
            currentProfile[0] = invocation.getArgument(0);
            return currentProfile[0];
        });
        when(behavioralSignalRepository.save(any(BehavioralSignal.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // Act - track a COMMENT (weight 2.0)
        behavioralTrackingService.trackComment("user123", "ship123");

        // Give async method time to complete
        Thread.sleep(200);

        // Assert - verify COMMENT weight (2.0) was applied
        ArgumentCaptor<com.shipspace.model.UserInterestProfile> profileCaptor = 
                ArgumentCaptor.forClass(com.shipspace.model.UserInterestProfile.class);
        verify(userInterestProfileRepository, atLeast(1)).save(profileCaptor.capture());

        com.shipspace.model.UserInterestProfile savedProfile = profileCaptor.getValue();
        Map<String, Double> techTagScores = savedProfile.getTechTagScores();

        // COMMENT signal has weight 2.0 and scores now accumulate without a hard cap.
        assertEquals(2.0, techTagScores.get("Java"), 0.001);
        assertEquals(2.0, techTagScores.get("Spring"), 0.001);
        assertEquals(2.0, techTagScores.get("MongoDB"), 0.001);
    }


    @Test
    @DisplayName("processSignal should add builder to followedBuilders for FOLLOW signals")
    void testProcessSignal_AddsFollowedBuilder() throws Exception {
        // Arrange
        when(shipRepository.findById("ship123")).thenReturn(Optional.of(testShip));
        when(projectRepository.findById("project789")).thenReturn(Optional.of(testProject));

        // Create an AtomicReference to hold the current profile state
        final UserInterestProfile[] currentProfile = {null};

        // Mock profile repository to maintain state across calls
        lenient().when(userInterestProfileRepository.findByUserId("user123")).thenAnswer(invocation -> 
            currentProfile[0] == null ? Optional.empty() : Optional.of(currentProfile[0])
        );
        when(userInterestProfileRepository.save(any())).thenAnswer(invocation -> {
            currentProfile[0] = invocation.getArgument(0);
            return currentProfile[0];
        });
        when(behavioralSignalRepository.save(any(BehavioralSignal.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        behavioralTrackingService.trackFollow("user123", "builder456", "ship123");

        // Give async method time to complete
        Thread.sleep(200);

        // Assert - verify builder was added to followedBuilders
        ArgumentCaptor<com.shipspace.model.UserInterestProfile> profileCaptor = 
                ArgumentCaptor.forClass(com.shipspace.model.UserInterestProfile.class);
        verify(userInterestProfileRepository, atLeast(1)).save(profileCaptor.capture());

        com.shipspace.model.UserInterestProfile savedProfile = profileCaptor.getValue();
        assertTrue(savedProfile.getFollowedBuilders().contains("builder456"), 
                   "Builder should be added to followed builders");

        // FOLLOW signal has weight 3.0 and scores now accumulate without a hard cap.
        Map<String, Double> techTagScores = savedProfile.getTechTagScores();
        assertEquals(3.0, techTagScores.get("Java"), 0.001);
    }


    @Test
    @DisplayName("processSignal should increment totalSignals counter")
    void testProcessSignal_IncrementsSignalCounter() throws Exception {
        // Arrange
        when(shipRepository.findById("ship123")).thenReturn(Optional.of(testShip));
        when(projectRepository.findById("project789")).thenReturn(Optional.of(testProject));
        
        // Create an AtomicReference to hold the current profile state
        final UserInterestProfile[] currentProfile = {null};
        
        // Mock profile repository to maintain state across calls
        lenient().when(userInterestProfileRepository.findByUserId("user123")).thenAnswer(invocation -> 
            currentProfile[0] == null ? Optional.empty() : Optional.of(currentProfile[0])
        );
        when(userInterestProfileRepository.save(any())).thenAnswer(invocation -> {
            currentProfile[0] = invocation.getArgument(0);
            return currentProfile[0];
        });
        when(behavioralSignalRepository.save(any(BehavioralSignal.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // Act - track multiple signals
        behavioralTrackingService.trackLike("user123", "ship123");
        Thread.sleep(150);
        behavioralTrackingService.trackComment("user123", "ship123");
        Thread.sleep(150);

        // Assert - verify totalSignals was incremented
        ArgumentCaptor<com.shipspace.model.UserInterestProfile> profileCaptor = 
                ArgumentCaptor.forClass(com.shipspace.model.UserInterestProfile.class);
        verify(userInterestProfileRepository, atLeast(2)).save(profileCaptor.capture());
        
        // Get the last saved profile
        List<com.shipspace.model.UserInterestProfile> savedProfiles = profileCaptor.getAllValues();
        com.shipspace.model.UserInterestProfile lastProfile = savedProfiles.get(savedProfiles.size() - 1);
        
        assertTrue(lastProfile.getTotalSignals() >= 2, 
                   "Total signals should be at least 2 after two signals");
    }

    @Test
    @DisplayName("processSignal should handle negative weights for SKIP signals")
    void testProcessSignal_HandlesNegativeWeights() throws Exception {
        // Arrange
        when(shipRepository.findById("ship123")).thenReturn(Optional.of(testShip));
        when(projectRepository.findById("project789")).thenReturn(Optional.of(testProject));

        // Create an AtomicReference to hold the current profile state
        final UserInterestProfile[] currentProfile = {null};

        // Mock profile repository to maintain state across calls
        lenient().when(userInterestProfileRepository.findByUserId("user123")).thenAnswer(invocation -> 
            currentProfile[0] == null ? Optional.empty() : Optional.of(currentProfile[0])
        );
        when(userInterestProfileRepository.save(any())).thenAnswer(invocation -> {
            currentProfile[0] = invocation.getArgument(0);
            return currentProfile[0];
        });
        when(behavioralSignalRepository.save(any(BehavioralSignal.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // Act - track a SKIP signal (weight -0.1)
        behavioralTrackingService.trackSkip("user123", "ship123");

        // Give async method time to complete
        Thread.sleep(200);

        // Assert - verify negative weight was applied
        ArgumentCaptor<com.shipspace.model.UserInterestProfile> profileCaptor = 
                ArgumentCaptor.forClass(com.shipspace.model.UserInterestProfile.class);
        verify(userInterestProfileRepository, atLeast(1)).save(profileCaptor.capture());

        com.shipspace.model.UserInterestProfile savedProfile = profileCaptor.getValue();
        Map<String, Double> techTagScores = savedProfile.getTechTagScores();

        // SKIP signal has weight -0.1
        // Starting from 0.0, adding -0.1 gives -0.1
        // incrementTagScore doesn't prevent negative values, so score will be negative
        assertTrue(techTagScores.get("Java") < 0.0, "Score should be negative for SKIP signal");
    }


    @Test
    @DisplayName("extractTechTags should extract tags from ship's project")
    void testExtractTechTags_FromShip() throws Exception {
        // Arrange
        when(shipRepository.findById("ship123")).thenReturn(Optional.of(testShip));
        when(projectRepository.findById("project789")).thenReturn(Optional.of(testProject));
        lenient().when(userInterestProfileRepository.findByUserId("user123")).thenReturn(Optional.empty());
        when(userInterestProfileRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(behavioralSignalRepository.save(any(BehavioralSignal.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        behavioralTrackingService.trackLike("user123", "ship123");
        Thread.sleep(200);

        // Assert - verify signal was saved with correct tech tags
        ArgumentCaptor<BehavioralSignal> signalCaptor = ArgumentCaptor.forClass(BehavioralSignal.class);
        verify(behavioralSignalRepository, atLeastOnce()).save(signalCaptor.capture());
        
        BehavioralSignal savedSignal = signalCaptor.getValue();
        List<String> techTags = savedSignal.getTechTags();
        
        assertEquals(3, techTags.size(), "Should have 3 tech tags from project");
        assertTrue(techTags.contains("Java"));
        assertTrue(techTags.contains("Spring"));
        assertTrue(techTags.contains("MongoDB"));
    }

    @Test
    @DisplayName("extractTechTags should extract tags directly from project")
    void testExtractTechTags_FromProject() throws Exception {
        // Arrange
        when(projectRepository.findById("project789")).thenReturn(Optional.of(testProject));
        lenient().when(userInterestProfileRepository.findByUserId("user123")).thenReturn(Optional.empty());
        when(userInterestProfileRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(behavioralSignalRepository.save(any(BehavioralSignal.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        behavioralTrackingService.trackView("user123", "project789", ContentType.PROJECT, 15);
        Thread.sleep(200);

        // Assert - verify signal was saved with correct tech tags
        ArgumentCaptor<BehavioralSignal> signalCaptor = ArgumentCaptor.forClass(BehavioralSignal.class);
        verify(behavioralSignalRepository, atLeastOnce()).save(signalCaptor.capture());
        
        BehavioralSignal savedSignal = signalCaptor.getValue();
        List<String> techTags = savedSignal.getTechTags();
        
        assertEquals(3, techTags.size(), "Should have 3 tech tags from project");
        assertTrue(techTags.contains("Java"));
        assertTrue(techTags.contains("Spring"));
        assertTrue(techTags.contains("MongoDB"));
    }

    // ========== Tests for time decay scheduled job ==========

    @Test
    @DisplayName("applyTimeDecayToAllProfiles should apply decay to all profiles with signals")
    void testApplyTimeDecayToAllProfiles_AppliesDecay() {
        // Arrange
        UserInterestProfile profile1 = UserInterestProfile.builder()
                .id("profile1")
                .userId("user1")
                .techTagScores(Map.of("Java", 0.8, "Python", 0.6))
                .totalSignals(5)
                .lastSignalAt(Instant.now().minus(30, java.time.temporal.ChronoUnit.DAYS))
                .build();

        UserInterestProfile profile2 = UserInterestProfile.builder()
                .id("profile2")
                .userId("user2")
                .techTagScores(Map.of("React", 0.9, "TypeScript", 0.7))
                .totalSignals(10)
                .lastSignalAt(Instant.now().minus(60, java.time.temporal.ChronoUnit.DAYS))
                .build();

        when(userInterestProfileRepository.findAll(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(profile1, profile2)));
        when(userInterestProfileRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        behavioralTrackingService.applyTimeDecayToAllProfiles();

        // Assert
        ArgumentCaptor<UserInterestProfile> profileCaptor = ArgumentCaptor.forClass(UserInterestProfile.class);
        verify(userInterestProfileRepository, times(2)).save(profileCaptor.capture());

        List<UserInterestProfile> savedProfiles = profileCaptor.getAllValues();
        
        // Verify both profiles were updated
        assertEquals(2, savedProfiles.size());
        
        // Verify decay was applied (scores should be lower than original)
        for (UserInterestProfile savedProfile : savedProfiles) {
            for (Double score : savedProfile.getTechTagScores().values()) {
                assertTrue(score < 1.0, "Scores should be reduced by decay");
                assertTrue(score >= 0.0, "Scores should not be negative");
            }
        }
    }

    @Test
    @DisplayName("applyTimeDecayToAllProfiles should skip profiles with no signals")
    void testApplyTimeDecayToAllProfiles_SkipsProfilesWithNoSignals() {
        // Arrange
        UserInterestProfile profileWithSignals = UserInterestProfile.builder()
                .id("profile1")
                .userId("user1")
                .techTagScores(Map.of("Java", 0.8))
                .totalSignals(5)
                .lastSignalAt(Instant.now().minus(30, java.time.temporal.ChronoUnit.DAYS))
                .build();

        UserInterestProfile profileWithoutSignals = UserInterestProfile.builder()
                .id("profile2")
                .userId("user2")
                .techTagScores(Map.of("Python", 0.6))
                .totalSignals(0)
                .lastSignalAt(null)
                .build();

        when(userInterestProfileRepository.findAll(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(profileWithSignals, profileWithoutSignals)));
        when(userInterestProfileRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        behavioralTrackingService.applyTimeDecayToAllProfiles();

        // Assert - only profile with signals should be saved
        ArgumentCaptor<UserInterestProfile> profileCaptor = ArgumentCaptor.forClass(UserInterestProfile.class);
        verify(userInterestProfileRepository, times(1)).save(profileCaptor.capture());

        UserInterestProfile savedProfile = profileCaptor.getValue();
        assertEquals("user1", savedProfile.getUserId(), "Only profile with signals should be updated");
    }

    @Test
    @DisplayName("applyTimeDecayToAllProfiles should skip profiles with null lastSignalAt")
    void testApplyTimeDecayToAllProfiles_SkipsProfilesWithNullLastSignalAt() {
        // Arrange
        UserInterestProfile profileWithTimestamp = UserInterestProfile.builder()
                .id("profile1")
                .userId("user1")
                .techTagScores(Map.of("Java", 0.8))
                .totalSignals(5)
                .lastSignalAt(Instant.now().minus(30, java.time.temporal.ChronoUnit.DAYS))
                .build();

        UserInterestProfile profileWithoutTimestamp = UserInterestProfile.builder()
                .id("profile2")
                .userId("user2")
                .techTagScores(Map.of("Python", 0.6))
                .totalSignals(3)
                .lastSignalAt(null)
                .build();

        when(userInterestProfileRepository.findAll(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(profileWithTimestamp, profileWithoutTimestamp)));
        when(userInterestProfileRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        behavioralTrackingService.applyTimeDecayToAllProfiles();

        // Assert - only profile with timestamp should be saved
        ArgumentCaptor<UserInterestProfile> profileCaptor = ArgumentCaptor.forClass(UserInterestProfile.class);
        verify(userInterestProfileRepository, times(1)).save(profileCaptor.capture());

        UserInterestProfile savedProfile = profileCaptor.getValue();
        assertEquals("user1", savedProfile.getUserId(), "Only profile with timestamp should be updated");
    }

    @Test
    @DisplayName("applyTimeDecayToAllProfiles should handle empty profile list")
    void testApplyTimeDecayToAllProfiles_HandlesEmptyList() {
        // Arrange
        when(userInterestProfileRepository.findAll(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        // Act
        behavioralTrackingService.applyTimeDecayToAllProfiles();

        // Assert - no profiles should be saved
        verify(userInterestProfileRepository, never()).save(any());
    }

    @Test
    @DisplayName("applyTimeDecayToAllProfiles should continue processing after individual profile error")
    void testApplyTimeDecayToAllProfiles_ContinuesAfterError() {
        // Arrange
        UserInterestProfile profile1 = UserInterestProfile.builder()
                .id("profile1")
                .userId("user1")
                .techTagScores(Map.of("Java", 0.8))
                .totalSignals(5)
                .lastSignalAt(Instant.now().minus(30, java.time.temporal.ChronoUnit.DAYS))
                .build();

        UserInterestProfile profile2 = UserInterestProfile.builder()
                .id("profile2")
                .userId("user2")
                .techTagScores(Map.of("Python", 0.6))
                .totalSignals(3)
                .lastSignalAt(Instant.now().minus(15, java.time.temporal.ChronoUnit.DAYS))
                .build();

        when(userInterestProfileRepository.findAll(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(profile1, profile2)));
        
        // Make the first save throw an exception, but second should succeed
        when(userInterestProfileRepository.save(any()))
                .thenThrow(new RuntimeException("Database error"))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        behavioralTrackingService.applyTimeDecayToAllProfiles();

        // Assert - both profiles should be attempted to save
        verify(userInterestProfileRepository, times(2)).save(any());
    }

    @Test
    @DisplayName("applyTimeDecayToAllProfiles should update updatedAt timestamp")
    void testApplyTimeDecayToAllProfiles_UpdatesTimestamp() {
        // Arrange
        Instant oldTimestamp = Instant.now().minus(1, java.time.temporal.ChronoUnit.DAYS);
        UserInterestProfile profile = UserInterestProfile.builder()
                .id("profile1")
                .userId("user1")
                .techTagScores(Map.of("Java", 0.8))
                .totalSignals(5)
                .lastSignalAt(Instant.now().minus(30, java.time.temporal.ChronoUnit.DAYS))
                .updatedAt(oldTimestamp)
                .build();

        when(userInterestProfileRepository.findAll(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(profile)));
        when(userInterestProfileRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        behavioralTrackingService.applyTimeDecayToAllProfiles();

        // Assert
        ArgumentCaptor<UserInterestProfile> profileCaptor = ArgumentCaptor.forClass(UserInterestProfile.class);
        verify(userInterestProfileRepository).save(profileCaptor.capture());

        UserInterestProfile savedProfile = profileCaptor.getValue();
        assertTrue(savedProfile.getUpdatedAt().isAfter(oldTimestamp), 
                   "updatedAt timestamp should be updated to current time");
    }
}
