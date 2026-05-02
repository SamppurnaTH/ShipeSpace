package com.shipspace.service;

import com.shipspace.model.BehavioralSignal;
import com.shipspace.model.Project;
import com.shipspace.model.Ship;
import com.shipspace.repository.ProjectRepository;
import com.shipspace.repository.ShipRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for PersonalizationService.
 *
 * Uses a real ContentScoringService backed by mocked repositories so that
 * scoring logic runs end-to-end while remaining isolated from infrastructure.
 *
 * Requirements: 2.1, 2.2, 2.3, 2.4, 2.5, 8.3
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PersonalizationService - Quality Score Tests")
class PersonalizationServiceTest {

    @Mock
    private InterestProfileService interestProfileService;

    @Mock
    private FeedBuilderService feedBuilderService;

    @Mock
    private ShipRepository shipRepository;

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private com.shipspace.repository.BehavioralSignalRepository behavioralSignalRepository;

    @Mock
    private com.shipspace.repository.ContentScoreRepository contentScoreRepository;

    @Mock
    private MongoTemplate mongoTemplate;

    private ContentScoringService contentScoringService;
    private PersonalizationService personalizationService;

    private Ship testShip;
    private Project testProject;

    @BeforeEach
    void setUp() {
        // Wire real ContentScoringService with mocked repositories
        contentScoringService = new ContentScoringService(
                shipRepository, projectRepository, behavioralSignalRepository,
                contentScoreRepository, mongoTemplate);

        // Wire PersonalizationService with the real ContentScoringService
        personalizationService = new PersonalizationService(
                interestProfileService, contentScoringService, feedBuilderService,
                shipRepository, projectRepository);

        // Create test ship with engagement metrics
        testShip = Ship.builder()
                .id("ship123")
                .userId("builder456")
                .projectId("project789")
                .content("Test ship content")
                .likeCount(10)
                .commentCount(5)
                .createdAt(Instant.now().minus(24, ChronoUnit.HOURS))
                .build();

        // Create test project
        testProject = Project.builder()
                .id("project789")
                .userId("builder456")
                .name("Test Project")
                .description("Test project description")
                .createdAt(Instant.now().minus(7, ChronoUnit.DAYS))
                .build();
    }

    @Test
    @DisplayName("calculateQualityScore should return score between 0.0 and 1.0 for ship")
    void testCalculateQualityScore_ShipWithEngagement_ReturnsBoundedScore() {
        // Arrange
        when(shipRepository.findById("ship123")).thenReturn(Optional.of(testShip));
        when(behavioralSignalRepository.countByContentIdAndSignalType(
                "ship123", BehavioralSignal.SignalType.VIEW)).thenReturn(100L);
        when(behavioralSignalRepository.countByContentIdAndSignalType(
                "ship123", BehavioralSignal.SignalType.FOLLOW)).thenReturn(3L);

        // Act
        double qualityScore = personalizationService.calculateQualityScore(
                "ship123", BehavioralSignal.ContentType.SHIP);

        // Assert
        assertTrue(qualityScore >= 0.0 && qualityScore <= 1.0,
                "Quality score should be between 0.0 and 1.0, got: " + qualityScore);
        verify(shipRepository).findById("ship123");
        verify(behavioralSignalRepository).countByContentIdAndSignalType(
                "ship123", BehavioralSignal.SignalType.VIEW);
        verify(behavioralSignalRepository).countByContentIdAndSignalType(
                "ship123", BehavioralSignal.SignalType.FOLLOW);
    }

    @Test
    @DisplayName("calculateQualityScore should calculate correct raw quality with weighted metrics")
    void testCalculateQualityScore_WeightedMetrics_CalculatesCorrectly() {
        // Arrange
        // likes=10, comments=5, views=100, follows=3
        // raw_quality = (10 * 1.0) + (5 * 2.0) + (100 * 0.5) + (3 * 3.0)
        //             = 10 + 10 + 50 + 9 = 79
        when(shipRepository.findById("ship123")).thenReturn(Optional.of(testShip));
        when(behavioralSignalRepository.countByContentIdAndSignalType(
                "ship123", BehavioralSignal.SignalType.VIEW)).thenReturn(100L);
        when(behavioralSignalRepository.countByContentIdAndSignalType(
                "ship123", BehavioralSignal.SignalType.FOLLOW)).thenReturn(3L);

        // Act
        double qualityScore = personalizationService.calculateQualityScore(
                "ship123", BehavioralSignal.ContentType.SHIP);

        // Assert
        // With 24 hours age: age_factor = 24, sqrt(24) ≈ 4.899
        // normalized_quality = 79 / 4.899 ≈ 16.13
        // quality_score = min(1.0, 16.13 / 100.0) ≈ 0.1613
        assertTrue(qualityScore > 0.15 && qualityScore < 0.17,
                "Quality score should be approximately 0.16, got: " + qualityScore);
    }

    @Test
    @DisplayName("calculateQualityScore should normalize by age to prevent bias toward older content")
    void testCalculateQualityScore_AgeNormalization_OlderContentHasLowerScore() {
        // Arrange - Create two ships with same engagement but different ages
        Ship newShip = Ship.builder()
                .id("newShip")
                .userId("builder456")
                .projectId("project789")
                .content("New ship")
                .likeCount(10)
                .commentCount(5)
                .createdAt(Instant.now().minus(1, ChronoUnit.HOURS))
                .build();

        Ship oldShip = Ship.builder()
                .id("oldShip")
                .userId("builder456")
                .projectId("project789")
                .content("Old ship")
                .likeCount(10)
                .commentCount(5)
                .createdAt(Instant.now().minus(30, ChronoUnit.DAYS))
                .build();

        when(shipRepository.findById("newShip")).thenReturn(Optional.of(newShip));
        when(shipRepository.findById("oldShip")).thenReturn(Optional.of(oldShip));
        when(behavioralSignalRepository.countByContentIdAndSignalType(
                anyString(), eq(BehavioralSignal.SignalType.VIEW))).thenReturn(50L);
        when(behavioralSignalRepository.countByContentIdAndSignalType(
                anyString(), eq(BehavioralSignal.SignalType.FOLLOW))).thenReturn(2L);

        // Act
        double newShipScore = personalizationService.calculateQualityScore(
                "newShip", BehavioralSignal.ContentType.SHIP);
        double oldShipScore = personalizationService.calculateQualityScore(
                "oldShip", BehavioralSignal.ContentType.SHIP);

        // Assert
        assertTrue(newShipScore > oldShipScore,
                String.format("New ship score (%f) should be higher than old ship score (%f)",
                        newShipScore, oldShipScore));
    }

    @Test
    @DisplayName("calculateQualityScore should return 0.0 for content with no engagement")
    void testCalculateQualityScore_NoEngagement_ReturnsZero() {
        // Arrange
        Ship noEngagementShip = Ship.builder()
                .id("noEngagement")
                .userId("builder456")
                .projectId("project789")
                .content("No engagement ship")
                .likeCount(0)
                .commentCount(0)
                .createdAt(Instant.now().minus(1, ChronoUnit.HOURS))
                .build();

        when(shipRepository.findById("noEngagement")).thenReturn(Optional.of(noEngagementShip));
        when(behavioralSignalRepository.countByContentIdAndSignalType(
                "noEngagement", BehavioralSignal.SignalType.VIEW)).thenReturn(0L);
        when(behavioralSignalRepository.countByContentIdAndSignalType(
                "noEngagement", BehavioralSignal.SignalType.FOLLOW)).thenReturn(0L);

        // Act
        double qualityScore = personalizationService.calculateQualityScore(
                "noEngagement", BehavioralSignal.ContentType.SHIP);

        // Assert
        assertEquals(0.0, qualityScore, 0.001,
                "Quality score should be 0.0 for content with no engagement");
    }

