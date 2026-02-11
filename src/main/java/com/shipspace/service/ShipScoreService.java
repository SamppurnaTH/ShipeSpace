package com.shipspace.service;

import com.shipspace.exception.ResourceNotFoundException;
import com.shipspace.model.RankTier;
import com.shipspace.model.ScoreEventLog;
import com.shipspace.model.ScoreEventType;
import com.shipspace.model.User;
import com.shipspace.repository.ScoreEventLogRepository;
import com.shipspace.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Service responsible for managing ShipScore progression, including score calculations,
 * level and rank determination, and score event application.
 * 
 * <p>This service provides core functionality for the rank and level progression system:
 * <ul>
 *   <li>Calculate user level from ShipScore using the formula: min(30, floor(shipScore / 40) + 1)</li>
 *   <li>Determine rank tier from level</li>
 *   <li>Calculate the ShipScore needed to reach the next level</li>
 *   <li>Apply score events with idempotency and daily cap enforcement</li>
 * </ul>
 * 
 * <p>The service ensures transactional safety and maintains data consistency when
 * updating user progression data.
 * 
 * @see <a href=".kiro/specs/rank-level-progression/requirements.md">Requirements 7.1, 8.7, 9.1, 12.3, 15.2</a>
 */
@Service
public class ShipScoreService {

    private final UserRepository userRepository;
    private final ScoreEventLogRepository scoreEventLogRepository;

    /**
     * Constructs a ShipScoreService with the required repository dependencies.
     * 
     * @param userRepository the repository for user data access
     * @param scoreEventLogRepository the repository for score event logging
     */
    @Autowired
    public ShipScoreService(
        UserRepository userRepository,
        ScoreEventLogRepository scoreEventLogRepository
    ) {
        this.userRepository = userRepository;
        this.scoreEventLogRepository = scoreEventLogRepository;
    }

    /**
     * Calculate the user's level from their ShipScore.
     * 
     * <p>The level is calculated using the formula: level = min(30, floor(shipScore / 40) + 1)
     * 
     * <p>This ensures:
     * <ul>
     *   <li>Minimum level is 1 (when shipScore is 0-39)</li>
     *   <li>Maximum level is 30 (capped regardless of shipScore)</li>
     *   <li>Each level requires 40 additional ShipScore points</li>
     * </ul>
     * 
     * <p>Examples:
     * <ul>
     *   <li>shipScore = 0 → level = 1</li>
     *   <li>shipScore = 39 → level = 1</li>
     *   <li>shipScore = 40 → level = 2</li>
     *   <li>shipScore = 80 → level = 3</li>
     *   <li>shipScore = 1160 → level = 30</li>
     *   <li>shipScore = 2000 → level = 30 (capped)</li>
     * </ul>
     * 
     * @param shipScore the user's accumulated ShipScore points (must be non-negative)
     * @return the calculated level (always between 1 and 30 inclusive)
     * @see <a href=".kiro/specs/rank-level-progression/requirements.md">Requirements 7.1, 7.2, 7.3</a>
     */
    public int calculateLevel(int shipScore) {
        return Math.min(30, (shipScore / 40) + 1);
    }

    /**
     * Calculate the user's rank tier from their level.
     * 
     * <p>Rank tiers are determined by level ranges:
     * <ul>
     *   <li>Levels 1-5: DOCKED</li>
     *   <li>Levels 6-10: LAUNCHER</li>
     *   <li>Levels 11-15: ORBITER</li>
     *   <li>Levels 16-20: NAVIGATOR</li>
     *   <li>Levels 21-25: COMMANDER</li>
     *   <li>Levels 26-30: FLEET_ADMIRAL</li>
     * </ul>
     * 
     * @param level the user's level (expected to be between 1 and 30)
     * @return the corresponding RankTier for the given level
     * @throws IllegalArgumentException if level is outside the valid range (1-30)
     * @see <a href=".kiro/specs/rank-level-progression/requirements.md">Requirements 8.1-8.6, 8.7</a>
     */
    public RankTier calculateRank(int level) {
        return RankTier.fromLevel(level);
    }

    /**
     * Calculate the ShipScore value required to reach the next level.
     * 
     * <p>The next level score is calculated as: (currentLevel) * 40
     * 
     * <p>This represents the minimum ShipScore needed to advance to the next level.
     * For users at the maximum level (30), this method returns null to indicate
     * that no further progression is possible.
     * 
     * <p>Examples:
     * <ul>
     *   <li>currentLevel = 1, currentScore = 20 → nextLevelScore = 40</li>
     *   <li>currentLevel = 5, currentScore = 180 → nextLevelScore = 200</li>
     *   <li>currentLevel = 29, currentScore = 1150 → nextLevelScore = 1160</li>
     *   <li>currentLevel = 30, currentScore = 1200 → nextLevelScore = null</li>
     * </ul>
     * 
     * @param currentLevel the user's current level (1-30)
     * @param currentScore the user's current ShipScore (used for validation, not calculation)
     * @return the ShipScore needed for the next level, or null if at maximum level (30)
     * @see <a href=".kiro/specs/rank-level-progression/requirements.md">Requirements 12.3, 12.4</a>
     */
    public Integer calculateNextLevelScore(int currentLevel, int currentScore) {
        if (currentLevel >= 30) {
            return null; // Max level reached, no next level
        }
        int nextLevel = currentLevel + 1;
        return (nextLevel - 1) * 40;
    }


