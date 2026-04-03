package com.shipspace.service;

import com.shipspace.model.RankTier;
import com.shipspace.model.User;
import com.shipspace.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Scheduled job that applies score decay to inactive users.
 * Runs daily at 2:00 AM. Reduces ShipScore of users inactive for 60+ days by 10%.
 */
@Component
public class ScoreDecayJob {

    private static final Logger log = LoggerFactory.getLogger(ScoreDecayJob.class);

    private final UserRepository userRepository;
    private final ShipScoreService shipScoreService;

    @Autowired
    public ScoreDecayJob(UserRepository userRepository, ShipScoreService shipScoreService) {
        this.userRepository = userRepository;
        this.shipScoreService = shipScoreService;
    }

    @Scheduled(cron = "0 0 2 * * *")
    public void applyScoreDecay() {
        log.info("Starting scheduled score decay job");

        Instant sixtyDaysAgo = Instant.now().minus(60, ChronoUnit.DAYS);
        List<User> inactiveUsers = userRepository.findByLastShipActivityAtBefore(sixtyDaysAgo);

        log.info("Found {} inactive users for score decay", inactiveUsers.size());

        int successCount = 0;
        int failureCount = 0;

        for (User user : inactiveUsers) {
            try {
                applyDecayToUser(user);
                successCount++;
            } catch (Exception e) {
                log.error("Failed to apply decay to user {} after retries: {}", user.getId(), e.getMessage());
                failureCount++;
            }
        }

        log.info("Completed score decay job: {} successes, {} failures", successCount, failureCount);
    }

    /**
     * Applies decay to a single user with automatic retry on transient DB failures.
     * Uses exponential backoff: 500ms, 1000ms, 2000ms.
     * Does NOT block the thread — Spring Retry handles scheduling between attempts.
     */
    @Retryable(
        retryFor = DataAccessException.class,
        maxAttempts = 3,
        backoff = @Backoff(delay = 500, multiplier = 2)
    )
    public void applyDecayToUser(User user) {
        int currentScore = user.getShipScore();
        int decayedScore = (int) Math.floor(currentScore * 0.9);

        user.setShipScore(decayedScore);

        int oldLevel = user.getLevel();
        RankTier oldRank = user.getRank();

        int newLevel = shipScoreService.calculateLevel(decayedScore);
        user.setLevel(newLevel);

        if (newLevel != oldLevel) {
            RankTier newRank = shipScoreService.calculateRank(newLevel);
            user.setRank(newRank);
            if (newRank != oldRank) {
                user.setRankUpdatedAt(Instant.now());
            }
        }

        // Do NOT modify lastShipActivityAt — decay is not an activity
        userRepository.save(user);

        log.debug("Applied decay to user {}: {} -> {} (level {} -> {})",
                user.getId(), currentScore, decayedScore, oldLevel, newLevel);
    }
}