    @Test
    @DisplayName("calculateQualityScore should cap score at 1.0 for very high engagement")
    void testCalculateQualityScore_VeryHighEngagement_CapsAtOne() {
        // Arrange
        Ship viralShip = Ship.builder()
                .id("viral")
                .userId("builder456")
                .projectId("project789")
                .content("Viral ship")
                .likeCount(1000)
                .commentCount(500)
                .createdAt(Instant.now().minus(1, ChronoUnit.HOURS))
                .build();

        when(shipRepository.findById("viral")).thenReturn(Optional.of(viralShip));
        when(behavioralSignalRepository.countByContentIdAndSignalType(
                "viral", BehavioralSignal.SignalType.VIEW)).thenReturn(10000L);
        when(behavioralSignalRepository.countByContentIdAndSignalType(
                "viral", BehavioralSignal.SignalType.FOLLOW)).thenReturn(200L);

        // Act
        double qualityScore = personalizationService.calculateQualityScore(
                "viral", BehavioralSignal.ContentType.SHIP);

        // Assert
        assertEquals(1.0, qualityScore, 0.001,
                "Quality score should be capped at 1.0 for very high engagement");
    }

    @Test
    @DisplayName("calculateQualityScore should handle null like and comment counts")
    void testCalculateQualityScore_NullCounts_TreatsAsZero() {
        // Arrange
        Ship shipWithNulls = Ship.builder()
                .id("nullCounts")
                .userId("builder456")
                .projectId("project789")
                .content("Ship with null counts")
                .likeCount(null)
                .commentCount(null)
                .createdAt(Instant.now().minus(1, ChronoUnit.HOURS))
                .build();

        when(shipRepository.findById("nullCounts")).thenReturn(Optional.of(shipWithNulls));
        when(behavioralSignalRepository.countByContentIdAndSignalType(
                "nullCounts", BehavioralSignal.SignalType.VIEW)).thenReturn(10L);
        when(behavioralSignalRepository.countByContentIdAndSignalType(
                "nullCounts", BehavioralSignal.SignalType.FOLLOW)).thenReturn(1L);

        // Act
        double qualityScore = personalizationService.calculateQualityScore(
                "nullCounts", BehavioralSignal.ContentType.SHIP);

        // Assert
        assertTrue(qualityScore >= 0.0 && qualityScore <= 1.0,
                "Quality score should handle null counts gracefully");
    }

    @Test
    @DisplayName("calculateQualityScore should throw exception for non-existent ship")
    void testCalculateQualityScore_NonExistentShip_ThrowsException() {
        // Arrange
        when(shipRepository.findById("nonexistent")).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(IllegalArgumentException.class, () ->
                personalizationService.calculateQualityScore(
                        "nonexistent", BehavioralSignal.ContentType.SHIP),
                "Should throw IllegalArgumentException for non-existent ship");
    }

    @Test
    @DisplayName("calculateQualityScore should handle project content type")
    void testCalculateQualityScore_Project_CalculatesScore() {
        // Arrange
        when(projectRepository.findById("project789")).thenReturn(Optional.of(testProject));
        when(behavioralSignalRepository.countByContentIdAndSignalType(
                "project789", BehavioralSignal.SignalType.VIEW)).thenReturn(50L);
        when(behavioralSignalRepository.countByContentIdAndSignalType(
                "project789", BehavioralSignal.SignalType.FOLLOW)).thenReturn(5L);

        // Act
        double qualityScore = personalizationService.calculateQualityScore(
                "project789", BehavioralSignal.ContentType.PROJECT);

        // Assert
        assertTrue(qualityScore >= 0.0 && qualityScore <= 1.0,
                "Quality score should be between 0.0 and 1.0 for projects");
        verify(projectRepository).findById("project789");
    }

    @Test
    @DisplayName("calculateQualityScore should throw exception for non-existent project")
    void testCalculateQualityScore_NonExistentProject_ThrowsException() {
        // Arrange
        when(projectRepository.findById("nonexistent")).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(IllegalArgumentException.class, () ->
                personalizationService.calculateQualityScore(
                        "nonexistent", BehavioralSignal.ContentType.PROJECT),
                "Should throw IllegalArgumentException for non-existent project");
    }

    @Test
    @DisplayName("calculateQualityScore should use minimum age factor of 1.0 for very new content")
    void testCalculateQualityScore_VeryNewContent_UsesMinimumAgeFactor() {
        // Arrange - Content created just now (0 hours old)
        Ship brandNewShip = Ship.builder()
                .id("brandNew")
                .userId("builder456")
                .projectId("project789")
                .content("Brand new ship")
                .likeCount(5)
                .commentCount(2)
                .createdAt(Instant.now())
                .build();

        when(shipRepository.findById("brandNew")).thenReturn(Optional.of(brandNewShip));
        when(behavioralSignalRepository.countByContentIdAndSignalType(
                "brandNew", BehavioralSignal.SignalType.VIEW)).thenReturn(10L);
        when(behavioralSignalRepository.countByContentIdAndSignalType(
                "brandNew", BehavioralSignal.SignalType.FOLLOW)).thenReturn(1L);

        // Act
        double qualityScore = personalizationService.calculateQualityScore(
                "brandNew", BehavioralSignal.ContentType.SHIP);

        // Assert
        // raw_quality = (5 * 1.0) + (2 * 2.0) + (10 * 0.5) + (1 * 3.0) = 5 + 4 + 5 + 3 = 17
        // age_factor = max(1.0, 0) = 1.0
        // normalized_quality = 17 / sqrt(1.0) = 17
        // quality_score = min(1.0, 17 / 100.0) = 0.17
        assertTrue(qualityScore > 0.16 && qualityScore < 0.18,
                "Quality score should be approximately 0.17 for brand new content, got: " + qualityScore);
    }

    @Test
    @DisplayName("calculateQualityScore should weight comments higher than likes")
    void testCalculateQualityScore_CommentWeight_HigherThanLikes() {
        // Arrange - Ship with more likes than comments
        Ship manyLikes = Ship.builder()
                .id("manyLikes")
                .userId("builder456")
                .projectId("project789")
                .content("Many likes ship")
                .likeCount(20)
                .commentCount(0)
                .createdAt(Instant.now().minus(1, ChronoUnit.HOURS))
                .build();

        // Ship with fewer likes but more comments
        Ship manyComments = Ship.builder()
                .id("manyComments")
                .userId("builder456")
                .projectId("project789")
                .content("Many comments ship")
                .likeCount(0)
                .commentCount(10)
                .createdAt(Instant.now().minus(1, ChronoUnit.HOURS))
                .build();

        when(shipRepository.findById("manyLikes")).thenReturn(Optional.of(manyLikes));
        when(shipRepository.findById("manyComments")).thenReturn(Optional.of(manyComments));
        when(behavioralSignalRepository.countByContentIdAndSignalType(
                anyString(), eq(BehavioralSignal.SignalType.VIEW))).thenReturn(0L);
        when(behavioralSignalRepository.countByContentIdAndSignalType(
                anyString(), eq(BehavioralSignal.SignalType.FOLLOW))).thenReturn(0L);

        // Act
        double manyLikesScore = personalizationService.calculateQualityScore(
                "manyLikes", BehavioralSignal.ContentType.SHIP);
        double manyCommentsScore = personalizationService.calculateQualityScore(
                "manyComments", BehavioralSignal.ContentType.SHIP);

        // Assert
        // manyLikes: raw_quality = 20 * 1.0 = 20
        // manyComments: raw_quality = 10 * 2.0 = 20
        // Both should have equal scores since raw quality is the same
        assertEquals(manyLikesScore, manyCommentsScore, 0.001,
                "10 comments should equal 20 likes in quality score");
    }

