package com.shipspace.service;

import com.shipspace.model.BehavioralSignal;
import com.shipspace.model.Project;
import com.shipspace.model.Ship;
import com.shipspace.repository.BehavioralSignalRepository;
import com.shipspace.repository.ContentScoreRepository;
import com.shipspace.repository.ProjectRepository;
import com.shipspace.repository.ShipRepository;
import com.shipspace.repository.UserInterestProfileRepository;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.LongRange;
import net.jqwik.api.lifecycle.BeforeTry;
import org.mockito.Mockito;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Property-based tests for quality score bounds in PersonalizationService.
 * 
 * These tests verify that the calculateQualityScore method always returns
 * values within the expected bounds (0.0 to 1.0) regardless of input values.
 * 
 * The quality score formula is:
 * raw_quality = (likes * 1.0) + (comments * 2.0) + (views * 0.5) + (follows * 3.0)
 * age_factor = max(1.0, hours_since_creation)
 * normalized_quality = raw_quality / sqrt(age_factor)
 * quality_score = min(1.0, normalized_quality / 100.0)
 * 
 * **Validates: Requirements 2.1, 2.2, 2.3, 2.4**
 */
class QualityScoreBoundsPropertyTest {

    private PersonalizationService personalizationService;
    private ShipRepository shipRepository;
    private ProjectRepository projectRepository;
    private BehavioralSignalRepository behavioralSignalRepository;
    private UserInterestProfileRepository userInterestProfileRepository;
    private ContentScoreRepository contentScoreRepository;

    @BeforeTry
    void setUp() {
        // Create mocks for all dependencies
        userInterestProfileRepository = Mockito.mock(UserInterestProfileRepository.class);
        behavioralSignalRepository = Mockito.mock(BehavioralSignalRepository.class);
        contentScoreRepository = Mockito.mock(ContentScoreRepository.class);
        shipRepository = Mockito.mock(ShipRepository.class);
        projectRepository = Mockito.mock(ProjectRepository.class);

        // Create the service with mocked dependencies
        personalizationService = new PersonalizationService(
                userInterestProfileRepository,
                behavioralSignalRepository,
                contentScoreRepository,
                shipRepository,
                projectRepository
        );
    }

    /**
     * Property: Quality scores are bounded
     * 
     * For any content with any engagement metrics (likes, comments, views, follows)
     * and any age, the quality score must always be between 0.0 and 1.0 (inclusive).
     * 
     * This property verifies that:
     * 1. Quality score is never negative
     * 2. Quality score is never greater than 1.0
     * 3. The bounds hold for all possible combinations of engagement metrics
     * 4. The bounds hold for content of any age (from brand new to very old)
     * 
     * **Validates: Requirements 2.1, 2.2, 2.3, 2.4**
     */
    @Property(tries = 200)
    @Label("Feature: personalized-feed-algorithm, Property: Quality scores are bounded")
    void qualityScoresAreBounded(
            @ForAll @IntRange(min = 0, max = 10000) int likes,
            @ForAll @IntRange(min = 0, max = 5000) int comments,
            @ForAll @LongRange(min = 0, max = 100000) long views,
            @ForAll @LongRange(min = 0, max = 1000) long follows,
            @ForAll @IntRange(min = 0, max = 8760) int hoursOld // 0 to 1 year
    ) {
        // Arrange - Create a ship with the given engagement metrics
        Instant createdAt = Instant.now().minus(hoursOld, ChronoUnit.HOURS);
        
        Ship testShip = Ship.builder()
                .id("testShip")
                .userId("builder123")
                .projectId("project456")
                .content("Test ship content")
                .likeCount(likes)
                .commentCount(comments)
                .createdAt(createdAt)
                .build();

        // Mock repository responses
        when(shipRepository.findById("testShip")).thenReturn(Optional.of(testShip));
        when(behavioralSignalRepository.countByContentIdAndSignalType(
                "testShip", BehavioralSignal.SignalType.VIEW)).thenReturn(views);
        when(behavioralSignalRepository.countByContentIdAndSignalType(
                "testShip", BehavioralSignal.SignalType.FOLLOW)).thenReturn(follows);

        // Act - Calculate quality score
        double qualityScore = personalizationService.calculateQualityScore(
                "testShip", BehavioralSignal.ContentType.SHIP);

        // Assert - Verify bounds
        assertTrue(qualityScore >= 0.0,
                String.format("Quality score should be >= 0.0, got: %.6f (likes=%d, comments=%d, views=%d, follows=%d, age=%dh)",
                        qualityScore, likes, comments, views, follows, hoursOld));
        
        assertTrue(qualityScore <= 1.0,
                String.format("Quality score should be <= 1.0, got: %.6f (likes=%d, comments=%d, views=%d, follows=%d, age=%dh)",
                        qualityScore, likes, comments, views, follows, hoursOld));
    }

