package com.shipspace.service;

import com.shipspace.config.ScheduledJobMetrics;
import com.shipspace.model.RankTier;
import com.shipspace.model.User;
import com.shipspace.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * Scheduled job that applies score decay to inactive users.
 * Runs daily at 2:00 AM UTC.
 * Uses DistributedLockService for consistent locking across all scheduled jobs.
 */
@Component
@RequiredArgsConstructor
public class ScoreDecayJob {

    private static final Logger log = LoggerFactory.getLogger(ScoreDecayJob.class);
    private static final Duration LOCK_TTL = Duration.ofMinutes(30);
    private static final int BATCH_SIZE = 200;

    private final UserRepository userRepository;
    private final ShipScoreService shipScoreService;
    private final MongoTemplate mongoTemplate;
    private final DistributedLockService distributedLockService;
    private final ScheduledJobMetrics scheduledJobMetrics;

    @Scheduled(cron = "0 0 2 * * *")
    public void applyScoreDecay() {
        distributedLockService.runWithLock("score-decay-job", LOCK_TTL, () -> {
            runDecay();
            scheduledJobMetrics.recordSuccess("score-decay-job");
        });
    }

    private void runDecay() {
        log.info("Starting score decay job");
        Instant sixtyDaysAgo = Instant.now().minus(60, ChronoUnit.DAYS);

        int page = 0;
        int successCount = 0;
        int failureCount = 0;

        Page<User> batch;
        do {
            // Paginated query — never loads the full collection
            batch = userRepository.findByLastShipActivityAtBefore(
                    sixtyDaysAgo, PageRequest.of(page, BATCH_SIZE));

            List<User> dirty = new ArrayList<>();
            for (User user : batch.getContent()) {
                try {
                    User updated = computeDecay(user);
                    if (updated != null) {
                        dirty.add(updated);
                        successCount++;
                    }
                } catch (Exception e) {
                    log.error("Failed to compute decay for user {}: {}", user.getId(), e.getMessage());
                    failureCount++;
                }
            }

            if (!dirty.isEmpty()) {
                bulkWriteDecay(dirty);
            }

            page++;
        } while (batch.hasNext());

        log.info("Score decay job completed: {} successes, {} failures across {} pages",
                successCount, failureCount, page);
    }

    /**
     * Compute decayed values for a user. Returns null if score is already 0.
     */
    private User computeDecay(User user) {
        int currentScore = user.getShipScore();
        if (currentScore <= 0) return null;

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
        // Do NOT touch lastShipActivityAt — decay is not an activity
        return user;
    }

    /**
     * Bulk-write decayed users — one MongoDB round-trip per batch.
     */
    private void bulkWriteDecay(List<User> users) {
        try {
            BulkOperations bulkOps = mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, User.class);
            for (User user : users) {
                Query q = new Query(Criteria.where("_id").is(user.getId()));
                Update u = new Update()
                        .set("shipScore", user.getShipScore())
                        .set("level", user.getLevel())
                        .set("rank", user.getRank());
                if (user.getRankUpdatedAt() != null) {
                    u.set("rankUpdatedAt", user.getRankUpdatedAt());
                }
                bulkOps.updateOne(q, u);
            }
            bulkOps.execute();
            log.debug("Bulk-wrote decay for {} users", users.size());
        } catch (DataAccessException e) {
            log.error("Bulk write failed for decay batch, falling back to individual saves: {}", e.getMessage());
            users.forEach(user -> {
                try {
                    userRepository.save(user);
                } catch (Exception ex) {
                    log.error("Individual save failed for user {}: {}", user.getId(), ex.getMessage());
                }
            });
        }
    }
}
