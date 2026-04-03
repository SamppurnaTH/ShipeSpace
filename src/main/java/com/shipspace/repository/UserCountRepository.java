package com.shipspace.repository;

import com.shipspace.exception.ResourceNotFoundException;
import com.shipspace.model.User;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

/**
 * Provides atomic counter operations on User documents using MongoDB $inc.
 * Eliminates read-modify-write races on followerCount, followingCount, projectCount.
 */
@Repository
@RequiredArgsConstructor
public class UserCountRepository {

    private final MongoTemplate mongoTemplate;

    /** Atomically increments followerCount on the followee. */
    public void incrementFollowerCount(String userId, int delta) {
        atomicIncrement(userId, "followerCount", delta);
    }

    /** Atomically increments followingCount on the follower. */
    public void incrementFollowingCount(String userId, int delta) {
        atomicIncrement(userId, "followingCount", delta);
    }

    /** Atomically increments projectCount. */
    public void incrementProjectCount(String userId, int delta) {
        atomicIncrement(userId, "projectCount", delta);
    }

    private void atomicIncrement(String userId, String field, int delta) {
        Query query = new Query(Criteria.where("_id").is(userId));
        Update update = new Update().inc(field, delta);
        // We don't need the return value here — fire and forget within the transaction
        mongoTemplate.updateFirst(query, update, User.class);
    }
}
