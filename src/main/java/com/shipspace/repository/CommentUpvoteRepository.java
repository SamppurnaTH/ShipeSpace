package com.shipspace.repository;

import com.shipspace.model.CommentUpvote;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository interface for CommentUpvote document operations.
 * Provides CRUD operations and custom query methods for comment upvote tracking.
 */
@Repository
public interface CommentUpvoteRepository extends MongoRepository<CommentUpvote, String> {

    /**
     * Find an upvote by user ID and comment ID.
     * Used to check if a user has already upvoted a comment.
     *
     * @param userId the ID of the user
     * @param commentId the ID of the comment
     * @return Optional containing the upvote if found
     */
    Optional<CommentUpvote> findByUserIdAndCommentId(String userId, String commentId);

    /**
     * Check if a user has upvoted a specific comment.
     *
     * @param userId the ID of the user
     * @param commentId the ID of the comment
     * @return true if the user has upvoted the comment
     */
    boolean existsByUserIdAndCommentId(String userId, String commentId);

    /**
     * Count the number of upvotes for a specific comment.
     *
     * @param commentId the ID of the comment
     * @return the count of upvotes
     */
    long countByCommentId(String commentId);

    /**
     * Delete an upvote by user ID and comment ID.
     * Used when a user removes their upvote.
     *
     * @param userId the ID of the user
     * @param commentId the ID of the comment
     */
    void deleteByUserIdAndCommentId(String userId, String commentId);
}