    /**
     * Apply a score event to a user's ShipScore.
     *
     * <p>This method processes scoring events with the following validations:
     * <ul>
     *   <li>Idempotency: Prevents duplicate scoring for the same event</li>
     *   <li>Daily caps: Enforces limits on repeatable actions (e.g., PROJECT_UPDATED)</li>
     *   <li>User existence: Validates that the user exists in the system</li>
     * </ul>
     *
     * <p>When a score event is successfully applied:
     * <ul>
     *   <li>The user's shipScore is increased by the event's point value</li>
     *   <li>The user's lastShipActivityAt timestamp is updated</li>
     *   <li>The user's level is recalculated</li>
     *   <li>If the level changes, the user's rank is recalculated</li>
     *   <li>If the rank changes, the user's rankUpdatedAt timestamp is updated</li>
     *   <li>A ScoreEventLog entry is created to prevent future duplicates</li>
     * </ul>
     *
     * <p>All changes are persisted atomically to maintain data consistency.
     *
     * @param userId the ID of the user receiving the score
     * @param eventType the type of scoring event
     * @param entityId the unique identifier for the entity (project, comment, etc.)
     * @return true if the score was applied successfully, false if rejected (duplicate or cap exceeded)
     * @throws IllegalArgumentException if any parameter is null
     * @throws ResourceNotFoundException if the user doesn't exist
     * @see <a href=".kiro/specs/rank-level-progression/requirements.md">Requirements 6.3, 6.4, 9.1-9.8</a>
     */
    @Transactional
    public boolean applyScoreEvent(
        String userId,
        ScoreEventType eventType,
        String entityId
    ) {
        // Validate parameters
        if (userId == null || eventType == null || entityId == null) {
            throw new IllegalArgumentException("userId, eventType, and entityId must not be null");
        }

        // Check idempotency: Has this exact event already been logged?
        boolean isDuplicate = scoreEventLogRepository.existsByUserIdAndEventTypeAndEntityId(
            userId,
            eventType,
            entityId
        );

        if (isDuplicate) {
            // Event has already been processed, reject to maintain idempotency
            return false;
        }

        // Check daily cap: Does this event type have a daily limit?
        if (eventType.hasDailyCap()) {
            // Calculate start of current day in UTC
            Instant startOfDay = Instant.now().truncatedTo(ChronoUnit.DAYS);
            
            // Count how many events of this type the user has triggered today
            long eventCountToday = scoreEventLogRepository.countByUserIdAndEventTypeAndCreatedAtAfter(
                userId,
                eventType,
                startOfDay
            );
            
            // Check if the user has reached or exceeded the daily cap
            if (eventCountToday >= eventType.getDailyCap()) {
                // Daily cap exceeded, reject the event
                return false;
            }
        }

        // Fetch user by userId (throw ResourceNotFoundException if not found)
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));

        // Store old level and rank for comparison
        int oldLevel = user.getLevel();
        RankTier oldRank = user.getRank();

        // Add event type points to user.shipScore
        int newShipScore = user.getShipScore() + eventType.getPoints();
        user.setShipScore(newShipScore);

        // Update user.lastShipActivityAt to current timestamp
        user.setLastShipActivityAt(Instant.now());

        // Recalculate level using calculateLevel
        int newLevel = calculateLevel(newShipScore);
        user.setLevel(newLevel);

        // If level changed, recalculate rank using calculateRank
        if (newLevel != oldLevel) {
            RankTier newRank = calculateRank(newLevel);
            user.setRank(newRank);

            // If rank changed, update user.rankUpdatedAt to current timestamp
            if (newRank != oldRank) {
                user.setRankUpdatedAt(Instant.now());
            }
        }

        // Create ScoreEventLog entry with current timestamp
        ScoreEventLog eventLog = ScoreEventLog.builder()
            .userId(userId)
            .eventType(eventType)
            .entityId(entityId)
            .createdAt(Instant.now())
            .build();

        // Save user and event log atomically (within @Transactional context)
        userRepository.save(user);
        scoreEventLogRepository.save(eventLog);

        // Return true on success
        return true;
    }

}
