package com.shipspace.repository;

import com.shipspace.model.Comment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository interface for Comment document operations.
 * Provides CRUD operations and custom query methods for comment engagement tracking.
 */
@Repository
public interface CommentRepository extends MongoRepository<Comment, String> {

    /**
     * Find all comments for a specific ship.
     * Returns paginated results ordered by creation time.
     *
     * @param shipId the ID of the ship
     * @param pageable pagination and sorting parameters
     * @return Page of comments for the ship
     */
    Page<Comment> findByShipId(String shipId, Pageable pageable);

    /**
     * Find all comments by a specific user.
     * Used for user activity tracking.
     *
     * @param userId the ID of the user
     * @param pageable pagination and sorting parameters
     * @return Page of comments by the user
     */
    Page<Comment> findByUserId(String userId, Pageable pageable);

    /**
     * Count the number of comments for a specific ship.
     *
     * @param shipId the ID of the ship
     * @return the count of comments
     */
    long countByShipId(String shipId);

    /**
     * Count the number of comments by a specific user.
     *
     * @param userId the ID of the user
     * @return the count of comments by the user
     */
    long countByUserId(String userId);
}
