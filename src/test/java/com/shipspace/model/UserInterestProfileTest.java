package com.shipspace.model;

import org.junit.jupiter.api.Test;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit and property-based tests for UserInterestProfile model.
 * Tests model structure, helper methods, and correctness properties.
 */
class UserInterestProfileTest {

    @Test
    void testUserInterestProfileCreationWithBuilder() {
        // Given
        Instant now = Instant.now();
        Map<String, Double> techTagScores = new HashMap<>();
        techTagScores.put("React", 0.85);
        techTagScores.put("Python", 0.62);
        
        Set<String> followedBuilders = new HashSet<>();
        followedBuilders.add("user123");
        followedBuilders.add("user456");

        // When
        UserInterestProfile profile = UserInterestProfile.builder()
                .id("profile123")
                .userId("user789")
                .techTagScores(techTagScores)
                .followedBuilders(followedBuilders)
                .totalSignals(47)
                .lastSignalAt(now)
                .updatedAt(now)
                .createdAt(now)
                .build();

        // Then
        assertNotNull(profile);
        assertEquals("profile123", profile.getId());
        assertEquals("user789", profile.getUserId());
        assertEquals(2, profile.getTechTagScores().size());
        assertEquals(0.85, profile.getTechTagScores().get("React"));
        assertEquals(0.62, profile.getTechTagScores().get("Python"));
        assertEquals(2, profile.getFollowedBuilders().size());
        assertTrue(profile.getFollowedBuilders().contains("user123"));
        assertTrue(profile.getFollowedBuilders().contains("user456"));
        assertEquals(47, profile.getTotalSignals());
        assertEquals(now, profile.getLastSignalAt());
        assertEquals(now, profile.getUpdatedAt());
        assertEquals(now, profile.getCreatedAt());
    }

    @Test
    void testUserInterestProfileDefaultValues() {
        // When
        UserInterestProfile profile = UserInterestProfile.builder()
                .userId("user123")
                .build();

        // Then
        assertNotNull(profile.getTechTagScores());
        assertTrue(profile.getTechTagScores().isEmpty());
        assertNotNull(profile.getFollowedBuilders());
        assertTrue(profile.getFollowedBuilders().isEmpty());
        assertEquals(0, profile.getTotalSignals());
        assertNotNull(profile.getCreatedAt());
        assertNotNull(profile.getUpdatedAt());
    }

    @Test
    void testIncrementTagScore_NewTag() {
        // Given
        UserInterestProfile profile = UserInterestProfile.builder()
                .userId("user123")
                .build();

        // When
        profile.incrementTagScore("React", 0.5);

        // Then
        assertEquals(0.5, profile.getTechTagScores().get("React"));
    }

    @Test
    void testIncrementTagScore_ExistingTag() {
        // Given
        UserInterestProfile profile = UserInterestProfile.builder()
                .userId("user123")
                .build();
        profile.incrementTagScore("React", 0.3);

        // When
        profile.incrementTagScore("React", 0.4);

        // Then
        assertEquals(0.7, profile.getTechTagScores().get("React"), 0.001);
    }

    @Test
    void testIncrementTagScore_CappedAtOne() {
        // Given
        UserInterestProfile profile = UserInterestProfile.builder()
                .userId("user123")
                .build();
        profile.incrementTagScore("React", 0.8);

        // When
        profile.incrementTagScore("React", 0.5);

        // Then
        assertEquals(1.0, profile.getTechTagScores().get("React"));
    }

    @Test
    void testIncrementTagScore_NullTag() {
        // Given
        UserInterestProfile profile = UserInterestProfile.builder()
                .userId("user123")
                .build();

        // When
        profile.incrementTagScore(null, 0.5);

        // Then
        assertTrue(profile.getTechTagScores().isEmpty());
    }

    @Test
    void testIncrementTagScore_EmptyTag() {
        // Given
        UserInterestProfile profile = UserInterestProfile.builder()
                .userId("user123")
                .build();

        // When
        profile.incrementTagScore("", 0.5);

        // Then
        assertTrue(profile.getTechTagScores().isEmpty());
    }

    @Test
    void testIncrementTagScore_WhitespaceTag() {
        // Given
        UserInterestProfile profile = UserInterestProfile.builder()
                .userId("user123")
                .build();

        // When
        profile.incrementTagScore("   ", 0.5);

        // Then
        assertTrue(profile.getTechTagScores().isEmpty());
    }

