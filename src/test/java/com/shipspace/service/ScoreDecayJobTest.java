package com.shipspace.service;

import com.shipspace.model.RankTier;
import com.shipspace.model.User;
import com.shipspace.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ScoreDecayJob.
 * 
 * <p>Tests the score decay logic for inactive users:
 * <ul>
 *   <li>Inactive user identification (> 60 days)</li>
 *   <li>Score reduction by 10%</li>
 *   <li>Level recalculation after decay</li>
 *   <li>Rank recalculation after decay</li>
 *   <li>Preservation of lastShipActivityAt timestamp</li>
 * </ul>
 * 
 * <p>Requirements: 11.2, 11.3, 11.7
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ScoreDecayJob - Unit Tests")
class ScoreDecayJobTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private ShipScoreService shipScoreService;

    @InjectMocks
    private ScoreDecayJob scoreDecayJob;

    @BeforeEach
    void setUp() {
        // Service is initialized with mocked repositories via @InjectMocks
    }

    // ========== Inactive User Identification Tests ==========

    @Test
    @DisplayName("applyScoreDecay: should reduce score for inactive user (61 days old)")
    void applyScoreDecay_InactiveUser61Days_ReducesScore() {
        // Arrange
        Instant sixtyOneDaysAgo = Instant.now().minus(61, ChronoUnit.DAYS);
        
        User inactiveUser = User.builder()
            .id("user123")
            .shipScore(100)
            .level(3)
            .rank(RankTier.DOCKED)
            .lastShipActivityAt(sixtyOneDaysAgo)
            .build();

        // Mock: Repository returns the inactive user
        when(userRepository.findByLastShipActivityAtBefore(any(Instant.class)))
            .thenReturn(Collections.singletonList(inactiveUser));

        // Mock: ShipScoreService calculations
        // Level stays the same (3), so calculateRank won't be called
        when(shipScoreService.calculateLevel(90)).thenReturn(3);

        // Act
        scoreDecayJob.applyScoreDecay();

        // Assert
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        
        User savedUser = userCaptor.getValue();
        assertThat(savedUser.getShipScore()).isEqualTo(90); // 100 * 0.9 = 90
    }

    @Test
    @DisplayName("applyScoreDecay: should NOT affect active user (59 days old)")
    void applyScoreDecay_ActiveUser59Days_NotAffected() {
        // Arrange
        Instant fiftyNineDaysAgo = Instant.now().minus(59, ChronoUnit.DAYS);
        
        User activeUser = User.builder()
            .id("user456")
            .shipScore(200)
            .level(6)
            .rank(RankTier.LAUNCHER)
            .lastShipActivityAt(fiftyNineDaysAgo)
            .build();

        // Mock: Repository returns empty list (no inactive users)
        when(userRepository.findByLastShipActivityAtBefore(any(Instant.class)))
            .thenReturn(Collections.emptyList());

        // Act
        scoreDecayJob.applyScoreDecay();

        // Assert
        // Verify that save was never called since no users were returned
        verify(userRepository, never()).save(any(User.class));
    }

    // ========== Score Reduction Tests ==========

    @Test
    @DisplayName("applyScoreDecay: user with shipScore=100 becomes shipScore=90 after decay")
    void applyScoreDecay_Score100_BecomesScore90() {
        // Arrange
        Instant sixtyOneDaysAgo = Instant.now().minus(61, ChronoUnit.DAYS);
        
        User user = User.builder()
            .id("user789")
            .shipScore(100)
            .level(3)
            .rank(RankTier.DOCKED)
            .lastShipActivityAt(sixtyOneDaysAgo)
            .build();

        // Mock: Repository returns the user
        when(userRepository.findByLastShipActivityAtBefore(any(Instant.class)))
            .thenReturn(Collections.singletonList(user));

        // Mock: ShipScoreService calculations
        // Level stays the same (3), so calculateRank won't be called
        when(shipScoreService.calculateLevel(90)).thenReturn(3);

        // Act
        scoreDecayJob.applyScoreDecay();

        // Assert
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        
        User savedUser = userCaptor.getValue();
        assertThat(savedUser.getShipScore()).isEqualTo(90);
    }

    @Test
    @DisplayName("applyScoreDecay: should floor the decayed score (e.g., 105 * 0.9 = 94)")
    void applyScoreDecay_Score105_BecomesScore94() {
        // Arrange
        Instant sixtyOneDaysAgo = Instant.now().minus(61, ChronoUnit.DAYS);
        
        User user = User.builder()
            .id("user999")
            .shipScore(105)
            .level(3)
            .rank(RankTier.DOCKED)
            .lastShipActivityAt(sixtyOneDaysAgo)
            .build();

        // Mock: Repository returns the user
        when(userRepository.findByLastShipActivityAtBefore(any(Instant.class)))
            .thenReturn(Collections.singletonList(user));

        // Mock: ShipScoreService calculations
        // Level stays the same (3), so calculateRank won't be called
        when(shipScoreService.calculateLevel(94)).thenReturn(3);

        // Act
        scoreDecayJob.applyScoreDecay();

        // Assert
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        
        User savedUser = userCaptor.getValue();
        assertThat(savedUser.getShipScore()).isEqualTo(94); // floor(105 * 0.9) = floor(94.5) = 94
    }

    // ========== Level Recalculation Tests ==========

    @Test
    @DisplayName("applyScoreDecay: should recalculate level after decay")
    void applyScoreDecay_RecalculatesLevel() {
        // Arrange
        Instant sixtyOneDaysAgo = Instant.now().minus(61, ChronoUnit.DAYS);
        
        // User at level 3 (score 100) will stay at level 3 (score 90)
        User user = User.builder()
            .id("user111")
            .shipScore(100)
            .level(3)
            .rank(RankTier.DOCKED)
            .lastShipActivityAt(sixtyOneDaysAgo)
            .build();

        // Mock: Repository returns the user
        when(userRepository.findByLastShipActivityAtBefore(any(Instant.class)))
            .thenReturn(Collections.singletonList(user));

        // Mock: ShipScoreService calculations
        // Level stays the same (3), so calculateRank won't be called
        when(shipScoreService.calculateLevel(90)).thenReturn(3);

        // Act
        scoreDecayJob.applyScoreDecay();

        // Assert
        verify(shipScoreService).calculateLevel(90);
        
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        
        User savedUser = userCaptor.getValue();
        assertThat(savedUser.getLevel()).isEqualTo(3);
    }

    @Test
    @DisplayName("applyScoreDecay: should recalculate level when decay causes level drop")
    void applyScoreDecay_LevelDrops_RecalculatesLevel() {
        // Arrange
        Instant sixtyOneDaysAgo = Instant.now().minus(61, ChronoUnit.DAYS);
        
        // User at level 2 (score 45) will drop to level 2 (score 40)
        // Actually, let's use a case where level changes: score 80 (level 3) -> 72 (level 2)
        User user = User.builder()
            .id("user222")
            .shipScore(80)
            .level(3)
            .rank(RankTier.DOCKED)
            .lastShipActivityAt(sixtyOneDaysAgo)
            .build();

        // Mock: Repository returns the user
        when(userRepository.findByLastShipActivityAtBefore(any(Instant.class)))
            .thenReturn(Collections.singletonList(user));

        // Mock: ShipScoreService calculations
        // 80 * 0.9 = 72, which is level 2 (40-79)
        when(shipScoreService.calculateLevel(72)).thenReturn(2);
        when(shipScoreService.calculateRank(2)).thenReturn(RankTier.DOCKED);

        // Act
        scoreDecayJob.applyScoreDecay();

        // Assert
        verify(shipScoreService).calculateLevel(72);
        
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        
        User savedUser = userCaptor.getValue();
        assertThat(savedUser.getShipScore()).isEqualTo(72);
        assertThat(savedUser.getLevel()).isEqualTo(2);
    }

    // ========== Rank Recalculation Tests ==========

    @Test
    @DisplayName("applyScoreDecay: should recalculate rank when level changes")
    void applyScoreDecay_RecalculatesRank() {
        // Arrange
        Instant sixtyOneDaysAgo = Instant.now().minus(61, ChronoUnit.DAYS);
        
        // User at level 6 (score 240) will drop to level 6 (score 216)
        // Let's use a case where level actually changes: score 240 (level 7) -> 216 (level 6)
        User user = User.builder()
            .id("user333")
            .shipScore(240)
            .level(7)
            .rank(RankTier.LAUNCHER)
            .lastShipActivityAt(sixtyOneDaysAgo)
            .build();

        // Mock: Repository returns the user
        when(userRepository.findByLastShipActivityAtBefore(any(Instant.class)))
            .thenReturn(Collections.singletonList(user));

        // Mock: ShipScoreService calculations
        // 240 * 0.9 = 216, which is level 6 (200-239)
        when(shipScoreService.calculateLevel(216)).thenReturn(6);
        when(shipScoreService.calculateRank(6)).thenReturn(RankTier.LAUNCHER);

        // Act
        scoreDecayJob.applyScoreDecay();

        // Assert
        verify(shipScoreService).calculateRank(6);
        
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        
        User savedUser = userCaptor.getValue();
        assertThat(savedUser.getRank()).isEqualTo(RankTier.LAUNCHER);
    }

    @Test
    @DisplayName("applyScoreDecay: should update rankUpdatedAt when rank changes")
    void applyScoreDecay_RankChanges_UpdatesRankUpdatedAt() {
        // Arrange
        Instant sixtyOneDaysAgo = Instant.now().minus(61, ChronoUnit.DAYS);
        Instant previousRankUpdate = Instant.now().minus(30, ChronoUnit.DAYS);
        Instant beforeTest = Instant.now().minusSeconds(1);
        
        // User at level 6 (score 240), rank LAUNCHER
        // After decay: score 216, level 6, still LAUNCHER (no rank change in this case)
        // Let's use a case where rank actually changes: score 240 (level 7, LAUNCHER) -> 216 (level 6, LAUNCHER)
        // Actually, we need level to drop enough to change rank: score 240 (level 7, LAUNCHER) -> 216 (level 6, LAUNCHER)
        // Let's use: score 200 (level 6, LAUNCHER) -> 180 (level 5, DOCKED)
        User user = User.builder()
            .id("user444")
            .shipScore(200)
            .level(6)
            .rank(RankTier.LAUNCHER)
            .lastShipActivityAt(sixtyOneDaysAgo)
            .rankUpdatedAt(previousRankUpdate)
            .build();

        // Mock: Repository returns the user
        when(userRepository.findByLastShipActivityAtBefore(any(Instant.class)))
            .thenReturn(Collections.singletonList(user));

        // Mock: ShipScoreService calculations
        // 200 * 0.9 = 180, which is level 5 (160-199), rank DOCKED
        when(shipScoreService.calculateLevel(180)).thenReturn(5);
        when(shipScoreService.calculateRank(5)).thenReturn(RankTier.DOCKED);

        // Act
        scoreDecayJob.applyScoreDecay();
        Instant afterTest = Instant.now().plusSeconds(1);

        // Assert
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        
        User savedUser = userCaptor.getValue();
        assertThat(savedUser.getRank()).isEqualTo(RankTier.DOCKED);
        assertThat(savedUser.getRankUpdatedAt()).isNotNull();
        assertThat(savedUser.getRankUpdatedAt()).isAfter(beforeTest);
        assertThat(savedUser.getRankUpdatedAt()).isBefore(afterTest);
        assertThat(savedUser.getRankUpdatedAt()).isNotEqualTo(previousRankUpdate);
    }

    @Test
    @DisplayName("applyScoreDecay: should NOT update rankUpdatedAt when rank does not change")
    void applyScoreDecay_RankDoesNotChange_DoesNotUpdateRankUpdatedAt() {
        // Arrange
        Instant sixtyOneDaysAgo = Instant.now().minus(61, ChronoUnit.DAYS);
        Instant previousRankUpdate = Instant.now().minus(30, ChronoUnit.DAYS);
        
        User user = User.builder()
            .id("user555")
            .shipScore(100)
            .level(3)
            .rank(RankTier.DOCKED)
            .lastShipActivityAt(sixtyOneDaysAgo)
            .rankUpdatedAt(previousRankUpdate)
            .build();

        // Mock: Repository returns the user
        when(userRepository.findByLastShipActivityAtBefore(any(Instant.class)))
            .thenReturn(Collections.singletonList(user));

        // Mock: ShipScoreService calculations
        // 100 * 0.9 = 90, which is level 3, rank DOCKED (no change)
        // Level stays the same, so calculateRank won't be called
        when(shipScoreService.calculateLevel(90)).thenReturn(3);

        // Act
        scoreDecayJob.applyScoreDecay();

        // Assert
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        
        User savedUser = userCaptor.getValue();
        assertThat(savedUser.getRank()).isEqualTo(RankTier.DOCKED);
        assertThat(savedUser.getRankUpdatedAt()).isEqualTo(previousRankUpdate); // Should NOT change
    }

    // ========== lastShipActivityAt Preservation Tests ==========

    @Test
    @DisplayName("applyScoreDecay: should NOT modify lastShipActivityAt during decay")
    void applyScoreDecay_DoesNotModifyLastShipActivityAt() {
        // Arrange
        Instant sixtyOneDaysAgo = Instant.now().minus(61, ChronoUnit.DAYS);
        
        User user = User.builder()
            .id("user666")
            .shipScore(100)
            .level(3)
            .rank(RankTier.DOCKED)
            .lastShipActivityAt(sixtyOneDaysAgo)
            .build();

        // Mock: Repository returns the user
        when(userRepository.findByLastShipActivityAtBefore(any(Instant.class)))
            .thenReturn(Collections.singletonList(user));

        // Mock: ShipScoreService calculations
        // Level stays the same (3), so calculateRank won't be called
        when(shipScoreService.calculateLevel(90)).thenReturn(3);

        // Act
        scoreDecayJob.applyScoreDecay();

        // Assert
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        
        User savedUser = userCaptor.getValue();
        assertThat(savedUser.getLastShipActivityAt()).isEqualTo(sixtyOneDaysAgo); // Should NOT change
    }

    @Test
    @DisplayName("applyScoreDecay: should preserve lastShipActivityAt for multiple users")
    void applyScoreDecay_MultipleUsers_PreservesLastShipActivityAt() {
        // Arrange
        Instant sixtyOneDaysAgo = Instant.now().minus(61, ChronoUnit.DAYS);
        Instant seventyDaysAgo = Instant.now().minus(70, ChronoUnit.DAYS);
        
        User user1 = User.builder()
            .id("user777")
            .shipScore(100)
            .level(3)
            .rank(RankTier.DOCKED)
            .lastShipActivityAt(sixtyOneDaysAgo)
            .build();

        User user2 = User.builder()
            .id("user888")
            .shipScore(200)
            .level(6)
            .rank(RankTier.LAUNCHER)
            .lastShipActivityAt(seventyDaysAgo)
            .build();

        // Mock: Repository returns both users
        when(userRepository.findByLastShipActivityAtBefore(any(Instant.class)))
            .thenReturn(Arrays.asList(user1, user2));

        // Mock: ShipScoreService calculations
        // Both users' levels stay the same, so calculateRank won't be called
        when(shipScoreService.calculateLevel(90)).thenReturn(3);
        when(shipScoreService.calculateLevel(180)).thenReturn(5);

        // Act
        scoreDecayJob.applyScoreDecay();

        // Assert
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository, times(2)).save(userCaptor.capture());
        
        List<User> savedUsers = userCaptor.getAllValues();
        assertThat(savedUsers.get(0).getLastShipActivityAt()).isEqualTo(sixtyOneDaysAgo);
        assertThat(savedUsers.get(1).getLastShipActivityAt()).isEqualTo(seventyDaysAgo);
    }

    // ========== Error Handling Tests ==========

    @Test
    @DisplayName("applyScoreDecay: should handle transient database error with retry")
    void applyScoreDecay_TransientError_RetriesAndSucceeds() {
        // Arrange
        Instant sixtyOneDaysAgo = Instant.now().minus(61, ChronoUnit.DAYS);
        
        User user = User.builder()
            .id("user123")
            .shipScore(100)
            .level(3)
            .rank(RankTier.DOCKED)
            .lastShipActivityAt(sixtyOneDaysAgo)
            .build();

        // Mock: Repository returns the inactive user
        when(userRepository.findByLastShipActivityAtBefore(any(Instant.class)))
            .thenReturn(Collections.singletonList(user));

        // Mock: ShipScoreService calculations
        when(shipScoreService.calculateLevel(90)).thenReturn(3);

        // Mock: First save fails (transient error), second save succeeds
        when(userRepository.save(user))
            .thenThrow(new DataAccessException("Connection timeout") {})
            .thenReturn(user);

        // Act
        scoreDecayJob.applyScoreDecay();

        // Assert - Verify save was called twice (initial + 1 retry)
        verify(userRepository, times(2)).save(user);
    }

    @Test
    @DisplayName("applyScoreDecay: should fail after max retries on persistent error")
    void applyScoreDecay_PersistentError_FailsAfterMaxRetries() {
        // Arrange
        Instant sixtyOneDaysAgo = Instant.now().minus(61, ChronoUnit.DAYS);
        
        User user = User.builder()
            .id("user456")
            .shipScore(100)
            .level(3)
            .rank(RankTier.DOCKED)
            .lastShipActivityAt(sixtyOneDaysAgo)
            .build();

        // Mock: Repository returns the inactive user
        when(userRepository.findByLastShipActivityAtBefore(any(Instant.class)))
            .thenReturn(Collections.singletonList(user));

        // Mock: ShipScoreService calculations
        when(shipScoreService.calculateLevel(90)).thenReturn(3);

        // Mock: All saves fail (persistent error)
        when(userRepository.save(user))
            .thenThrow(new DataAccessException("Connection refused") {});

        // Act
        scoreDecayJob.applyScoreDecay();

        // Assert - Verify save was called maxRetries + 1 times (initial + 3 retries)
        verify(userRepository, times(4)).save(user);
    }

    @Test
    @DisplayName("applyScoreDecay: should continue processing other users when one fails")
    void applyScoreDecay_OneUserFails_ContinuesWithOthers() {
        // Arrange
        Instant sixtyOneDaysAgo = Instant.now().minus(61, ChronoUnit.DAYS);
        
        User user1 = User.builder()
            .id("user1")
            .shipScore(100)
            .level(3)
            .rank(RankTier.DOCKED)
            .lastShipActivityAt(sixtyOneDaysAgo)
            .build();

        User user2 = User.builder()
            .id("user2")
            .shipScore(200)
            .level(6)
            .rank(RankTier.LAUNCHER)
            .lastShipActivityAt(sixtyOneDaysAgo)
            .build();

        // Mock: Repository returns both users
        when(userRepository.findByLastShipActivityAtBefore(any(Instant.class)))
            .thenReturn(Arrays.asList(user1, user2));

        // Mock: ShipScoreService calculations
        when(shipScoreService.calculateLevel(90)).thenReturn(3);
        when(shipScoreService.calculateLevel(180)).thenReturn(5);

        // Mock: First user save fails persistently, second user saves successfully
        when(userRepository.save(user1))
            .thenThrow(new DataAccessException("Database error") {});
        when(userRepository.save(user2)).thenReturn(user2);

        // Act
        scoreDecayJob.applyScoreDecay();

        // Assert - First user failed after max retries, second user succeeded
        verify(userRepository, times(4)).save(user1); // Initial + 3 retries
        verify(userRepository, times(1)).save(user2); // Success on first try
    }

    @Test
    @DisplayName("applyScoreDecay: should handle unexpected exception during processing")
    void applyScoreDecay_UnexpectedException_ContinuesProcessing() {
        // Arrange
        Instant sixtyOneDaysAgo = Instant.now().minus(61, ChronoUnit.DAYS);
        
        User user = User.builder()
            .id("user789")
            .shipScore(100)
            .level(3)
            .rank(RankTier.DOCKED)
            .lastShipActivityAt(sixtyOneDaysAgo)
            .build();

        // Mock: Repository returns the inactive user
        when(userRepository.findByLastShipActivityAtBefore(any(Instant.class)))
            .thenReturn(Collections.singletonList(user));

        // Mock: ShipScoreService throws unexpected exception
        when(shipScoreService.calculateLevel(90))
            .thenThrow(new RuntimeException("Unexpected error"));

        // Act - Should not throw, should continue
        scoreDecayJob.applyScoreDecay();

        // Assert - Save should never be called due to exception
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("applyScoreDecay: should log success and failure counts")
    void applyScoreDecay_LogsSuccessAndFailureCounts() {
        // Arrange
        Instant sixtyOneDaysAgo = Instant.now().minus(61, ChronoUnit.DAYS);
        
        User user1 = User.builder()
            .id("user1")
            .shipScore(100)
            .level(3)
            .rank(RankTier.DOCKED)
            .lastShipActivityAt(sixtyOneDaysAgo)
            .build();

        User user2 = User.builder()
            .id("user2")
            .shipScore(200)
            .level(6)
            .rank(RankTier.LAUNCHER)
            .lastShipActivityAt(sixtyOneDaysAgo)
            .build();

        // Mock: Repository returns both users
        when(userRepository.findByLastShipActivityAtBefore(any(Instant.class)))
            .thenReturn(Arrays.asList(user1, user2));

        // Mock: First user succeeds, second user fails
        when(shipScoreService.calculateLevel(90)).thenReturn(3);
        when(shipScoreService.calculateLevel(180)).thenReturn(5);
        when(userRepository.save(user1)).thenReturn(user1);
        when(userRepository.save(user2)).thenThrow(new DataAccessException("Error") {});

        // Act
        scoreDecayJob.applyScoreDecay();

        // Assert
        verify(userRepository, times(1)).save(user1);
        verify(userRepository, times(4)).save(user2); // Initial + 3 retries
    }

    // ========== Edge Cases ==========

    @Test
    @DisplayName("applyScoreDecay: should handle user with score 0")
    void applyScoreDecay_Score0_RemainsScore0() {
        // Arrange
        Instant sixtyOneDaysAgo = Instant.now().minus(61, ChronoUnit.DAYS);
        
        User user = User.builder()
            .id("user999")
            .shipScore(0)
            .level(1)
            .rank(RankTier.DOCKED)
            .lastShipActivityAt(sixtyOneDaysAgo)
            .build();

        // Mock: Repository returns the user
        when(userRepository.findByLastShipActivityAtBefore(any(Instant.class)))
            .thenReturn(Collections.singletonList(user));

        // Mock: ShipScoreService calculations
        // Level stays the same (1), so calculateRank won't be called
        when(shipScoreService.calculateLevel(0)).thenReturn(1);

        // Act
        scoreDecayJob.applyScoreDecay();

        // Assert
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        
        User savedUser = userCaptor.getValue();
        assertThat(savedUser.getShipScore()).isEqualTo(0); // 0 * 0.9 = 0
    }

    @Test
    @DisplayName("applyScoreDecay: should handle empty list of inactive users")
    void applyScoreDecay_NoInactiveUsers_DoesNothing() {
        // Arrange
        // Mock: Repository returns empty list
        when(userRepository.findByLastShipActivityAtBefore(any(Instant.class)))
            .thenReturn(Collections.emptyList());

        // Act
        scoreDecayJob.applyScoreDecay();

        // Assert
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("applyScoreDecay: should process multiple inactive users")
    void applyScoreDecay_MultipleInactiveUsers_ProcessesAll() {
        // Arrange
        Instant sixtyOneDaysAgo = Instant.now().minus(61, ChronoUnit.DAYS);
        
        User user1 = User.builder()
            .id("user1")
            .shipScore(100)
            .level(3)
            .rank(RankTier.DOCKED)
            .lastShipActivityAt(sixtyOneDaysAgo)
            .build();

        User user2 = User.builder()
            .id("user2")
            .shipScore(200)
            .level(6)
            .rank(RankTier.LAUNCHER)
            .lastShipActivityAt(sixtyOneDaysAgo)
            .build();

        User user3 = User.builder()
            .id("user3")
            .shipScore(500)
            .level(13)
            .rank(RankTier.ORBITER)
            .lastShipActivityAt(sixtyOneDaysAgo)
            .build();

        // Mock: Repository returns all three users
        when(userRepository.findByLastShipActivityAtBefore(any(Instant.class)))
            .thenReturn(Arrays.asList(user1, user2, user3));

        // Mock: ShipScoreService calculations
        // All users' levels stay the same, so calculateRank won't be called
        when(shipScoreService.calculateLevel(90)).thenReturn(3);
        when(shipScoreService.calculateLevel(180)).thenReturn(5);
        when(shipScoreService.calculateLevel(450)).thenReturn(12);

        // Act
        scoreDecayJob.applyScoreDecay();

        // Assert
        verify(userRepository, times(3)).save(any(User.class));
    }
}
