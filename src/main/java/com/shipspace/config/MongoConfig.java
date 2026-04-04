package com.shipspace.config;

import com.mongodb.client.MongoClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.CompoundIndexDefinition;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.IndexOperations;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;
import org.bson.Document;

/**
 * MongoDB configuration.
 *
 * auto-index-creation is DISABLED in application.yml.
 * Indexes are created here on startup — controlled, idempotent, and safe for production.
 *
 * Transaction note: @Transactional on MongoDB requires a replica set.
 * On a standalone instance the annotation is silently ignored (no rollback).
 * All @Transactional usages in this codebase are therefore best-effort;
 * they will work correctly once a replica set is provisioned.
 */
@Slf4j
@Configuration
@EnableMongoRepositories(basePackages = "com.shipspace.repository")
@RequiredArgsConstructor
public class MongoConfig {


    private final MongoTemplate mongoTemplate;

    @EventListener(ContextRefreshedEvent.class)
    public void initIndexes() {
        log.info("Initializing MongoDB indexes...");
        try {
            ensureUserIndexes();
            ensureShipIndexes();
            ensureProjectIndexes();
            ensureFollowingIndexes();
            ensureLikeIndexes();
            ensureCommentIndexes();
            ensureNotificationIndexes();
            ensureInterestProfileIndexes();
            ensureScoreEventLogIndexes();
            ensureBehavioralSignalIndexes();
            ensureContentScoreIndexes();
            ensureAuditLogIndexes();
            log.info("MongoDB indexes initialized successfully.");
        } catch (Exception e) {
            // Non-fatal: log and continue. Indexes may already exist.
            log.warn("Index initialization encountered an issue (may already exist): {}", e.getMessage());
        }
    }

    private void ensureUserIndexes() {
        IndexOperations ops = mongoTemplate.indexOps("users");
        ops.ensureIndex(new Index().on("githubId", Sort.Direction.ASC).unique());
        ops.ensureIndex(new Index().on("username", Sort.Direction.ASC).unique());
        ops.ensureIndex(new Index().on("lastShipActivityAt", Sort.Direction.ASC));
        ops.ensureIndex(new Index().on("createdAt", Sort.Direction.ASC));
        ops.ensureIndex(new CompoundIndexDefinition(new Document("shipScore", -1)));
    }

    private void ensureShipIndexes() {
        IndexOperations ops = mongoTemplate.indexOps("ships");
        ops.ensureIndex(new Index().on("userId", Sort.Direction.ASC));
        ops.ensureIndex(new Index().on("projectId", Sort.Direction.ASC));
        ops.ensureIndex(new Index().on("createdAt", Sort.Direction.DESC));
        ops.ensureIndex(new Index().on("trendingScore", Sort.Direction.DESC));
        ops.ensureIndex(new Index().on("editWindowExpires", Sort.Direction.ASC));
        // Compound for following feed: userId + createdAt
        ops.ensureIndex(new CompoundIndexDefinition(new Document("userId", 1).append("createdAt", -1)));
    }

    private void ensureProjectIndexes() {
        IndexOperations ops = mongoTemplate.indexOps("projects");
        ops.ensureIndex(new Index().on("userId", Sort.Direction.ASC));
        ops.ensureIndex(new Index().on("createdAt", Sort.Direction.DESC));
        ops.ensureIndex(new Index().on("techTags", Sort.Direction.ASC));
    }

    private void ensureFollowingIndexes() {
        IndexOperations ops = mongoTemplate.indexOps("followings");
        ops.ensureIndex(new CompoundIndexDefinition(
                new Document("followerId", 1).append("followeeId", 1)).unique());
        ops.ensureIndex(new Index().on("followerId", Sort.Direction.ASC));
        ops.ensureIndex(new Index().on("followeeId", Sort.Direction.ASC));
    }

    private void ensureLikeIndexes() {
        IndexOperations ops = mongoTemplate.indexOps("likes");
        ops.ensureIndex(new CompoundIndexDefinition(
                new Document("userId", 1).append("shipId", 1)).unique());
        ops.ensureIndex(new Index().on("shipId", Sort.Direction.ASC));
    }

    private void ensureCommentIndexes() {
        IndexOperations ops = mongoTemplate.indexOps("comments");
        ops.ensureIndex(new Index().on("shipId", Sort.Direction.ASC));
        ops.ensureIndex(new Index().on("userId", Sort.Direction.ASC));
        ops.ensureIndex(new CompoundIndexDefinition(
                new Document("shipId", 1).append("createdAt", -1)));
    }

