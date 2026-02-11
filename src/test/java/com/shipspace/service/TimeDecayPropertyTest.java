package com.shipspace.service;

import com.shipspace.model.UserInterestProfile;
import net.jqwik.api.*;
import net.jqwik.api.constraints.DoubleRange;
import net.jqwik.api.constraints.IntRange;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Property-based tests for time decay functionality in UserInterestProfile.
 * 
 * These tests verify the correctness properties of the time decay algorithm
 * across a wide range of inputs using property-based testing with jqwik.
 * 
 * **Validates: Requirements 1.5**
 */
class TimeDecayPropertyTest {

    /**
     * Property: Time decay reduces scores
     * 
     * For any interest profile with scores, applying time decay reduces all scores
     * according to the exponential decay formula:
     * decayed_score = original_score * (0.5 ^ (days_elapsed / half_life_days))
     * 
     * This property verifies that:
     * 1. All scores are reduced (or stay the same if no time has elapsed)
     * 2. The decay follows the exponential formula correctly
     * 3. Scores never become negative
     * 4. Very small scores (< 0.01) are removed
     * 
     * **Validates: Requirements 1.5**
     */
    @Property(tries = 100)
    @Label("Feature: personalized-feed-algorithm, Property: Time decay reduces scores")
    void timeDecayReducesScores(
            @ForAll @IntRange(min = 1, max = 365) int daysElapsed,
            @ForAll @IntRange(min = 1, max = 90) int halfLifeDays,
            @ForAll("techTagScores") Map<String, Double> originalScores
    ) {
        // Arrange - Create a profile with the given scores
        Instant lastSignalAt = Instant.now().minus(daysElapsed, ChronoUnit.DAYS);
        
        UserInterestProfile profile = UserInterestProfile.builder()
                .userId("testUser")
                .techTagScores(new HashMap<>(originalScores)) // Copy to avoid mutation
                .totalSignals(10)
                .lastSignalAt(lastSignalAt)
                .build();

        // Store original scores for comparison
        Map<String, Double> scoresBefore = new HashMap<>(profile.getTechTagScores());

        // Act - Apply time decay
        profile.applyTimeDecay(halfLifeDays);

        // Assert - Verify decay properties
        Map<String, Double> scoresAfter = profile.getTechTagScores();

        // Calculate expected decay factor
        double expectedDecayFactor = Math.pow(0.5, (double) daysElapsed / halfLifeDays);

        // Property 1: All remaining scores should be reduced (or equal if decay factor is 1.0)
        for (Map.Entry<String, Double> entry : scoresAfter.entrySet()) {
            String tag = entry.getKey();
            double scoreAfter = entry.getValue();
            double scoreBefore = scoresBefore.get(tag);

            // Score should be reduced by the decay factor
            double expectedScore = scoreBefore * expectedDecayFactor;
            
            // Allow small floating point error (0.0001)
            assertEquals(expectedScore, scoreAfter, 0.0001,
                    String.format("Score for tag '%s' should be reduced from %.4f to %.4f (decay factor: %.4f)",
                            tag, scoreBefore, expectedScore, expectedDecayFactor));

            // Property 2: Scores should never be negative
            assertTrue(scoreAfter >= 0.0,
                    String.format("Score for tag '%s' should not be negative: %.4f", tag, scoreAfter));

            // Property 3: Scores should be less than or equal to original (unless decay factor is 1.0)
            assertTrue(scoreAfter <= scoreBefore + 0.0001,
                    String.format("Score for tag '%s' should not increase: before=%.4f, after=%.4f",
                            tag, scoreBefore, scoreAfter));
        }

        // Property 4: Very small scores (< 0.01) should be removed
        for (Map.Entry<String, Double> entry : scoresBefore.entrySet()) {
            String tag = entry.getKey();
            double scoreBefore = entry.getValue();
            double expectedScore = scoreBefore * expectedDecayFactor;

            if (expectedScore < 0.01) {
                assertFalse(scoresAfter.containsKey(tag),
                        String.format("Tag '%s' with score %.4f should be removed (below 0.01 threshold)",
                                tag, expectedScore));
            }
        }

        // Property 5: No new tags should be added
        for (String tag : scoresAfter.keySet()) {
            assertTrue(scoresBefore.containsKey(tag),
                    String.format("Tag '%s' should not be added by decay", tag));
        }
    }

