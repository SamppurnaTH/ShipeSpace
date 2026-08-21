package com.shipspace.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.CompoundIndexDefinition;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.IndexOperations;
import org.springframework.data.mongodb.core.index.PartialIndexFilter;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Explicit index creation at startup.
 *
 * auto-index-creation is disabled in production, so all indexes must be
 * created here. This runs once on ApplicationReadyEvent and is idempotent —
 * MongoDB ignores ensureIndex calls for indexes that already exist.
 *
 * Indexes created here supplement the @Indexed / @CompoundIndex annotations
 * on model classes (which are only applied when auto-index-creation=true).
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Profile("!test")
public class MongoIndexInitializer {

    public static final String REPORT_STATUS_CREATED_AT_INDEX = "status_createdAt_idx";
    public static final String AUDIT_EVENT_TYPE_TIMESTAMP_INDEX = "eventType_timestamp_idx";
    public static final String AUDIT_TIMESTAMP_INDEX = "audit_timestamp_desc";

    public static final java.util.Set<String> INDEXED_COLLECTIONS = java.util.Set.of(
            "users",
            "ships",
            "projects",
            "likes",
            "comments",
            "comment_upvotes",
            "following",
            "notifications",
            "behavioral_signals",
            "score_event_log",
            "content_scores",
            "collaboration_requests",
            "reports",
            "audit_logs"
    );

    private final MongoTemplate mongoTemplate;

    @EventListener(ApplicationReadyEvent.class)
    public void createIndexes() {
        log.info("Creating MongoDB indexes...");
        try {
            createUserIndexes();
            createShipIndexes();
            createProjectIndexes();
            createLikeIndexes();
            createCommentIndexes();
            createCommentUpvoteIndexes();
            createFollowingIndexes();
            createNotificationIndexes();
            createBehavioralSignalIndexes();
            createScoreEventLogIndexes();
            createContentScoreIndexes();
            createCollaborationRequestIndexes();
            createReportIndexes();
            createAuditLogIndexes();
            log.info("MongoDB index creation completed successfully");
        } catch (Exception e) {
            log.error("Failed to create MongoDB indexes: {}", e.getMessage(), e);
            // Don't fail startup — indexes can be created manually if needed
        }
    }

    private void createUserIndexes() {
        // Drop old auto-created index that conflicts with our named one
        try {
            mongoTemplate.getDb().getCollection("users").dropIndex("githubId_1");
        } catch (Exception ignored) {}
        IndexOperations ops = mongoTemplate.indexOps("users");
        ops.ensureIndex(new Index().on("githubId", Sort.Direction.ASC).unique().named("githubId_unique"));
        ops.ensureIndex(new Index().on("username", Sort.Direction.ASC).unique().named("username_unique"));
        ops.ensureIndex(new Index().on("shipScore", Sort.Direction.DESC).named("shipScore_desc"));
        ops.ensureIndex(new Index().on("rank", Sort.Direction.ASC).named("rank_asc"));
        ops.ensureIndex(new Index().on("lastShipActivityAt", Sort.Direction.ASC).named("lastShipActivityAt_asc"));
        ops.ensureIndex(new Index().on("createdAt", Sort.Direction.ASC).named("createdAt_asc"));
        // Text index for search — covers username and bio
        mongoTemplate.getDb().getCollection("users").createIndex(
                new org.bson.Document("username", "text").append("bio", "text"),
                new com.mongodb.client.model.IndexOptions().name("users_text_search")
        );
    }

