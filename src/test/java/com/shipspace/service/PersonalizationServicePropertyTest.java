package com.shipspace.service;

import com.shipspace.model.BehavioralSignal;
import com.shipspace.model.Project;
import com.shipspace.model.Ship;
import com.shipspace.model.UserInterestProfile;
import com.shipspace.repository.BehavioralSignalRepository;
import com.shipspace.repository.ContentScoreRepository;
import com.shipspace.repository.ProjectRepository;
import com.shipspace.repository.ShipRepository;
import net.jqwik.api.*;
import net.jqwik.api.constraints.AlphaChars;
import net.jqwik.api.constraints.DoubleRange;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.StringLength;
import net.jqwik.api.lifecycle.BeforeProperty;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Property-based tests for ContentScoringService — relevance and performance scoring.
 *
 * Refactored from PersonalizationService (Phase 3 split): scoring logic now lives in
 * ContentScoringService. Tests validate bounds and monotonicity of relevance scores.
 *
 * Requirements: 3.1, 3.2, 3.3, 3.4, 3.5
 */
class PersonalizationServicePropertyTest {

    @Mock private ShipRepository shipRepository;
    @Mock private ProjectRepository projectRepository;
    @Mock private BehavioralSignalRepository behavioralSignalRepository;
    @Mock private ContentScoreRepository contentScoreRepository;
    @Mock private MongoTemplate mongoTemplate;

    private ContentScoringService contentScoringService;

    @BeforeProperty
    void setUp() {
        MockitoAnnotations.openMocks(this);
        contentScoringService = new ContentScoringService(
                shipRepository, projectRepository, behavioralSignalRepository,
                contentScoreRepository, mongoTemplate);
    }

    /**
     * Property: Relevance scores are bounded between 0.1 and 1.0
     */
    @Property(tries = 100)
    @Label("Feature: personalized-feed-algorithm, Property: Relevance scores are bounded")
    void relevanceScoresAreBounded(
            @ForAll @AlphaChars @StringLength(min = 5, max = 20) String userId,
            @ForAll @AlphaChars @StringLength(min = 5, max = 20) String builderId,
            @ForAll("techTagScores") Map<String, Double> techTagScores,
            @ForAll("techTagList") List<String> contentTechTags,
            @ForAll boolean isBuilderFollowed
    ) {
        Set<String> followedBuilders = isBuilderFollowed ? Set.of(builderId) : Set.of();

        UserInterestProfile profile = UserInterestProfile.builder()
                .userId(userId)
                .techTagScores(techTagScores)
                .followedBuilders(followedBuilders)
                .totalSignals(15)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        Ship ship = Ship.builder()
                .id("ship-" + userId)
                .userId(builderId)
                .projectId("proj-" + userId)
                .content("Test content")
                .createdAt(Instant.now())
                .build();

        Project project = Project.builder()
                .id("proj-" + userId)
                .userId(builderId)
                .name("Test Project")
                .techTags(contentTechTags)
                .createdAt(Instant.now())
                .build();

        double relevanceScore = contentScoringService.relevanceScore(profile, ship, project);

        assertTrue(relevanceScore >= 0.1,
                String.format("Relevance score should be >= 0.1, but was %f", relevanceScore));
        assertTrue(relevanceScore <= 1.0,
                String.format("Relevance score should be <= 1.0, but was %f", relevanceScore));
    }

    /**
     * Property: Followed builders increase relevance score
     */
    @Property(tries = 100)
    @Label("Feature: personalized-feed-algorithm, Property: Followed builders increase relevance")
    void followedBuildersIncreaseRelevance(
            @ForAll @AlphaChars @StringLength(min = 5, max = 20) String userId,
            @ForAll @AlphaChars @StringLength(min = 5, max = 20) String builderId,
            @ForAll("techTagScores") Map<String, Double> techTagScores,
            @ForAll("techTagList") List<String> contentTechTags
    ) {
        UserInterestProfile withoutFollow = UserInterestProfile.builder()
                .userId(userId)
                .techTagScores(techTagScores)
                .followedBuilders(Set.of())
                .totalSignals(15)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        UserInterestProfile withFollow = UserInterestProfile.builder()
                .userId(userId)
                .techTagScores(techTagScores)
                .followedBuilders(Set.of(builderId))
                .totalSignals(15)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        Ship ship = Ship.builder()
                .id("ship-" + userId)
                .userId(builderId)
                .projectId("proj-" + userId)
                .content("Test content")
                .createdAt(Instant.now())
                .build();

        Project project = Project.builder()
                .id("proj-" + userId)
                .userId(builderId)
                .name("Test Project")
                .techTags(contentTechTags)
                .createdAt(Instant.now())
                .build();

        double scoreWithout = contentScoringService.relevanceScore(withoutFollow, ship, project);
        double scoreWith = contentScoringService.relevanceScore(withFollow, ship, project);

        assertTrue(scoreWith >= scoreWithout,
                String.format("Score with follow (%f) should be >= score without (%f)", scoreWith, scoreWithout));
    }

    /**
     * Property: Performance score returns 0.0 for uncached content (neutral fallback)
     */
    @Property(tries = 50)
    @Label("Feature: personalized-feed-algorithm, Property: Performance score neutral fallback")
    void performanceScoreNeutralFallbackForUncachedContent(
            @ForAll @AlphaChars @StringLength(min = 5, max = 20) String shipId
    ) {
        Ship ship = Ship.builder()
                .id(shipId)
                .userId("builder")
                .projectId("proj")
                .content("Test")
                .createdAt(Instant.now())
                .build();

        when(contentScoreRepository.findByContentId(shipId)).thenReturn(Optional.empty());

        double performanceScore = contentScoringService.performanceScore(ship);

        assertEquals(0.0, performanceScore, 0.0001,
                "Uncached content should return 0.0 neutral performance score");
    }

    // ── Providers ──────────────────────────────────────────────────────────────

    @Provide
    Arbitrary<Map<String, Double>> techTagScores() {
        return Arbitraries.maps(
                Arbitraries.strings().alpha().ofMinLength(2).ofMaxLength(10),
                Arbitraries.doubles().between(0.0, 1.0)
        ).ofMaxSize(5);
    }

    @Provide
    Arbitrary<List<String>> techTagList() {
        return Arbitraries.strings().alpha().ofMinLength(2).ofMaxLength(10)
                .list().ofMaxSize(5);
    }
}
