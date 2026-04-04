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

    /** Atomically increments followerCount on the followee. Floor at 0. */
    public void incrementFollowerCount(String userId, int delta) {
        atomicIncrement(userId, "followerCount", delta);
    }

    /** Atomically increments followingCount on the follower. Floor at 0. */
    public void incrementFollowingCount(String userId, int delta) {
        atomicIncrement(userId, "followingCount", delta);
    }

    /** Atomically increments projectCount. Floor at 0. */
    public void incrementProjectCount(String userId, int delta) {
        atomicIncrement(userId, "projectCount", delta);
    }

    /**
     * Atomic increment with floor-at-zero protection.
     *
     * For decrements (delta < 0) we use a conditional update:
     *   { $inc: { field: delta } } WHERE field >= abs(delta)
     * If the field is already 0, the update is a no-op — no negative counts.
     *
     * For increments (delta > 0) we use a plain $inc — no floor needed.
     */
    private void atomicIncrement(String userId, String field, int delta) {
        Query query = new Query(Criteria.where("_id").is(userId));
        Update update;
        if (delta < 0) {
            // Only decrement if the current value is >= abs(delta) to prevent going negative
            query.addCriteria(Criteria.where(field).gte(Math.abs(delta)));
            update = new Update().inc(field, delta);
        } else {
            update = new Update().inc(field, delta);
        }
        mongoTemplate.updateFirst(query, update, User.class);
    }
}