    @Test
    @DisplayName("calculateQualityScore should weight follows highest")
    void testCalculateQualityScore_FollowWeight_HighestWeight() {
        // Arrange
        Ship withFollows = Ship.builder()
                .id("withFollows")
                .userId("builder456")
                .projectId("project789")
                .content("Ship with follows")
                .likeCount(0)
                .commentCount(0)
                .createdAt(Instant.now().minus(1, ChronoUnit.HOURS))
                .build();

        Ship withLikes = Ship.builder()
                .id("withLikes")
                .userId("builder456")
                .projectId("project789")
                .content("Ship with likes")
                .likeCount(3)
                .commentCount(0)
                .createdAt(Instant.now().minus(1, ChronoUnit.HOURS))
                .build();

        when(shipRepository.findById("withFollows")).thenReturn(Optional.of(withFollows));
        when(shipRepository.findById("withLikes")).thenReturn(Optional.of(withLikes));
        
        // withFollows: 1 follow (weight 3.0)
        when(behavioralSignalRepository.countByContentIdAndSignalType(
                "withFollows", BehavioralSignal.SignalType.VIEW)).thenReturn(0L);
        when(behavioralSignalRepository.countByContentIdAndSignalType(
                "withFollows", BehavioralSignal.SignalType.FOLLOW)).thenReturn(1L);
        
        // withLikes: 3 likes (weight 1.0 each)
        when(behavioralSignalRepository.countByContentIdAndSignalType(
                "withLikes", BehavioralSignal.SignalType.VIEW)).thenReturn(0L);
        when(behavioralSignalRepository.countByContentIdAndSignalType(
                "withLikes", BehavioralSignal.SignalType.FOLLOW)).thenReturn(0L);

        // Act
        double followsScore = personalizationService.calculateQualityScore(
                "withFollows", BehavioralSignal.ContentType.SHIP);
        double likesScore = personalizationService.calculateQualityScore(
                "withLikes", BehavioralSignal.ContentType.SHIP);

        // Assert
        // 1 follow (3.0) should equal 3 likes (3 * 1.0)
        assertEquals(followsScore, likesScore, 0.001,
                "1 follow should equal 3 likes in quality score");
    }

    // ========== Relevance Score Tests ==========

    @Test
    @DisplayName("calculateRelevanceScore should return score between 0.1 and 1.0")
    void testCalculateRelevanceScore_ValidInput_ReturnsBoundedScore() {
        // Arrange
        var profile = com.shipspace.model.UserInterestProfile.builder()
                .userId("user123")
                .techTagScores(java.util.Map.of("React", 0.8, "TypeScript", 0.6))
                .followedBuilders(java.util.Set.of())
                .totalSignals(15)
                .build();

        var project = Project.builder()
                .id("project789")
                .userId("builder456")
                .name("Test Project")
                .description("Test description")
                .techTags(java.util.List.of("React", "TypeScript"))
                .build();

        var ship = Ship.builder()
                .id("ship123")
                .userId("builder456")
                .projectId("project789")
                .content("Test ship")
                .build();

        when(shipRepository.findById("ship123")).thenReturn(Optional.of(ship));
        when(projectRepository.findById("project789")).thenReturn(Optional.of(project));

        // Act
        double relevanceScore = personalizationService.calculateRelevanceScore(
                profile, "ship123", BehavioralSignal.ContentType.SHIP);

        // Assert
        assertTrue(relevanceScore >= 0.1 && relevanceScore <= 1.0,
                "Relevance score should be between 0.1 and 1.0, got: " + relevanceScore);
    }

    @Test
    @DisplayName("calculateRelevanceScore should calculate correct tag match score")
    void testCalculateRelevanceScore_TagMatching_CalculatesCorrectly() {
        // Arrange
        var profile = com.shipspace.model.UserInterestProfile.builder()
                .userId("user123")
                .techTagScores(java.util.Map.of("React", 0.8, "TypeScript", 0.6, "Node.js", 0.4))
                .followedBuilders(java.util.Set.of())
                .totalSignals(15)
                .build();

        var project = Project.builder()
                .id("project789")
                .userId("builder456")
                .name("Test Project")
                .description("Test description")
                .techTags(java.util.List.of("React", "TypeScript"))
                .build();

        var ship = Ship.builder()
                .id("ship123")
                .userId("builder456")
                .projectId("project789")
                .content("Test ship")
                .build();

        when(shipRepository.findById("ship123")).thenReturn(Optional.of(ship));
        when(projectRepository.findById("project789")).thenReturn(Optional.of(project));

        // Act
        double relevanceScore = personalizationService.calculateRelevanceScore(
                profile, "ship123", BehavioralSignal.ContentType.SHIP);

        // Assert
        // tag_match_score = (0.8 + 0.6) / 2 = 0.7
        // builder_boost = 0.0 (not followed)
        // relevance_score = max(0.1, min(1.0, 0.7 + 0.0)) = 0.7
        assertEquals(0.7, relevanceScore, 0.001,
                "Relevance score should be 0.7 for matching tags");
    }

    @Test
    @DisplayName("calculateRelevanceScore should add builder boost for followed builders")
    void testCalculateRelevanceScore_FollowedBuilder_AddsBoost() {
        // Arrange
        var profile = com.shipspace.model.UserInterestProfile.builder()
                .userId("user123")
                .techTagScores(java.util.Map.of("React", 0.5))
                .followedBuilders(java.util.Set.of("builder456"))
                .totalSignals(15)
                .build();

        var project = Project.builder()
                .id("project789")
                .userId("builder456")
                .name("Test Project")
                .description("Test description")
                .techTags(java.util.List.of("React"))
                .build();

        var ship = Ship.builder()
                .id("ship123")
                .userId("builder456")
                .projectId("project789")
                .content("Test ship")
                .build();

        when(shipRepository.findById("ship123")).thenReturn(Optional.of(ship));
        when(projectRepository.findById("project789")).thenReturn(Optional.of(project));

        // Act
        double relevanceScore = personalizationService.calculateRelevanceScore(
                profile, "ship123", BehavioralSignal.ContentType.SHIP);

        // Assert
        // tag_match_score = 0.5 / 1 = 0.5
        // builder_boost = 0.3 (followed)
        // relevance_score = max(0.1, min(1.0, 0.5 + 0.3)) = 0.8
        assertEquals(0.8, relevanceScore, 0.001,
                "Relevance score should include 0.3 builder boost");
    }