    /**
     * Property: Time decay with zero elapsed time has no effect
     * 
     * When no time has elapsed (lastSignalAt is now or in the future),
     * applying time decay should not change any scores.
     * 
     * **Validates: Requirements 1.5**
     */
    @Property(tries = 50)
    @Label("Feature: personalized-feed-algorithm, Property: Time decay with zero elapsed time")
    void timeDecayWithZeroElapsedTime(
            @ForAll @IntRange(min = 1, max = 90) int halfLifeDays,
            @ForAll("techTagScores") Map<String, Double> originalScores
    ) {
        // Arrange - Create a profile with lastSignalAt = now (0 days elapsed)
        UserInterestProfile profile = UserInterestProfile.builder()
                .userId("testUser")
                .techTagScores(new HashMap<>(originalScores))
                .totalSignals(10)
                .lastSignalAt(Instant.now())
                .build();

        Map<String, Double> scoresBefore = new HashMap<>(profile.getTechTagScores());

        // Act
        profile.applyTimeDecay(halfLifeDays);

        // Assert - Scores should be unchanged
        Map<String, Double> scoresAfter = profile.getTechTagScores();
        
        assertEquals(scoresBefore.size(), scoresAfter.size(),
                "Number of tags should remain the same");

        for (Map.Entry<String, Double> entry : scoresBefore.entrySet()) {
            String tag = entry.getKey();
            double scoreBefore = entry.getValue();
            double scoreAfter = scoresAfter.get(tag);

            assertEquals(scoreBefore, scoreAfter, 0.0001,
                    String.format("Score for tag '%s' should be unchanged when no time has elapsed", tag));
        }
    }

    /**
     * Property: Time decay with null lastSignalAt has no effect
     * 
     * When lastSignalAt is null, applying time decay should not change any scores.
     * 
     * **Validates: Requirements 1.5**
     */
    @Property(tries = 50)
    @Label("Feature: personalized-feed-algorithm, Property: Time decay with null lastSignalAt")
    void timeDecayWithNullLastSignalAt(
            @ForAll @IntRange(min = 1, max = 90) int halfLifeDays,
            @ForAll("techTagScores") Map<String, Double> originalScores
    ) {
        // Arrange - Create a profile with null lastSignalAt
        UserInterestProfile profile = UserInterestProfile.builder()
                .userId("testUser")
                .techTagScores(new HashMap<>(originalScores))
                .totalSignals(10)
                .lastSignalAt(null)
                .build();

        Map<String, Double> scoresBefore = new HashMap<>(profile.getTechTagScores());

        // Act
        profile.applyTimeDecay(halfLifeDays);

        // Assert - Scores should be unchanged
        Map<String, Double> scoresAfter = profile.getTechTagScores();
        
        assertEquals(scoresBefore, scoresAfter,
                "Scores should be unchanged when lastSignalAt is null");
    }

    /**
     * Property: Time decay at exactly half-life reduces scores by 50%
     * 
     * When the elapsed time equals the half-life, all scores should be
     * reduced to exactly 50% of their original values (or removed if < 0.01).
     * 
     * **Validates: Requirements 1.5**
     */
    @Property(tries = 50)
    @Label("Feature: personalized-feed-algorithm, Property: Time decay at half-life")
    void timeDecayAtHalfLife(
            @ForAll @IntRange(min = 1, max = 90) int halfLifeDays,
            @ForAll("techTagScores") Map<String, Double> originalScores
    ) {
        // Arrange - Create a profile with lastSignalAt = halfLifeDays ago
        Instant lastSignalAt = Instant.now().minus(halfLifeDays, ChronoUnit.DAYS);
        
        UserInterestProfile profile = UserInterestProfile.builder()
                .userId("testUser")
                .techTagScores(new HashMap<>(originalScores))
                .totalSignals(10)
                .lastSignalAt(lastSignalAt)
                .build();

        Map<String, Double> scoresBefore = new HashMap<>(profile.getTechTagScores());

        // Act
        profile.applyTimeDecay(halfLifeDays);

        // Assert - Scores should be reduced to 50% (or removed if < 0.01)
        Map<String, Double> scoresAfter = profile.getTechTagScores();

        for (Map.Entry<String, Double> entry : scoresBefore.entrySet()) {
            String tag = entry.getKey();
            double scoreBefore = entry.getValue();
            double expectedScore = scoreBefore * 0.5;

            if (expectedScore >= 0.01) {
                assertTrue(scoresAfter.containsKey(tag),
                        String.format("Tag '%s' should still exist after half-life decay", tag));
                
                double scoreAfter = scoresAfter.get(tag);
                assertEquals(expectedScore, scoreAfter, 0.0001,
                        String.format("Score for tag '%s' should be reduced to 50%% at half-life: %.4f -> %.4f",
                                tag, scoreBefore, expectedScore));
            } else {
                assertFalse(scoresAfter.containsKey(tag),
                        String.format("Tag '%s' with score %.4f should be removed (below 0.01 threshold)",
                                tag, expectedScore));
            }
        }
    }