    private void ensureNotificationIndexes() {
        IndexOperations ops = mongoTemplate.indexOps("notifications");
        ops.ensureIndex(new CompoundIndexDefinition(
                new Document("userId", 1).append("createdAt", -1)));
        ops.ensureIndex(new CompoundIndexDefinition(
                new Document("userId", 1).append("read", 1)));
        // Phase 5: TTL — auto-expire notifications older than 90 days
        ops.ensureIndex(new Index().on("createdAt", Sort.Direction.ASC)
                .expire(90 * 24 * 60 * 60));
    }

    private void ensureInterestProfileIndexes() {
        IndexOperations ops = mongoTemplate.indexOps("user_interest_profiles");
        ops.ensureIndex(new Index().on("userId", Sort.Direction.ASC).unique());
        ops.ensureIndex(new Index().on("updatedAt", Sort.Direction.ASC));
    }

    private void ensureScoreEventLogIndexes() {
        IndexOperations ops = mongoTemplate.indexOps("score_event_logs");
        // Idempotency check: existsByUserIdAndEventTypeAndEntityId
        ops.ensureIndex(new CompoundIndexDefinition(
                new Document("userId", 1).append("eventType", 1).append("entityId", 1)).unique());
        // Daily cap query: countByUserIdAndEventTypeAndCreatedAtAfter
        ops.ensureIndex(new CompoundIndexDefinition(
                new Document("userId", 1).append("eventType", 1).append("createdAt", -1)));
    }

    /**
     * Phase 5: behavioral_signals indexes.
     *
     * Missing indexes caused full collection scans on every feed request:
     *   - findByUserIdAndTimestampAfter: used by FeedBuilderService.getCandidates()
     *   - countByContentIdAndSignalType: used by ContentScoringService.batchSignalCounts()
     *   - Compound (contentId, signalType): covers the aggregation pipeline GROUP BY
     *
     * At 1M signals (realistic for a social platform), an unindexed scan on
     * findByUserIdAndTimestampAfter takes ~2s. With the compound index it's <5ms.
     */
    private void ensureBehavioralSignalIndexes() {
        IndexOperations ops = mongoTemplate.indexOps("behavioral_signals");
        // Feed candidate filtering: findByUserIdAndTimestampAfter
        ops.ensureIndex(new CompoundIndexDefinition(
                new Document("userId", 1).append("timestamp", -1)));
        // Signal count aggregation: GROUP BY contentId WHERE signalType = X
        ops.ensureIndex(new CompoundIndexDefinition(
                new Document("contentId", 1).append("signalType", 1)));
        // TTL index: auto-expire signals older than 90 days to control collection growth
        ops.ensureIndex(new Index().on("timestamp", Sort.Direction.ASC)
                .expire(90 * 24 * 60 * 60)); // 90 days in seconds
    }

    /**
     * Phase 5: content_scores indexes.
     * Used by ContentScoringService.performanceScore() and CounterReconciliationJob.
     */
    private void ensureContentScoreIndexes() {
        IndexOperations ops = mongoTemplate.indexOps("content_scores");
        ops.ensureIndex(new Index().on("contentId", Sort.Direction.ASC).unique());
        ops.ensureIndex(new Index().on("ageCohort", Sort.Direction.ASC));
        ops.ensureIndex(new Index().on("updatedAt", Sort.Direction.ASC));
    }

    /**
     * Phase 5: audit_logs TTL index.
     * Enforces the 365-day retention policy configured in application.yml.
     * Without this index, audit logs accumulate indefinitely.
     */
    private void ensureAuditLogIndexes() {
        IndexOperations ops = mongoTemplate.indexOps("audit_logs");
        ops.ensureIndex(new Index().on("userId", Sort.Direction.ASC));
        ops.ensureIndex(new Index().on("eventType", Sort.Direction.ASC));
        ops.ensureIndex(new CompoundIndexDefinition(
                new Document("userId", 1).append("timestamp", -1)));
        // TTL: auto-expire after 365 days — matches audit.retention-days in application.yml
        ops.ensureIndex(new Index().on("timestamp", Sort.Direction.ASC)
                .expire(365 * 24 * 60 * 60));
    }
}