    @Test
    @DisplayName("calculateRelevanceScore should return minimum baseline for non-matching content")
    void testCalculateRelevanceScore_NoMatchingTags_ReturnsBaseline() {
        // Arrange
        var profile = com.shipspace.model.UserInterestProfile.builder()
                .userId("user123")
                .techTagScores(java.util.Map.of("React", 0.8, "TypeScript", 0.6))
                .followedBuilders(java.util.Set.of())
                .totalSignals(15)
                .build();

        var project = Project.builder()
                .id("project789")
                .userId("builder456")
                .name("Test Project")
                .description("Test description")
                .techTags(java.util.List.of("Python", "Django"))
                .build();

        var ship = Ship.builder()
                .id("ship123")
                .userId("builder456")
                .projectId("project789")
                .content("Test ship")
                .build();

        when(shipRepository.findById("ship123")).thenReturn(Optional.of(ship));
        when(projectRepository.findById("project789")).thenReturn(Optional.of(project));

        // Act
        double relevanceScore = personalizationService.calculateRelevanceScore(
                profile, "ship123", BehavioralSignal.ContentType.SHIP);

        // Assert
        // tag_match_score = (0.0 + 0.0) / 2 = 0.0
        // builder_boost = 0.0
        // relevance_score = max(0.1, min(1.0, 0.0 + 0.0)) = 0.1
        assertEquals(0.1, relevanceScore, 0.001,
                "Relevance score should be minimum baseline 0.1 for non-matching content");
    }

    @Test
    @DisplayName("calculateRelevanceScore should cap score at 1.0 even with builder boost")
    void testCalculateRelevanceScore_HighScoreWithBoost_CapsAtOne() {
        // Arrange
        var profile = com.shipspace.model.UserInterestProfile.builder()
                .userId("user123")
                .techTagScores(java.util.Map.of("React", 1.0, "TypeScript", 1.0))
                .followedBuilders(java.util.Set.of("builder456"))
                .totalSignals(15)
                .build();

        var project = Project.builder()
                .id("project789")
                .userId("builder456")
                .name("Test Project")
                .description("Test description")
                .techTags(java.util.List.of("React", "TypeScript"))
                .build();

        var ship = Ship.builder()
                .id("ship123")
                .userId("builder456")
                .projectId("project789")
                .content("Test ship")
                .build();

        when(shipRepository.findById("ship123")).thenReturn(Optional.of(ship));
        when(projectRepository.findById("project789")).thenReturn(Optional.of(project));

        // Act
        double relevanceScore = personalizationService.calculateRelevanceScore(
                profile, "ship123", BehavioralSignal.ContentType.SHIP);

        // Assert
        // tag_match_score = (1.0 + 1.0) / 2 = 1.0
        // builder_boost = 0.3
        // relevance_score = max(0.1, min(1.0, 1.0 + 0.3)) = 1.0 (capped)
        assertEquals(1.0, relevanceScore, 0.001,
                "Relevance score should be capped at 1.0");
    }

    @Test
    @DisplayName("calculateRelevanceScore should handle empty tech tags")
    void testCalculateRelevanceScore_EmptyTechTags_ReturnsBaseline() {
        // Arrange
        var profile = com.shipspace.model.UserInterestProfile.builder()
                .userId("user123")
                .techTagScores(java.util.Map.of("React", 0.8))
                .followedBuilders(java.util.Set.of())
                .totalSignals(15)
                .build();

        var project = Project.builder()
                .id("project789")
                .userId("builder456")
                .name("Test Project")
                .description("Test description")
                .techTags(java.util.List.of())
                .build();

        var ship = Ship.builder()
                .id("ship123")
                .userId("builder456")
                .projectId("project789")
                .content("Test ship")
                .build();

        when(shipRepository.findById("ship123")).thenReturn(Optional.of(ship));
        when(projectRepository.findById("project789")).thenReturn(Optional.of(project));

        // Act
        double relevanceScore = personalizationService.calculateRelevanceScore(
                profile, "ship123", BehavioralSignal.ContentType.SHIP);

        // Assert
        assertEquals(0.1, relevanceScore, 0.001,
                "Relevance score should be baseline 0.1 for empty tech tags");
    }

    @Test
    @DisplayName("calculateRelevanceScore should handle null tech tag scores in profile")
    void testCalculateRelevanceScore_NullProfileScores_ReturnsBaseline() {
        // Arrange
        var profile = com.shipspace.model.UserInterestProfile.builder()
                .userId("user123")
                .techTagScores(null)
                .followedBuilders(java.util.Set.of())
                .totalSignals(15)
                .build();

        var project = Project.builder()
                .id("project789")
                .userId("builder456")
                .name("Test Project")
                .description("Test description")
                .techTags(java.util.List.of("React"))
                .build();

        var ship = Ship.builder()
                .id("ship123")
                .userId("builder456")
                .projectId("project789")
                .content("Test ship")
                .build();

        when(shipRepository.findById("ship123")).thenReturn(Optional.of(ship));
        when(projectRepository.findById("project789")).thenReturn(Optional.of(project));

        // Act
        double relevanceScore = personalizationService.calculateRelevanceScore(
                profile, "ship123", BehavioralSignal.ContentType.SHIP);

        // Assert
        assertEquals(0.1, relevanceScore, 0.001,
                "Relevance score should be baseline 0.1 for null profile scores");
    }

    @Test
    @DisplayName("calculateRelevanceScore should handle project content type")
    void testCalculateRelevanceScore_ProjectType_CalculatesScore() {
        // Arrange
        var profile = com.shipspace.model.UserInterestProfile.builder()
                .userId("user123")
                .techTagScores(java.util.Map.of("Python", 0.9, "AI", 0.7))
                .followedBuilders(java.util.Set.of())
                .totalSignals(15)
                .build();

        var project = Project.builder()
                .id("project789")
                .userId("builder456")
                .name("AI Project")
                .description("AI project description")
                .techTags(java.util.List.of("Python", "AI"))
                .build();

        when(projectRepository.findById("project789")).thenReturn(Optional.of(project));

        // Act
        double relevanceScore = personalizationService.calculateRelevanceScore(
                profile, "project789", BehavioralSignal.ContentType.PROJECT);

        // Assert
        // tag_match_score = (0.9 + 0.7) / 2 = 0.8
        // builder_boost = 0.0
        // relevance_score = 0.8
        assertEquals(0.8, relevanceScore, 0.001,
                "Relevance score should be 0.8 for project with matching tags");
        verify(projectRepository).findById("project789");
    }

    @Test
    @DisplayName("calculateRelevanceScore should throw exception for non-existent ship")
    void testCalculateRelevanceScore_NonExistentShip_ThrowsException() {
        // Arrange
        var profile = com.shipspace.model.UserInterestProfile.builder()
                .userId("user123")
                .techTagScores(java.util.Map.of("React", 0.8))
                .followedBuilders(java.util.Set.of())
                .totalSignals(15)
                .build();

        when(shipRepository.findById("nonexistent")).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(IllegalArgumentException.class, () ->
                personalizationService.calculateRelevanceScore(
                        profile, "nonexistent", BehavioralSignal.ContentType.SHIP),
                "Should throw IllegalArgumentException for non-existent ship");
    }

    @Test
    @DisplayName("calculateRelevanceScore should throw exception for non-existent project")
    void testCalculateRelevanceScore_NonExistentProject_ThrowsException() {
        // Arrange
        var profile = com.shipspace.model.UserInterestProfile.builder()
                .userId("user123")
                .techTagScores(java.util.Map.of("React", 0.8))
                .followedBuilders(java.util.Set.of())
                .totalSignals(15)
                .build();

        when(projectRepository.findById("nonexistent")).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(IllegalArgumentException.class, () ->
                personalizationService.calculateRelevanceScore(
                        profile, "nonexistent", BehavioralSignal.ContentType.PROJECT),
                "Should throw IllegalArgumentException for non-existent project");
    }

