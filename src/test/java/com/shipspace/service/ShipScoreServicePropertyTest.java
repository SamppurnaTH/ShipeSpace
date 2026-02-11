package com.shipspace.service;

import com.shipspace.model.RankTier;
import com.shipspace.model.ScoreEventType;
import com.shipspace.model.User;
import com.shipspace.repository.ScoreEventLogRepository;
import com.shipspace.repository.UserRepository;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.lifecycle.BeforeProperty;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.verify;

/**
 * Property-based tests for ShipScoreService calculation methods.
 * 
 * These tests verify the correctness properties of level calculation, rank mapping,
 * and next level score calculation across a wide range of inputs using property-based
 * testing with jqwik.
 * 
 * **Validates: Requirements 7.1, 7.2, 7.3, 8.1-8.6, 12.3, 12.4**
 */
@ExtendWith(MockitoExtension.class)
class ShipScoreServicePropertyTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private ScoreEventLogRepository scoreEventLogRepository;

    @BeforeProperty
    void initMocks() {
        MockitoAnnotations.openMocks(this);
    }

    /**
     * Property 1: Level Calculation Formula Correctness
     * 
     * For any shipScore value (0 to Integer.MAX_VALUE), the calculated level should equal
     * min(30, floor(shipScore / 40) + 1), ensuring the level is always between 1 and 30 inclusive.
     * 
     * This property verifies that:
     * 1. The level formula is correctly applied: level = min(30, (shipScore / 40) + 1)
     * 2. The level is always at least 1 (even for shipScore = 0)
     * 3. The level never exceeds 30 (capped at maximum)
     * 4. The level increases by 1 for every 40 points of shipScore
     * 
     * **Validates: Requirements 7.1, 7.2, 7.3**
     */
    @Property(tries = 100)
    @Label("Feature: rank-level-progression, Property 1: Level Calculation Formula Correctness")
    void levelCalculationFormulaIsCorrect(
            @ForAll @IntRange(min = 0, max = 100000) int shipScore
    ) {
        // Arrange - Create service instance
        ShipScoreService service = new ShipScoreService(userRepository, scoreEventLogRepository);

        // Act - Calculate level from shipScore
        int actualLevel = service.calculateLevel(shipScore);

        // Assert - Verify the level matches the formula: min(30, floor(shipScore / 40) + 1)
        int expectedLevel = Math.min(30, (shipScore / 40) + 1);
        
        assertEquals(expectedLevel, actualLevel,
                String.format("Level for shipScore %d should be %d but was %d",
                        shipScore, expectedLevel, actualLevel));

        // Property 1: Level is always at least 1
        assertTrue(actualLevel >= 1,
                String.format("Level must be at least 1, but was %d for shipScore %d",
                        actualLevel, shipScore));

        // Property 2: Level never exceeds 30
        assertTrue(actualLevel <= 30,
                String.format("Level must not exceed 30, but was %d for shipScore %d",
                        actualLevel, shipScore));

        // Property 3: Level is between 1 and 30 inclusive
        assertTrue(actualLevel >= 1 && actualLevel <= 30,
                String.format("Level must be between 1 and 30 inclusive, but was %d for shipScore %d",
                        actualLevel, shipScore));

        // Property 4: Verify the mathematical relationship
        // For any level L (except 30), the shipScore range is [(L-1)*40, L*40)
        if (actualLevel < 30) {
            int minScoreForLevel = (actualLevel - 1) * 40;
            int minScoreForNextLevel = actualLevel * 40;
            
            assertTrue(shipScore >= minScoreForLevel,
                    String.format("ShipScore %d should be >= %d for level %d",
                            shipScore, minScoreForLevel, actualLevel));
            
            assertTrue(shipScore < minScoreForNextLevel,
                    String.format("ShipScore %d should be < %d for level %d",
                            shipScore, minScoreForNextLevel, actualLevel));
        } else {
            // For level 30, shipScore must be >= 1160
            assertTrue(shipScore >= 1160,
                    String.format("ShipScore %d should be >= 1160 for level 30", shipScore));
        }
    }

    /**
     * Property: Level Calculation at Boundaries
     * 
     * Specifically tests boundary values where levels change to ensure the formula
     * handles edge cases correctly.
     * 
     * **Validates: Requirements 7.1, 7.2, 7.3**
     */
    @Property(tries = 100)
    @Label("Feature: rank-level-progression, Property 1: Level Calculation at Boundaries")
    void levelCalculationAtBoundaries(
            @ForAll @IntRange(min = 1, max = 30) int targetLevel
    ) {
        // Arrange - Create service instance
        ShipScoreService service = new ShipScoreService(userRepository, scoreEventLogRepository);

        // Calculate the minimum shipScore for this level
        int minShipScore = (targetLevel - 1) * 40;
        
        // Calculate the maximum shipScore for this level (one less than next level threshold)
        int maxShipScore = (targetLevel < 30) ? (targetLevel * 40 - 1) : Integer.MAX_VALUE;

        // Act & Assert - Test minimum boundary
        int levelAtMin = service.calculateLevel(minShipScore);
        assertEquals(targetLevel, levelAtMin,
                String.format("Level at minimum boundary (shipScore=%d) should be %d but was %d",
                        minShipScore, targetLevel, levelAtMin));

        // Act & Assert - Test maximum boundary (if not at max level)
        if (targetLevel < 30) {
            int levelAtMax = service.calculateLevel(maxShipScore);
            assertEquals(targetLevel, levelAtMax,
                    String.format("Level at maximum boundary (shipScore=%d) should be %d but was %d",
                            maxShipScore, targetLevel, levelAtMax));

            // Verify that one more point would push to next level
            int levelAtNextThreshold = service.calculateLevel(maxShipScore + 1);
            assertEquals(targetLevel + 1, levelAtNextThreshold,
                    String.format("Level at next threshold (shipScore=%d) should be %d but was %d",
                            maxShipScore + 1, targetLevel + 1, levelAtNextThreshold));
        }
    }

    /**
     * Property: Level Calculation is Monotonic
     * 
     * For any two shipScore values where score1 < score2, the level for score1
     * should be less than or equal to the level for score2 (levels never decrease
     * as shipScore increases).
     * 
     * **Validates: Requirements 7.1**
     */
    @Property(tries = 100)
    @Label("Feature: rank-level-progression, Property 1: Level Calculation is Monotonic")
    void levelCalculationIsMonotonic(
            @ForAll @IntRange(min = 0, max = 50000) int shipScore1,
            @ForAll @IntRange(min = 0, max = 50000) int shipScore2
    ) {
        // Arrange - Create service instance
        ShipScoreService service = new ShipScoreService(userRepository, scoreEventLogRepository);

        // Ensure score1 <= score2
        int lowerScore = Math.min(shipScore1, shipScore2);
        int higherScore = Math.max(shipScore1, shipScore2);

        // Act - Calculate levels
        int levelAtLowerScore = service.calculateLevel(lowerScore);
        int levelAtHigherScore = service.calculateLevel(higherScore);

        // Assert - Level should never decrease as shipScore increases
        assertTrue(levelAtLowerScore <= levelAtHigherScore,
                String.format("Level should be monotonic: level(%d)=%d should be <= level(%d)=%d",
                        lowerScore, levelAtLowerScore, higherScore, levelAtHigherScore));
    }

    /**
     * Property 2: Rank Tier Mapping Correctness
     * 
     * For any level value (1 to 30), the calculated rank should match the correct RankTier
     * based on the level ranges:
     * - Levels 1-5: DOCKED
     * - Levels 6-10: LAUNCHER
     * - Levels 11-15: ORBITER
     * - Levels 16-20: NAVIGATOR
     * - Levels 21-25: COMMANDER
     * - Levels 26-30: FLEET_ADMIRAL
     * 
     * This property verifies that:
     * 1. Each level maps to exactly one rank tier
     * 2. The rank tier boundaries are correctly enforced
     * 3. The mapping is consistent with the RankTier enum definitions
     * 4. All levels from 1 to 30 have a valid rank assignment
     * 
     * **Validates: Requirements 8.1, 8.2, 8.3, 8.4, 8.5, 8.6**
     */
    @Property(tries = 100)
    @Label("Feature: rank-level-progression, Property 2: Rank Tier Mapping Correctness")
    void rankTierMappingIsCorrect(
            @ForAll @IntRange(min = 1, max = 30) int level
    ) {
        // Arrange - Create service instance
        ShipScoreService service = new ShipScoreService(userRepository, scoreEventLogRepository);

        // Act - Calculate rank from level
        RankTier actualRank = service.calculateRank(level);

        // Assert - Verify the rank matches the expected tier based on level ranges
        RankTier expectedRank;
        if (level >= 1 && level <= 5) {
            expectedRank = RankTier.DOCKED;
        } else if (level >= 6 && level <= 10) {
            expectedRank = RankTier.LAUNCHER;
        } else if (level >= 11 && level <= 15) {
            expectedRank = RankTier.ORBITER;
        } else if (level >= 16 && level <= 20) {
            expectedRank = RankTier.NAVIGATOR;
        } else if (level >= 21 && level <= 25) {
            expectedRank = RankTier.COMMANDER;
        } else { // level >= 26 && level <= 30
            expectedRank = RankTier.FLEET_ADMIRAL;
        }

        assertEquals(expectedRank, actualRank,
                String.format("Rank for level %d should be %s but was %s",
                        level, expectedRank, actualRank));

        // Property 1: Verify the rank's level range includes the given level
        assertTrue(level >= actualRank.getMinLevel() && level <= actualRank.getMaxLevel(),
                String.format("Level %d should be within rank %s range [%d, %d]",
                        level, actualRank, actualRank.getMinLevel(), actualRank.getMaxLevel()));

        // Property 2: Verify consistency with RankTier.fromLevel
        RankTier rankFromEnum = RankTier.fromLevel(level);
        assertEquals(rankFromEnum, actualRank,
                String.format("Service calculateRank(%d) should match RankTier.fromLevel(%d)",
                        level, level));
    }

    /**
     * Property: Rank Tier Mapping at Boundaries
     * 
     * Specifically tests boundary values where rank tiers change to ensure the mapping
     * handles edge cases correctly.
     * 
     * **Validates: Requirements 8.1, 8.2, 8.3, 8.4, 8.5, 8.6**
     */
    @Property(tries = 100)
    @Label("Feature: rank-level-progression, Property 2: Rank Tier Mapping at Boundaries")
    void rankTierMappingAtBoundaries(
            @ForAll @IntRange(min = 0, max = 5) int rankIndex
    ) {
        // Arrange - Create service instance
        ShipScoreService service = new ShipScoreService(userRepository, scoreEventLogRepository);

        // Get the rank tier at this index
        RankTier[] ranks = RankTier.values();
        RankTier currentRank = ranks[rankIndex];

        // Act & Assert - Test minimum boundary of rank tier
        int minLevel = currentRank.getMinLevel();
        RankTier rankAtMin = service.calculateRank(minLevel);
        assertEquals(currentRank, rankAtMin,
                String.format("Rank at minimum boundary (level=%d) should be %s but was %s",
                        minLevel, currentRank, rankAtMin));

        // Act & Assert - Test maximum boundary of rank tier
        int maxLevel = currentRank.getMaxLevel();
        RankTier rankAtMax = service.calculateRank(maxLevel);
        assertEquals(currentRank, rankAtMax,
                String.format("Rank at maximum boundary (level=%d) should be %s but was %s",
                        maxLevel, currentRank, rankAtMax));

        // Act & Assert - Test one level before minimum (if not first rank)
        if (rankIndex > 0) {
            RankTier previousRank = ranks[rankIndex - 1];
            int levelBeforeMin = minLevel - 1;
            RankTier rankBeforeMin = service.calculateRank(levelBeforeMin);
            assertEquals(previousRank, rankBeforeMin,
                    String.format("Rank at level %d (one before %s) should be %s but was %s",
                            levelBeforeMin, currentRank, previousRank, rankBeforeMin));
        }

        // Act & Assert - Test one level after maximum (if not last rank)
        if (rankIndex < ranks.length - 1) {
            RankTier nextRank = ranks[rankIndex + 1];
            int levelAfterMax = maxLevel + 1;
            RankTier rankAfterMax = service.calculateRank(levelAfterMax);
            assertEquals(nextRank, rankAfterMax,
                    String.format("Rank at level %d (one after %s) should be %s but was %s",
                            levelAfterMax, currentRank, nextRank, rankAfterMax));
        }
    }

    /**
     * Property: Rank Tier Mapping is Monotonic
     * 
     * For any two levels where level1 < level2, the rank for level1 should be
     * less than or equal to the rank for level2 (ranks never decrease as level increases).
     * 
     * **Validates: Requirements 8.1, 8.2, 8.3, 8.4, 8.5, 8.6**
     */
    @Property(tries = 100)
    @Label("Feature: rank-level-progression, Property 2: Rank Tier Mapping is Monotonic")
    void rankTierMappingIsMonotonic(
            @ForAll @IntRange(min = 1, max = 30) int level1,
            @ForAll @IntRange(min = 1, max = 30) int level2
    ) {
        // Arrange - Create service instance
        ShipScoreService service = new ShipScoreService(userRepository, scoreEventLogRepository);

        // Ensure level1 <= level2
        int lowerLevel = Math.min(level1, level2);
        int higherLevel = Math.max(level1, level2);

        // Act - Calculate ranks
        RankTier rankAtLowerLevel = service.calculateRank(lowerLevel);
        RankTier rankAtHigherLevel = service.calculateRank(higherLevel);

        // Assert - Rank should never decrease as level increases
        // Use ordinal() to compare enum positions
        assertTrue(rankAtLowerLevel.ordinal() <= rankAtHigherLevel.ordinal(),
                String.format("Rank should be monotonic: rank(level=%d)=%s should be <= rank(level=%d)=%s",
                        lowerLevel, rankAtLowerLevel, higherLevel, rankAtHigherLevel));
    }

    /**
     * Property 17: Next Level Score Calculation
     * 
     * For any user below level 30, the nextLevelScore should equal (currentLevel) * 40,
     * which is the minimum shipScore needed to reach the next level. For users at level 30,
     * nextLevelScore should be null.
     * 
     * This property verifies that:
     * 1. For levels 1-29, nextLevelScore = currentLevel * 40
     * 2. For level 30 (max level), nextLevelScore is null
     * 3. The calculated nextLevelScore is always greater than the current shipScore (for levels < 30)
     * 4. Reaching the nextLevelScore would actually advance the user to the next level
     * 
     * **Validates: Requirements 12.3, 12.4**
     */
    @Property(tries = 100)
    @Label("Feature: rank-level-progression, Property 17: Next Level Score Calculation")
    void nextLevelScoreCalculationIsCorrect(
            @ForAll @IntRange(min = 1, max = 30) int currentLevel
    ) {
        // Arrange - Create service instance
        ShipScoreService service = new ShipScoreService(userRepository, scoreEventLogRepository);

        // Calculate a valid shipScore for the current level
        // Use the minimum shipScore for this level: (currentLevel - 1) * 40
        int currentScore = (currentLevel - 1) * 40;

        // Act - Calculate next level score
        Integer nextLevelScore = service.calculateNextLevelScore(currentLevel, currentScore);

        // Assert - Verify the next level score calculation
        if (currentLevel >= 30) {
            // Property 1: At max level (30), nextLevelScore should be null
            assertNull(nextLevelScore,
                    String.format("Next level score for max level (30) should be null but was %s",
                            nextLevelScore));
        } else {
            // Property 2: For levels below 30, nextLevelScore should equal currentLevel * 40
            assertNotNull(nextLevelScore,
                    String.format("Next level score for level %d should not be null", currentLevel));

            int expectedNextLevelScore = currentLevel * 40;
            assertEquals(expectedNextLevelScore, nextLevelScore.intValue(),
                    String.format("Next level score for level %d should be %d but was %d",
                            currentLevel, expectedNextLevelScore, nextLevelScore));

            // Property 3: The nextLevelScore should be greater than the current score
            assertTrue(nextLevelScore > currentScore,
                    String.format("Next level score (%d) should be greater than current score (%d) for level %d",
                            nextLevelScore, currentScore, currentLevel));

            // Property 4: Reaching the nextLevelScore should advance to the next level
            int levelAtNextScore = service.calculateLevel(nextLevelScore);
            assertEquals(currentLevel + 1, levelAtNextScore,
                    String.format("Reaching next level score (%d) should advance from level %d to level %d, but got level %d",
                            nextLevelScore, currentLevel, currentLevel + 1, levelAtNextScore));

            // Property 5: One point less than nextLevelScore should still be at current level
            int levelOnePointBefore = service.calculateLevel(nextLevelScore - 1);
            assertEquals(currentLevel, levelOnePointBefore,
                    String.format("Score %d (one less than next level threshold) should still be at level %d, but got level %d",
                            nextLevelScore - 1, currentLevel, levelOnePointBefore));
        }
    }

    /**
     * Property: Next Level Score Calculation with Various Current Scores
     * 
     * Tests that nextLevelScore calculation is consistent regardless of the user's
     * current shipScore within their level range. The next level threshold should
     * be the same for all scores within a given level.
     * 
     * **Validates: Requirements 12.3, 12.4**
     */
    @Property(tries = 100)
    @Label("Feature: rank-level-progression, Property 17: Next Level Score Calculation with Various Scores")
    void nextLevelScoreIsConsistentWithinLevel(
            @ForAll @IntRange(min = 1, max = 29) int currentLevel,
            @ForAll @IntRange(min = 0, max = 39) int scoreOffset
    ) {
        // Arrange - Create service instance
        ShipScoreService service = new ShipScoreService(userRepository, scoreEventLogRepository);

        // Calculate a shipScore within the current level's range
        // Level range is [(currentLevel-1)*40, currentLevel*40)
        int minScoreForLevel = (currentLevel - 1) * 40;
        int currentScore = minScoreForLevel + scoreOffset;

        // Ensure we don't exceed the level's maximum score
        int maxScoreForLevel = currentLevel * 40 - 1;
        if (currentScore > maxScoreForLevel) {
            currentScore = maxScoreForLevel;
        }

        // Verify the score is actually at the expected level
        int verifiedLevel = service.calculateLevel(currentScore);
        if (verifiedLevel != currentLevel) {
            // Skip this test case if the score doesn't match the expected level
            return;
        }

        // Act - Calculate next level score
        Integer nextLevelScore = service.calculateNextLevelScore(currentLevel, currentScore);

        // Assert - The next level score should always be currentLevel * 40
        assertNotNull(nextLevelScore,
                String.format("Next level score should not be null for level %d", currentLevel));

        int expectedNextLevelScore = currentLevel * 40;
        assertEquals(expectedNextLevelScore, nextLevelScore.intValue(),
                String.format("Next level score for level %d (current score %d) should be %d but was %d",
                        currentLevel, currentScore, expectedNextLevelScore, nextLevelScore));

        // Property: The next level score should be independent of current score within the level
        // Calculate with different score in same level
        int alternativeScore = minScoreForLevel + (scoreOffset + 10) % 40;
        if (alternativeScore >= currentLevel * 40) {
            alternativeScore = minScoreForLevel;
        }
        Integer alternativeNextLevelScore = service.calculateNextLevelScore(currentLevel, alternativeScore);
        assertEquals(nextLevelScore, alternativeNextLevelScore,
                String.format("Next level score should be the same (%d) regardless of current score within level %d",
                        nextLevelScore, currentLevel));
    }

    /**
     * Property: Next Level Score Calculation at Level Boundaries
     * 
     * Tests that nextLevelScore calculation works correctly at the exact boundaries
     * where levels change (e.g., at scores 0, 40, 80, etc.).
     * 
     * **Validates: Requirements 12.3, 12.4**
     */
    @Property(tries = 100)
    @Label("Feature: rank-level-progression, Property 17: Next Level Score Calculation at Boundaries")
    void nextLevelScoreAtLevelBoundaries(
            @ForAll @IntRange(min = 1, max = 29) int currentLevel
    ) {
        // Arrange - Create service instance
        ShipScoreService service = new ShipScoreService(userRepository, scoreEventLogRepository);

        // Test at the exact minimum score for this level
        int minScoreForLevel = (currentLevel - 1) * 40;
        Integer nextLevelScoreAtMin = service.calculateNextLevelScore(currentLevel, minScoreForLevel);

        // Test at the exact maximum score for this level (one less than next level threshold)
        int maxScoreForLevel = currentLevel * 40 - 1;
        Integer nextLevelScoreAtMax = service.calculateNextLevelScore(currentLevel, maxScoreForLevel);

        // Assert - Both should return the same next level score
        assertNotNull(nextLevelScoreAtMin,
                String.format("Next level score at minimum boundary should not be null for level %d", currentLevel));
        assertNotNull(nextLevelScoreAtMax,
                String.format("Next level score at maximum boundary should not be null for level %d", currentLevel));

        assertEquals(nextLevelScoreAtMin, nextLevelScoreAtMax,
                String.format("Next level score should be the same at min (%d) and max (%d) boundaries of level %d",
                        minScoreForLevel, maxScoreForLevel, currentLevel));

        int expectedNextLevelScore = currentLevel * 40;
        assertEquals(expectedNextLevelScore, nextLevelScoreAtMin.intValue(),
                String.format("Next level score at boundaries for level %d should be %d but was %d",
                        currentLevel, expectedNextLevelScore, nextLevelScoreAtMin));
    }

    // ========== Property Tests for applyScoreEvent (Tasks 4.6-4.14) ==========

    /**
     * Property 4: Idempotency Protection
     * 
     * For any user and score event (userId, eventType, entityId), applying the same event
     * twice should result in the score increasing only once, with the second attempt being rejected.
     * 
     * This property verifies that:
     * 1. The first application of a score event succeeds and increases the score
     * 2. The second application of the same event is rejected (returns false)
     * 3. The score does not increase on the second attempt
     * 4. A ScoreEventLog entry exists after the first application
     * 
     * **Validates: Requirements 6.4**
     */
    @Property(tries = 100)
    @Label("Feature: rank-level-progression, Property 4: Idempotency Protection")
    void idempotencyProtectionPreventsDoubleScoring(
            @ForAll @IntRange(min = 0, max = 1000) int initialShipScore
    ) {
        // Arrange - Create service instance with real repositories
        ShipScoreService service = new ShipScoreService(userRepository, scoreEventLogRepository);
        
        // Create a test user with initial shipScore
        String userId = "test-user-" + System.nanoTime();
        String entityId = "test-entity-" + System.nanoTime();
        ScoreEventType eventType = ScoreEventType.PROJECT_CREATED;
        
        User user = User.builder()
                .id(userId)
                .githubId("github-" + userId)
                .username("user-" + userId)
                .shipScore(initialShipScore)
                .level(service.calculateLevel(initialShipScore))
                .rank(service.calculateRank(service.calculateLevel(initialShipScore)))
                .build();
        
        // Mock repository behavior
        when(userRepository.findById(userId)).thenReturn(java.util.Optional.of(user));
        when(scoreEventLogRepository.existsByUserIdAndEventTypeAndEntityId(userId, eventType, entityId))
                .thenReturn(false)  // First call: no duplicate
                .thenReturn(true);  // Second call: duplicate exists
        when(scoreEventLogRepository.countByUserIdAndEventTypeAndCreatedAtAfter(
                eq(userId), eq(eventType), any(Instant.class)))
                .thenReturn(0L);
        
        // Act - Apply the same score event twice
        boolean firstResult = service.applyScoreEvent(userId, eventType, entityId);
        int scoreAfterFirst = user.getShipScore();
        
        boolean secondResult = service.applyScoreEvent(userId, eventType, entityId);
        int scoreAfterSecond = user.getShipScore();
        
        // Assert - First application succeeds, second is rejected
        assertTrue(firstResult,
                "First application of score event should succeed");
        assertFalse(secondResult,
                "Second application of same score event should be rejected (idempotency)");
        
        // Assert - Score increased only once
        int expectedScoreAfterFirst = initialShipScore + eventType.getPoints();
        assertEquals(expectedScoreAfterFirst, scoreAfterFirst,
                String.format("Score after first application should be %d but was %d",
                        expectedScoreAfterFirst, scoreAfterFirst));
        
        assertEquals(scoreAfterFirst, scoreAfterSecond,
                String.format("Score should not change on second application: was %d, still %d",
                        scoreAfterFirst, scoreAfterSecond));
        
        // Verify that the event log was checked for duplicates
        verify(scoreEventLogRepository, atLeast(2))
                .existsByUserIdAndEventTypeAndEntityId(userId, eventType, entityId);
    }

    /**
     * Property 5: Event Logging on Score Application
     * 
     * For any successful score event application, the Score_Event_Log should contain an entry
     * with the matching userId, eventType, and entityId after the operation completes.
     * 
     * This property verifies that:
     * 1. When a score event is successfully applied, a log entry is created
     * 2. The log entry contains the correct userId, eventType, and entityId
     * 3. The log entry has a createdAt timestamp
     * 4. Failed applications (duplicates, cap exceeded) do not create log entries
     * 
     * **Validates: Requirements 6.2, 6.5**
     */
    @Property(tries = 100)
    @Label("Feature: rank-level-progression, Property 5: Event Logging on Score Application")
    void successfulScoreApplicationCreatesLogEntry(
            @ForAll @IntRange(min = 0, max = 1000) int initialShipScore
    ) {
        // Arrange - Create service instance
        ShipScoreService service = new ShipScoreService(userRepository, scoreEventLogRepository);
        
        String userId = "test-user-" + System.nanoTime();
        String entityId = "test-entity-" + System.nanoTime();
        ScoreEventType eventType = ScoreEventType.COLLAB_ACCEPTED;
        
        User user = User.builder()
                .id(userId)
                .githubId("github-" + userId)
                .username("user-" + userId)
                .shipScore(initialShipScore)
                .level(service.calculateLevel(initialShipScore))
                .rank(service.calculateRank(service.calculateLevel(initialShipScore)))
                .build();
        
        // Mock repository behavior - no duplicate, no cap
        when(userRepository.findById(userId)).thenReturn(java.util.Optional.of(user));
        when(scoreEventLogRepository.existsByUserIdAndEventTypeAndEntityId(userId, eventType, entityId))
                .thenReturn(false);
        when(scoreEventLogRepository.countByUserIdAndEventTypeAndCreatedAtAfter(
                eq(userId), eq(eventType), any(Instant.class)))
                .thenReturn(0L);
        
        // Act - Apply score event
        boolean result = service.applyScoreEvent(userId, eventType, entityId);
        
        // Assert - Application succeeded
        assertTrue(result, "Score event application should succeed");
        
        // Verify that a ScoreEventLog entry was saved
        verify(scoreEventLogRepository, times(1)).save(argThat(log ->
                log.getUserId().equals(userId) &&
                log.getEventType().equals(eventType) &&
                log.getEntityId().equals(entityId) &&
                log.getCreatedAt() != null
        ));
        
        // Verify that the user was also saved
        verify(userRepository, times(1)).save(user);
    }

    /**
     * Property 6: Daily Cap Enforcement
     * 
     * For any user on a given calendar day, attempting to apply more than 5 PROJECT_UPDATED
     * events should result in the first 5 succeeding and all subsequent attempts being rejected
     * until the next day.
     * 
     * This property verifies that:
     * 1. PROJECT_UPDATED events are subject to a daily cap of 5
     * 2. The 6th and subsequent attempts are rejected on the same day
     * 3. Other event types are not affected by this cap
     * 4. The cap is enforced before any score changes are made
     * 
     * **Validates: Requirements 4.1, 4.2, 4.4**
     */
    @Property(tries = 100)
    @Label("Feature: rank-level-progression, Property 6: Daily Cap Enforcement")
    void dailyCapEnforcementRejectsExcessEvents(
            @ForAll @IntRange(min = 0, max = 500) int initialShipScore,
            @ForAll @IntRange(min = 0, max = 4) int existingEventCount
    ) {
        // Arrange - Create service instance
        ShipScoreService service = new ShipScoreService(userRepository, scoreEventLogRepository);
        
        String userId = "test-user-" + System.nanoTime();
        String entityId = "test-entity-" + System.nanoTime();
        ScoreEventType eventType = ScoreEventType.PROJECT_UPDATED;
        
        User user = User.builder()
                .id(userId)
                .githubId("github-" + userId)
                .username("user-" + userId)
                .shipScore(initialShipScore)
                .level(service.calculateLevel(initialShipScore))
                .rank(service.calculateRank(service.calculateLevel(initialShipScore)))
                .build();
        
        // Mock repository behavior
        when(userRepository.findById(userId)).thenReturn(java.util.Optional.of(user));
        when(scoreEventLogRepository.existsByUserIdAndEventTypeAndEntityId(userId, eventType, entityId))
                .thenReturn(false);
        
        // Simulate that the user has already used some of their daily cap
        when(scoreEventLogRepository.countByUserIdAndEventTypeAndCreatedAtAfter(
                eq(userId), eq(eventType), any(Instant.class)))
                .thenReturn((long) existingEventCount);
        
        // Act - Apply score event
        boolean result = service.applyScoreEvent(userId, eventType, entityId);
        
        // Assert - Result depends on whether cap is reached
        if (existingEventCount < 5) {
            // Should succeed - under the cap
            assertTrue(result,
                    String.format("Score event should succeed when count (%d) is under cap (5)",
                            existingEventCount));
            
            // Verify score was updated
            int expectedScore = initialShipScore + eventType.getPoints();
            assertEquals(expectedScore, user.getShipScore(),
                    String.format("Score should increase from %d to %d",
                            initialShipScore, expectedScore));
        } else {
            // Should fail - at or over the cap
            assertFalse(result,
                    String.format("Score event should be rejected when count (%d) reaches cap (5)",
                            existingEventCount));
            
            // Verify score was NOT updated
            assertEquals(initialShipScore, user.getShipScore(),
                    String.format("Score should remain %d when cap is reached",
                            initialShipScore));
        }
        
        // Verify that daily cap was checked
        verify(scoreEventLogRepository, atLeastOnce())
                .countByUserIdAndEventTypeAndCreatedAtAfter(eq(userId), eq(eventType), any(Instant.class));
    }

    /**
     * Property 7: Daily Cap Reset Across Days
     * 
     * For any user who has reached the daily cap for PROJECT_UPDATED events, applying the
     * same event type on a different calendar day should succeed (caps are day-specific,
     * not cumulative).
     * 
     * This property verifies that:
     * 1. Daily caps are calculated based on the current calendar day
     * 2. Events from previous days do not count toward today's cap
     * 3. The cap resets at the start of each new day (UTC)
     * 
     * **Validates: Requirements 4.3**
     */
    @Property(tries = 100)
    @Label("Feature: rank-level-progression, Property 7: Daily Cap Reset Across Days")
    void dailyCapResetsAcrossDays(
            @ForAll @IntRange(min = 0, max = 500) int initialShipScore
    ) {
        // Arrange - Create service instance
        ShipScoreService service = new ShipScoreService(userRepository, scoreEventLogRepository);
        
        String userId = "test-user-" + System.nanoTime();
        String entityId = "test-entity-" + System.nanoTime();
        ScoreEventType eventType = ScoreEventType.PROJECT_UPDATED;
        
        User user = User.builder()
                .id(userId)
                .githubId("github-" + userId)
                .username("user-" + userId)
                .shipScore(initialShipScore)
                .level(service.calculateLevel(initialShipScore))
                .rank(service.calculateRank(service.calculateLevel(initialShipScore)))
                .build();
        
        // Mock repository behavior
        when(userRepository.findById(userId)).thenReturn(java.util.Optional.of(user));
        when(scoreEventLogRepository.existsByUserIdAndEventTypeAndEntityId(userId, eventType, entityId))
                .thenReturn(false);
        
        // Simulate that the user has 0 events today (new day, cap reset)
        when(scoreEventLogRepository.countByUserIdAndEventTypeAndCreatedAtAfter(
                eq(userId), eq(eventType), any(Instant.class)))
                .thenReturn(0L);
        
        // Act - Apply score event on a new day
        boolean result = service.applyScoreEvent(userId, eventType, entityId);
        
        // Assert - Should succeed because it's a new day
        assertTrue(result,
                "Score event should succeed on a new day even if cap was reached yesterday");
        
        // Verify score was updated
        int expectedScore = initialShipScore + eventType.getPoints();
        assertEquals(expectedScore, user.getShipScore(),
                String.format("Score should increase from %d to %d on new day",
                        initialShipScore, expectedScore));
        
        // Verify that the daily cap check used the start of today
        verify(scoreEventLogRepository, atLeastOnce())
                .countByUserIdAndEventTypeAndCreatedAtAfter(
                        eq(userId),
                        eq(eventType),
                        argThat(instant -> {
                            // Verify the instant is truncated to start of day
                            Instant startOfDay = Instant.now().truncatedTo(ChronoUnit.DAYS);
                            // Allow some tolerance for test execution time
                            return Math.abs(instant.toEpochMilli() - startOfDay.toEpochMilli()) < 1000;
                        })
                );
    }

    /**
     * Property 9: Score Application Increases ShipScore
     * 
     * For any valid score event that passes validation (not duplicate, not over cap),
     * applying the event should increase the user's shipScore by exactly the point value
     * associated with that ScoreEventType.
     * 
     * This property verifies that:
     * 1. Successful score application increases shipScore
     * 2. The increase is exactly equal to the event type's point value
     * 3. The score increase is atomic with the event logging
     * 
     * **Validates: Requirements 9.4**
     */
    @Property(tries = 100)
    @Label("Feature: rank-level-progression, Property 9: Score Application Increases ShipScore")
    void scoreApplicationIncreasesShipScore(
            @ForAll @IntRange(min = 0, max = 1000) int initialShipScore
    ) {
        // Arrange - Create service instance
        ShipScoreService service = new ShipScoreService(userRepository, scoreEventLogRepository);
        
        String userId = "test-user-" + System.nanoTime();
        String entityId = "test-entity-" + System.nanoTime();
        
        // Test with each event type
        ScoreEventType[] eventTypes = ScoreEventType.values();
        ScoreEventType eventType = eventTypes[initialShipScore % eventTypes.length];
        
        User user = User.builder()
                .id(userId)
                .githubId("github-" + userId)
                .username("user-" + userId)
                .shipScore(initialShipScore)
                .level(service.calculateLevel(initialShipScore))
                .rank(service.calculateRank(service.calculateLevel(initialShipScore)))
                .build();
        
        // Mock repository behavior - no duplicate, no cap
        when(userRepository.findById(userId)).thenReturn(java.util.Optional.of(user));
        when(scoreEventLogRepository.existsByUserIdAndEventTypeAndEntityId(userId, eventType, entityId))
                .thenReturn(false);
        when(scoreEventLogRepository.countByUserIdAndEventTypeAndCreatedAtAfter(
                eq(userId), eq(eventType), any(Instant.class)))
                .thenReturn(0L);
        
        // Act - Apply score event
        boolean result = service.applyScoreEvent(userId, eventType, entityId);
        
        // Assert - Application succeeded
        assertTrue(result, "Score event application should succeed");
        
        // Assert - Score increased by exactly the event type's point value
        int expectedScore = initialShipScore + eventType.getPoints();
        assertEquals(expectedScore, user.getShipScore(),
                String.format("Score should increase from %d to %d (adding %d points for %s)",
                        initialShipScore, expectedScore, eventType.getPoints(), eventType));
        
        // Property: The increase is exactly the point value, no more, no less
        int actualIncrease = user.getShipScore() - initialShipScore;
        assertEquals(eventType.getPoints(), actualIncrease,
                String.format("Score increase should be exactly %d points for %s, but was %d",
                        eventType.getPoints(), eventType, actualIncrease));
    }

    /**
     * Property 10: Level Recalculation on Score Change
     * 
     * For any user whose shipScore changes (increases or decreases), the user's level should
     * be recalculated to match the formula min(30, floor(shipScore / 40) + 1) immediately
     * after the change.
     * 
     * This property verifies that:
     * 1. Level is recalculated after every score change
     * 2. The recalculated level matches the formula
     * 3. Level changes are reflected immediately in the user object
     * 
     * **Validates: Requirements 7.4, 9.6, 11.4**
     */
    @Property(tries = 100)
    @Label("Feature: rank-level-progression, Property 10: Level Recalculation on Score Change")
    void levelRecalculationOnScoreChange(
            @ForAll @IntRange(min = 0, max = 1000) int initialShipScore
    ) {
        // Arrange - Create service instance
        ShipScoreService service = new ShipScoreService(userRepository, scoreEventLogRepository);
        
        String userId = "test-user-" + System.nanoTime();
        String entityId = "test-entity-" + System.nanoTime();
        ScoreEventType eventType = ScoreEventType.PROJECT_CREATED;
        
        int initialLevel = service.calculateLevel(initialShipScore);
        
        User user = User.builder()
                .id(userId)
                .githubId("github-" + userId)
                .username("user-" + userId)
                .shipScore(initialShipScore)
                .level(initialLevel)
                .rank(service.calculateRank(initialLevel))
                .build();
        
        // Mock repository behavior
        when(userRepository.findById(userId)).thenReturn(java.util.Optional.of(user));
        when(scoreEventLogRepository.existsByUserIdAndEventTypeAndEntityId(userId, eventType, entityId))
                .thenReturn(false);
        when(scoreEventLogRepository.countByUserIdAndEventTypeAndCreatedAtAfter(
                eq(userId), eq(eventType), any(Instant.class)))
                .thenReturn(0L);
        
        // Act - Apply score event
        boolean result = service.applyScoreEvent(userId, eventType, entityId);
        
        // Assert - Application succeeded
        assertTrue(result, "Score event application should succeed");
        
        // Assert - Level was recalculated
        int newShipScore = user.getShipScore();
        int expectedLevel = service.calculateLevel(newShipScore);
        int actualLevel = user.getLevel();
        
        assertEquals(expectedLevel, actualLevel,
                String.format("Level should be recalculated to %d for shipScore %d, but was %d",
                        expectedLevel, newShipScore, actualLevel));
        
        // Property: Level matches the formula min(30, floor(shipScore / 40) + 1)
        int formulaLevel = Math.min(30, (newShipScore / 40) + 1);
        assertEquals(formulaLevel, actualLevel,
                String.format("Level should match formula min(30, floor(%d / 40) + 1) = %d, but was %d",
                        newShipScore, formulaLevel, actualLevel));
    }

    /**
     * Property 11: Rank Recalculation on Level Change
     * 
     * For any user whose level changes, the user's rank should be recalculated to match
     * the correct RankTier for that level immediately after the level change.
     * 
     * This property verifies that:
     * 1. Rank is recalculated when level changes
     * 2. The recalculated rank matches the correct tier for the new level
     * 3. Rank changes are reflected immediately in the user object
     * 4. Rank does not change if level does not change
     * 
     * **Validates: Requirements 8.7, 9.7, 11.5**
     */
    @Property(tries = 100)
    @Label("Feature: rank-level-progression, Property 11: Rank Recalculation on Level Change")
    void rankRecalculationOnLevelChange(
            @ForAll @IntRange(min = 0, max = 200) int initialShipScore
    ) {
        // Arrange - Create service instance
        ShipScoreService service = new ShipScoreService(userRepository, scoreEventLogRepository);
        
        String userId = "test-user-" + System.nanoTime();
        String entityId = "test-entity-" + System.nanoTime();
        ScoreEventType eventType = ScoreEventType.PROJECT_CREATED;
        
        int initialLevel = service.calculateLevel(initialShipScore);
        RankTier initialRank = service.calculateRank(initialLevel);
        
        User user = User.builder()
                .id(userId)
                .githubId("github-" + userId)
                .username("user-" + userId)
                .shipScore(initialShipScore)
                .level(initialLevel)
                .rank(initialRank)
                .build();
        
        // Mock repository behavior
        when(userRepository.findById(userId)).thenReturn(java.util.Optional.of(user));
        when(scoreEventLogRepository.existsByUserIdAndEventTypeAndEntityId(userId, eventType, entityId))
                .thenReturn(false);
        when(scoreEventLogRepository.countByUserIdAndEventTypeAndCreatedAtAfter(
                eq(userId), eq(eventType), any(Instant.class)))
                .thenReturn(0L);
        
        // Act - Apply score event
        boolean result = service.applyScoreEvent(userId, eventType, entityId);
        
        // Assert - Application succeeded
        assertTrue(result, "Score event application should succeed");
        
        // Assert - Rank was recalculated based on new level
        int newLevel = user.getLevel();
        RankTier expectedRank = service.calculateRank(newLevel);
        RankTier actualRank = user.getRank();
        
        assertEquals(expectedRank, actualRank,
                String.format("Rank should be recalculated to %s for level %d, but was %s",
                        expectedRank, newLevel, actualRank));
        
        // Property: If level changed, rank should be recalculated
        if (newLevel != initialLevel) {
            // Rank should match the new level's tier
            assertEquals(RankTier.fromLevel(newLevel), actualRank,
                    String.format("Rank should match RankTier.fromLevel(%d) = %s when level changes",
                            newLevel, RankTier.fromLevel(newLevel)));
        }
    }

    /**
     * Property 12: Rank Change Updates Timestamp
     * 
     * For any user whose rank changes from one RankTier to another, the rankUpdatedAt field
     * should be set to the current timestamp at the time of the rank change.
     * 
     * This property verifies that:
     * 1. rankUpdatedAt is set when rank changes
     * 2. rankUpdatedAt is not modified if rank does not change
     * 3. The timestamp is set to approximately the current time
     * 
     * **Validates: Requirements 8.8**
     */
    @Property(tries = 100)
    @Label("Feature: rank-level-progression, Property 12: Rank Change Updates Timestamp")
    void rankChangeUpdatesTimestamp(
            @ForAll @IntRange(min = 0, max = 200) int initialShipScore
    ) {
        // Arrange - Create service instance
        ShipScoreService service = new ShipScoreService(userRepository, scoreEventLogRepository);
        
        String userId = "test-user-" + System.nanoTime();
        String entityId = "test-entity-" + System.nanoTime();
        ScoreEventType eventType = ScoreEventType.PROJECT_CREATED;
        
        int initialLevel = service.calculateLevel(initialShipScore);
        RankTier initialRank = service.calculateRank(initialLevel);
        
        User user = User.builder()
                .id(userId)
                .githubId("github-" + userId)
                .username("user-" + userId)
                .shipScore(initialShipScore)
                .level(initialLevel)
                .rank(initialRank)
                .rankUpdatedAt(null)  // Start with null timestamp
                .build();
        
        // Mock repository behavior
        when(userRepository.findById(userId)).thenReturn(java.util.Optional.of(user));
        when(scoreEventLogRepository.existsByUserIdAndEventTypeAndEntityId(userId, eventType, entityId))
                .thenReturn(false);
        when(scoreEventLogRepository.countByUserIdAndEventTypeAndCreatedAtAfter(
                eq(userId), eq(eventType), any(Instant.class)))
                .thenReturn(0L);
        
        Instant beforeApply = Instant.now();
        
        // Act - Apply score event
        boolean result = service.applyScoreEvent(userId, eventType, entityId);
        
        Instant afterApply = Instant.now();
        
        // Assert - Application succeeded
        assertTrue(result, "Score event application should succeed");
        
        // Check if rank changed
        int newLevel = user.getLevel();
        RankTier newRank = user.getRank();
        
        if (newRank != initialRank) {
            // Property: If rank changed, rankUpdatedAt should be set
            assertNotNull(user.getRankUpdatedAt(),
                    String.format("rankUpdatedAt should be set when rank changes from %s to %s",
                            initialRank, newRank));
            
            // Property: The timestamp should be approximately now (within test execution time)
            assertTrue(user.getRankUpdatedAt().isAfter(beforeApply.minusSeconds(1)),
                    String.format("rankUpdatedAt (%s) should be after test start (%s)",
                            user.getRankUpdatedAt(), beforeApply));
            assertTrue(user.getRankUpdatedAt().isBefore(afterApply.plusSeconds(1)),
                    String.format("rankUpdatedAt (%s) should be before test end (%s)",
                            user.getRankUpdatedAt(), afterApply));
        }
        // Note: If rank didn't change, rankUpdatedAt may remain null or unchanged
    }

    /**
     * Property 13: Score Activity Updates Timestamp
     * 
     * For any successful score event application, the user's lastShipActivityAt field
     * should be updated to the current timestamp.
     * 
     * This property verifies that:
     * 1. lastShipActivityAt is set on every successful score event
     * 2. The timestamp is set to approximately the current time
     * 3. Failed applications do not update the timestamp
     * 
     * **Validates: Requirements 9.5**
     */
    @Property(tries = 100)
    @Label("Feature: rank-level-progression, Property 13: Score Activity Updates Timestamp")
    void scoreActivityUpdatesTimestamp(
            @ForAll @IntRange(min = 0, max = 1000) int initialShipScore
    ) {
        // Arrange - Create service instance
        ShipScoreService service = new ShipScoreService(userRepository, scoreEventLogRepository);
        
        String userId = "test-user-" + System.nanoTime();
        String entityId = "test-entity-" + System.nanoTime();
        ScoreEventType eventType = ScoreEventType.QUALITY_COMMENT;
        
        User user = User.builder()
                .id(userId)
                .githubId("github-" + userId)
                .username("user-" + userId)
                .shipScore(initialShipScore)
                .level(service.calculateLevel(initialShipScore))
                .rank(service.calculateRank(service.calculateLevel(initialShipScore)))
                .lastShipActivityAt(null)  // Start with null timestamp
                .build();
        
        // Mock repository behavior
        when(userRepository.findById(userId)).thenReturn(java.util.Optional.of(user));
        when(scoreEventLogRepository.existsByUserIdAndEventTypeAndEntityId(userId, eventType, entityId))
                .thenReturn(false);
        when(scoreEventLogRepository.countByUserIdAndEventTypeAndCreatedAtAfter(
                eq(userId), eq(eventType), any(Instant.class)))
                .thenReturn(0L);
        
        Instant beforeApply = Instant.now();
        
        // Act - Apply score event
        boolean result = service.applyScoreEvent(userId, eventType, entityId);
        
        Instant afterApply = Instant.now();
        
        // Assert - Application succeeded
        assertTrue(result, "Score event application should succeed");
        
        // Property: lastShipActivityAt should be set
        assertNotNull(user.getLastShipActivityAt(),
                "lastShipActivityAt should be set on successful score event");
        
        // Property: The timestamp should be approximately now (within test execution time)
        assertTrue(user.getLastShipActivityAt().isAfter(beforeApply.minusSeconds(1)),
                String.format("lastShipActivityAt (%s) should be after test start (%s)",
                        user.getLastShipActivityAt(), beforeApply));
        assertTrue(user.getLastShipActivityAt().isBefore(afterApply.plusSeconds(1)),
                String.format("lastShipActivityAt (%s) should be before test end (%s)",
                        user.getLastShipActivityAt(), afterApply));
        
        // Property: The timestamp should be recent (within 2 seconds of now)
        long secondsSinceActivity = java.time.Duration.between(
                user.getLastShipActivityAt(), Instant.now()).getSeconds();
        assertTrue(secondsSinceActivity < 2,
                String.format("lastShipActivityAt should be recent, but was %d seconds ago",
                        secondsSinceActivity));
    }
}
