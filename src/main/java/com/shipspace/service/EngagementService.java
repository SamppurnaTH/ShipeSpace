package com.shipspace.service;

import com.shipspace.dto.CommentCreateRequest;
import com.shipspace.dto.CommentResponse;
import com.shipspace.dto.CommentUpvoteToggleResponse;
import com.shipspace.dto.LikeResponse;
import com.shipspace.dto.LikeToggleResponse;
import com.shipspace.exception.ResourceNotFoundException;
import com.shipspace.model.Comment;
import com.shipspace.model.CommentUpvote;
import com.shipspace.model.Like;
import com.shipspace.model.ScoreEventType;
import com.shipspace.model.Ship;
import com.shipspace.model.User;
import com.shipspace.repository.CommentRepository;
import com.shipspace.repository.CommentUpvoteRepository;
import com.shipspace.repository.LikeRepository;
import com.shipspace.repository.ShipRepository;
import com.shipspace.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

/**
 * Service for managing engagement operations (likes and comments).
 * Handles like toggling with duplicate prevention and race condition handling,
 * and comment creation with validation and pagination.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EngagementService {

    private final LikeRepository likeRepository;
    private final CommentRepository commentRepository;
    private final CommentUpvoteRepository commentUpvoteRepository;
    private final ShipRepository shipRepository;
    private final UserRepository userRepository;
    private final CommentQualificationService commentQualificationService;
    private final ShipScoreService shipScoreService;

    /**
     * Toggle like state for a ship.
     * If the user has already liked the ship, the like is removed.
     * If the user has not liked the ship, a like is added.
     * Handles race conditions using database-level unique constraints.
     *
     * @param userId the ID of the user toggling the like
     * @param shipId the ID of the ship to like/unlike
     * @return LikeToggleResponse indicating the new state
     * @throws ResourceNotFoundException if the ship or user does not exist
     */
    @Transactional
    public LikeToggleResponse toggleLike(String userId, String shipId) {
        log.debug("Toggling like for user {} on ship {}", userId, shipId);

        // Verify ship exists
        Ship ship = shipRepository.findById(shipId)
                .orElseThrow(() -> new ResourceNotFoundException("Ship not found with id: " + shipId));

        // Verify user exists
        userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));

        // Check if like already exists
        Optional<Like> existingLike = likeRepository.findByUserIdAndShipId(userId, shipId);

        if (existingLike.isPresent()) {
            // Unlike: Remove the like and decrement count
            likeRepository.deleteByUserIdAndShipId(userId, shipId);
            ship.setLikeCount(Math.max(0, ship.getLikeCount() - 1));
            shipRepository.save(ship);

            log.info("User {} unliked ship {}", userId, shipId);
            return LikeToggleResponse.builder()
                    .liked(false)
                    .likeCount(ship.getLikeCount())
                    .message("Ship unliked successfully")
                    .build();
        } else {
            // Like: Add the like and increment count
            try {
                Like like = Like.builder()
                        .userId(userId)
                        .shipId(shipId)
                        .createdAt(Instant.now())
                        .build();
                likeRepository.save(like);

                ship.setLikeCount(ship.getLikeCount() + 1);
                shipRepository.save(ship);

                log.info("User {} liked ship {}", userId, shipId);
                return LikeToggleResponse.builder()
                        .liked(true)
                        .likeCount(ship.getLikeCount())
                        .message("Ship liked successfully")
                        .build();
            } catch (org.springframework.dao.DuplicateKeyException e) {
                // Race condition: Another thread created the like between our check and insert
                // Treat this as if the like already existed and remove it
                log.warn("Race condition detected while liking ship {} by user {}. Treating as unlike.", shipId, userId);
                likeRepository.deleteByUserIdAndShipId(userId, shipId);
                ship.setLikeCount(Math.max(0, ship.getLikeCount() - 1));
                shipRepository.save(ship);

                return LikeToggleResponse.builder()
                        .liked(false)
                        .likeCount(ship.getLikeCount())
                        .message("Ship unliked successfully")
                        .build();
            }
        }
    }

    /**
     * Add a comment to a ship.
     * Validates content and updates ship comment count.
     *
     * @param userId the ID of the user creating the comment
     * @param shipId the ID of the ship to comment on
     * @param request the comment creation request
     * @return CommentResponse with commenter profile information
     * @throws ResourceNotFoundException if the ship or user does not exist
     */
    @Transactional
    public CommentResponse addComment(String userId, String shipId, CommentCreateRequest request) {
        log.debug("Adding comment to ship {} by user {}", shipId, userId);

        // Verify ship exists
        Ship ship = shipRepository.findById(shipId)
                .orElseThrow(() -> new ResourceNotFoundException("Ship not found with id: " + shipId));

        // Verify user exists and get profile
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));

        // Create and save comment
        Comment comment = Comment.builder()
                .userId(userId)
                .shipId(shipId)
                .content(request.getContent())
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        comment = commentRepository.save(comment);

        // Update ship comment count
        ship.setCommentCount(ship.getCommentCount() + 1);
        shipRepository.save(ship);

        log.info("Comment {} added to ship {} by user {}", comment.getId(), shipId, userId);

        // Build response with commenter profile
        return buildCommentResponse(comment, user);
    }

    /**
     * Toggle upvote state for a comment.
     * If the user has already upvoted the comment, the upvote is removed.
     * If the user has not upvoted the comment, an upvote is added.
     * When a comment reaches qualification thresholds (>= 50 chars and >= 2 upvotes),
     * triggers QUALITY_COMMENT scoring event.
     * Handles race conditions using database-level unique constraints.
     *
     * @param userId the ID of the user toggling the upvote
     * @param commentId the ID of the comment to upvote/remove upvote
     * @return CommentUpvoteToggleResponse indicating the new state and qualification status
     * @throws ResourceNotFoundException if the comment or user does not exist
     */
    @Transactional
    public CommentUpvoteToggleResponse toggleCommentUpvote(String userId, String commentId) {
        log.debug("Toggling upvote for user {} on comment {}", userId, commentId);

        // Verify comment exists
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new ResourceNotFoundException("Comment not found with id: " + commentId));

        // Verify user exists
        userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));

        // Check if upvote already exists
        Optional<CommentUpvote> existingUpvote = commentUpvoteRepository.findByUserIdAndCommentId(userId, commentId);

        if (existingUpvote.isPresent()) {
            // Remove upvote: Remove the upvote and decrement count
            commentUpvoteRepository.deleteByUserIdAndCommentId(userId, commentId);
            comment.setUpvotes(Math.max(0, comment.getUpvotes() - 1));
            commentRepository.save(comment);

            log.info("User {} removed upvote from comment {}", userId, commentId);
            return CommentUpvoteToggleResponse.builder()
                    .upvoted(false)
                    .upvoteCount(comment.getUpvotes())
                    .qualifiedForScoring(false)
                    .build();
        } else {
            // Add upvote: Add the upvote and increment count
            try {
                CommentUpvote upvote = CommentUpvote.builder()
                        .userId(userId)
                        .commentId(commentId)
                        .createdAt(Instant.now())
                        .build();
                commentUpvoteRepository.save(upvote);

                // Store previous qualification status
                boolean wasQualified = commentQualificationService.isQualified(comment);

                // Update upvote count
                comment.setUpvotes(comment.getUpvotes() + 1);
                commentRepository.save(comment);

                // Check if comment now qualifies for QUALITY_COMMENT scoring
                boolean isNowQualified = commentQualificationService.isQualified(comment);
                boolean qualifiedForScoring = false;

                // If comment just became qualified (wasn't before, but is now), apply score event
                if (!wasQualified && isNowQualified) {
                    try {
                        boolean scoreApplied = shipScoreService.applyScoreEvent(
                                comment.getUserId(),
                                ScoreEventType.QUALITY_COMMENT,
                                commentId
                        );
                        if (scoreApplied) {
                            log.info("Successfully applied QUALITY_COMMENT score event for comment {} by user {}",
                                    commentId, comment.getUserId());
                            qualifiedForScoring = true;
                        } else {
                            log.debug("QUALITY_COMMENT score event was not applied (duplicate or cap reached) for comment {}",
                                    commentId);
                        }
                    } catch (Exception e) {
                        // Log the error but don't fail the upvote operation
                        log.error("Failed to apply QUALITY_COMMENT score event for comment {}", commentId, e);
                    }
                }

                log.info("User {} upvoted comment {}", userId, commentId);
                return CommentUpvoteToggleResponse.builder()
                        .upvoted(true)
                        .upvoteCount(comment.getUpvotes())
                        .qualifiedForScoring(qualifiedForScoring)
                        .build();
            } catch (org.springframework.dao.DuplicateKeyException e) {
                // Race condition: Another thread created the upvote between our check and insert
                // Treat this as if the upvote already existed and remove it
                log.warn("Race condition detected while upvoting comment {} by user {}. Treating as remove upvote.",
                        commentId, userId);
                commentUpvoteRepository.deleteByUserIdAndCommentId(userId, commentId);
                comment.setUpvotes(Math.max(0, comment.getUpvotes() - 1));
                commentRepository.save(comment);

                return CommentUpvoteToggleResponse.builder()
                        .upvoted(false)
                        .upvoteCount(comment.getUpvotes())
                        .qualifiedForScoring(false)
                        .build();
            }
        }
    }

    /**
     * Get comments for a ship with pagination.
     * Includes commenter profile information and timestamps.
     *
     * @param shipId the ID of the ship
     * @param pageable pagination and sorting parameters
     * @return Page of CommentResponse objects
     * @throws ResourceNotFoundException if the ship does not exist
     */
    public Page<CommentResponse> getShipComments(String shipId, Pageable pageable) {
        log.debug("Fetching comments for ship {} with pagination", shipId);

        // Verify ship exists
        shipRepository.findById(shipId)
                .orElseThrow(() -> new ResourceNotFoundException("Ship not found with id: " + shipId));

        // Fetch comments with pagination
        Page<Comment> comments = commentRepository.findByShipId(shipId, pageable);

        // Map to response DTOs with user information
        return comments.map(comment -> {
            User user = userRepository.findById(comment.getUserId())
                    .orElse(null); // Handle case where user might be deleted
            return buildCommentResponse(comment, user);
        });
    }

    /**
     * Get likes for a ship with pagination.
     * Includes user profile information and timestamps.
     *
     * @param shipId the ID of the ship
     * @param pageable pagination and sorting parameters
     * @return Page of LikeResponse objects
     * @throws ResourceNotFoundException if the ship does not exist
     */
    public Page<LikeResponse> getShipLikes(String shipId, Pageable pageable) {
        log.debug("Fetching likes for ship {} with pagination", shipId);

        // Verify ship exists
        shipRepository.findById(shipId)
                .orElseThrow(() -> new ResourceNotFoundException("Ship not found with id: " + shipId));

        // Fetch likes with pagination
        Page<Like> likes = likeRepository.findByShipId(shipId, pageable);

        // Map to response DTOs with user information
        return likes.map(like -> {
            User user = userRepository.findById(like.getUserId())
                    .orElse(null); // Handle case where user might be deleted
            return buildLikeResponse(like, user);
        });
    }

    /**
     * Check if a user has liked a specific ship.
     *
     * @param userId the ID of the user
     * @param shipId the ID of the ship
     * @return true if the user has liked the ship
     */
    public boolean hasUserLikedShip(String userId, String shipId) {
        return likeRepository.existsByUserIdAndShipId(userId, shipId);
    }

    /**
     * Build a CommentResponse from a Comment and User.
     *
     * @param comment the comment entity
     * @param user the user entity (can be null if user is deleted)
     * @return CommentResponse with profile information
     */
    private CommentResponse buildCommentResponse(Comment comment, User user) {
        CommentResponse.UserProfileSummary commenter = null;
        if (user != null) {
            commenter = CommentResponse.UserProfileSummary.builder()
                    .id(user.getId())
                    .username(user.getUsername())
                    .avatarUrl(user.getAvatarUrl())
                    .bio(user.getBio())
                    .build();
        }

        return CommentResponse.builder()
                .id(comment.getId())
                .shipId(comment.getShipId())
                .content(comment.getContent())
                .commenter(commenter)
                .createdAt(comment.getCreatedAt())
                .updatedAt(comment.getUpdatedAt())
                .build();
    }

    /**
     * Build a LikeResponse from a Like and User.
     *
     * @param like the like entity
     * @param user the user entity (can be null if user is deleted)
     * @return LikeResponse with profile information
     */
    private LikeResponse buildLikeResponse(Like like, User user) {
        LikeResponse.UserProfileSummary userSummary = null;
        if (user != null) {
            userSummary = LikeResponse.UserProfileSummary.builder()
                    .id(user.getId())
                    .username(user.getUsername())
                    .avatarUrl(user.getAvatarUrl())
                    .bio(user.getBio())
                    .build();
        }

        return LikeResponse.builder()
                .id(like.getId())
                .shipId(like.getShipId())
                .user(userSummary)
                .createdAt(like.getCreatedAt())
                .build();
    }
}