    private void createShipIndexes() {
        IndexOperations ops = mongoTemplate.indexOps("ships");
        ops.ensureIndex(new Index().on("userId", Sort.Direction.ASC).named("userId_asc"));
        ops.ensureIndex(new Index().on("projectId", Sort.Direction.ASC).named("projectId_asc"));
        ops.ensureIndex(new Index().on("trendingScore", Sort.Direction.DESC).named("trendingScore_desc"));
        ops.ensureIndex(new Index().on("createdAt", Sort.Direction.DESC).named("createdAt_desc"));
        ops.ensureIndex(new Index().on("editWindowExpires", Sort.Direction.ASC).named("editWindowExpires_asc"));
        // Compound index for following feed query: userId + createdAt
        ops.ensureIndex(new CompoundIndexDefinition(
                new org.bson.Document("userId", 1).append("createdAt", -1))
                .named("userId_createdAt_idx"));
        // Text index for search — covers ship content
        mongoTemplate.getDb().getCollection("ships").createIndex(
                new org.bson.Document("content", "text"),
                new com.mongodb.client.model.IndexOptions().name("ships_text_search")
        );
    }

    private void createProjectIndexes() {
        IndexOperations ops = mongoTemplate.indexOps("projects");
        ops.ensureIndex(new Index().on("userId", Sort.Direction.ASC).named("userId_asc"));
        ops.ensureIndex(new Index().on("createdAt", Sort.Direction.DESC).named("createdAt_desc"));
        ops.ensureIndex(new Index().on("techTags", Sort.Direction.ASC).named("techTags_asc"));
    }

    private void createLikeIndexes() {
        IndexOperations ops = mongoTemplate.indexOps("likes");
        ops.ensureIndex(new Index().on("userId", Sort.Direction.ASC).named("userId_asc"));
        ops.ensureIndex(new Index().on("shipId", Sort.Direction.ASC).named("shipId_asc"));
        ops.ensureIndex(new CompoundIndexDefinition(
                new org.bson.Document("userId", 1).append("shipId", 1))
                .unique().named("user_ship_unique"));
    }

    private void createCommentIndexes() {
        IndexOperations ops = mongoTemplate.indexOps("comments");
        ops.ensureIndex(new Index().on("userId", Sort.Direction.ASC).named("userId_asc"));
        ops.ensureIndex(new Index().on("shipId", Sort.Direction.ASC).named("shipId_asc"));
        ops.ensureIndex(new Index().on("createdAt", Sort.Direction.DESC).named("createdAt_desc"));
    }

    private void createCommentUpvoteIndexes() {
        IndexOperations ops = mongoTemplate.indexOps("comment_upvotes");
        ops.ensureIndex(new CompoundIndexDefinition(
                new org.bson.Document("userId", 1).append("commentId", 1))
                .unique().named("user_comment_unique"));
    }

    private void createFollowingIndexes() {
        IndexOperations ops = mongoTemplate.indexOps("following");
        ops.ensureIndex(new Index().on("followerId", Sort.Direction.ASC).named("followerId_asc"));
        ops.ensureIndex(new Index().on("followeeId", Sort.Direction.ASC).named("followeeId_asc"));
        ops.ensureIndex(new CompoundIndexDefinition(
                new org.bson.Document("followerId", 1).append("followeeId", 1))
                .unique().named("follower_followee_unique"));
    }

    private void createNotificationIndexes() {
        IndexOperations ops = mongoTemplate.indexOps("notifications");
        ops.ensureIndex(new Index().on("userId", Sort.Direction.ASC).named("userId_asc"));
        ops.ensureIndex(new CompoundIndexDefinition(
                new org.bson.Document("userId", 1).append("read", 1).append("createdAt", -1))
                .named("userId_read_created_idx"));
        // TTL index — auto-expire after 90 days
        ops.ensureIndex(new Index().on("createdAt", Sort.Direction.ASC)
                .expire(Duration.ofDays(90)).named("createdAt_ttl"));
    }

