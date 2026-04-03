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
     * Atomically increments likeCount by delta (+1 or -1) and returns the updated Ship.
     * Uses findAndModify with $inc — single round-trip, no race window.
     */
    public Ship incrementLikeCount(String shipId, int delta) {
        Query query = new Query(Criteria.where("_id").is(shipId));
        Update update = new Update().inc("likeCount", delta);
        FindAndModifyOptions options = FindAndModifyOptions.options().returnNew(true);
        Ship updated = mongoTemplate.findAndModify(query, update, options, Ship.class);
        if (updated == null) {
            throw new com.shipspace.exception.ResourceNotFoundException("Ship", shipId);
        }
        return updated;
    }

    /**
     * Atomically increments commentCount by delta (+1 or -1) and returns the updated Ship.
     */
    public Ship incrementCommentCount(String shipId, int delta) {
        Query query = new Query(Criteria.where("_id").is(shipId));
        Update update = new Update().inc("commentCount", delta);
        FindAndModifyOptions options = FindAndModifyOptions.options().returnNew(true);
        Ship updated = mongoTemplate.findAndModify(query, update, options, Ship.class);
        if (updated == null) {
            throw new com.shipspace.exception.ResourceNotFoundException("Ship", shipId);
        }
        return updated;
    }
}