    /**
     * Property: Quality scores are bounded for projects
     * 
     * For any project with any engagement metrics (views, follows) and any age,
     * the quality score must always be between 0.0 and 1.0 (inclusive).
     * 
     * Note: Projects don't have direct like/comment counts in the current implementation,
     * so we test with views and follows only.
     * 
     * **Validates: Requirements 2.1, 2.2, 2.3, 2.4**
     */
    @Property(tries = 200)
    @Label("Feature: personalized-feed-algorithm, Property: Quality scores are bounded for projects")
    void qualityScoresAreBoundedForProjects(
            @ForAll @LongRange(min = 0, max = 100000) long views,
            @ForAll @LongRange(min = 0, max = 1000) long follows,
            @ForAll @IntRange(min = 0, max = 8760) int hoursOld // 0 to 1 year
    ) {
        // Arrange - Create a project with the given engagement metrics
        Instant createdAt = Instant.now().minus(hoursOld, ChronoUnit.HOURS);
        
        Project testProject = Project.builder()
                .id("testProject")
                .userId("builder123")
                .name("Test Project")
                .description("Test project description")
                .createdAt(createdAt)
                .build();

        // Mock repository responses
        when(projectRepository.findById("testProject")).thenReturn(Optional.of(testProject));
        when(behavioralSignalRepository.countByContentIdAndSignalType(
                "testProject", BehavioralSignal.SignalType.VIEW)).thenReturn(views);
        when(behavioralSignalRepository.countByContentIdAndSignalType(
                "testProject", BehavioralSignal.SignalType.FOLLOW)).thenReturn(follows);

        // Act - Calculate quality score
        double qualityScore = personalizationService.calculateQualityScore(
                "testProject", BehavioralSignal.ContentType.PROJECT);

        // Assert - Verify bounds
        assertTrue(qualityScore >= 0.0,
                String.format("Quality score should be >= 0.0, got: %.6f (views=%d, follows=%d, age=%dh)",
                        qualityScore, views, follows, hoursOld));
        
        assertTrue(qualityScore <= 1.0,
                String.format("Quality score should be <= 1.0, got: %.6f (views=%d, follows=%d, age=%dh)",
                        qualityScore, views, follows, hoursOld));
    }

    /**
     * Property: Quality scores are bounded with null engagement counts
     * 
     * For any ship with null like/comment counts (which should be treated as 0),
     * the quality score must still be between 0.0 and 1.0.
     * 
     * This tests the robustness of the implementation when dealing with missing data.
     * 
     * **Validates: Requirements 2.1, 2.2, 2.3, 2.4**
     */
    @Property(tries = 100)
    @Label("Feature: personalized-feed-algorithm, Property: Quality scores are bounded with null counts")
    void qualityScoresAreBoundedWithNullCounts(
            @ForAll @LongRange(min = 0, max = 10000) long views,
            @ForAll @LongRange(min = 0, max = 500) long follows,
            @ForAll @IntRange(min = 0, max = 2000) int hoursOld
    ) {
        // Arrange - Create a ship with null like and comment counts
        Instant createdAt = Instant.now().minus(hoursOld, ChronoUnit.HOURS);
        
        Ship testShip = Ship.builder()
                .id("testShip")
                .userId("builder123")
                .projectId("project456")
                .content("Test ship content")
                .likeCount(null) // Null count
                .commentCount(null) // Null count
                .createdAt(createdAt)
                .build();

        // Mock repository responses
        when(shipRepository.findById("testShip")).thenReturn(Optional.of(testShip));
        when(behavioralSignalRepository.countByContentIdAndSignalType(
                "testShip", BehavioralSignal.SignalType.VIEW)).thenReturn(views);
        when(behavioralSignalRepository.countByContentIdAndSignalType(
                "testShip", BehavioralSignal.SignalType.FOLLOW)).thenReturn(follows);

        // Act - Calculate quality score
        double qualityScore = personalizationService.calculateQualityScore(
                "testShip", BehavioralSignal.ContentType.SHIP);

        // Assert - Verify bounds
        assertTrue(qualityScore >= 0.0,
                String.format("Quality score should be >= 0.0 with null counts, got: %.6f (views=%d, follows=%d, age=%dh)",
                        qualityScore, views, follows, hoursOld));
        
        assertTrue(qualityScore <= 1.0,
                String.format("Quality score should be <= 1.0 with null counts, got: %.6f (views=%d, follows=%d, age=%dh)",
                        qualityScore, views, follows, hoursOld));
    }

