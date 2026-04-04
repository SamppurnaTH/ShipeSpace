package com.shipspace.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;

/**
 * Handles cascade deletion of orphaned documents when ships or projects are deleted.
 *
 * MongoDB has no foreign-key constraints, so we must clean up manually.
 * All deletes use MongoTemplate for bulk operations — no N individual removes.
 *
 * Collections cleaned per ship deletion:
 *   likes, comments, comment_upvotes, notifications (targetId), behavioral_signals, content_scores
 *
 * Collections cleaned per project deletion (in addition to ships):
 *   collaboration_requests
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CascadeDeleteService {

    private final MongoTemplate mongoTemplate;

    /**
     * Delete all documents referencing the given shipId.
     * Called after a ship document is deleted.
     */
    public void deleteShipCascade(String shipId) {
        deleteShipsCascade(List.of(shipId));
    }

    /**
     * Batch cascade delete for multiple ships (used when a project is deleted).
     */
    public void deleteShipsCascade(Collection<String> shipIds) {
        if (shipIds == null || shipIds.isEmpty()) return;

        Query byShipId = new Query(Criteria.where("shipId").in(shipIds));
        Query byContentId = new Query(Criteria.where("contentId").in(shipIds));
        Query byTargetId = new Query(Criteria.where("targetId").in(shipIds));

        long likes = mongoTemplate.remove(byShipId, "likes").getDeletedCount();
        long comments = mongoTemplate.remove(byShipId, "comments").getDeletedCount();
        long upvotes = mongoTemplate.remove(byShipId, "comment_upvotes").getDeletedCount();
        long notifications = mongoTemplate.remove(byTargetId, "notifications").getDeletedCount();
        long signals = mongoTemplate.remove(byContentId, "behavioral_signals").getDeletedCount();
        long scores = mongoTemplate.remove(byContentId, "content_scores").getDeletedCount();

        log.info("Cascade delete for {} ships: likes={}, comments={}, upvotes={}, " +
                        "notifications={}, signals={}, scores={}",
                shipIds.size(), likes, comments, upvotes, notifications, signals, scores);
    }

    /**
     * Delete all documents referencing the given projectId.
     * Called after a project and its ships are deleted.
     */
    public void deleteProjectCascade(String projectId, Collection<String> shipIds) {
        // Clean up ship-level orphans first
        deleteShipsCascade(shipIds);

        // Clean up project-level orphans
        Query byProjectId = new Query(Criteria.where("projectId").is(projectId));
        long collabRequests = mongoTemplate.remove(byProjectId, "collaboration_requests").getDeletedCount();

        log.info("Cascade delete for project {}: collaboration_requests={}", projectId, collabRequests);
    }
}