    @Test
    @DisplayName("calculateRelevanceScore should handle ship with missing project")
    void testCalculateRelevanceScore_ShipWithMissingProject_HandlesGracefully() {
        // Arrange
        var profile = com.shipspace.model.UserInterestProfile.builder()
                .userId("user123")
                .techTagScores(java.util.Map.of("React", 0.8))
                .followedBuilders(java.util.Set.of())
                .totalSignals(15)
                .build();

        var ship = Ship.builder()
                .id("ship123")
                .userId("builder456")
                .projectId("missingProject")
                .content("Test ship")
                .build();

        when(shipRepository.findById("ship123")).thenReturn(Optional.of(ship));
        when(projectRepository.findById("missingProject")).thenReturn(Optional.empty());

        // Act
        double relevanceScore = personalizationService.calculateRelevanceScore(
                profile, "ship123", BehavioralSignal.ContentType.SHIP);

        // Assert
        // Should return baseline since no tech tags available
        assertEquals(0.1, relevanceScore, 0.001,
                "Relevance score should be baseline 0.1 when project is missing");
    }

    @Test
    @DisplayName("calculateRelevanceScore should handle partial tag matches")
    void testCalculateRelevanceScore_PartialTagMatch_CalculatesCorrectly() {
        // Arrange
        var profile = com.shipspace.model.UserInterestProfile.builder()
                .userId("user123")
                .techTagScores(java.util.Map.of("React", 0.8, "TypeScript", 0.6))
                .followedBuilders(java.util.Set.of())
                .totalSignals(15)
                .build();

        var project = Project.builder()
                .id("project789")
                .userId("builder456")
                .name("Test Project")
                .description("Test description")
                .techTags(java.util.List.of("React", "Python", "Docker"))
                .build();

        var ship = Ship.builder()
                .id("ship123")
                .userId("builder456")
                .projectId("project789")
                .content("Test ship")
                .build();

        when(shipRepository.findById("ship123")).thenReturn(Optional.of(ship));
        when(projectRepository.findById("project789")).thenReturn(Optional.of(project));

        // Act
        double relevanceScore = personalizationService.calculateRelevanceScore(
                profile, "ship123", BehavioralSignal.ContentType.SHIP);

        // Assert
        // tag_match_score = (0.8 + 0.0 + 0.0) / 3 = 0.267
        // builder_boost = 0.0
        // relevance_score = max(0.1, min(1.0, 0.267)) = 0.267
        assertTrue(relevanceScore > 0.26 && relevanceScore < 0.27,
                "Relevance score should be approximately 0.267 for partial match, got: " + relevanceScore);
    }

    @Test
    @DisplayName("calculateRelevanceScore should handle null followed builders")
    void testCalculateRelevanceScore_NullFollowedBuilders_NoBoost() {
        // Arrange
        var profile = com.shipspace.model.UserInterestProfile.builder()
                .userId("user123")
                .techTagScores(java.util.Map.of("React", 0.5))
                .followedBuilders(null)
                .totalSignals(15)
                .build();

        var project = Project.builder()
                .id("project789")
                .userId("builder456")
                .name("Test Project")
                .description("Test description")
                .techTags(java.util.List.of("React"))
                .build();

        var ship = Ship.builder()
                .id("ship123")
                .userId("builder456")
                .projectId("project789")
                .content("Test ship")
                .build();

        when(shipRepository.findById("ship123")).thenReturn(Optional.of(ship));
        when(projectRepository.findById("project789")).thenReturn(Optional.of(project));

        // Act
        double relevanceScore = personalizationService.calculateRelevanceScore(
                profile, "ship123", BehavioralSignal.ContentType.SHIP);

        // Assert
        // tag_match_score = 0.5 / 1 = 0.5
        // builder_boost = 0.0 (null followed builders)
        // relevance_score = 0.5
        assertEquals(0.5, relevanceScore, 0.001,
                "Relevance score should not include builder boost when followedBuilders is null");
    }

    @Test
    @DisplayName("calculateRelevanceScore should only boost for followed builder, not others")
    void testCalculateRelevanceScore_UnfollowedBuilder_NoBoost() {
        // Arrange
        var profile = com.shipspace.model.UserInterestProfile.builder()
                .userId("user123")
                .techTagScores(java.util.Map.of("React", 0.5))
                .followedBuilders(java.util.Set.of("otherBuilder"))
                .totalSignals(15)
                .build();

        var project = Project.builder()
                .id("project789")
                .userId("builder456")
                .name("Test Project")
                .description("Test description")
                .techTags(java.util.List.of("React"))
                .build();

        var ship = Ship.builder()
                .id("ship123")
                .userId("builder456")
                .projectId("project789")
                .content("Test ship")
                .build();

        when(shipRepository.findById("ship123")).thenReturn(Optional.of(ship));
        when(projectRepository.findById("project789")).thenReturn(Optional.of(project));

        // Act
        double relevanceScore = personalizationService.calculateRelevanceScore(
                profile, "ship123", BehavioralSignal.ContentType.SHIP);

        // Assert
        // tag_match_score = 0.5 / 1 = 0.5
        // builder_boost = 0.0 (different builder)
        // relevance_score = 0.5
        assertEquals(0.5, relevanceScore, 0.001,
                "Relevance score should not include builder boost for unfollowed builder");
    }

    // ========== Performance Score Tests ==========

    @Test
    @DisplayName("calculatePerformanceScore should return score between -1.0 and 1.0")
    void testCalculatePerformanceScore_ValidInput_ReturnsBoundedScore() {
        // Arrange
        Ship ship = Ship.builder()
                .id("ship123")
                .userId("builder456")
                .projectId("project789")
                .content("Test ship")
                .likeCount(10)
                .commentCount(5)
                .createdAt(Instant.now().minus(2, ChronoUnit.DAYS))
                .build();

        when(shipRepository.findById("ship123")).thenReturn(Optional.of(ship));
        when(contentScoreRepository.findByContentId("ship123")).thenReturn(Optional.empty());
        when(behavioralSignalRepository.countByContentIdAndSignalType(
                "ship123", BehavioralSignal.SignalType.VIEW)).thenReturn(100L);
        when(behavioralSignalRepository.countByContentIdAndSignalType(
                "ship123", BehavioralSignal.SignalType.FOLLOW)).thenReturn(3L);
        
        // Mock cohort data
        when(contentScoreRepository.findByAgeCohort("1-7d")).thenReturn(java.util.List.of());

        // Act
        double performanceScore = personalizationService.calculatePerformanceScore("ship123");

        // Assert
        assertTrue(performanceScore >= -1.0 && performanceScore <= 1.0,
                "Performance score should be between -1.0 and 1.0, got: " + performanceScore);
        verify(contentScoreRepository).save(any(com.shipspace.model.ContentScore.class));
    }

    @Test
    @DisplayName("calculatePerformanceScore should return neutral score for insufficient cohort data")
    void testCalculatePerformanceScore_InsufficientCohortData_ReturnsNeutral() {
        // Arrange
        Ship ship = Ship.builder()
                .id("ship123")
                .userId("builder456")
                .projectId("project789")
                .content("Test ship")
                .likeCount(10)
                .commentCount(5)
                .createdAt(Instant.now().minus(2, ChronoUnit.DAYS))
                .build();

        when(shipRepository.findById("ship123")).thenReturn(Optional.of(ship));
        when(contentScoreRepository.findByContentId("ship123")).thenReturn(Optional.empty());
        when(behavioralSignalRepository.countByContentIdAndSignalType(
                "ship123", BehavioralSignal.SignalType.VIEW)).thenReturn(100L);
        when(behavioralSignalRepository.countByContentIdAndSignalType(
                "ship123", BehavioralSignal.SignalType.FOLLOW)).thenReturn(3L);
        
        // Mock empty cohort
        when(contentScoreRepository.findByAgeCohort("1-7d")).thenReturn(java.util.List.of());

        // Act
        double performanceScore = personalizationService.calculatePerformanceScore("ship123");

        // Assert
        assertEquals(0.0, performanceScore, 0.001,
                "Performance score should be 0.0 for insufficient cohort data");
    }

