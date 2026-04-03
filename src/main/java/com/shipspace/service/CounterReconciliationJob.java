package com.shipspace.service;

import com.shipspace.model.Ship;
import com.shipspace.repository.CommentRepository;
import com.shipspace.repository.LikeRepository;
import com.shipspace.repository.ShipRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Nightly job that reconciles denormalized likeCount and commentCount on Ship documents
 * against the actual Like and Comment collections.
 *
 * Runs at 3 AM daily. Idempotent — only writes ships whose counts are wrong.
 * Uses bulk unordered writes to minimize round-trips.
 *
 * Trade-off: counts may drift by up to 24 hours between runs.
 * For tighter consistency, reduce the cron interval or trigger on-demand.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CounterReconciliationJob {

    private final ShipRepository shipRepository;
    private final LikeRepository likeRepository;
    private final CommentRepository commentRepository;
    private final MongoTemplate mongoTemplate;

    @Scheduled(cron = "0 0 3 * * *")
    public void reconcileCounters() {
        log.info("Starting counter reconciliation job");
        int page = 0;
        final int batchSize = 200;
        int fixedCount = 0;

        Page<Ship> batch;
        do {
            batch = shipRepository.findAll(PageRequest.of(page, batchSize));
            BulkOperations bulkOps = mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, Ship.class);
            boolean hasDirty = false;

            for (Ship ship : batch.getContent()) {
                long actualLikes = likeRepository.countByShipId(ship.getId());
                long actualComments = commentRepository.countByShipId(ship.getId());

                boolean likeDrift = ship.getLikeCount() == null || ship.getLikeCount() != actualLikes;
                boolean commentDrift = ship.getCommentCount() == null || ship.getCommentCount() != actualComments;

                if (likeDrift || commentDrift) {
                    Query q = new Query(Criteria.where("_id").is(ship.getId()));
                    Update u = new Update()
                            .set("likeCount", (int) actualLikes)
                            .set("commentCount", (int) actualComments);
                    bulkOps.updateOne(q, u);
                    hasDirty = true;
                    fixedCount++;
                }
            }

            if (hasDirty) {
                bulkOps.execute();
            }
            page++;
        } while (batch.hasNext());

        log.info("Counter reconciliation completed. Fixed {} ships.", fixedCount);
    }
}
