package com.shipspace.service;

import com.shipspace.config.ScheduledJobMetrics;
import com.shipspace.repository.ShipRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Nightly job that reconciles denormalized likeCount and commentCount on Ship documents.
 *
 * Phase 2 rewrite: replaced the previous O(N×2) approach (one countByShipId query per
 * ship for likes, one for comments) with two MongoDB aggregation pipelines that compute
 * ALL counts in a single server-side pass each. For 10K ships this reduces the number
 * of MongoDB round-trips from ~20,000 to 2 + one bulk write.
 *
 * Pipeline:
 *   likes collection  → $group by shipId → count → map<shipId, likeCount>
 *   comments collection → $group by shipId → count → map<shipId, commentCount>
 *   ships collection  → compare denormalized vs actual → bulk update drifted docs
 *
 * Runs at 3 AM daily. Idempotent — only writes ships whose counts are wrong.
 * Distributed lock prevents duplicate execution across instances.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CounterReconciliationJob {

    private final ShipRepository shipRepository;
    private final MongoTemplate mongoTemplate;
    private final DistributedLockService lockService;
    private final ScheduledJobMetrics scheduledJobMetrics;

    @Scheduled(cron = "0 0 3 * * *")
    public void reconcileCounters() {
        lockService.runWithLock("counter-reconciliation", Duration.ofMinutes(60), () -> {
            runReconciliation();
            scheduledJobMetrics.recordSuccess("counter-reconciliation");
        });
    }

    private void runReconciliation() {
        log.info("Starting counter reconciliation job (aggregation pipeline mode)");

        // Phase 2: Two aggregation pipelines replace N×2 individual count queries.
        // Each pipeline groups all documents by shipId and counts them server-side.
        Map<String, Long> likeCounts = aggregateCounts("likes", "shipId");
        Map<String, Long> commentCounts = aggregateCounts("comments", "shipId");

        log.info("Aggregated counts: {} ships have likes, {} ships have comments",
                likeCounts.size(), commentCounts.size());

        // Collect all shipIds that appear in either map
        java.util.Set<String> allShipIds = new java.util.HashSet<>();
        allShipIds.addAll(likeCounts.keySet());
        allShipIds.addAll(commentCounts.keySet());

        // Also scan ships that have non-zero denormalized counts but zero actual counts
        // (i.e., all likes/comments were deleted — they won't appear in the aggregation maps)
        int page = 0;
        final int batchSize = 500;
        org.springframework.data.domain.Page<com.shipspace.model.Ship> batch;
        int fixedCount = 0;

        do {
            batch = shipRepository.findAll(
                    org.springframework.data.domain.PageRequest.of(page, batchSize));

            // Collect ships that need updating in this batch
            List<Document> updates = new java.util.ArrayList<>();
            for (com.shipspace.model.Ship ship : batch.getContent()) {
                long actualLikes = likeCounts.getOrDefault(ship.getId(), 0L);
                long actualComments = commentCounts.getOrDefault(ship.getId(), 0L);

                int storedLikes = ship.getLikeCount() != null ? ship.getLikeCount() : 0;
                int storedComments = ship.getCommentCount() != null ? ship.getCommentCount() : 0;

                if (storedLikes != actualLikes || storedComments != actualComments) {
                    updates.add(new Document("_id", ship.getId())
                            .append("likeCount", (int) actualLikes)
                            .append("commentCount", (int) actualComments));
                    fixedCount++;
                }
            }

            if (!updates.isEmpty()) {
                bulkUpdateCounts(updates);
            }
            page++;
        } while (batch.hasNext());

        log.info("Counter reconciliation completed. Fixed {} ships.", fixedCount);
    }

    /**
     * Aggregate counts from a collection grouped by a field.
     * Returns a map of fieldValue → count.
     *
     * Single aggregation pipeline replaces N individual count queries.
     * Performance: O(collection_size) server-side vs O(N × round_trips) before.
     */
    @SuppressWarnings("unchecked")
    private Map<String, Long> aggregateCounts(String collection, String groupField) {
        try {
            Aggregation agg = Aggregation.newAggregation(
                    Aggregation.match(Criteria.where(groupField).exists(true).ne(null)),
                    Aggregation.group(groupField).count().as("cnt")
            );
            AggregationResults<Map> results = mongoTemplate.aggregate(agg, collection, Map.class);

            Map<String, Long> counts = new java.util.HashMap<>();
            for (Map<?, ?> row : results.getMappedResults()) {
                String id = (String) row.get("_id");
                long cnt = row.get("cnt") instanceof Number n ? n.longValue() : 0L;
                if (id != null) counts.put(id, cnt);
            }
            return counts;
        } catch (Exception e) {
            log.error("Aggregation failed for collection '{}': {}", collection, e.getMessage());
            return java.util.Collections.emptyMap();
        }
    }

    /**
     * Bulk-update likeCount and commentCount for a batch of ships.
     * Uses unordered bulk writes — one MongoDB round-trip for the entire batch.
     */
    private void bulkUpdateCounts(List<Document> updates) {
        try {
            com.mongodb.client.MongoCollection<Document> collection =
                    mongoTemplate.getCollection("ships");
            com.mongodb.client.model.BulkWriteOptions options =
                    new com.mongodb.client.model.BulkWriteOptions().ordered(false);

            List<com.mongodb.client.model.WriteModel<Document>> writeModels = new java.util.ArrayList<>();
            for (Document update : updates) {
                String id = update.getString("_id");
                int likeCount = update.getInteger("likeCount");
                int commentCount = update.getInteger("commentCount");
                writeModels.add(new com.mongodb.client.model.UpdateOneModel<>(
                        new Document("_id", id),
                        new Document("$set", new Document("likeCount", likeCount)
                                .append("commentCount", commentCount))
                ));
            }
            collection.bulkWrite(writeModels, options);
            log.debug("Bulk-updated {} ships in reconciliation", updates.size());
        } catch (Exception e) {
            log.error("Bulk update failed in reconciliation: {}", e.getMessage());
            // Fallback: individual updates
            for (Document update : updates) {
                try {
                    Query q = new Query(Criteria.where("_id").is(update.getString("_id")));
                    Update u = new Update()
                            .set("likeCount", update.getInteger("likeCount"))
                            .set("commentCount", update.getInteger("commentCount"));
                    mongoTemplate.updateFirst(q, u, com.shipspace.model.Ship.class);
                } catch (Exception ex) {
                    log.error("Individual update failed for ship {}: {}", update.getString("_id"), ex.getMessage());
                }
            }
        }
    }
}
