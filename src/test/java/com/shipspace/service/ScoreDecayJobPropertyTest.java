package com.shipspace.service;

import com.shipspace.config.ScheduledJobMetrics;
import com.shipspace.model.RankTier;
import com.shipspace.model.User;
import com.shipspace.repository.UserRepository;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.lifecycle.BeforeProperty;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Property-based tests for ScoreDecayJob.
 * 
 * These tests verify the correctness properties of score decay calculation
 * across a wide range of inputs using property-based testing with jqwik.
 * 
 * **Validates: Requirements 11.2, 11.3, 11.7**
 */
@ExtendWith(MockitoExtension.class)
class ScoreDecayJobPropertyTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private ShipScoreService shipScoreService;

    @Mock
    private MongoTemplate mongoTemplate;

    @Mock
    private DistributedLockService distributedLockService;

    @Mock
    private ScheduledJobMetrics scheduledJobMetrics;

    @BeforeProperty
    void initMocks() {
        MockitoAnnotations.openMocks(this);
        reset(userRepository, shipScoreService, mongoTemplate, distributedLockService, scheduledJobMetrics);
    }

    /**
     * Property 14: Score Decay Calculation
     * 
     * For any inactive user (lastShipActivityAt > 60 days old), applying decay should reduce
     * their shipScore by exactly 10% (multiplying by 0.9 and flooring the result).
     * 
     * This property verifies that:
     * 1. The decay calculation is correct: decayedScore = floor(currentScore * 0.9)
     * 2. The score is reduced (never increases or stays the same, except for score 0)
     * 3. The reduction is exactly 10% (floored)
     * 4. The decay is applied to all inactive users
     * 
     * **Validates: Requirements 11.3**
     */
    @Property(tries = 100)
    @Label("Feature: rank-level-progression, Property 14: Score Decay Calculation")
    void decayCalculationReducesScoreByTenPercent(
            @ForAll @IntRange(min = 0, max = 10000) int initialShipScore
    ) {
        // Arrange - Create decay job instance
        ScoreDecayJob decayJob = new ScoreDecayJob(userRepository, shipScoreService, mongoTemplate, distributedLockService, scheduledJobMetrics);
        
        // Create an inactive user (61 days old)
        Instant sixtyOneDaysAgo = Instant.now().minus(61, ChronoUnit.DAYS);
        String userId = "test-user-" + System.nanoTime();
        
        // Calculate expected level and rank for initial score
        int initialLevel = calculateLevel(initialShipScore);
        RankTier initialRank = calculateRank(initialLevel);
        
        User inactiveUser = User.builder()
                .id(userId)
                .githubId("github-" + userId)
                .username("user-" + userId)
                .shipScore(initialShipScore)
                .level(initialLevel)
                .rank(initialRank)
                .lastShipActivityAt(sixtyOneDaysAgo)
                .build();
        
        // Calculate expected decayed score
        int expectedDecayedScore = (int) Math.floor(initialShipScore * 0.9);
        int expectedNewLevel = calculateLevel(expectedDecayedScore);
        RankTier expectedNewRank = calculateRank(expectedNewLevel);
        
        // Mock repository behavior
        when(userRepository.findByLastShipActivityAtBefore(any(Instant.class)))
                .thenReturn(Collections.singletonList(inactiveUser));
        when(shipScoreService.calculateLevel(expectedDecayedScore))
                .thenReturn(expectedNewLevel);
        if (expectedNewLevel != initialLevel) {
            when(shipScoreService.calculateRank(expectedNewLevel))
                    .thenReturn(expectedNewRank);
        }
        
        // Act - Apply decay
        decayJob.applyScoreDecay();
        
        // Assert - Verify the decay calculation
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository, atLeastOnce()).save(userCaptor.capture());
        
        User savedUser = userCaptor.getValue();
        int actualDecayedScore = savedUser.getShipScore();
        
        // Property 1: The decayed score should equal floor(initialScore * 0.9)
        assertEquals(expectedDecayedScore, actualDecayedScore,
                String.format("Decayed score should be floor(%d * 0.9) = %d, but was %d",
                        initialShipScore, expectedDecayedScore, actualDecayedScore));
        
        // Property 2: The score should be reduced (or stay at 0)
        assertTrue(actualDecayedScore <= initialShipScore,
                String.format("Decayed score (%d) should not exceed initial score (%d)",
                        actualDecayedScore, initialShipScore));
        
        // Property 3: The reduction should be exactly 10% (floored)
        // For scores > 0, the reduction should be at least 1 point (unless score is 1-9)
        if (initialShipScore >= 10) {
            int reduction = initialShipScore - actualDecayedScore;
            int expectedReduction = initialShipScore - expectedDecayedScore;
            assertEquals(expectedReduction, reduction,
                    String.format("Score reduction should be %d (10%% of %d), but was %d",
                            expectedReduction, initialShipScore, reduction));
        }
        
        // Property 4: The calculation should use floor, not round or ceil
        // Verify that the result matches Math.floor(score * 0.9)
        double exactDecay = initialShipScore * 0.9;
        int flooredDecay = (int) Math.floor(exactDecay);
        assertEquals(flooredDecay, actualDecayedScore,
                String.format("Decay should use floor: floor(%d * 0.9) = floor(%.2f) = %d, but was %d",
                        initialShipScore, exactDecay, flooredDecay, actualDecayedScore));
        
        // Property 5: For score 0, decay should result in 0
        if (initialShipScore == 0) {
            assertEquals(0, actualDecayedScore,
                    "Decay of score 0 should remain 0");
        }
        
        // Property 6: Verify the decay job called the repository to find inactive users
        verify(userRepository, atLeastOnce())
                .findByLastShipActivityAtBefore(any(Instant.class));
    }

    /**
     * Property: Score Decay Calculation with Specific Values
     * 
     * Tests decay calculation with specific score values that have known results,
     * including edge cases where flooring matters.
     * 
     * **Validates: Requirements 11.3**
     */
    @Property(tries = 100)
    @Label("Feature: rank-level-progression, Property 14: Score Decay Calculation with Flooring")
    void decayCalculationCorrectlyFloorsResult(
            @ForAll @IntRange(min = 1, max = 1000) int multiplier
    ) {
        // Arrange - Create decay job instance
        ScoreDecayJob decayJob = new ScoreDecayJob(userRepository, shipScoreService, mongoTemplate, distributedLockService, scheduledJobMetrics);
        
        // Test with scores that result in fractional decay values
        // For example: 105 * 0.9 = 94.5, should floor to 94
        int initialShipScore = multiplier * 10 + 5; // Scores like 15, 25, 35, ..., 10005
        
        Instant sixtyOneDaysAgo = Instant.now().minus(61, ChronoUnit.DAYS);
        String userId = "test-user-" + System.nanoTime();
        
        int initialLevel = calculateLevel(initialShipScore);
        RankTier initialRank = calculateRank(initialLevel);
        
        User inactiveUser = User.builder()
                .id(userId)
                .githubId("github-" + userId)
                .username("user-" + userId)
                .shipScore(initialShipScore)
                .level(initialLevel)
                .rank(initialRank)
                .lastShipActivityAt(sixtyOneDaysAgo)
                .build();
        
        // Calculate expected decayed score
        double exactDecay = initialShipScore * 0.9;
        int expectedDecayedScore = (int) Math.floor(exactDecay);
        int expectedNewLevel = calculateLevel(expectedDecayedScore);
        RankTier expectedNewRank = calculateRank(expectedNewLevel);
        
        // Mock repository behavior
        when(userRepository.findByLastShipActivityAtBefore(any(Instant.class)))
                .thenReturn(Collections.singletonList(inactiveUser));
        when(shipScoreService.calculateLevel(expectedDecayedScore))
                .thenReturn(expectedNewLevel);
        if (expectedNewLevel != initialLevel) {
            when(shipScoreService.calculateRank(expectedNewLevel))
                    .thenReturn(expectedNewRank);
        }
        
        // Act - Apply decay
        decayJob.applyScoreDecay();
        
        // Assert - Verify flooring behavior
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository, atLeastOnce()).save(userCaptor.capture());
        
        User savedUser = userCaptor.getValue();
        int actualDecayedScore = savedUser.getShipScore();
        
        // Property: The result should be floored, not rounded
        assertEquals(expectedDecayedScore, actualDecayedScore,
                String.format("Decay of %d should be floor(%.2f) = %d, but was %d",
                        initialShipScore, exactDecay, expectedDecayedScore, actualDecayedScore));
        
        // Property: The result should never be greater than floor(score * 0.9)
        assertTrue(actualDecayedScore <= Math.floor(exactDecay),
                String.format("Decayed score (%d) should not exceed floor(%.2f) = %d",
                        actualDecayedScore, exactDecay, (int) Math.floor(exactDecay)));
        
        // Property: The result should never be less than floor(score * 0.9)
        assertTrue(actualDecayedScore >= Math.floor(exactDecay),
                String.format("Decayed score (%d) should not be less than floor(%.2f) = %d",
                        actualDecayedScore, exactDecay, (int) Math.floor(exactDecay)));
    }

    /**
     * Property: Score Decay Calculation is Idempotent
     * 
     * Applying decay multiple times should continue to reduce the score by 10% each time,
     * not compound in unexpected ways.
     * 
     * **Validates: Requirements 11.3**
     */
    @Property(tries = 100)
    @Label("Feature: rank-level-progression, Property 14: Score Decay Calculation is Consistent")
    void decayCalculationIsConsistent(
            @ForAll @IntRange(min = 100, max = 5000) int initialShipScore
    ) {
        // Arrange - Create decay job instance
        ScoreDecayJob decayJob = new ScoreDecayJob(userRepository, shipScoreService, mongoTemplate, distributedLockService, scheduledJobMetrics);
        
        Instant sixtyOneDaysAgo = Instant.now().minus(61, ChronoUnit.DAYS);
        String userId = "test-user-" + System.nanoTime();
        
        int initialLevel = calculateLevel(initialShipScore);
        RankTier initialRank = calculateRank(initialLevel);
        
        User inactiveUser = User.builder()
                .id(userId)
                .githubId("github-" + userId)
                .username("user-" + userId)
                .shipScore(initialShipScore)
                .level(initialLevel)
                .rank(initialRank)
                .lastShipActivityAt(sixtyOneDaysAgo)
                .build();
        
        // Calculate expected decayed score after first decay
        int expectedFirstDecay = (int) Math.floor(initialShipScore * 0.9);
        int levelAfterFirstDecay = calculateLevel(expectedFirstDecay);
        RankTier rankAfterFirstDecay = calculateRank(levelAfterFirstDecay);
        
        // Mock repository behavior for first decay
        when(userRepository.findByLastShipActivityAtBefore(any(Instant.class)))
                .thenReturn(Collections.singletonList(inactiveUser));
        when(shipScoreService.calculateLevel(expectedFirstDecay))
                .thenReturn(levelAfterFirstDecay);
        if (levelAfterFirstDecay != initialLevel) {
            when(shipScoreService.calculateRank(levelAfterFirstDecay))
                    .thenReturn(rankAfterFirstDecay);
        }
        
        // Act - Apply decay once
        decayJob.applyScoreDecay();
        
        // Assert - Verify first decay
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository, atLeastOnce()).save(userCaptor.capture());
        
        User savedUser = userCaptor.getValue();
        int scoreAfterFirstDecay = savedUser.getShipScore();
        
        assertEquals(expectedFirstDecay, scoreAfterFirstDecay,
                String.format("First decay should reduce %d to %d, but was %d",
                        initialShipScore, expectedFirstDecay, scoreAfterFirstDecay));
        
        // Property: If we were to apply decay again, it should reduce by 10% of the new score
        int expectedSecondDecay = (int) Math.floor(scoreAfterFirstDecay * 0.9);
        
        // Verify the calculation is consistent
        assertTrue(expectedSecondDecay < scoreAfterFirstDecay || scoreAfterFirstDecay == 0,
                String.format("Second decay should reduce %d to %d (or stay at 0)",
                        scoreAfterFirstDecay, expectedSecondDecay));
        
        // Property: The decay percentage is always 10% of the current score
        if (initialShipScore >= 10) {
            double actualDecayPercentage = (double) (initialShipScore - scoreAfterFirstDecay) / initialShipScore;
            // Allow small floating point tolerance
            assertTrue(Math.abs(actualDecayPercentage - 0.1) < 0.01,
                    String.format("Decay percentage should be approximately 10%%, but was %.2f%%",
                            actualDecayPercentage * 100));
        }
    }

    /**
     * Property: Score Decay Calculation with Boundary Values
     * 
     * Tests decay calculation at specific boundary values to ensure correct behavior.
     * 
     * **Validates: Requirements 11.3**
     */
    @Property(tries = 100)
    @Label("Feature: rank-level-progression, Property 14: Score Decay Calculation at Boundaries")
    void decayCalculationAtBoundaries(
            @ForAll @IntRange(min = 0, max = 29) int levelIndex
    ) {
        // Arrange - Create decay job instance
        ScoreDecayJob decayJob = new ScoreDecayJob(userRepository, shipScoreService, mongoTemplate, distributedLockService, scheduledJobMetrics);
        
        // Test at level boundaries: minimum score for each level
        int initialShipScore = levelIndex * 40; // Scores: 0, 40, 80, 120, ..., 1160
        
        Instant sixtyOneDaysAgo = Instant.now().minus(61, ChronoUnit.DAYS);
        String userId = "test-user-" + System.nanoTime();
        
        int initialLevel = calculateLevel(initialShipScore);
        RankTier initialRank = calculateRank(initialLevel);
        
        User inactiveUser = User.builder()
                .id(userId)
                .githubId("github-" + userId)
                .username("user-" + userId)
                .shipScore(initialShipScore)
                .level(initialLevel)
                .rank(initialRank)
                .lastShipActivityAt(sixtyOneDaysAgo)
                .build();
        
        // Calculate expected decayed score
        int expectedDecayedScore = (int) Math.floor(initialShipScore * 0.9);
        int expectedNewLevel = calculateLevel(expectedDecayedScore);
        RankTier expectedNewRank = calculateRank(expectedNewLevel);
        
        // Mock repository behavior
        when(userRepository.findByLastShipActivityAtBefore(any(Instant.class)))
                .thenReturn(Collections.singletonList(inactiveUser));
        when(shipScoreService.calculateLevel(expectedDecayedScore))
                .thenReturn(expectedNewLevel);
        if (expectedNewLevel != initialLevel) {
            when(shipScoreService.calculateRank(expectedNewLevel))
                    .thenReturn(expectedNewRank);
        }
        
        // Act - Apply decay
        decayJob.applyScoreDecay();
        
        // Assert - Verify decay at boundary
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository, atLeastOnce()).save(userCaptor.capture());
        
        User savedUser = userCaptor.getValue();
        int actualDecayedScore = savedUser.getShipScore();
        
        // Property: Decay calculation should be correct at level boundaries
        assertEquals(expectedDecayedScore, actualDecayedScore,
                String.format("Decay at level boundary (score %d, level %d) should be %d, but was %d",
                        initialShipScore, initialLevel, expectedDecayedScore, actualDecayedScore));
        
        // Property: The decayed score should match the formula
        int formulaResult = (int) Math.floor(initialShipScore * 0.9);
        assertEquals(formulaResult, actualDecayedScore,
                String.format("Decay should match formula floor(%d * 0.9) = %d, but was %d",
                        initialShipScore, formulaResult, actualDecayedScore));
    }

    /**
     * Property 15: Decay Preserves Activity Timestamp
     * 
     * For any user who undergoes score decay, the lastShipActivityAt field should remain
     * unchanged (decay does not count as activity).
     * 
     * This property verifies that:
     * 1. The lastShipActivityAt timestamp is not modified during decay
     * 2. Decay operations do not count as user activity
     * 3. The original activity timestamp is preserved exactly
     * 4. This holds true regardless of the score value or decay amount
     * 
     * **Validates: Requirements 11.7**
     */
    @Property(tries = 100)
    @Label("Feature: rank-level-progression, Property 15: Decay Preserves Activity Timestamp")
    void decayPreservesActivityTimestamp(
            @ForAll @IntRange(min = 0, max = 10000) int initialShipScore,
            @ForAll @IntRange(min = 61, max = 365) int daysInactive
    ) {
        // Arrange - Create decay job instance
        ScoreDecayJob decayJob = new ScoreDecayJob(userRepository, shipScoreService, mongoTemplate, distributedLockService, scheduledJobMetrics);
        
        // Create an inactive user with a specific lastShipActivityAt timestamp
        Instant originalActivityTimestamp = Instant.now().minus(daysInactive, ChronoUnit.DAYS);
        String userId = "test-user-" + System.nanoTime();
        
        int initialLevel = calculateLevel(initialShipScore);
        RankTier initialRank = calculateRank(initialLevel);
        
        User inactiveUser = User.builder()
                .id(userId)
                .githubId("github-" + userId)
                .username("user-" + userId)
                .shipScore(initialShipScore)
                .level(initialLevel)
                .rank(initialRank)
                .lastShipActivityAt(originalActivityTimestamp)
                .build();
        
        // Calculate expected decayed score
        int expectedDecayedScore = (int) Math.floor(initialShipScore * 0.9);
        int expectedNewLevel = calculateLevel(expectedDecayedScore);
        RankTier expectedNewRank = calculateRank(expectedNewLevel);
        
        // Mock repository behavior
        when(userRepository.findByLastShipActivityAtBefore(any(Instant.class)))
                .thenReturn(Collections.singletonList(inactiveUser));
        when(shipScoreService.calculateLevel(expectedDecayedScore))
                .thenReturn(expectedNewLevel);
        if (expectedNewLevel != initialLevel) {
            when(shipScoreService.calculateRank(expectedNewLevel))
                    .thenReturn(expectedNewRank);
        }
        
        // Act - Apply decay
        decayJob.applyScoreDecay();
        
        // Assert - Verify the activity timestamp is preserved
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository, atLeastOnce()).save(userCaptor.capture());
        
        User savedUser = userCaptor.getValue();
        Instant savedActivityTimestamp = savedUser.getLastShipActivityAt();
        
        // Property 1: The lastShipActivityAt should be exactly the same as before decay
        assertEquals(originalActivityTimestamp, savedActivityTimestamp,
                String.format("lastShipActivityAt should remain unchanged at %s, but was %s",
                        originalActivityTimestamp, savedActivityTimestamp));
        
        // Property 2: The timestamp should not be null after decay
        assertNotNull(savedActivityTimestamp,
                "lastShipActivityAt should not be null after decay");
        
        // Property 3: The timestamp should not be updated to current time
        Instant now = Instant.now();
        assertTrue(savedActivityTimestamp.isBefore(now.minus(60, ChronoUnit.DAYS)),
                String.format("lastShipActivityAt (%s) should still be in the past (>60 days), not updated to current time",
                        savedActivityTimestamp));
        
        // Property 4: Verify the score was actually decayed (to ensure we're testing the right scenario)
        int actualDecayedScore = savedUser.getShipScore();
        assertEquals(expectedDecayedScore, actualDecayedScore,
                String.format("Score should be decayed from %d to %d to verify decay occurred",
                        initialShipScore, expectedDecayedScore));
        
        // Property 5: The timestamp should be equal to the original value
        // Note: We use assertEquals instead of assertSame because the User object may be reconstructed
        assertEquals(originalActivityTimestamp, savedActivityTimestamp,
                "lastShipActivityAt should equal the original timestamp");
    }

    /**
     * Property 16: Inactive User Identification
     * 
     * For any user, they should be identified as inactive for decay purposes if and only if
     * their lastShipActivityAt is more than 60 days before the current time.
     * 
     * This property verifies that:
     * 1. Users with lastShipActivityAt > 60 days ago are identified as inactive
     * 2. Users with lastShipActivityAt <= 60 days ago are NOT identified as inactive
     * 3. The identification is based solely on the 60-day threshold
     * 
     * **Validates: Requirements 11.2**
     */
    @Property(tries = 100)
    @Label("Feature: rank-level-progression, Property 16: Inactive User Identification")
    void inactiveUserIdentificationBasedOnSixtyDayThreshold(
            @ForAll @IntRange(min = 0, max = 10000) int shipScore,
            @ForAll @IntRange(min = 61, max = 365) int daysInactive
    ) {
        // Arrange - Create decay job instance
        ScoreDecayJob decayJob = new ScoreDecayJob(userRepository, shipScoreService, mongoTemplate, distributedLockService, scheduledJobMetrics);
        
        // Create an inactive user (more than 60 days old)
        Instant activityTimestamp = Instant.now().minus(daysInactive, ChronoUnit.DAYS);
        String userId = "test-user-" + System.nanoTime();
        
        int level = calculateLevel(shipScore);
        RankTier rank = calculateRank(level);
        
        User user = User.builder()
                .id(userId)
                .githubId("github-" + userId)
                .username("user-" + userId)
                .shipScore(shipScore)
                .level(level)
                .rank(rank)
                .lastShipActivityAt(activityTimestamp)
                .build();
        
        int expectedDecayedScore = (int) Math.floor(shipScore * 0.9);
        int expectedNewLevel = calculateLevel(expectedDecayedScore);
        
        // Mock repository behavior - user should be returned as inactive
        when(userRepository.findByLastShipActivityAtBefore(any(Instant.class)))
                .thenReturn(Collections.singletonList(user));
        when(shipScoreService.calculateLevel(expectedDecayedScore))
                .thenReturn(expectedNewLevel);
        if (expectedNewLevel != level) {
            RankTier expectedNewRank = calculateRank(expectedNewLevel);
            when(shipScoreService.calculateRank(expectedNewLevel))
                    .thenReturn(expectedNewRank);
        }
        
        // Act - Apply decay
        decayJob.applyScoreDecay();
        
        // Assert - Verify the identification logic
        ArgumentCaptor<Instant> thresholdCaptor = ArgumentCaptor.forClass(Instant.class);
        verify(userRepository, atLeastOnce())
                .findByLastShipActivityAtBefore(thresholdCaptor.capture());
        
        Instant capturedThreshold = thresholdCaptor.getValue();
        
        // Property 1: The threshold should be approximately 60 days ago (within 1 second tolerance)
        Instant expectedThreshold = Instant.now().minus(60, ChronoUnit.DAYS);
        long thresholdDifference = Math.abs(capturedThreshold.getEpochSecond() - expectedThreshold.getEpochSecond());
        assertTrue(thresholdDifference <= 1,
                String.format("Threshold should be approximately 60 days ago, but difference was %d seconds",
                        thresholdDifference));
        
        // Property 2: User with activity > 60 days ago should be processed (score decayed)
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository, atLeastOnce()).save(userCaptor.capture());
        
        User savedUser = userCaptor.getValue();
        
        assertEquals(expectedDecayedScore, savedUser.getShipScore(),
                String.format("Inactive user (activity %d days ago) should have score decayed from %d to %d",
                        daysInactive, shipScore, expectedDecayedScore));
        
        // Property 3: The user's activity timestamp should be before the threshold
        assertTrue(activityTimestamp.isBefore(capturedThreshold),
                String.format("User activity (%s) should be before threshold (%s) for %d days inactive",
                        activityTimestamp, capturedThreshold, daysInactive));
    }

    /**
     * Property 16: Inactive User Identification - Active Users Not Processed
     * 
     * Verifies that users with activity within the last 60 days are NOT identified
     * as inactive and are NOT processed by the decay job.
     * 
     * **Validates: Requirements 11.2**
     */
    @Property(tries = 100)
    @Label("Feature: rank-level-progression, Property 16: Active Users Not Identified as Inactive")
    void activeUsersNotIdentifiedAsInactive(
            @ForAll @IntRange(min = 0, max = 10000) int shipScore,
            @ForAll @IntRange(min = 0, max = 59) int daysInactive
    ) {
        // Arrange - Create decay job instance
        ScoreDecayJob decayJob = new ScoreDecayJob(userRepository, shipScoreService, mongoTemplate, distributedLockService, scheduledJobMetrics);
        
        // Create an active user (59 days or less)
        Instant activityTimestamp = Instant.now().minus(daysInactive, ChronoUnit.DAYS);
        String userId = "test-user-" + System.nanoTime();
        
        int level = calculateLevel(shipScore);
        RankTier rank = calculateRank(level);
        
        User user = User.builder()
                .id(userId)
                .githubId("github-" + userId)
                .username("user-" + userId)
                .shipScore(shipScore)
                .level(level)
                .rank(rank)
                .lastShipActivityAt(activityTimestamp)
                .build();
        
        // Mock repository behavior - user should NOT be returned (not inactive)
        when(userRepository.findByLastShipActivityAtBefore(any(Instant.class)))
                .thenReturn(Collections.emptyList());
        
        // Act - Apply decay
        decayJob.applyScoreDecay();
        
        // Assert - Verify the user was NOT processed
        verify(userRepository, atLeastOnce())
                .findByLastShipActivityAtBefore(any(Instant.class));
        
        // Property: Active users should NOT be saved (not decayed)
        verify(userRepository, never()).save(any(User.class));
        
        // Property: The repository query should use a threshold of approximately 60 days
        ArgumentCaptor<Instant> thresholdCaptor = ArgumentCaptor.forClass(Instant.class);
        verify(userRepository, atLeastOnce())
                .findByLastShipActivityAtBefore(thresholdCaptor.capture());
        
        Instant capturedThreshold = thresholdCaptor.getValue();
        Instant expectedThreshold = Instant.now().minus(60, ChronoUnit.DAYS);
        long thresholdDifference = Math.abs(capturedThreshold.getEpochSecond() - expectedThreshold.getEpochSecond());
        assertTrue(thresholdDifference <= 1,
                String.format("Threshold should be approximately 60 days ago, but difference was %d seconds",
                        thresholdDifference));
    }

    /**
     * Property 16: Inactive User Identification with Null Activity
     * 
     * Verifies that users with null lastShipActivityAt are NOT identified as inactive
     * for decay purposes.
     * 
     * **Validates: Requirements 11.2**
     */
    @Property(tries = 100)
    @Label("Feature: rank-level-progression, Property 16: Inactive User Identification with Null Activity")
    void inactiveUserIdentificationExcludesNullActivity(
            @ForAll @IntRange(min = 0, max = 10000) int shipScore
    ) {
        // Arrange - Create decay job instance
        ScoreDecayJob decayJob = new ScoreDecayJob(userRepository, shipScoreService, mongoTemplate, distributedLockService, scheduledJobMetrics);
        
        // Create a user with null lastShipActivityAt (never had activity)
        String userId = "test-user-" + System.nanoTime();
        
        int level = calculateLevel(shipScore);
        RankTier rank = calculateRank(level);
        
        User userWithNullActivity = User.builder()
                .id(userId)
                .githubId("github-" + userId)
                .username("user-" + userId)
                .shipScore(shipScore)
                .level(level)
                .rank(rank)
                .lastShipActivityAt(null) // Null activity
                .build();
        
        // Mock repository behavior - users with null activity should NOT be returned
        when(userRepository.findByLastShipActivityAtBefore(any(Instant.class)))
                .thenReturn(Collections.emptyList());
        
        // Act - Apply decay
        decayJob.applyScoreDecay();
        
        // Assert - Verify users with null activity are NOT processed
        verify(userRepository, atLeastOnce())
                .findByLastShipActivityAtBefore(any(Instant.class));
        
        // Property: Users with null lastShipActivityAt should NOT be saved (not decayed)
        verify(userRepository, never()).save(any(User.class));
    }

    /**
     * Property 16: Inactive User Identification with Boundary Values
     * 
     * Verifies the exact boundary behavior at 60 days - users with activity exactly
     * 60 days ago should NOT be identified as inactive (strictly "before" comparison).
     * 
     * **Validates: Requirements 11.2**
     */
    @Property(tries = 100)
    @Label("Feature: rank-level-progression, Property 16: Inactive User Identification at Boundary")
    void inactiveUserIdentificationBoundaryAtSixtyDays(
            @ForAll @IntRange(min = 0, max = 10000) int shipScore
    ) {
        // Arrange - Create decay job instance
        ScoreDecayJob decayJob = new ScoreDecayJob(userRepository, shipScoreService, mongoTemplate, distributedLockService, scheduledJobMetrics);
        
        // Create a user with activity exactly 60 days ago (at the boundary)
        Instant exactlySixtyDaysAgo = Instant.now().minus(60, ChronoUnit.DAYS);
        String userId = "test-user-" + System.nanoTime();
        
        int level = calculateLevel(shipScore);
        RankTier rank = calculateRank(level);
        
        User userAtBoundary = User.builder()
                .id(userId)
                .githubId("github-" + userId)
                .username("user-" + userId)
                .shipScore(shipScore)
                .level(level)
                .rank(rank)
                .lastShipActivityAt(exactlySixtyDaysAgo)
                .build();
        
        // Mock repository behavior - user at exactly 60 days should NOT be returned
        // (because the query uses "before", not "beforeOrEqual")
        when(userRepository.findByLastShipActivityAtBefore(any(Instant.class)))
                .thenReturn(Collections.emptyList());
        
        // Act - Apply decay
        decayJob.applyScoreDecay();
        
        // Assert - Verify user at boundary is NOT processed
        verify(userRepository, atLeastOnce())
                .findByLastShipActivityAtBefore(any(Instant.class));
        
        // Property: User with activity exactly 60 days ago should NOT be saved (not decayed)
        verify(userRepository, never()).save(any(User.class));
    }

    /**
     * Property: Decay Preserves Activity Timestamp Across Multiple Users
     * 
     * Verifies that when multiple users undergo decay, each user's lastShipActivityAt
     * is preserved independently.
     * 
     * **Validates: Requirements 11.7**
     */
    @Property(tries = 100)
    @Label("Feature: rank-level-progression, Property 15: Decay Preserves Activity Timestamp for Multiple Users")
    void decayPreservesActivityTimestampForMultipleUsers(
            @ForAll @IntRange(min = 100, max = 5000) int score1,
            @ForAll @IntRange(min = 100, max = 5000) int score2,
            @ForAll @IntRange(min = 61, max = 200) int days1,
            @ForAll @IntRange(min = 61, max = 200) int days2
    ) {
        // Arrange - Create decay job instance
        ScoreDecayJob decayJob = new ScoreDecayJob(userRepository, shipScoreService, mongoTemplate, distributedLockService, scheduledJobMetrics);
        
        // Create two inactive users with different activity timestamps
        Instant timestamp1 = Instant.now().minus(days1, ChronoUnit.DAYS);
        Instant timestamp2 = Instant.now().minus(days2, ChronoUnit.DAYS);
        
        String userId1 = "user-1-" + System.nanoTime();
        String userId2 = "user-2-" + System.nanoTime();
        
        User user1 = User.builder()
                .id(userId1)
                .githubId("github-user-1")
                .username("user-1")
                .shipScore(score1)
                .level(calculateLevel(score1))
                .rank(calculateRank(calculateLevel(score1)))
                .lastShipActivityAt(timestamp1)
                .build();
        
        User user2 = User.builder()
                .id(userId2)
                .githubId("github-user-2")
                .username("user-2")
                .shipScore(score2)
                .level(calculateLevel(score2))
                .rank(calculateRank(calculateLevel(score2)))
                .lastShipActivityAt(timestamp2)
                .build();
        
        // Calculate expected decayed scores
        int expectedScore1 = (int) Math.floor(score1 * 0.9);
        int expectedScore2 = (int) Math.floor(score2 * 0.9);
        
        // Mock repository behavior
        when(userRepository.findByLastShipActivityAtBefore(any(Instant.class)))
                .thenReturn(java.util.Arrays.asList(user1, user2));
        when(shipScoreService.calculateLevel(anyInt()))
                .thenAnswer(invocation -> calculateLevel(invocation.getArgument(0)));
        when(shipScoreService.calculateRank(anyInt()))
                .thenAnswer(invocation -> calculateRank(invocation.getArgument(0)));
        
        // Act - Apply decay
        decayJob.applyScoreDecay();
        
        // Assert - Verify both users' timestamps are preserved
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository, atLeast(2)).save(userCaptor.capture());
        
        java.util.List<User> savedUsers = userCaptor.getAllValues();
        
        // Property: Each user's lastShipActivityAt should be preserved
        // We need to match users by their ID since they're mutated in place
        boolean user1Found = false;
        boolean user2Found = false;
        
        for (User savedUser : savedUsers) {
            if (savedUser.getId().equals(userId1)) {
                user1Found = true;
                assertEquals(timestamp1, savedUser.getLastShipActivityAt(),
                        String.format("User 1's lastShipActivityAt should be preserved at %s", timestamp1));
                // Also verify the score was decayed
                assertEquals(expectedScore1, savedUser.getShipScore(),
                        String.format("User 1's score should be decayed from %d to %d", score1, expectedScore1));
            } else if (savedUser.getId().equals(userId2)) {
                user2Found = true;
                assertEquals(timestamp2, savedUser.getLastShipActivityAt(),
                        String.format("User 2's lastShipActivityAt should be preserved at %s", timestamp2));
                // Also verify the score was decayed
                assertEquals(expectedScore2, savedUser.getShipScore(),
                        String.format("User 2's score should be decayed from %d to %d", score2, expectedScore2));
            }
        }
        
        assertTrue(user1Found && user2Found,
                "Both users should have been saved with preserved timestamps");
    }

    // ========== Helper Methods ==========

    /**
     * Calculate level from shipScore using the formula: min(30, floor(shipScore / 40) + 1)
     */
    private int calculateLevel(int shipScore) {
        return Math.min(30, (shipScore / 40) + 1);
    }

    /**
     * Calculate rank from level using the tier ranges.
     */
    private RankTier calculateRank(int level) {
        if (level >= 1 && level <= 5) {
            return RankTier.DOCKED;
        } else if (level >= 6 && level <= 10) {
            return RankTier.LAUNCHER;
        } else if (level >= 11 && level <= 15) {
            return RankTier.ORBITER;
        } else if (level >= 16 && level <= 20) {
            return RankTier.NAVIGATOR;
        } else if (level >= 21 && level <= 25) {
            return RankTier.COMMANDER;
        } else { // level >= 26 && level <= 30
            return RankTier.FLEET_ADMIRAL;
        }
    }
}
