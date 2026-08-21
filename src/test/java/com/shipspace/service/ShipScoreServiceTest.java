package com.shipspace.service;

import com.shipspace.model.RankTier;
import com.shipspace.model.ScoreEventLog;
import com.shipspace.model.ScoreEventType;
import com.shipspace.model.User;
import com.shipspace.repository.ScoreEventLogRepository;
import com.shipspace.repository.UserRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Unit tests for ShipScoreService calculation methods.
 * 
 * <p>Tests the core calculation logic for:
 * <ul>
 *   <li>Level calculation from ShipScore</li>
 *   <li>Rank tier determination from level</li>
 *   <li>Next level score calculation</li>
 * </ul>
 * 
 * <p>Requirements: 7.1, 7.2, 7.3, 8.7, 12.3, 12.4
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ShipScoreService - Calculation Methods Tests")
class ShipScoreServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private ScoreEventLogRepository scoreEventLogRepository;

    private ShipScoreService shipScoreService;

    @BeforeEach
    void setUp() {
        shipScoreService = new ShipScoreService(
            userRepository,
            scoreEventLogRepository,
            new SimpleMeterRegistry()
        );
    }

    // ========== calculateLevel Tests ==========

    @Test
    @DisplayName("calculateLevel: shipScore 0 should return level 1")
    void calculateLevel_ZeroScore_ReturnsLevelOne() {
        // Act
        int level = shipScoreService.calculateLevel(0);

        // Assert
        assertThat(level).isEqualTo(1);
    }

    @Test
    @DisplayName("calculateLevel: shipScore 39 should return level 1")
    void calculateLevel_ThirtyNineScore_ReturnsLevelOne() {
        // Act
        int level = shipScoreService.calculateLevel(39);

        // Assert
        assertThat(level).isEqualTo(1);
    }

    @Test
    @DisplayName("calculateLevel: shipScore 40 should return level 2")
    void calculateLevel_FortyScore_ReturnsLevelTwo() {
        // Act
        int level = shipScoreService.calculateLevel(40);

        // Assert
        assertThat(level).isEqualTo(2);
    }

    @Test
    @DisplayName("calculateLevel: shipScore 80 should return level 3")
    void calculateLevel_EightyScore_ReturnsLevelThree() {
        // Act
        int level = shipScoreService.calculateLevel(80);

        // Assert
        assertThat(level).isEqualTo(3);
    }

    @Test
    @DisplayName("calculateLevel: shipScore 1160 should return level 30 (max level)")
    void calculateLevel_ElevenSixtyScore_ReturnsLevelThirty() {
        // Act
        int level = shipScoreService.calculateLevel(1160);

        // Assert
        assertThat(level).isEqualTo(30);
    }

    @Test
    @DisplayName("calculateLevel: shipScore 2000 should return level 30 (capped)")
    void calculateLevel_TwoThousandScore_ReturnsLevelThirtyCapped() {
        // Act
        int level = shipScoreService.calculateLevel(2000);

        // Assert
        assertThat(level).isEqualTo(30);
    }

    @Test
    @DisplayName("calculateLevel: boundary test - shipScore 41 should return level 2")
    void calculateLevel_FortyOneScore_ReturnsLevelTwo() {
        // Act
        int level = shipScoreService.calculateLevel(41);

        // Assert
        assertThat(level).isEqualTo(2);
    }

    @Test
    @DisplayName("calculateLevel: boundary test - shipScore 79 should return level 2")
    void calculateLevel_SeventyNineScore_ReturnsLevelTwo() {
        // Act
        int level = shipScoreService.calculateLevel(79);

        // Assert
        assertThat(level).isEqualTo(2);
    }

    // ========== calculateRank Tests ==========

    @Test
    @DisplayName("calculateRank: level 1 should return DOCKED")
    void calculateRank_LevelOne_ReturnsDocked() {
        // Act
        RankTier rank = shipScoreService.calculateRank(1);

        // Assert
        assertThat(rank).isEqualTo(RankTier.DOCKED);
    }

    @Test
    @DisplayName("calculateRank: level 5 should return DOCKED (boundary)")
    void calculateRank_LevelFive_ReturnsDocked() {
        // Act
        RankTier rank = shipScoreService.calculateRank(5);

        // Assert
        assertThat(rank).isEqualTo(RankTier.DOCKED);
    }

    @Test
    @DisplayName("calculateRank: level 6 should return LAUNCHER")
    void calculateRank_LevelSix_ReturnsLauncher() {
        // Act
        RankTier rank = shipScoreService.calculateRank(6);

        // Assert
        assertThat(rank).isEqualTo(RankTier.LAUNCHER);
    }

    @Test
    @DisplayName("calculateRank: level 10 should return LAUNCHER (boundary)")
    void calculateRank_LevelTen_ReturnsLauncher() {
        // Act
        RankTier rank = shipScoreService.calculateRank(10);

        // Assert
        assertThat(rank).isEqualTo(RankTier.LAUNCHER);
    }

    @Test
    @DisplayName("calculateRank: level 11 should return ORBITER")
    void calculateRank_LevelEleven_ReturnsOrbiter() {
        // Act
        RankTier rank = shipScoreService.calculateRank(11);

        // Assert
        assertThat(rank).isEqualTo(RankTier.ORBITER);
    }

    @Test
    @DisplayName("calculateRank: level 15 should return ORBITER (boundary)")
    void calculateRank_LevelFifteen_ReturnsOrbiter() {
        // Act
        RankTier rank = shipScoreService.calculateRank(15);

        // Assert
        assertThat(rank).isEqualTo(RankTier.ORBITER);
    }

    @Test
    @DisplayName("calculateRank: level 16 should return NAVIGATOR")
    void calculateRank_LevelSixteen_ReturnsNavigator() {
        // Act
        RankTier rank = shipScoreService.calculateRank(16);

        // Assert
        assertThat(rank).isEqualTo(RankTier.NAVIGATOR);
    }

    @Test
    @DisplayName("calculateRank: level 20 should return NAVIGATOR (boundary)")
    void calculateRank_LevelTwenty_ReturnsNavigator() {
        // Act
        RankTier rank = shipScoreService.calculateRank(20);

        // Assert
        assertThat(rank).isEqualTo(RankTier.NAVIGATOR);
    }

    @Test
    @DisplayName("calculateRank: level 21 should return COMMANDER")
    void calculateRank_LevelTwentyOne_ReturnsCommander() {
        // Act
        RankTier rank = shipScoreService.calculateRank(21);

        // Assert
        assertThat(rank).isEqualTo(RankTier.COMMANDER);
    }

    @Test
    @DisplayName("calculateRank: level 25 should return COMMANDER (boundary)")
    void calculateRank_LevelTwentyFive_ReturnsCommander() {
        // Act
        RankTier rank = shipScoreService.calculateRank(25);

        // Assert
        assertThat(rank).isEqualTo(RankTier.COMMANDER);
    }

    @Test
    @DisplayName("calculateRank: level 26 should return FLEET_ADMIRAL")
    void calculateRank_LevelTwentySix_ReturnsFleetAdmiral() {
        // Act
        RankTier rank = shipScoreService.calculateRank(26);

        // Assert
        assertThat(rank).isEqualTo(RankTier.FLEET_ADMIRAL);
    }

    @Test
    @DisplayName("calculateRank: level 30 should return FLEET_ADMIRAL (boundary)")
    void calculateRank_LevelThirty_ReturnsFleetAdmiral() {
        // Act
        RankTier rank = shipScoreService.calculateRank(30);

        // Assert
        assertThat(rank).isEqualTo(RankTier.FLEET_ADMIRAL);
    }

    // ========== calculateNextLevelScore Tests ==========

    @Test
    @DisplayName("calculateNextLevelScore: level 1 should return 40")
    void calculateNextLevelScore_LevelOne_ReturnsForty() {
        // Act
        Integer nextLevelScore = shipScoreService.calculateNextLevelScore(1, 0);

        // Assert
        assertThat(nextLevelScore).isEqualTo(40);
    }

    @Test
    @DisplayName("calculateNextLevelScore: level 2 should return 80")
    void calculateNextLevelScore_LevelTwo_ReturnsEighty() {
        // Act
        Integer nextLevelScore = shipScoreService.calculateNextLevelScore(2, 50);

        // Assert
        assertThat(nextLevelScore).isEqualTo(80);
    }

    @Test
    @DisplayName("calculateNextLevelScore: level 5 should return 200")
    void calculateNextLevelScore_LevelFive_ReturnsTwoHundred() {
        // Act
        Integer nextLevelScore = shipScoreService.calculateNextLevelScore(5, 180);

        // Assert
        assertThat(nextLevelScore).isEqualTo(200);
    }

    @Test
    @DisplayName("calculateNextLevelScore: level 10 should return 400")
    void calculateNextLevelScore_LevelTen_ReturnsFourHundred() {
        // Act
        Integer nextLevelScore = shipScoreService.calculateNextLevelScore(10, 380);

        // Assert
        assertThat(nextLevelScore).isEqualTo(400);
    }

    @Test
    @DisplayName("calculateNextLevelScore: level 15 should return 600")
    void calculateNextLevelScore_LevelFifteen_ReturnsSixHundred() {
        // Act
        Integer nextLevelScore = shipScoreService.calculateNextLevelScore(15, 580);

        // Assert
        assertThat(nextLevelScore).isEqualTo(600);
    }

    @Test
    @DisplayName("calculateNextLevelScore: level 20 should return 800")
    void calculateNextLevelScore_LevelTwenty_ReturnsEightHundred() {
        // Act
        Integer nextLevelScore = shipScoreService.calculateNextLevelScore(20, 780);

        // Assert
        assertThat(nextLevelScore).isEqualTo(800);
    }

    @Test
    @DisplayName("calculateNextLevelScore: level 25 should return 1000")
    void calculateNextLevelScore_LevelTwentyFive_ReturnsOneThousand() {
        // Act
        Integer nextLevelScore = shipScoreService.calculateNextLevelScore(25, 980);

        // Assert
        assertThat(nextLevelScore).isEqualTo(1000);
    }

    @Test
    @DisplayName("calculateNextLevelScore: level 29 should return 1160")
    void calculateNextLevelScore_LevelTwentyNine_ReturnsElevenSixty() {
        // Act
        Integer nextLevelScore = shipScoreService.calculateNextLevelScore(29, 1150);

        // Assert
        assertThat(nextLevelScore).isEqualTo(1160);
    }

    @Test
    @DisplayName("calculateNextLevelScore: level 30 should return null (max level)")
    void calculateNextLevelScore_LevelThirty_ReturnsNull() {
        // Act
        Integer nextLevelScore = shipScoreService.calculateNextLevelScore(30, 1200);

        // Assert
        assertThat(nextLevelScore).isNull();
    }

    @Test
    @DisplayName("calculateNextLevelScore: level 30 with exact threshold score should return null")
    void calculateNextLevelScore_LevelThirtyExactScore_ReturnsNull() {
        // Act
        Integer nextLevelScore = shipScoreService.calculateNextLevelScore(30, 1160);

        // Assert
        assertThat(nextLevelScore).isNull();
    }

    // ========== applyScoreEvent Tests (Task 4.1 - Idempotency Check) ==========

    @Test
    @DisplayName("applyScoreEvent: should reject duplicate event (idempotency)")
    void applyScoreEvent_DuplicateEvent_ReturnsFalse() {
        // Arrange
        String userId = "user123";
        ScoreEventType eventType = ScoreEventType.PROJECT_CREATED;
        String entityId = "project456";

        // Mock: Event already exists in the log
        when(scoreEventLogRepository.existsByUserIdAndEventTypeAndEntityId(userId, eventType, entityId))
            .thenReturn(true);

        // Act
        boolean result = shipScoreService.applyScoreEvent(userId, eventType, entityId);

        // Assert
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("applyScoreEvent: should accept new event (not duplicate)")
    void applyScoreEvent_NewEvent_ReturnsTrue() {
        // Arrange
        String userId = "user123";
        ScoreEventType eventType = ScoreEventType.PROJECT_CREATED;
        String entityId = "project456";

        // Mock: Event does not exist in the log
        when(scoreEventLogRepository.existsByUserIdAndEventTypeAndEntityId(
            org.mockito.ArgumentMatchers.eq(userId),
            org.mockito.ArgumentMatchers.eq(eventType),
            org.mockito.ArgumentMatchers.anyString()))
            .thenReturn(false);

        // Mock: User exists with initial score
        User mockUser = User.builder()
            .id(userId)
            .shipScore(0)
            .level(1)
            .rank(RankTier.DOCKED)
            .build();
        when(userRepository.findById(userId)).thenReturn(Optional.of(mockUser));

        // Act
        boolean result = shipScoreService.applyScoreEvent(userId, eventType, entityId);

        // Assert
        // Note: Returns true for now since persistence is not yet implemented (task 4.4)
        assertThat(result).isTrue();
        ArgumentCaptor<ScoreEventLog> eventCaptor = ArgumentCaptor.forClass(ScoreEventLog.class);
        org.mockito.Mockito.verify(scoreEventLogRepository).save(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getPoints()).isEqualTo(ScoreEventType.PROJECT_CREATED.getPoints());
        assertThat(eventCaptor.getValue().getEntityId()).isEqualTo(entityId);
    }

    @Test
    @DisplayName("applyScoreEvent: should reject duplicate-key race before mutating score")
    void applyScoreEvent_DuplicateKeyRace_DoesNotMutateUser() {
        String userId = "user123";
        ScoreEventType eventType = ScoreEventType.PROJECT_CREATED;
        String entityId = "project456";
        User mockUser = User.builder()
            .id(userId)
            .shipScore(100)
            .level(3)
            .rank(RankTier.DOCKED)
            .build();

        when(scoreEventLogRepository.existsByUserIdAndEventTypeAndEntityId(userId, eventType, entityId))
            .thenReturn(false);
        when(userRepository.findById(userId)).thenReturn(Optional.of(mockUser));
        when(scoreEventLogRepository.save(any(ScoreEventLog.class))).thenThrow(new DuplicateKeyException("duplicate"));

        boolean result = shipScoreService.applyScoreEvent(userId, eventType, entityId);

        assertThat(result).isFalse();
        assertThat(mockUser.getShipScore()).isEqualTo(100);
        org.mockito.Mockito.verify(userRepository, org.mockito.Mockito.never()).save(any(User.class));
    }

    @Test
    @DisplayName("applyScoreEvent: should throw IllegalArgumentException when userId is null")
    void applyScoreEvent_NullUserId_ThrowsException() {
        // Arrange
        String userId = null;
        ScoreEventType eventType = ScoreEventType.PROJECT_CREATED;
        String entityId = "project456";

        // Act & Assert
        assertThatThrownBy(() -> shipScoreService.applyScoreEvent(userId, eventType, entityId))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("userId, eventType, and entityId must not be null");
    }

    @Test
    @DisplayName("applyScoreEvent: should throw IllegalArgumentException when eventType is null")
    void applyScoreEvent_NullEventType_ThrowsException() {
        // Arrange
        String userId = "user123";
        ScoreEventType eventType = null;
        String entityId = "project456";

        // Act & Assert
        assertThatThrownBy(() -> shipScoreService.applyScoreEvent(userId, eventType, entityId))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("userId, eventType, and entityId must not be null");
    }

    @Test
    @DisplayName("applyScoreEvent: should throw IllegalArgumentException when entityId is null")
    void applyScoreEvent_NullEntityId_ThrowsException() {
        // Arrange
        String userId = "user123";
        ScoreEventType eventType = ScoreEventType.PROJECT_CREATED;
        String entityId = null;

        // Act & Assert
        assertThatThrownBy(() -> shipScoreService.applyScoreEvent(userId, eventType, entityId))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("userId, eventType, and entityId must not be null");
    }

    // ========== applyScoreEvent Tests (Task 4.2 - Daily Cap Validation) ==========

    @Test
    @DisplayName("applyScoreEvent: should accept PROJECT_UPDATED event when under daily cap")
    void applyScoreEvent_ProjectUpdatedUnderCap_ReturnsTrue() {
        // Arrange
        String userId = "user123";
        ScoreEventType eventType = ScoreEventType.PROJECT_UPDATED;
        String entityId = "project456";

        // Mock: Event is not a duplicate
        when(scoreEventLogRepository.existsByUserIdAndEventTypeAndEntityId(
            org.mockito.ArgumentMatchers.eq(userId),
            org.mockito.ArgumentMatchers.eq(eventType),
            org.mockito.ArgumentMatchers.anyString()))
            .thenReturn(false);

        // Mock: User has only 4 PROJECT_UPDATED events today (under cap of 5)
        when(scoreEventLogRepository.countByUserIdAndEventTypeAndCreatedAtAfter(
            org.mockito.ArgumentMatchers.eq(userId),
            org.mockito.ArgumentMatchers.eq(eventType),
            org.mockito.ArgumentMatchers.any(Instant.class)
        )).thenReturn(4L);

        // Mock: User exists with initial score
        User mockUser = User.builder()
            .id(userId)
            .shipScore(100)
            .level(3)
            .rank(RankTier.DOCKED)
            .build();
        when(userRepository.findById(userId)).thenReturn(Optional.of(mockUser));

        // Act
        boolean result = shipScoreService.applyScoreEvent(userId, eventType, entityId);

        // Assert
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("applyScoreEvent: should reject PROJECT_UPDATED event when daily cap reached")
    void applyScoreEvent_ProjectUpdatedAtCap_ReturnsFalse() {
        // Arrange
        String userId = "user123";
        ScoreEventType eventType = ScoreEventType.PROJECT_UPDATED;
        String entityId = "project456";

        // Mock: Event is not a duplicate
        when(scoreEventLogRepository.existsByUserIdAndEventTypeAndEntityId(
            org.mockito.ArgumentMatchers.eq(userId),
            org.mockito.ArgumentMatchers.eq(eventType),
            org.mockito.ArgumentMatchers.anyString()))
            .thenReturn(false);

        // Mock: User has already reached the daily cap of 5 PROJECT_UPDATED events
        when(scoreEventLogRepository.countByUserIdAndEventTypeAndCreatedAtAfter(
            org.mockito.ArgumentMatchers.eq(userId),
            org.mockito.ArgumentMatchers.eq(eventType),
            org.mockito.ArgumentMatchers.any(Instant.class)
        )).thenReturn(5L);

        // Act
        boolean result = shipScoreService.applyScoreEvent(userId, eventType, entityId);

        // Assert
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("applyScoreEvent: should reject PROJECT_UPDATED event when daily cap exceeded")
    void applyScoreEvent_ProjectUpdatedOverCap_ReturnsFalse() {
        // Arrange
        String userId = "user123";
        ScoreEventType eventType = ScoreEventType.PROJECT_UPDATED;
        String entityId = "project456";

        // Mock: Event is not a duplicate
        when(scoreEventLogRepository.existsByUserIdAndEventTypeAndEntityId(
            org.mockito.ArgumentMatchers.eq(userId),
            org.mockito.ArgumentMatchers.eq(eventType),
            org.mockito.ArgumentMatchers.anyString()))
            .thenReturn(false);

        // Mock: User has exceeded the daily cap (6 events when cap is 5)
        when(scoreEventLogRepository.countByUserIdAndEventTypeAndCreatedAtAfter(
            org.mockito.ArgumentMatchers.eq(userId),
            org.mockito.ArgumentMatchers.eq(eventType),
            org.mockito.ArgumentMatchers.any(Instant.class)
        )).thenReturn(6L);

        // Act
        boolean result = shipScoreService.applyScoreEvent(userId, eventType, entityId);

        // Assert
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("applyScoreEvent: should not check daily cap for PROJECT_CREATED (no cap)")
    void applyScoreEvent_ProjectCreatedNoCap_ReturnsTrue() {
        // Arrange
        String userId = "user123";
        ScoreEventType eventType = ScoreEventType.PROJECT_CREATED;
        String entityId = "project456";

        // Mock: Event is not a duplicate
        when(scoreEventLogRepository.existsByUserIdAndEventTypeAndEntityId(userId, eventType, entityId))
            .thenReturn(false);

        // Mock: User exists with initial score
        User mockUser = User.builder()
            .id(userId)
            .shipScore(0)
            .level(1)
            .rank(RankTier.DOCKED)
            .build();
        when(userRepository.findById(userId)).thenReturn(Optional.of(mockUser));

        // Note: No need to mock countByUserIdAndEventTypeAndCreatedAtAfter
        // because PROJECT_CREATED has no daily cap, so the count method should not be called

        // Act
        boolean result = shipScoreService.applyScoreEvent(userId, eventType, entityId);

        // Assert
        assertThat(result).isTrue();
        
        // Verify that the count method was never called for event types without daily caps
        org.mockito.Mockito.verify(scoreEventLogRepository, org.mockito.Mockito.never())
            .countByUserIdAndEventTypeAndCreatedAtAfter(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
            );
    }

    @Test
    @DisplayName("applyScoreEvent: should not check daily cap for COLLAB_ACCEPTED (no cap)")
    void applyScoreEvent_CollabAcceptedNoCap_ReturnsTrue() {
        // Arrange
        String userId = "user123";
        ScoreEventType eventType = ScoreEventType.COLLAB_ACCEPTED;
        String entityId = "collab789";

        // Mock: Event is not a duplicate
        when(scoreEventLogRepository.existsByUserIdAndEventTypeAndEntityId(userId, eventType, entityId))
            .thenReturn(false);

        // Mock: User exists with initial score
        User mockUser = User.builder()
            .id(userId)
            .shipScore(50)
            .level(2)
            .rank(RankTier.DOCKED)
            .build();
        when(userRepository.findById(userId)).thenReturn(Optional.of(mockUser));

        // Act
        boolean result = shipScoreService.applyScoreEvent(userId, eventType, entityId);

        // Assert
        assertThat(result).isTrue();
        
        // Verify that the count method was never called
        org.mockito.Mockito.verify(scoreEventLogRepository, org.mockito.Mockito.never())
            .countByUserIdAndEventTypeAndCreatedAtAfter(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
            );
    }

    @Test
    @DisplayName("applyScoreEvent: should not check daily cap for QUALITY_COMMENT (no cap)")
    void applyScoreEvent_QualityCommentNoCap_ReturnsTrue() {
        // Arrange
        String userId = "user123";
        ScoreEventType eventType = ScoreEventType.QUALITY_COMMENT;
        String entityId = "comment999";

        // Mock: Event is not a duplicate
        when(scoreEventLogRepository.existsByUserIdAndEventTypeAndEntityId(userId, eventType, entityId))
            .thenReturn(false);

        // Mock: User exists with initial score
        User mockUser = User.builder()
            .id(userId)
            .shipScore(200)
            .level(6)
            .rank(RankTier.LAUNCHER)
            .build();
        when(userRepository.findById(userId)).thenReturn(Optional.of(mockUser));

        // Act
        boolean result = shipScoreService.applyScoreEvent(userId, eventType, entityId);

        // Assert
        assertThat(result).isTrue();
        
        // Verify that the count method was never called
        org.mockito.Mockito.verify(scoreEventLogRepository, org.mockito.Mockito.never())
            .countByUserIdAndEventTypeAndCreatedAtAfter(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
            );
    }

    // ========== applyScoreEvent Tests (Task 4.5 - Additional Tests) ==========

    @Test
    @DisplayName("applyScoreEvent: should increase shipScore by correct amount")
    void applyScoreEvent_SuccessfulApplication_IncreasesShipScore() {
        // Arrange
        String userId = "user123";
        ScoreEventType eventType = ScoreEventType.PROJECT_CREATED;
        String entityId = "project456";
        int initialScore = 100;

        // Mock: Event is not a duplicate
        when(scoreEventLogRepository.existsByUserIdAndEventTypeAndEntityId(userId, eventType, entityId))
            .thenReturn(false);

        // Mock: User exists with initial score
        User mockUser = User.builder()
            .id(userId)
            .shipScore(initialScore)
            .level(3)
            .rank(RankTier.DOCKED)
            .build();
        when(userRepository.findById(userId)).thenReturn(Optional.of(mockUser));

        // Act
        boolean result = shipScoreService.applyScoreEvent(userId, eventType, entityId);

        // Assert
        assertThat(result).isTrue();
        assertThat(mockUser.getShipScore()).isEqualTo(initialScore + eventType.getPoints());
        assertThat(mockUser.getShipScore()).isEqualTo(150); // 100 + 50
    }

    @Test
    @DisplayName("applyScoreEvent: should update lastShipActivityAt on successful event")
    void applyScoreEvent_SuccessfulApplication_UpdatesLastShipActivityAt() {
        // Arrange
        String userId = "user123";
        ScoreEventType eventType = ScoreEventType.PROJECT_CREATED;
        String entityId = "project456";
        Instant beforeTest = Instant.now().minusSeconds(1);

        // Mock: Event is not a duplicate
        when(scoreEventLogRepository.existsByUserIdAndEventTypeAndEntityId(userId, eventType, entityId))
            .thenReturn(false);

        // Mock: User exists with no previous activity timestamp
        User mockUser = User.builder()
            .id(userId)
            .shipScore(0)
            .level(1)
            .rank(RankTier.DOCKED)
            .lastShipActivityAt(null)
            .build();
        when(userRepository.findById(userId)).thenReturn(Optional.of(mockUser));

        // Act
        boolean result = shipScoreService.applyScoreEvent(userId, eventType, entityId);
        Instant afterTest = Instant.now().plusSeconds(1);

        // Assert
        assertThat(result).isTrue();
        assertThat(mockUser.getLastShipActivityAt()).isNotNull();
        assertThat(mockUser.getLastShipActivityAt()).isAfter(beforeTest);
        assertThat(mockUser.getLastShipActivityAt()).isBefore(afterTest);
    }

    @Test
    @DisplayName("applyScoreEvent: should recalculate level when score crosses threshold")
    void applyScoreEvent_ScoreCrossesThreshold_RecalculatesLevel() {
        // Arrange
        String userId = "user123";
        ScoreEventType eventType = ScoreEventType.PROJECT_CREATED;
        String entityId = "project456";
        int initialScore = 35; // Level 1 (0-39)
        int expectedNewScore = 85; // 35 + 50 = 85, which is Level 3 (80-119)

        // Mock: Event is not a duplicate
        when(scoreEventLogRepository.existsByUserIdAndEventTypeAndEntityId(userId, eventType, entityId))
            .thenReturn(false);

        // Mock: User exists at level 1
        User mockUser = User.builder()
            .id(userId)
            .shipScore(initialScore)
            .level(1)
            .rank(RankTier.DOCKED)
            .build();
        when(userRepository.findById(userId)).thenReturn(Optional.of(mockUser));

        // Act
        boolean result = shipScoreService.applyScoreEvent(userId, eventType, entityId);

        // Assert
        assertThat(result).isTrue();
        assertThat(mockUser.getShipScore()).isEqualTo(expectedNewScore);
        assertThat(mockUser.getLevel()).isEqualTo(3); // Level should be recalculated
    }

    @Test
    @DisplayName("applyScoreEvent: should recalculate rank when level crosses rank threshold")
    void applyScoreEvent_LevelCrossesRankThreshold_RecalculatesRank() {
        // Arrange
        String userId = "user123";
        ScoreEventType eventType = ScoreEventType.PROJECT_CREATED;
        String entityId = "project456";
        int initialScore = 190; // Level 5 (160-199), Rank DOCKED
        int expectedNewScore = 240; // 190 + 50 = 240, which is Level 7 (240-279), Rank LAUNCHER

        // Mock: Event is not a duplicate
        when(scoreEventLogRepository.existsByUserIdAndEventTypeAndEntityId(userId, eventType, entityId))
            .thenReturn(false);

        // Mock: User exists at level 5, rank DOCKED
        User mockUser = User.builder()
            .id(userId)
            .shipScore(initialScore)
            .level(5)
            .rank(RankTier.DOCKED)
            .rankUpdatedAt(null)
            .build();
        when(userRepository.findById(userId)).thenReturn(Optional.of(mockUser));

        // Act
        boolean result = shipScoreService.applyScoreEvent(userId, eventType, entityId);

        // Assert
        assertThat(result).isTrue();
        assertThat(mockUser.getShipScore()).isEqualTo(expectedNewScore);
        assertThat(mockUser.getLevel()).isEqualTo(7); // Level should be recalculated
        assertThat(mockUser.getRank()).isEqualTo(RankTier.LAUNCHER); // Rank should be recalculated
    }

    @Test
    @DisplayName("applyScoreEvent: should update rankUpdatedAt when rank changes")
    void applyScoreEvent_RankChanges_UpdatesRankUpdatedAt() {
        // Arrange
        String userId = "user123";
        ScoreEventType eventType = ScoreEventType.PROJECT_CREATED;
        String entityId = "project456";
        int initialScore = 190; // Level 5 (160-199), Rank DOCKED
        int expectedNewScore = 240; // 190 + 50 = 240, which is Level 7 (240-279), Rank LAUNCHER
        Instant beforeTest = Instant.now().minusSeconds(1);

        // Mock: Event is not a duplicate
        when(scoreEventLogRepository.existsByUserIdAndEventTypeAndEntityId(userId, eventType, entityId))
            .thenReturn(false);

        // Mock: User exists at level 5, rank DOCKED
        User mockUser = User.builder()
            .id(userId)
            .shipScore(initialScore)
            .level(5)
            .rank(RankTier.DOCKED)
            .rankUpdatedAt(null)
            .build();
        when(userRepository.findById(userId)).thenReturn(Optional.of(mockUser));

        // Act
        boolean result = shipScoreService.applyScoreEvent(userId, eventType, entityId);
        Instant afterTest = Instant.now().plusSeconds(1);

        // Assert
        assertThat(result).isTrue();
        assertThat(mockUser.getRank()).isEqualTo(RankTier.LAUNCHER);
        assertThat(mockUser.getRankUpdatedAt()).isNotNull();
        assertThat(mockUser.getRankUpdatedAt()).isAfter(beforeTest);
        assertThat(mockUser.getRankUpdatedAt()).isBefore(afterTest);
    }

    @Test
    @DisplayName("applyScoreEvent: should NOT update rankUpdatedAt when rank does not change")
    void applyScoreEvent_RankDoesNotChange_DoesNotUpdateRankUpdatedAt() {
        // Arrange
        String userId = "user123";
        ScoreEventType eventType = ScoreEventType.QUALITY_COMMENT;
        String entityId = "comment456";
        int initialScore = 100; // Level 3, Rank DOCKED
        int expectedNewScore = 105; // 100 + 5 = 105, still Level 3, still Rank DOCKED
        Instant previousRankUpdate = Instant.now().minus(5, java.time.temporal.ChronoUnit.DAYS);

        // Mock: Event is not a duplicate
        when(scoreEventLogRepository.existsByUserIdAndEventTypeAndEntityId(userId, eventType, entityId))
            .thenReturn(false);

        // Mock: User exists at level 3, rank DOCKED
        User mockUser = User.builder()
            .id(userId)
            .shipScore(initialScore)
            .level(3)
            .rank(RankTier.DOCKED)
            .rankUpdatedAt(previousRankUpdate)
            .build();
        when(userRepository.findById(userId)).thenReturn(Optional.of(mockUser));

        // Act
        boolean result = shipScoreService.applyScoreEvent(userId, eventType, entityId);

        // Assert
        assertThat(result).isTrue();
        assertThat(mockUser.getShipScore()).isEqualTo(expectedNewScore);
        assertThat(mockUser.getLevel()).isEqualTo(3); // Level stays the same
        assertThat(mockUser.getRank()).isEqualTo(RankTier.DOCKED); // Rank stays the same
        assertThat(mockUser.getRankUpdatedAt()).isEqualTo(previousRankUpdate); // Timestamp should NOT change
    }

    @Test
    @DisplayName("applyScoreEvent: should throw ResourceNotFoundException for invalid userId")
    void applyScoreEvent_InvalidUserId_ThrowsResourceNotFoundException() {
        // Arrange
        String userId = "nonexistent";
        ScoreEventType eventType = ScoreEventType.PROJECT_CREATED;
        String entityId = "project456";

        // Mock: Event is not a duplicate
        when(scoreEventLogRepository.existsByUserIdAndEventTypeAndEntityId(userId, eventType, entityId))
            .thenReturn(false);

        // Mock: User does not exist
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> shipScoreService.applyScoreEvent(userId, eventType, entityId))
            .isInstanceOf(com.shipspace.exception.ResourceNotFoundException.class)
            .hasMessageContaining("User not found with id: " + userId);
    }
}