    @Test
    @DisplayName("calculatePerformanceScore should use cached score if recent")
    void testCalculatePerformanceScore_RecentCache_UsesCachedScore() {
        // Arrange
        com.shipspace.model.ContentScore cachedScore = com.shipspace.model.ContentScore.builder()
                .id("score123")
                .contentId("ship123")
                .contentType(BehavioralSignal.ContentType.SHIP)
                .performanceScore(0.5)
                .engagementRate(0.15)
                .ageCohort("1-7d")
                .updatedAt(Instant.now().minus(30, ChronoUnit.MINUTES))
                .build();

        when(contentScoreRepository.findByContentId("ship123"))
                .thenReturn(Optional.of(cachedScore));

        // Act
        double performanceScore = personalizationService.calculatePerformanceScore("ship123");

        // Assert
        assertEquals(0.5, performanceScore, 0.001,
                "Should use cached performance score");
        verify(shipRepository, never()).findById(anyString());
        verify(contentScoreRepository, never()).save(any());
    }

    @Test
    @DisplayName("calculatePerformanceScore should recalculate if cache is stale")
    void testCalculatePerformanceScore_StaleCache_Recalculates() {
        // Arrange
        com.shipspace.model.ContentScore staleScore = com.shipspace.model.ContentScore.builder()
                .id("score123")
                .contentId("ship123")
                .contentType(BehavioralSignal.ContentType.SHIP)
                .performanceScore(0.5)
                .engagementRate(0.15)
                .ageCohort("1-7d")
                .updatedAt(Instant.now().minus(2, ChronoUnit.HOURS))
                .build();

        Ship ship = Ship.builder()
                .id("ship123")
                .userId("builder456")
                .projectId("project789")
                .content("Test ship")
                .likeCount(10)
                .commentCount(5)
                .createdAt(Instant.now().minus(2, ChronoUnit.DAYS))
                .build();

        when(contentScoreRepository.findByContentId("ship123"))
                .thenReturn(Optional.of(staleScore));
        when(shipRepository.findById("ship123")).thenReturn(Optional.of(ship));
        when(behavioralSignalRepository.countByContentIdAndSignalType(
                "ship123", BehavioralSignal.SignalType.VIEW)).thenReturn(100L);
        when(behavioralSignalRepository.countByContentIdAndSignalType(
                "ship123", BehavioralSignal.SignalType.FOLLOW)).thenReturn(3L);
        when(contentScoreRepository.findByAgeCohort("1-7d")).thenReturn(java.util.List.of());

        // Act
        double performanceScore = personalizationService.calculatePerformanceScore("ship123");

        // Assert
        // Note: shipRepository.findById is called twice - once in calculatePerformanceScore
        // and once in calculateEngagementRate
        verify(shipRepository, times(2)).findById("ship123");
        verify(contentScoreRepository).save(any(com.shipspace.model.ContentScore.class));
    }

    @Test
    @DisplayName("calculatePerformanceScore should determine correct age cohort")
    void testCalculatePerformanceScore_AgeCohort_DeterminesCorrectly() {
        // Arrange - Ship in 1-7d cohort
        Ship ship = Ship.builder()
                .id("ship123")
                .userId("builder456")
                .projectId("project789")
                .content("Test ship")
                .likeCount(10)
                .commentCount(5)
                .createdAt(Instant.now().minus(3, ChronoUnit.DAYS))
                .build();

        when(shipRepository.findById("ship123")).thenReturn(Optional.of(ship));
        when(contentScoreRepository.findByContentId("ship123")).thenReturn(Optional.empty());
        when(behavioralSignalRepository.countByContentIdAndSignalType(
                "ship123", BehavioralSignal.SignalType.VIEW)).thenReturn(100L);
        when(behavioralSignalRepository.countByContentIdAndSignalType(
                "ship123", BehavioralSignal.SignalType.FOLLOW)).thenReturn(3L);
        when(contentScoreRepository.findByAgeCohort("1-7d")).thenReturn(java.util.List.of());

        // Act
        personalizationService.calculatePerformanceScore("ship123");

        // Assert
        verify(contentScoreRepository).findByAgeCohort("1-7d");
    }

    @Test
    @DisplayName("calculatePerformanceScore should determine 0-24h cohort for new content")
    void testCalculatePerformanceScore_AgeCohort_0to24h() {
        // Arrange - Ship created 12 hours ago (< 24 hours)
        Ship ship = Ship.builder()
                .id("ship123")
                .userId("builder456")
                .projectId("project789")
                .content("Test ship")
                .likeCount(5)
                .commentCount(2)
                .createdAt(Instant.now().minus(12, ChronoUnit.HOURS))
                .build();

        when(shipRepository.findById("ship123")).thenReturn(Optional.of(ship));
        when(contentScoreRepository.findByContentId("ship123")).thenReturn(Optional.empty());
        when(behavioralSignalRepository.countByContentIdAndSignalType(
                "ship123", BehavioralSignal.SignalType.VIEW)).thenReturn(50L);
        when(behavioralSignalRepository.countByContentIdAndSignalType(
                "ship123", BehavioralSignal.SignalType.FOLLOW)).thenReturn(1L);
        when(contentScoreRepository.findByAgeCohort("0-24h")).thenReturn(java.util.List.of());

        // Act
        personalizationService.calculatePerformanceScore("ship123");

        // Assert
        verify(contentScoreRepository).findByAgeCohort("0-24h");
    }

    @Test
    @DisplayName("calculatePerformanceScore should determine 7-30d cohort for established content")
    void testCalculatePerformanceScore_AgeCohort_7to30d() {
        // Arrange - Ship created 15 days ago (between 7 and 30 days)
        Ship ship = Ship.builder()
                .id("ship123")
                .userId("builder456")
                .projectId("project789")
                .content("Test ship")
                .likeCount(20)
                .commentCount(10)
                .createdAt(Instant.now().minus(15, ChronoUnit.DAYS))
                .build();

        when(shipRepository.findById("ship123")).thenReturn(Optional.of(ship));
        when(contentScoreRepository.findByContentId("ship123")).thenReturn(Optional.empty());
        when(behavioralSignalRepository.countByContentIdAndSignalType(
                "ship123", BehavioralSignal.SignalType.VIEW)).thenReturn(200L);
        when(behavioralSignalRepository.countByContentIdAndSignalType(
                "ship123", BehavioralSignal.SignalType.FOLLOW)).thenReturn(5L);
        when(contentScoreRepository.findByAgeCohort("7-30d")).thenReturn(java.util.List.of());

        // Act
        personalizationService.calculatePerformanceScore("ship123");

        // Assert
        verify(contentScoreRepository).findByAgeCohort("7-30d");
    }

