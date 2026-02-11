package com.shipspace.service;

import com.shipspace.model.RankTier;
import com.shipspace.model.User;
import com.shipspace.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Scheduled job that applies score decay to inactive users.
 * 
 * <p>This job runs daily at 2:00 AM server time and reduces the ShipScore
 * of users who have been inactive for more than 60 days by 10%.
 * 
 * <p>The decay process:
 * <ul>
 *   <li>Identifies users with lastShipActivityAt more than 60 days in the past</li>
 *   <li>Reduces their shipScore by 10% (multiplied by 0.9, floored)</li>
 *   <li>Recalculates level and rank based on the new score</li>
 *   <li>Updates rankUpdatedAt if the rank changes</li>
 *   <li>Does NOT modify lastShipActivityAt (decay is not an activity)</li>
 * </ul>
 * 
 * @see <a href=".kiro/specs/rank-level-progression/requirements.md">Requirements 11.1, 15.4</a>
 */
@Component
public class ScoreDecayJob {

    private static final Logger log = LoggerFactory.getLogger(ScoreDecayJob.class);

    private final UserRepository userRepository;
    private final ShipScoreService shipScoreService;

    /**
     * Constructs a ScoreDecayJob with the required dependencies.
     * 
     * @param userRepository the repository for user data access
     * @param shipScoreService the service for score calculations
     */
    @Autowired
    public ScoreDecayJob(
        UserRepository userRepository,
        ShipScoreService shipScoreService
    ) {
        this.userRepository = userRepository;
        this.shipScoreService = shipScoreService;
    }

    /**
     * Applies score decay to all inactive users.
     * 
     * <p>This method is scheduled to run daily at 2:00 AM server time.
     * It identifies users who have been inactive for more than 60 days
     * and reduces their ShipScore by 10%.
     * 
     * <p>The decay process:
     * <ol>
     *   <li>Calculate the threshold date (60 days ago)</li>
     *   <li>Find all users with lastShipActivityAt before the threshold</li>
     *   <li>For each inactive user, apply decay to their score</li>
     *   <li>Recalculate level and rank if necessary</li>
     *   <li>Persist all changes</li>
     * </ol>
     * 
     * <p>Error handling:
     * <ul>
     *   <li>Individual user processing errors are caught and logged</li>
     *   <li>The job continues with the next user if one fails</li>
     *   <li>Retry logic with exponential backoff for transient failures</li>
     *   <li>Success/failure metrics are logged at completion</li>
     * </ul>
     * 
     * @see <a href=".kiro/specs/rank-level-progression/requirements.md">Requirements 11.1, 11.2</a>
     */
    @Scheduled(cron = "0 0 2 * * *") // Daily at 2:00 AM
    public void applyScoreDecay() {
        log.info("Starting scheduled score decay job");
        
        // Calculate threshold: 60 days ago
        Instant sixtyDaysAgo = Instant.now().minus(60, ChronoUnit.DAYS);
        
        // Find inactive users
        List<User> inactiveUsers = userRepository.findByLastShipActivityAtBefore(sixtyDaysAgo);
        
        log.info("Found {} inactive users for score decay", inactiveUsers.size());
        
        int successCount = 0;
        int failureCount = 0;
        
        // Apply decay to each inactive user with error handling
        for (User user : inactiveUsers) {
            try {
                boolean success = applyDecayToUserWithRetry(user, 3, 1000); // 3 retries, 1 second initial backoff
                if (success) {
                    successCount++;
                } else {
                    failureCount++;
                }
            } catch (Exception e) {
                log.error("Unexpected error processing user {} in score decay job: {}", 
                    user.getId(), e.getMessage(), e);
                failureCount++;
            }
        }
        
        log.info("Completed score decay job: {} successes, {} failures", 
            successCount, failureCount);
    }

    /**
     * Applies score decay to a user with retry logic for transient failures.
     * 
     * <p>This method implements exponential backoff retry for handling
     * transient failures such as database connection issues.
     * 
     * @param user the user to apply decay to
     * @param maxRetries maximum number of retry attempts
     * @param initialDelayMs initial delay in milliseconds before first retry
     * @return true if decay was applied successfully, false otherwise
     */
    private boolean applyDecayToUserWithRetry(User user, int maxRetries, long initialDelayMs) {
        int attempt = 0;
        long delay = initialDelayMs;
        
        while (attempt <= maxRetries) {
            try {
                applyDecayToUser(user);
                return true;
            } catch (DataAccessException e) {
                attempt++;
                if (attempt > maxRetries) {
                    log.error("Failed to apply decay to user {} after {} attempts: {}", 
                        user.getId(), maxRetries, e.getMessage());
                    return false;
                }
                log.warn("Transient error applying decay to user {}, retry {} of {} in {}ms: {}",
                    user.getId(), attempt, maxRetries, delay, e.getMessage());
                try {
                    Thread.sleep(delay);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    log.warn("Retry interrupted for user {} decay", user.getId());
                    return false;
                }
                delay *= 2; // Exponential backoff
            }
        }
        return false;
    }

    /**
     * Applies score decay to a single user.
     * 
     * <p>This method:
     * <ul>
     *   <li>Reduces the user's shipScore by 10% (multiplied by 0.9, floored)</li>
     *   <li>Recalculates the user's level based on the new score</li>
     *   <li>If the level changes, recalculates the user's rank</li>
     *   <li>If the rank changes, updates the rankUpdatedAt timestamp</li>
     *   <li>Does NOT modify lastShipActivityAt (decay is not an activity)</li>
     *   <li>Persists all changes to the database</li>
     * </ul>
     * 
     * @param user the user to apply decay to
     * @see <a href=".kiro/specs/rank-level-progression/requirements.md">Requirements 11.3, 11.4, 11.5, 11.6, 11.7</a>
     */
    private void applyDecayToUser(User user) {
        // Get current score
        int currentScore = user.getShipScore();
        
        // Calculate decayed score (reduce by 10%, floor the result)
        int decayedScore = (int) Math.floor(currentScore * 0.9);
        
        // Update user's shipScore
        user.setShipScore(decayedScore);
        
        // Store old level and rank for comparison
        int oldLevel = user.getLevel();
        RankTier oldRank = user.getRank();
        
        // Recalculate level
        int newLevel = shipScoreService.calculateLevel(decayedScore);
        user.setLevel(newLevel);
        
        // If level changed, recalculate rank
        if (newLevel != oldLevel) {
            RankTier newRank = shipScoreService.calculateRank(newLevel);
            user.setRank(newRank);
            
            // If rank changed, update rankUpdatedAt
            if (newRank != oldRank) {
                user.setRankUpdatedAt(Instant.now());
            }
        }
        
        // Save user (do NOT modify lastShipActivityAt)
        userRepository.save(user);
        
        log.debug("Applied decay to user {}: {} -> {} (level {} -> {})",
            user.getId(), currentScore, decayedScore, oldLevel, newLevel);
    }
}
