package com.shipspace.model;

import org.junit.jupiter.api.Test;
import net.jqwik.api.*;
import net.jqwik.api.constraints.DoubleRange;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit and property-based tests for ContentScore model.
 * Tests model structure, data integrity, and correctness properties.
 */
class ContentScoreTest {

    @Test
    void testContentScoreCreationWithBuilder() {
        // Given
        Instant now = Instant.now();

        // When
        ContentScore score = ContentScore.builder()
                .id("score123")
                .contentId("ship456")
                .contentType(BehavioralSignal.ContentType.SHIP)
                .qualityScore(0.78)
                .performanceScore(0.23)
                .ageCohort("1-7d")
                .engagementRate(0.15)
                .updatedAt(now)
                .build();

        // Then
        assertNotNull(score);
        assertEquals("score123", score.getId());
        assertEquals("ship456", score.getContentId());
        assertEquals(BehavioralSignal.ContentType.SHIP, score.getContentType());
        assertEquals(0.78, score.getQualityScore());
        assertEquals(0.23, score.getPerformanceScore());
        assertEquals("1-7d", score.getAgeCohort());
        assertEquals(0.15, score.getEngagementRate());
        assertEquals(now, score.getUpdatedAt());
    }

    @Test
    void testContentScoreDefaultValues() {
        // When
        ContentScore score = ContentScore.builder()
                .contentId("ship123")
                .build();

        // Then
        assertNotNull(score.getUpdatedAt());
        assertTrue(score.getUpdatedAt().isBefore(Instant.now().plusSeconds(1)));
        assertTrue(score.getUpdatedAt().isAfter(Instant.now().minusSeconds(5)));
    }

    @Test
    void testContentScoreForShipContent() {
        // When
        ContentScore score = ContentScore.builder()
                .contentId("ship123")
                .contentType(BehavioralSignal.ContentType.SHIP)
                .qualityScore(0.85)
                .performanceScore(0.45)
                .ageCohort("0-24h")
                .engagementRate(0.22)
                .build();

        // Then
        assertEquals(BehavioralSignal.ContentType.SHIP, score.getContentType());
        assertEquals("ship123", score.getContentId());
    }

    @Test
    void testContentScoreForProjectContent() {
        // When
        ContentScore score = ContentScore.builder()
                .contentId("project456")
                .contentType(BehavioralSignal.ContentType.PROJECT)
                .qualityScore(0.92)
                .performanceScore(-0.15)
                .ageCohort("7-30d")
                .engagementRate(0.08)
                .build();

        // Then
        assertEquals(BehavioralSignal.ContentType.PROJECT, score.getContentType());
        assertEquals("project456", score.getContentId());
    }

    @Test
    void testContentScoreWithAllAgeCohorts() {
        // Test all valid age cohorts
        String[] cohorts = {"0-24h", "1-7d", "7-30d", "30d+"};

        for (String cohort : cohorts) {
            ContentScore score = ContentScore.builder()
                    .contentId("content_" + cohort)
                    .ageCohort(cohort)
                    .build();

            assertEquals(cohort, score.getAgeCohort());
        }
    }

    @Test
    void testContentScoreWithZeroEngagementRate() {
        // When
        ContentScore score = ContentScore.builder()
                .contentId("ship123")
                .engagementRate(0.0)
                .build();

        // Then
        assertEquals(0.0, score.getEngagementRate());
    }

    @Test
    void testContentScoreWithMaxQualityScore() {
        // When
        ContentScore score = ContentScore.builder()
                .contentId("ship123")
                .qualityScore(1.0)
                .build();

        // Then
        assertEquals(1.0, score.getQualityScore());
    }

    @Test
    void testContentScoreWithMinQualityScore() {
        // When
        ContentScore score = ContentScore.builder()
                .contentId("ship123")
                .qualityScore(0.0)
                .build();

        // Then
        assertEquals(0.0, score.getQualityScore());
    }

    @Test
    void testContentScoreWithMaxPerformanceScore() {
        // When
        ContentScore score = ContentScore.builder()
                .contentId("ship123")
                .performanceScore(1.0)
                .build();

        // Then
        assertEquals(1.0, score.getPerformanceScore());
    }

    @Test
    void testContentScoreWithMinPerformanceScore() {
        // When
        ContentScore score = ContentScore.builder()
                .contentId("ship123")
                .performanceScore(-1.0)
                .build();

        // Then
        assertEquals(-1.0, score.getPerformanceScore());
    }

    @Test
    void testContentScoreWithNegativePerformanceScore() {
        // When
        ContentScore score = ContentScore.builder()
                .contentId("ship123")
                .performanceScore(-0.67)
                .build();

        // Then
        assertEquals(-0.67, score.getPerformanceScore());
    }

    // Property-Based Tests