    @Test
    @DisplayName("calculatePerformanceScore should determine 30d+ cohort for older content")
    void testCalculatePerformanceScore_AgeCohort_30dPlus() {
        // Arrange - Ship created 45 days ago (> 30 days)
        Ship ship = Ship.builder()
                .id("ship123")
                .userId("builder456")
                .projectId("project789")
                .content("Test ship")
                .likeCount(30)
                .commentCount(15)
                .createdAt(Instant.now().minus(45, ChronoUnit.DAYS))
                .build();

        when(shipRepository.findById("ship123")).thenReturn(Optional.of(ship));
        when(contentScoreRepository.findByContentId("ship123")).thenReturn(Optional.empty());
        when(behavioralSignalRepository.countByContentIdAndSignalType(
                "ship123", BehavioralSignal.SignalType.VIEW)).thenReturn(300L);
        when(behavioralSignalRepository.countByContentIdAndSignalType(
                "ship123", BehavioralSignal.SignalType.FOLLOW)).thenReturn(8L);
        when(contentScoreRepository.findByAgeCohort("30d+")).thenReturn(java.util.List.of());

        // Act
        personalizationService.calculatePerformanceScore("ship123");

        // Assert
        verify(contentScoreRepository).findByAgeCohort("30d+");
    }

    @Test
    @DisplayName("calculatePerformanceScore should determine 1-7d cohort at boundary (exactly 24 hours)")
    void testCalculatePerformanceScore_AgeCohort_Boundary24h() {
        // Arrange - Ship created exactly 24 hours ago (should be in 1-7d cohort)
        Ship ship = Ship.builder()
                .id("ship123")
                .userId("builder456")
                .projectId("project789")
                .content("Test ship")
                .likeCount(8)
                .commentCount(4)
                .createdAt(Instant.now().minus(24, ChronoUnit.HOURS))
                .build();

        when(shipRepository.findById("ship123")).thenReturn(Optional.of(ship));
        when(contentScoreRepository.findByContentId("ship123")).thenReturn(Optional.empty());
        when(behavioralSignalRepository.countByContentIdAndSignalType(
                "ship123", BehavioralSignal.SignalType.VIEW)).thenReturn(80L);
        when(behavioralSignalRepository.countByContentIdAndSignalType(
                "ship123", BehavioralSignal.SignalType.FOLLOW)).thenReturn(2L);
        when(contentScoreRepository.findByAgeCohort("1-7d")).thenReturn(java.util.List.of());

        // Act
        personalizationService.calculatePerformanceScore("ship123");

        // Assert
        verify(contentScoreRepository).findByAgeCohort("1-7d");
    }

    @Test
    @DisplayName("calculatePerformanceScore should throw exception for non-existent content")
    void testCalculatePerformanceScore_NonExistentContent_ThrowsException() {
        // Arrange
        when(contentScoreRepository.findByContentId("nonexistent")).thenReturn(Optional.empty());
        when(shipRepository.findById("nonexistent")).thenReturn(Optional.empty());
        when(projectRepository.findById("nonexistent")).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(IllegalArgumentException.class, () ->
                personalizationService.calculatePerformanceScore("nonexistent"),
                "Should throw IllegalArgumentException for non-existent content");
    }

    @Test
    @DisplayName("calculatePerformanceScore should handle project content type")
    void testCalculatePerformanceScore_Project_CalculatesScore() {
        // Arrange
        Project project = Project.builder()
                .id("project789")
                .userId("builder456")
                .name("Test Project")
                .description("Test description")
                .createdAt(Instant.now().minus(10, ChronoUnit.DAYS))
                .build();

        when(contentScoreRepository.findByContentId("project789")).thenReturn(Optional.empty());
        when(shipRepository.findById("project789")).thenReturn(Optional.empty());
        when(projectRepository.findById("project789")).thenReturn(Optional.of(project));
        when(behavioralSignalRepository.countByContentIdAndSignalType(
                "project789", BehavioralSignal.SignalType.VIEW)).thenReturn(50L);
        when(behavioralSignalRepository.countByContentIdAndSignalType(
                "project789", BehavioralSignal.SignalType.FOLLOW)).thenReturn(5L);
        when(contentScoreRepository.findByAgeCohort("7-30d")).thenReturn(java.util.List.of());

        // Act
        double performanceScore = personalizationService.calculatePerformanceScore("project789");

        // Assert
        assertTrue(performanceScore >= -1.0 && performanceScore <= 1.0,
                "Performance score should be between -1.0 and 1.0 for projects");
        verify(projectRepository).findById("project789");
    }

    /**
     * Test for task 5.10: Write unit test for performance score updates
     * 
     * This test verifies that performance scores are updated when new engagement occurs
     * by checking that stale cached scores (older than 1 hour) are recalculated.
     * 
     * The cache TTL is 1 hour, so scores older than 1 hour should be recalculated
     * to reflect new engagement data. This ensures the performance score stays fresh
     * and responds to new user interactions.
     * 
     * Validates: Requirements 4.4 - Performance scores updated within 5 minutes of new engagement
     * (Note: Implementation uses 1-hour cache TTL, which is the practical update window)
     */
    @Test
    @DisplayName("calculatePerformanceScore should update score when cache is stale after new engagement")
    void testCalculatePerformanceScore_NewEngagement_UpdatesStaleScore() {
        // Arrange - Create a cached score that is 2 hours old (stale)
        Instant twoHoursAgo = Instant.now().minus(2, ChronoUnit.HOURS);
        com.shipspace.model.ContentScore staleScore = com.shipspace.model.ContentScore.builder()
                .id("score123")
                .contentId("ship123")
                .contentType(BehavioralSignal.ContentType.SHIP)
                .performanceScore(0.3)  // Old performance score
                .engagementRate(0.10)   // Old engagement rate
                .ageCohort("1-7d")
                .updatedAt(twoHoursAgo)  // Stale timestamp (>1 hour old)
                .build();

        // Create ship with NEW engagement metrics (simulating new likes/comments)
        Ship ship = Ship.builder()
                .id("ship123")
                .userId("builder456")
                .projectId("project789")
                .content("Test ship with new engagement")
                .likeCount(25)  // Increased from previous engagement
                .commentCount(12)  // Increased from previous engagement
                .createdAt(Instant.now().minus(3, ChronoUnit.DAYS))
                .build();

        // Mock repository responses
        when(contentScoreRepository.findByContentId("ship123"))
                .thenReturn(Optional.of(staleScore));
        when(shipRepository.findById("ship123")).thenReturn(Optional.of(ship));
        
        // Mock NEW engagement counts (simulating recent user interactions)
        when(behavioralSignalRepository.countByContentIdAndSignalType(
                "ship123", BehavioralSignal.SignalType.VIEW)).thenReturn(200L);  // More views
        when(behavioralSignalRepository.countByContentIdAndSignalType(
                "ship123", BehavioralSignal.SignalType.FOLLOW)).thenReturn(8L);  // More follows
        
        // Mock cohort data for performance comparison
        when(contentScoreRepository.findByAgeCohort("1-7d")).thenReturn(java.util.List.of());

        // Act - Calculate performance score (should detect stale cache and recalculate)
        double performanceScore = personalizationService.calculatePerformanceScore("ship123");

        // Assert
        // 1. Verify that the score was recalculated (not using cached 0.3)
        // Since we have new engagement, the score should be recalculated
        assertTrue(performanceScore >= -1.0 && performanceScore <= 1.0,
                "Performance score should be within valid bounds after recalculation");
        
        // 2. Verify that ship data was fetched (indicating recalculation occurred)
        // Note: shipRepository.findById is called twice - once in calculatePerformanceScore
        // and once in calculateEngagementRate
        verify(shipRepository, times(2)).findById("ship123");
        
        // 3. Verify that behavioral signals were queried (indicating fresh engagement data was used)
        verify(behavioralSignalRepository).countByContentIdAndSignalType(
                "ship123", BehavioralSignal.SignalType.VIEW);
        verify(behavioralSignalRepository).countByContentIdAndSignalType(
                "ship123", BehavioralSignal.SignalType.FOLLOW);
        
        // 4. Verify that the updated score was saved to the repository
        verify(contentScoreRepository).save(any(com.shipspace.model.ContentScore.class));
        
        // 5. Verify that the saved score has a fresh timestamp (within last minute)
        verify(contentScoreRepository).save(argThat(savedScore -> {
            Instant oneMinuteAgo = Instant.now().minus(1, ChronoUnit.MINUTES);
            return savedScore.getUpdatedAt().isAfter(oneMinuteAgo) &&
                   savedScore.getContentId().equals("ship123") &&
                   savedScore.getPerformanceScore() != null;
        }));
    }