    @Test
    void testApplyTimeDecay_NoDecayForRecentSignals() {
        // Given
        Instant now = Instant.now();
        UserInterestProfile profile = UserInterestProfile.builder()
                .userId("user123")
                .lastSignalAt(now)
                .build();
        profile.incrementTagScore("React", 0.8);

        // When
        profile.applyTimeDecay(30);

        // Then
        assertEquals(0.8, profile.getTechTagScores().get("React"), 0.001);
    }

    @Test
    void testApplyTimeDecay_HalfLifeDecay() {
        // Given
        Instant thirtyDaysAgo = Instant.now().minus(30, ChronoUnit.DAYS);
        UserInterestProfile profile = UserInterestProfile.builder()
                .userId("user123")
                .lastSignalAt(thirtyDaysAgo)
                .build();
        profile.incrementTagScore("React", 0.8);

        // When
        profile.applyTimeDecay(30);

        // Then
        // After 30 days (one half-life), score should be halved
        assertEquals(0.4, profile.getTechTagScores().get("React"), 0.001);
    }

    @Test
    void testApplyTimeDecay_RemovesLowScores() {
        // Given
        Instant sixtyDaysAgo = Instant.now().minus(60, ChronoUnit.DAYS);
        UserInterestProfile profile = UserInterestProfile.builder()
                .userId("user123")
                .lastSignalAt(sixtyDaysAgo)
                .build();
        profile.incrementTagScore("React", 0.02);

        // When
        profile.applyTimeDecay(30);

        // Then
        // After 60 days (two half-lives), 0.02 * 0.25 = 0.005, which is below 0.01 threshold
        assertFalse(profile.getTechTagScores().containsKey("React"));
    }

    @Test
    void testApplyTimeDecay_NullLastSignalAt() {
        // Given
        UserInterestProfile profile = UserInterestProfile.builder()
                .userId("user123")
                .lastSignalAt(null)
                .build();
        profile.incrementTagScore("React", 0.8);

        // When
        profile.applyTimeDecay(30);

        // Then
        assertEquals(0.8, profile.getTechTagScores().get("React"), 0.001);
    }

    @Test
    void testIsColdStart_LessThanTenSignals() {
        // Given
        UserInterestProfile profile = UserInterestProfile.builder()
                .userId("user123")
                .totalSignals(5)
                .build();

        // When
        boolean isColdStart = profile.isColdStart();

        // Then
        assertTrue(isColdStart);
    }

    @Test
    void testIsColdStart_ExactlyTenSignals() {
        // Given
        UserInterestProfile profile = UserInterestProfile.builder()
                .userId("user123")
                .totalSignals(10)
                .build();

        // When
        boolean isColdStart = profile.isColdStart();

        // Then
        assertFalse(isColdStart);
    }

    @Test
    void testIsColdStart_MoreThanTenSignals() {
        // Given
        UserInterestProfile profile = UserInterestProfile.builder()
                .userId("user123")
                .totalSignals(47)
                .build();

        // When
        boolean isColdStart = profile.isColdStart();

        // Then
        assertFalse(isColdStart);
    }

    @Test
    void testIsColdStart_ZeroSignals() {
        // Given
        UserInterestProfile profile = UserInterestProfile.builder()
                .userId("user123")
                .totalSignals(0)
                .build();

        // When
        boolean isColdStart = profile.isColdStart();

        // Then
        assertTrue(isColdStart);
    }

    // Property-Based Tests

    @Property
    void incrementTagScore_alwaysIncreasesOrCapsScore(
            @ForAll("scoreValues") double initialScore,
            @ForAll("scoreValues") double weight) {
        // Given
        UserInterestProfile profile = UserInterestProfile.builder()
                .userId("user123")
                .build();
        profile.getTechTagScores().put("TestTag", initialScore);

        // When
        profile.incrementTagScore("TestTag", weight);

        // Then
        double finalScore = profile.getTechTagScores().get("TestTag");
        assertTrue(finalScore >= initialScore, "Score should never decrease");
        assertTrue(finalScore <= 1.0, "Score should never exceed 1.0");
        assertEquals(Math.min(1.0, initialScore + weight), finalScore, 0.0001);
    }

    @Property
    void incrementTagScore_multipleIncrements_neverExceedOne(
            @ForAll("tagWeights") Map<String, Double> weights) {
        // Given
        UserInterestProfile profile = UserInterestProfile.builder()
                .userId("user123")
                .build();

        // When
        for (Map.Entry<String, Double> entry : weights.entrySet()) {
            profile.incrementTagScore(entry.getKey(), entry.getValue());
        }

        // Then
        for (Double score : profile.getTechTagScores().values()) {
            assertTrue(score >= 0.0 && score <= 1.0, 
                    "All scores should be between 0.0 and 1.0");
        }
    }

