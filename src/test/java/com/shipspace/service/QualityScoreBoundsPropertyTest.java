package com.shipspace.service;

import com.shipspace.model.BehavioralSignal;
import com.shipspace.model.Ship;
import com.shipspace.repository.BehavioralSignalRepository;
import com.shipspace.repository.ContentScoreRepository;
import com.shipspace.repository.ProjectRepository;
import com.shipspace.repository.ShipRepository;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.LongRange;
import net.jqwik.api.lifecycle.BeforeTry;
import org.mockito.Mockito;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Property-based tests for quality score bounds in ContentScoringService.
 *
 * Refactored from PersonalizationService (Phase 3 split): quality scoring logic
 * now lives in ContentScoringService.qualityScore(Ship, long views, long follows).
 *
 * The quality score formula is:
 *   raw = (likes * 1.0) + (comments * 2.0) + (views * 0.5) + (follows * 3.0)
 *   age_factor = max(1.0, hours_since_creation)
 *   quality_score = min(1.0, (raw / sqrt(age_factor)) / 100.0)
 *
 * Validates: Requirements 2.1, 2.2, 2.3, 2.4
 */
class QualityScoreBoundsPropertyTest {

    private ContentScoringService contentScoringService;

    @BeforeTry
    void setUp() {
        contentScoringService = new ContentScoringService(
                Mockito.mock(ShipRepository.class),
                Mockito.mock(ProjectRepository.class),
                Mockito.mock(BehavioralSignalRepository.class),
                Mockito.mock(ContentScoreRepository.class),
                Mockito.mock(MongoTemplate.class)
        );
    }

    /**
     * Property: Quality scores are bounded between 0.0 and 1.0
     */
    @Property(tries = 200)
    @Label("Feature: personalized-feed-algorithm, Property: Quality scores are bounded")
    void qualityScoresAreBounded(
            @ForAll @IntRange(min = 0, max = 10000) int likes,
            @ForAll @IntRange(min = 0, max = 5000) int comments,
            @ForAll @LongRange(min = 0, max = 100000) long views,
            @ForAll @LongRange(min = 0, max = 1000) long follows,
            @ForAll @IntRange(min = 0, max = 8760) int hoursOld
    ) {
        Ship testShip = Ship.builder()
                .id("testShip")
                .userId("builder123")
                .projectId("project456")
                .content("Test ship content")
                .likeCount(likes)
                .commentCount(comments)
                .createdAt(Instant.now().minus(hoursOld, ChronoUnit.HOURS))
                .build();

        double qualityScore = contentScoringService.qualityScore(testShip, views, follows);

        assertTrue(qualityScore >= 0.0,
                String.format("Quality score should be >= 0.0, got: %.6f (likes=%d, comments=%d, views=%d, follows=%d, age=%dh)",
                        qualityScore, likes, comments, views, follows, hoursOld));
        assertTrue(qualityScore <= 1.0,
                String.format("Quality score should be <= 1.0, got: %.6f (likes=%d, comments=%d, views=%d, follows=%d, age=%dh)",
                        qualityScore, likes, comments, views, follows, hoursOld));
    }

    /**
     * Property: Quality scores are bounded with null engagement counts
     */
    @Property(tries = 100)
    @Label("Feature: personalized-feed-algorithm, Property: Quality scores are bounded with null counts")
    void qualityScoresAreBoundedWithNullCounts(
            @ForAll @LongRange(min = 0, max = 10000) long views,
            @ForAll @LongRange(min = 0, max = 500) long follows,
            @ForAll @IntRange(min = 0, max = 2000) int hoursOld
    ) {
        Ship testShip = Ship.builder()
                .id("testShip")
                .userId("builder123")
                .projectId("project456")
                .content("Test ship content")
                .likeCount(null)
                .commentCount(null)
                .createdAt(Instant.now().minus(hoursOld, ChronoUnit.HOURS))
                .build();

        double qualityScore = contentScoringService.qualityScore(testShip, views, follows);

        assertTrue(qualityScore >= 0.0, "Quality score should be >= 0.0 with null counts");
        assertTrue(qualityScore <= 1.0, "Quality score should be <= 1.0 with null counts");
    }

    /**
     * Property: Quality scores capped at 1.0 for extreme (viral) engagement
     */
    @Property(tries = 100)
    @Label("Feature: personalized-feed-algorithm, Property: Quality scores are bounded for extreme engagement")
    void qualityScoresAreBoundedForExtremeEngagement(
            @ForAll @IntRange(min = 10000, max = 1000000) int likes,
            @ForAll @IntRange(min = 5000, max = 500000) int comments,
            @ForAll @LongRange(min = 100000, max = 10000000) long views,
            @ForAll @LongRange(min = 1000, max = 100000) long follows,
            @ForAll @IntRange(min = 1, max = 168) int hoursOld
    ) {
        Ship viralShip = Ship.builder()
                .id("viralShip")
                .userId("builder123")
                .projectId("project456")
                .content("Viral ship content")
                .likeCount(likes)
                .commentCount(comments)
                .createdAt(Instant.now().minus(hoursOld, ChronoUnit.HOURS))
                .build();

        double qualityScore = contentScoringService.qualityScore(viralShip, views, follows);

        assertTrue(qualityScore >= 0.0, "Quality score should be >= 0.0");
        assertTrue(qualityScore <= 1.0, "Quality score should be capped at 1.0 for viral content");
        assertTrue(qualityScore >= 0.9,
                String.format("Quality score should be near 1.0 for extreme engagement, got: %.6f", qualityScore));
    }

    /**
     * Property: Quality score is 0.0 for zero engagement content
     */
    @Property(tries = 50)
    @Label("Feature: personalized-feed-algorithm, Property: Quality scores are bounded for zero engagement")
    void qualityScoresAreBoundedForZeroEngagement(
            @ForAll @IntRange(min = 0, max = 8760) int hoursOld
    ) {
        Ship noEngagementShip = Ship.builder()
                .id("noEngagement")
                .userId("builder123")
                .projectId("project456")
                .content("No engagement ship")
                .likeCount(0)
                .commentCount(0)
                .createdAt(Instant.now().minus(hoursOld, ChronoUnit.HOURS))
                .build();

        double qualityScore = contentScoringService.qualityScore(noEngagementShip, 0L, 0L);

        assertTrue(qualityScore >= 0.0, "Quality score should be >= 0.0 for zero engagement");
        assertTrue(qualityScore <= 1.0, "Quality score should be <= 1.0 for zero engagement");
        assertEquals(0.0, qualityScore, 0.0001,
                String.format("Quality score should be 0.0 for zero engagement, got: %.6f", qualityScore));
    }
}