    /**
     * Property: Quality scores are bounded for extreme engagement values
     * 
     * Tests the bounds with extremely high engagement values to ensure
     * the capping at 1.0 works correctly even with viral content.
     * 
     * **Validates: Requirements 2.1, 2.2, 2.3, 2.4**
     */
    @Property(tries = 100)
    @Label("Feature: personalized-feed-algorithm, Property: Quality scores are bounded for extreme engagement")
    void qualityScoresAreBoundedForExtremeEngagement(
            @ForAll @IntRange(min = 10000, max = 1000000) int likes,
            @ForAll @IntRange(min = 5000, max = 500000) int comments,
            @ForAll @LongRange(min = 100000, max = 10000000) long views,
            @ForAll @LongRange(min = 1000, max = 100000) long follows,
            @ForAll @IntRange(min = 1, max = 168) int hoursOld // 1 hour to 1 week
    ) {
        // Arrange - Create a viral ship with extreme engagement
        Instant createdAt = Instant.now().minus(hoursOld, ChronoUnit.HOURS);
        
        Ship viralShip = Ship.builder()
                .id("viralShip")
                .userId("builder123")
                .projectId("project456")
                .content("Viral ship content")
                .likeCount(likes)
                .commentCount(comments)
                .createdAt(createdAt)
                .build();

        // Mock repository responses
        when(shipRepository.findById("viralShip")).thenReturn(Optional.of(viralShip));
        when(behavioralSignalRepository.countByContentIdAndSignalType(
                "viralShip", BehavioralSignal.SignalType.VIEW)).thenReturn(views);
        when(behavioralSignalRepository.countByContentIdAndSignalType(
                "viralShip", BehavioralSignal.SignalType.FOLLOW)).thenReturn(follows);

        // Act - Calculate quality score
        double qualityScore = personalizationService.calculateQualityScore(
                "viralShip", BehavioralSignal.ContentType.SHIP);

        // Assert - Verify bounds (should be capped at 1.0)
        assertTrue(qualityScore >= 0.0,
                String.format("Quality score should be >= 0.0 for viral content, got: %.6f", qualityScore));
        
        assertTrue(qualityScore <= 1.0,
                String.format("Quality score should be capped at 1.0 for viral content, got: %.6f", qualityScore));
        
        // For extreme engagement, we expect the score to be at or very close to 1.0
        assertTrue(qualityScore >= 0.9,
                String.format("Quality score should be near 1.0 for extreme engagement, got: %.6f", qualityScore));
    }

    /**
     * Property: Quality scores are bounded for zero engagement
     * 
     * Tests that content with absolutely no engagement (all zeros) still
     * produces a valid score (should be 0.0).
     * 
     * **Validates: Requirements 2.1, 2.2, 2.3, 2.4**
     */
    @Property(tries = 50)
    @Label("Feature: personalized-feed-algorithm, Property: Quality scores are bounded for zero engagement")
    void qualityScoresAreBoundedForZeroEngagement(
            @ForAll @IntRange(min = 0, max = 8760) int hoursOld
    ) {
        // Arrange - Create a ship with zero engagement
        Instant createdAt = Instant.now().minus(hoursOld, ChronoUnit.HOURS);
        
        Ship noEngagementShip = Ship.builder()
                .id("noEngagement")
                .userId("builder123")
                .projectId("project456")
                .content("No engagement ship")
                .likeCount(0)
                .commentCount(0)
                .createdAt(createdAt)
                .build();

        // Mock repository responses
        when(shipRepository.findById("noEngagement")).thenReturn(Optional.of(noEngagementShip));
        when(behavioralSignalRepository.countByContentIdAndSignalType(
                "noEngagement", BehavioralSignal.SignalType.VIEW)).thenReturn(0L);
        when(behavioralSignalRepository.countByContentIdAndSignalType(
                "noEngagement", BehavioralSignal.SignalType.FOLLOW)).thenReturn(0L);

        // Act - Calculate quality score
        double qualityScore = personalizationService.calculateQualityScore(
                "noEngagement", BehavioralSignal.ContentType.SHIP);

        // Assert - Verify bounds and expected value
        assertTrue(qualityScore >= 0.0,
                String.format("Quality score should be >= 0.0 for zero engagement, got: %.6f", qualityScore));
        
        assertTrue(qualityScore <= 1.0,
                String.format("Quality score should be <= 1.0 for zero engagement, got: %.6f", qualityScore));
        
        assertEquals(0.0, qualityScore, 0.0001,
                String.format("Quality score should be 0.0 for zero engagement, got: %.6f", qualityScore));
    }
}