    @Property
    void qualityScore_shouldBeBetweenZeroAndOne(
            @ForAll @DoubleRange(min = 0.0, max = 1.0) double qualityScore) {
        // When
        ContentScore score = ContentScore.builder()
                .contentId("content123")
                .qualityScore(qualityScore)
                .build();

        // Then
        assertNotNull(score.getQualityScore());
        assertTrue(score.getQualityScore() >= 0.0, 
                "Quality score should be >= 0.0");
        assertTrue(score.getQualityScore() <= 1.0, 
                "Quality score should be <= 1.0");
        assertEquals(qualityScore, score.getQualityScore(), 0.0001);
    }

    @Property
    void performanceScore_shouldBeBetweenMinusOneAndOne(
            @ForAll @DoubleRange(min = -1.0, max = 1.0) double performanceScore) {
        // When
        ContentScore score = ContentScore.builder()
                .contentId("content123")
                .performanceScore(performanceScore)
                .build();

        // Then
        assertNotNull(score.getPerformanceScore());
        assertTrue(score.getPerformanceScore() >= -1.0, 
                "Performance score should be >= -1.0");
        assertTrue(score.getPerformanceScore() <= 1.0, 
                "Performance score should be <= 1.0");
        assertEquals(performanceScore, score.getPerformanceScore(), 0.0001);
    }

    @Property
    void engagementRate_shouldBeNonNegative(
            @ForAll @DoubleRange(min = 0.0, max = 1.0) double engagementRate) {
        // When
        ContentScore score = ContentScore.builder()
                .contentId("content123")
                .engagementRate(engagementRate)
                .build();

        // Then
        assertNotNull(score.getEngagementRate());
        assertTrue(score.getEngagementRate() >= 0.0, 
                "Engagement rate should be >= 0.0");
        assertEquals(engagementRate, score.getEngagementRate(), 0.0001);
    }

    @Property
    void contentScore_maintainsDataIntegrity(
            @ForAll("contentIds") String contentId,
            @ForAll("contentTypes") BehavioralSignal.ContentType contentType,
            @ForAll @DoubleRange(min = 0.0, max = 1.0) double qualityScore,
            @ForAll @DoubleRange(min = -1.0, max = 1.0) double performanceScore,
            @ForAll("ageCohorts") String ageCohort,
            @ForAll @DoubleRange(min = 0.0, max = 1.0) double engagementRate) {
        // When
        ContentScore score = ContentScore.builder()
                .contentId(contentId)
                .contentType(contentType)
                .qualityScore(qualityScore)
                .performanceScore(performanceScore)
                .ageCohort(ageCohort)
                .engagementRate(engagementRate)
                .build();

        // Then
        assertEquals(contentId, score.getContentId());
        assertEquals(contentType, score.getContentType());
        assertEquals(qualityScore, score.getQualityScore(), 0.0001);
        assertEquals(performanceScore, score.getPerformanceScore(), 0.0001);
        assertEquals(ageCohort, score.getAgeCohort());
        assertEquals(engagementRate, score.getEngagementRate(), 0.0001);
        assertNotNull(score.getUpdatedAt());
    }

    @Property
    void updatedAt_shouldBeReasonableTimestamp(
            @ForAll("contentIds") String contentId) {
        // Given
        Instant before = Instant.now();

        // When
        ContentScore score = ContentScore.builder()
                .contentId(contentId)
                .build();

        // Then
        Instant after = Instant.now();
        assertNotNull(score.getUpdatedAt());
        assertFalse(score.getUpdatedAt().isBefore(before), 
                "updatedAt should not be before creation");
        assertFalse(score.getUpdatedAt().isAfter(after), 
                "updatedAt should not be after creation");
    }

    @Property
    void contentScore_handlesAllContentTypes(
            @ForAll("contentTypes") BehavioralSignal.ContentType contentType) {
        // When
        ContentScore score = ContentScore.builder()
                .contentId("content123")
                .contentType(contentType)
                .build();

        // Then
        assertEquals(contentType, score.getContentType());
        assertNotNull(score.getContentId());
    }

    @Property
    void contentScore_handlesAllAgeCohorts(
            @ForAll("ageCohorts") String ageCohort) {
        // When
        ContentScore score = ContentScore.builder()
                .contentId("content123")
                .ageCohort(ageCohort)
                .build();

        // Then
        assertEquals(ageCohort, score.getAgeCohort());
    }

    // Providers for property-based tests

    @Provide
    Arbitrary<String> contentIds() {
        return Arbitraries.strings()
                .alpha()
                .numeric()
                .ofMinLength(5)
                .ofMaxLength(30);
    }

    @Provide
    Arbitrary<BehavioralSignal.ContentType> contentTypes() {
        return Arbitraries.of(BehavioralSignal.ContentType.values());
    }

    @Provide
    Arbitrary<String> ageCohorts() {
        return Arbitraries.of("0-24h", "1-7d", "7-30d", "30d+");
    }
}