    /**
     * Property: Multiple half-lives compound correctly
     * 
     * After N half-lives, scores should be reduced to (0.5^N) of their original values.
     * 
     * **Validates: Requirements 1.5**
     */
    @Property(tries = 50)
    @Label("Feature: personalized-feed-algorithm, Property: Multiple half-lives compound")
    void multipleHalfLivesCompound(
            @ForAll @IntRange(min = 1, max = 30) int halfLifeDays,
            @ForAll @IntRange(min = 2, max = 5) int numHalfLives,
            @ForAll("techTagScores") Map<String, Double> originalScores
    ) {
        // Arrange - Create a profile with lastSignalAt = numHalfLives * halfLifeDays ago
        int daysElapsed = numHalfLives * halfLifeDays;
        Instant lastSignalAt = Instant.now().minus(daysElapsed, ChronoUnit.DAYS);
        
        UserInterestProfile profile = UserInterestProfile.builder()
                .userId("testUser")
                .techTagScores(new HashMap<>(originalScores))
                .totalSignals(10)
                .lastSignalAt(lastSignalAt)
                .build();

        Map<String, Double> scoresBefore = new HashMap<>(profile.getTechTagScores());

        // Act
        profile.applyTimeDecay(halfLifeDays);

        // Assert - Scores should be reduced to (0.5^numHalfLives)
        Map<String, Double> scoresAfter = profile.getTechTagScores();
        double expectedDecayFactor = Math.pow(0.5, numHalfLives);

        for (Map.Entry<String, Double> entry : scoresBefore.entrySet()) {
            String tag = entry.getKey();
            double scoreBefore = entry.getValue();
            double expectedScore = scoreBefore * expectedDecayFactor;

            if (expectedScore >= 0.01) {
                assertTrue(scoresAfter.containsKey(tag),
                        String.format("Tag '%s' should still exist after %d half-lives", tag, numHalfLives));
                
                double scoreAfter = scoresAfter.get(tag);
                assertEquals(expectedScore, scoreAfter, 0.0001,
                        String.format("Score for tag '%s' should be reduced to %.4f after %d half-lives",
                                tag, expectedScore, numHalfLives));
            } else {
                assertFalse(scoresAfter.containsKey(tag),
                        String.format("Tag '%s' with score %.4f should be removed (below 0.01 threshold)",
                                tag, expectedScore));
            }
        }
    }

    /**
     * Arbitrary generator for tech tag scores.
     * Generates a map of 1-5 tech tags with scores between 0.01 and 1.0.
     */
    @Provide
    Arbitrary<Map<String, Double>> techTagScores() {
        Arbitrary<String> tagNames = Arbitraries.of(
                "Java", "Python", "JavaScript", "TypeScript", "React",
                "Vue", "Angular", "Spring", "Django", "Node.js",
                "MongoDB", "PostgreSQL", "Redis", "Docker", "Kubernetes"
        );

        Arbitrary<Double> scores = Arbitraries.doubles()
                .between(0.01, 1.0)
                .ofScale(2); // 2 decimal places

        return Combinators.combine(
                tagNames.list().ofMinSize(1).ofMaxSize(5).uniqueElements(),
                scores.list().ofMinSize(1).ofMaxSize(5)
        ).as((tags, scoreList) -> {
            Map<String, Double> map = new HashMap<>();
            for (int i = 0; i < tags.size(); i++) {
                map.put(tags.get(i), scoreList.get(i % scoreList.size()));
            }
            return map;
        });
    }
}