    private void createBehavioralSignalIndexes() {
        IndexOperations ops = mongoTemplate.indexOps("behavioral_signals");
        ops.ensureIndex(new Index().on("userId", Sort.Direction.ASC).named("userId_asc"));
        ops.ensureIndex(new Index().on("contentId", Sort.Direction.ASC).named("contentId_asc"));
        ops.ensureIndex(new CompoundIndexDefinition(
                new org.bson.Document("userId", 1).append("timestamp", -1))
                .named("user_timestamp_idx"));
        // Compound index for quality score calculation
        ops.ensureIndex(new CompoundIndexDefinition(
                new org.bson.Document("contentId", 1).append("signalType", 1))
                .named("contentId_signalType_idx"));
        // TTL index — auto-expire after 90 days
        ops.ensureIndex(new Index().on("timestamp", Sort.Direction.ASC)
                .expire(Duration.ofDays(90)).named("timestamp_ttl"));
    }

    private void createScoreEventLogIndexes() {
        IndexOperations ops = mongoTemplate.indexOps("score_event_log");
        ops.ensureIndex(new CompoundIndexDefinition(
                new org.bson.Document("userId", 1).append("eventType", 1).append("entityId", 1))
                .unique().named("unique_event"));
        ops.ensureIndex(new CompoundIndexDefinition(
                new org.bson.Document("userId", 1).append("createdAt", -1).append("eventType", 1))
                .named("user_date_type_idx"));
        // TTL index — auto-expire after 90 days
        ops.ensureIndex(new Index().on("createdAt", Sort.Direction.ASC)
                .expire(Duration.ofDays(90)).named("createdAt_ttl"));
    }

    private void createContentScoreIndexes() {
        IndexOperations ops = mongoTemplate.indexOps("content_scores");
        ops.ensureIndex(new Index().on("contentId", Sort.Direction.ASC).unique().named("contentId_unique"));
        ops.ensureIndex(new Index().on("ageCohort", Sort.Direction.ASC).named("ageCohort_asc"));
        ops.ensureIndex(new CompoundIndexDefinition(
                new org.bson.Document("contentId", 1).append("updatedAt", -1))
                .named("content_updated_idx"));
    }

    private void createCollaborationRequestIndexes() {
        IndexOperations ops = mongoTemplate.indexOps("collaboration_requests");
        ops.ensureIndex(new Index().on("requesterId", Sort.Direction.ASC).named("requesterId_asc"));
        ops.ensureIndex(new Index().on("projectId", Sort.Direction.ASC).named("projectId_asc"));
        ops.ensureIndex(new Index().on("ownerId", Sort.Direction.ASC).named("ownerId_asc"));
        ops.ensureIndex(new CompoundIndexDefinition(
                new org.bson.Document("requesterId", 1).append("projectId", 1))
                .unique().named("requester_project_unique"));
    }

    private void createReportIndexes() {
        IndexOperations ops = mongoTemplate.indexOps("reports");
        ops.ensureIndex(new Index().on("reporterId", Sort.Direction.ASC).named("reporterId_asc"));
        ops.ensureIndex(new Index().on("targetId", Sort.Direction.ASC).named("targetId_asc"));
        ops.ensureIndex(new CompoundIndexDefinition(
                new org.bson.Document("status", 1).append("createdAt", -1))
                .named(REPORT_STATUS_CREATED_AT_INDEX));
    }

    private void createAuditLogIndexes() {
        IndexOperations ops = mongoTemplate.indexOps("audit_logs");
        ops.ensureIndex(new CompoundIndexDefinition(
                new org.bson.Document("userId", 1).append("eventType", 1))
                .named("userId_eventType_idx"));
        ops.ensureIndex(new CompoundIndexDefinition(
                new org.bson.Document("eventType", 1).append("timestamp", -1))
                .named(AUDIT_EVENT_TYPE_TIMESTAMP_INDEX));
        ops.ensureIndex(new Index().on("timestamp", Sort.Direction.DESC).named(AUDIT_TIMESTAMP_INDEX));
        // TTL index — auto-expire after 365 days
        ops.ensureIndex(new Index().on("timestamp", Sort.Direction.ASC)
                .expire(Duration.ofDays(365)).named("timestamp_ttl"));
    }
}
