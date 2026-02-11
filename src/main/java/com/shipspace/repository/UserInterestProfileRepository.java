package com.shipspace.repository;

import com.shipspace.model.UserInterestProfile;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository interface for UserInterestProfile document operations.
 * Provides CRUD operations and custom query methods for user interest profile management.
 * 
 * UserInterestProfile tracks user interests derived from behavioral signals
 * (likes, comments, views, follows) and is used by the personalization algorithm
 * to generate relevant content recommendations.
 */
@Repository
public interface UserInterestProfileRepository extends MongoRepository<UserInterestProfile, String> {

    /**
     * Find a user interest profile by user ID.
     * Used to retrieve a user's learned interests for personalized feed generation.
     * 
     * The profile contains:
     * - Tech tag interest scores (0.0 to 1.0)
     * - Followed builders
     * - Total behavioral signals count
     * - Timestamps for decay calculations
     * 
     * @param userId the ID of the user
     * @return Optional containing the user interest profile if found
     */
    Optional<UserInterestProfile> findByUserId(String userId);
}
