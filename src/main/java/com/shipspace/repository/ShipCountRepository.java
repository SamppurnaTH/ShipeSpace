package com.shipspace.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

import com.shipspace.model.Ship;

/**
 * Provides atomic counter operations on Ship documents using MongoDB $inc.
 * Replaces read-modify-write patterns that are vulnerable to race conditions.
 */
@Repository
@RequiredArgsConstructor
public class ShipCountRepository {

    private final MongoTemplate mongoTemplate;

    /**
     * Atomically increments likeCount by delta and returns the updated Ship.
     * Floors at 0 if the result would go negative.
     */
    public Ship incrementLikeCount(String shipId, int delta) {
        Query query = new Query(Criteria.where("_id").is(shipId));
        FindAndModifyOptions options = FindAndModifyOptions.options().returnNew(true);
        Ship updated = mongoTemplate.findAndModify(query,
                new Update().inc("likeCount", delta), options, Ship.class);
        if (updated == null) {
            throw new com.shipspace.exception.ResourceNotFoundException("Ship", shipId);
        }
        if (updated.getLikeCount() != null && updated.getLikeCount() < 0) {
            mongoTemplate.updateFirst(query, new Update().set("likeCount", 0), Ship.class);
            updated.setLikeCount(0);
        }
        return updated;
    }

    /**
     * Atomically increments commentCount by delta and returns the updated Ship.
     * Floors at 0 if the result would go negative.
     */
    public Ship incrementCommentCount(String shipId, int delta) {
        Query query = new Query(Criteria.where("_id").is(shipId));
        FindAndModifyOptions options = FindAndModifyOptions.options().returnNew(true);
        Ship updated = mongoTemplate.findAndModify(query,
                new Update().inc("commentCount", delta), options, Ship.class);
        if (updated == null) {
            throw new com.shipspace.exception.ResourceNotFoundException("Ship", shipId);
        }
        if (updated.getCommentCount() != null && updated.getCommentCount() < 0) {
            mongoTemplate.updateFirst(query, new Update().set("commentCount", 0), Ship.class);
            updated.setCommentCount(0);
        }
        return updated;
    }
}