    @Provide
    Arbitrary<Map<String, Double>> tagWeights() {
        return Arbitraries.maps(
                Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(20),
                Arbitraries.doubles().between(0.0, 1.0)
        ).ofMinSize(1).ofMaxSize(10);
    }

    @Provide
    Arbitrary<Double> scoreValues() {
        return Arbitraries.doubles().between(0.0, 1.0);
    }

    @Property
    void applyTimeDecay_reducesScoresOverTime(
            @ForAll("scoreValues") double initialScore,
            @ForAll @IntRange(min = 1, max = 365) int daysElapsed,
            @ForAll @IntRange(min = 1, max = 90) int halfLifeDays) {
        // Filter out very low initial scores to avoid testing below threshold
        Assume.that(initialScore >= 0.1);
        
        // Given
        Instant pastTime = Instant.now().minus(daysElapsed, ChronoUnit.DAYS);
        UserInterestProfile profile = UserInterestProfile.builder()
                .userId("user123")
                .lastSignalAt(pastTime)
                .build();
        profile.getTechTagScores().put("TestTag", initialScore);

        // When
        profile.applyTimeDecay(halfLifeDays);

        // Then
        Double finalScore = profile.getTechTagScores().get("TestTag");
        if (finalScore != null) {
            assertTrue(finalScore <= initialScore, 
                    "Score should decrease or stay same after decay");
            
            // Verify exponential decay formula
            double expectedDecay = Math.pow(0.5, (double) daysElapsed / halfLifeDays);
            double expectedScore = initialScore * expectedDecay;
            
            if (expectedScore >= 0.01) {
                assertEquals(expectedScore, finalScore, 0.0001);
            }
        } else {
            // Score was removed because it fell below 0.01
            double expectedScore = initialScore * Math.pow(0.5, (double) daysElapsed / halfLifeDays);
            assertTrue(expectedScore < 0.01, 
                    "Score should only be removed if it falls below 0.01");
        }
    }

    @Property
    void isColdStart_correctlyIdentifiesThreshold(
            @ForAll @IntRange(min = 0, max = 100) int totalSignals) {
        // Given
        UserInterestProfile profile = UserInterestProfile.builder()
                .userId("user123")
                .totalSignals(totalSignals)
                .build();

        // When
        boolean isColdStart = profile.isColdStart();

        // Then
        assertEquals(totalSignals < 10, isColdStart,
                "Cold start should be true only when totalSignals < 10");
    }

    @Property
    void techTagScores_alwaysBetweenZeroAndOne(
            @ForAll("tagScoreOperations") Map<String, Double> operations) {
        // Given
        UserInterestProfile profile = UserInterestProfile.builder()
                .userId("user123")
                .build();

        // When - perform multiple increment operations
        for (Map.Entry<String, Double> entry : operations.entrySet()) {
            profile.incrementTagScore(entry.getKey(), entry.getValue());
        }

        // Then - all scores should be valid
        for (Map.Entry<String, Double> entry : profile.getTechTagScores().entrySet()) {
            assertTrue(entry.getValue() >= 0.0, 
                    "Score for " + entry.getKey() + " should be >= 0.0");
            assertTrue(entry.getValue() <= 1.0, 
                    "Score for " + entry.getKey() + " should be <= 1.0");
        }
    }

    @Provide
    Arbitrary<Map<String, Double>> tagScoreOperations() {
        return Arbitraries.maps(
                Arbitraries.of("React", "Python", "Java", "TypeScript", "Go"),
                Arbitraries.doubles().between(0.0, 2.0) // Allow values > 1.0 to test capping
        ).ofMinSize(5).ofMaxSize(20);
    }

    @Property
    void followedBuilders_maintainsSetSemantics(
            @ForAll("builderIds") Set<String> builderIds) {
        // Given
        UserInterestProfile profile = UserInterestProfile.builder()
                .userId("user123")
                .followedBuilders(new HashSet<>(builderIds))
                .build();

        // Then
        assertEquals(builderIds.size(), profile.getFollowedBuilders().size(),
                "Set should maintain unique elements");
        assertTrue(profile.getFollowedBuilders().containsAll(builderIds),
                "Set should contain all added builders");
    }

    @Provide
    Arbitrary<Set<String>> builderIds() {
        return Arbitraries.strings()
                .alpha()
                .ofMinLength(5)
                .ofMaxLength(20)
                .set()
                .ofMinSize(0)
                .ofMaxSize(10);
    }
}
