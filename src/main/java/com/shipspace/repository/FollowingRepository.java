package com.shipspace.repository;

import com.shipspace.model.Following;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for Following document operations.
 * Provides CRUD operations and custom query methods for social following relationships.
 */
@Repository
public interface FollowingRepository extends MongoRepository<Following, String> {

    /**
     * Find a following relationship between two users.
     * Used to check if a user is following another user.
     *
     * @param followerId the ID of the follower
     * @param followeeId the ID of the user being followed
     * @return Optional containing the following relationship if it exists
     */
    Optional<Following> findByFollowerIdAndFolloweeId(String followerId, String followeeId);

    /**
     * Find all users that a specific user is following.
     * Returns paginated results with user profile information.
     *
     * @param followerId the ID of the follower
     * @param pageable pagination and sorting parameters
     * @return Page of following relationships
     */
    Page<Following> findByFollowerId(String followerId, Pageable pageable);

    /**
     * Find all users following a specific user.
     * Returns paginated results with follower profile information.
     *
     * @param followeeId the ID of the user being followed
     * @param pageable pagination and sorting parameters
     * @return Page of following relationships
     */
    Page<Following> findByFolloweeId(String followeeId, Pageable pageable);

    /**
     * Get list of user IDs that a specific user is following.
     * Used for feed generation and filtering.
     *
     * @param followerId the ID of the follower
     * @return List of followee user IDs
     */
    List<Following> findByFollowerId(String followerId);

    /**
     * Count the number of users a specific user is following.
     *
     * @param followerId the ID of the follower
     * @return the count of users being followed
     */
    long countByFollowerId(String followerId);

    /**
     * Count the number of followers for a specific user.
     *
     * @param followeeId the ID of the user being followed
     * @return the count of followers
     */
    long countByFolloweeId(String followeeId);

    /**
     * Check if a following relationship exists between two users.
     *
     * @param followerId the ID of the follower
     * @param followeeId the ID of the user being followed
     * @return true if the relationship exists
     */
    boolean existsByFollowerIdAndFolloweeId(String followerId, String followeeId);

    /**
     * Delete a following relationship between two users.
     * Used for unfollowing operations.
     *
     * @param followerId the ID of the follower
     * @param followeeId the ID of the user being followed
     */
    void deleteByFollowerIdAndFolloweeId(String followerId, String followeeId);
}