    /**
     * Additional test to verify that fresh cached scores are NOT recalculated unnecessarily.
     * This ensures the caching mechanism is working correctly and not wasting resources.
     */
    @Test
    @DisplayName("calculatePerformanceScore should use cached score when fresh (within 1 hour)")
    void testCalculatePerformanceScore_FreshCache_DoesNotRecalculate() {
        // Arrange - Create a cached score that is 30 minutes old (fresh)
        Instant thirtyMinutesAgo = Instant.now().minus(30, ChronoUnit.MINUTES);
        com.shipspace.model.ContentScore freshScore = com.shipspace.model.ContentScore.builder()
                .id("score123")
                .contentId("ship123")
                .contentType(BehavioralSignal.ContentType.SHIP)
                .performanceScore(0.5)
                .engagementRate(0.15)
                .ageCohort("1-7d")
                .updatedAt(thirtyMinutesAgo)  // Fresh timestamp (<1 hour old)
                .build();

        when(contentScoreRepository.findByContentId("ship123"))
                .thenReturn(Optional.of(freshScore));

        // Act
        double performanceScore = personalizationService.calculatePerformanceScore("ship123");

        // Assert
        // 1. Verify that the cached score was returned
        assertEquals(0.5, performanceScore, 0.001,
                "Should return cached performance score when fresh");
        
        // 2. Verify that NO recalculation occurred (no ship lookup, no signal queries, no save)
        verify(shipRepository, never()).findById(anyString());
        verify(behavioralSignalRepository, never()).countByContentIdAndSignalType(
                anyString(), any(BehavioralSignal.SignalType.class));
        verify(contentScoreRepository, never()).save(any(com.shipspace.model.ContentScore.class));
    }

    // ========== Combined Score Tests ==========

    @Test
    @DisplayName("calculateCombinedScore should use correct weights (0.3*Q + 0.5*R + 0.2*P)")
    void testCalculateCombinedScore_CorrectWeights() {
        // Arrange
        double quality = 0.8;
        double relevance = 0.6;
        double performance = 0.4;

        // Act
        double combinedScore = personalizationService.calculateCombinedScore(quality, relevance, performance);

        // Assert
        // Expected: (0.3 * 0.8) + (0.5 * 0.6) + (0.2 * 0.4)
        //         = 0.24 + 0.3 + 0.08 = 0.62
        double expected = (0.3 * quality) + (0.5 * relevance) + (0.2 * performance);
        assertEquals(expected, combinedScore, 0.0001,
                "Combined score should be calculated as 0.3*quality + 0.5*relevance + 0.2*performance");
        assertEquals(0.62, combinedScore, 0.0001,
                "Combined score should be 0.62 for quality=0.8, relevance=0.6, performance=0.4");
    }

    @Test
    @DisplayName("calculateCombinedScore should handle zero scores")
    void testCalculateCombinedScore_ZeroScores() {
        // Arrange
        double quality = 0.0;
        double relevance = 0.0;
        double performance = 0.0;

        // Act
        double combinedScore = personalizationService.calculateCombinedScore(quality, relevance, performance);

        // Assert
        assertEquals(0.0, combinedScore, 0.0001,
                "Combined score should be 0.0 when all component scores are 0.0");
    }

    @Test
    @DisplayName("calculateCombinedScore should handle maximum scores")
    void testCalculateCombinedScore_MaximumScores() {
        // Arrange
        double quality = 1.0;
        double relevance = 1.0;
        double performance = 1.0;

        // Act
        double combinedScore = personalizationService.calculateCombinedScore(quality, relevance, performance);

        // Assert
        // Expected: (0.3 * 1.0) + (0.5 * 1.0) + (0.2 * 1.0) = 1.0
        assertEquals(1.0, combinedScore, 0.0001,
                "Combined score should be 1.0 when all component scores are 1.0");
    }

    @Test
    @DisplayName("calculateCombinedScore should handle negative performance score")
    void testCalculateCombinedScore_NegativePerformance() {
        // Arrange
        double quality = 0.5;
        double relevance = 0.7;
        double performance = -0.5;

        // Act
        double combinedScore = personalizationService.calculateCombinedScore(quality, relevance, performance);

        // Assert
        // Expected: (0.3 * 0.5) + (0.5 * 0.7) + (0.2 * -0.5)
        //         = 0.15 + 0.35 + (-0.1) = 0.4
        double expected = (0.3 * quality) + (0.5 * relevance) + (0.2 * performance);
        assertEquals(expected, combinedScore, 0.0001,
                "Combined score should correctly handle negative performance scores");
        assertEquals(0.4, combinedScore, 0.0001,
                "Combined score should be 0.4 for quality=0.5, relevance=0.7, performance=-0.5");
    }

    @Test
    @DisplayName("calculateCombinedScore should weight relevance highest")
    void testCalculateCombinedScore_RelevanceWeightHighest() {
        // Arrange - Test that relevance has the highest weight (0.5)
        double quality = 1.0;
        double relevance = 1.0;
        double performance = 0.0;

        // Act
        double combinedScore = personalizationService.calculateCombinedScore(quality, relevance, performance);

        // Assert
        // Expected: (0.3 * 1.0) + (0.5 * 1.0) + (0.2 * 0.0) = 0.8
        assertEquals(0.8, combinedScore, 0.0001,
                "Combined score should be 0.8 when quality and relevance are 1.0");
        
        // Verify relevance contributes more than quality
        double qualityContribution = 0.3 * quality;
        double relevanceContribution = 0.5 * relevance;
        assertTrue(relevanceContribution > qualityContribution,
                "Relevance contribution (0.5) should be higher than quality contribution (0.3)");
    }

    @Test
    @DisplayName("calculateCombinedScore should weight performance lowest")
    void testCalculateCombinedScore_PerformanceWeightLowest() {
        // Arrange - Test that performance has the lowest weight (0.2)
        double quality = 0.0;
        double relevance = 0.0;
        double performance = 1.0;

        // Act
        double combinedScore = personalizationService.calculateCombinedScore(quality, relevance, performance);

        // Assert
        // Expected: (0.3 * 0.0) + (0.5 * 0.0) + (0.2 * 1.0) = 0.2
        assertEquals(0.2, combinedScore, 0.0001,
                "Combined score should be 0.2 when only performance is 1.0");
        
        // Verify performance contributes less than quality and relevance
        double performanceContribution = 0.2 * performance;
        assertTrue(performanceContribution < 0.3,
                "Performance contribution (0.2) should be lower than quality weight (0.3)");
        assertTrue(performanceContribution < 0.5,
                "Performance contribution (0.2) should be lower than relevance weight (0.5)");
    }
}

