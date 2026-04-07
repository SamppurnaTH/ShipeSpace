package com.shipspace.repository;

import com.shipspace.model.Like;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository interface for Like document operations.
 * Provides CRUD operations and custom query methods for like engagement tracking.
 */
@Repository
public interface LikeRepository extends MongoRepository<Like, String> {

    /**
     * Find a like by user and ship.
     * Used to check if a user has already liked a ship.
     *
     * @param userId the ID of the user
     * @param shipId the ID of the ship
     * @return Optional containing the like if it exists
     */
    Optional<Like> findByUserIdAndShipId(String userId, String shipId);

    /**
     * Find all likes for a specific ship.
     * Returns paginated results with user information.
     *
     * @param shipId the ID of the ship
     * @param pageable pagination and sorting parameters
     * @return Page of likes for the ship
     */
    Page<Like> findByShipId(String shipId, Pageable pageable);

    /**
     * Find all likes by a specific user.
     * Used for user activity tracking.
     *
     * @param userId the ID of the user
     * @param pageable pagination and sorting parameters
     * @return Page of likes by the user
     */
    Page<Like> findByUserId(String userId, Pageable pageable);

    /**
     * Count the number of likes for a specific ship.
     *
     * @param shipId the ID of the ship
     * @return the count of likes
     */
    long countByShipId(String shipId);

    /**
     * Check if a user has liked a specific ship.
     *
     * @param userId the ID of the user
     * @param shipId the ID of the ship
     * @return true if the user has liked the ship
     */
    boolean existsByUserIdAndShipId(String userId, String shipId);

    /**
     * Delete a like by user and ship.
     * Returns the number of documents deleted (0 or 1).
     * Callers MUST check the return value before decrementing counters —
     * decrementing when nothing was deleted causes counter drift.
     *
     * @param userId the ID of the user
     * @param shipId the ID of the ship
     * @return number of documents deleted
     */
    long deleteByUserIdAndShipId(String userId, String shipId);

    /**
     * Find all ship IDs liked by a user within a given set.
     * Used for bulk like-status enrichment in feed responses.
     *
     * @param userId  the ID of the user
     * @param shipIds the set of ship IDs to check
     * @return list of Like documents matching the criteria
     */
    java.util.List<Like> findByUserIdAndShipIdIn(String userId, java.util.Collection<String> shipIds);
}
