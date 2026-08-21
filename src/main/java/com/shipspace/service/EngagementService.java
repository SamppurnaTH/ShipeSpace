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
import com.shipspace.repository.ShipCountRepository;
import com.shipspace.repository.ShipRepository;
import com.shipspace.repository.UserRepository;
import com.shipspace.util.InputSanitizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Service for managing engagement operations (likes and comments).
 *
 * Counter updates (likeCount, commentCount, upvotes) use atomic MongoDB $inc
 * via ShipCountRepository / direct MongoTemplate calls to eliminate
 * read-modify-write race conditions.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EngagementService {

    private final LikeRepository likeRepository;
    private final CommentRepository commentRepository;
    private final CommentUpvoteRepository commentUpvoteRepository;
    private final ShipRepository shipRepository;
    private final ShipCountRepository shipCountRepository;
    private final UserRepository userRepository;
    private final CommentQualificationService commentQualificationService;
    private final ShipScoreService shipScoreService;
    private final MongoTemplate mongoTemplate;
    private final NotificationDispatchService notificationDispatchService;
    private final InputSanitizer inputSanitizer;

    /**
     * Toggle like state for a ship.
     * Like document insert/delete is guarded by a unique compound index.
     * Counter update uses atomic $inc — no read-modify-write race.
     */
    @Transactional
    public LikeToggleResponse toggleLike(String userId, String shipId) {
        log.debug("Toggling like for user {} on ship {}", userId, shipId);

        // Verify ship and user exist
        if (!shipRepository.existsById(shipId)) {
            throw new ResourceNotFoundException("Ship", shipId);
        }
        if (!userRepository.existsById(userId)) {
            throw new ResourceNotFoundException("User", userId);
        }

        Optional<Like> existingLike = likeRepository.findByUserIdAndShipId(userId, shipId);

        if (existingLike.isPresent()) {
            long deleted = likeRepository.deleteByUserIdAndShipId(userId, shipId);
            if (deleted > 0) {
                // Only decrement if we actually deleted — prevents double-decrement on concurrent unlike
                Ship updated = shipCountRepository.incrementLikeCount(shipId, -1);
                log.info("User {} unliked ship {}", userId, shipId);
                return LikeToggleResponse.builder()
                        .liked(false)
                        .likeCount(updated.getLikeCount())
                        .message("Ship unliked successfully")
                        .build();
            }
            // Another concurrent request already deleted it — return current state
            Ship current = shipRepository.findById(shipId)
                    .orElseThrow(() -> new ResourceNotFoundException("Ship", shipId));
            return LikeToggleResponse.builder()
                    .liked(false)
                    .likeCount(current.getLikeCount())
                    .message("Ship unliked successfully")
                    .build();
        }

        try {
            likeRepository.save(Like.builder()
                    .userId(userId)
                    .shipId(shipId)
                    .createdAt(Instant.now())
                    .build());
            // Atomic increment — returns the document with the new value
            Ship updated = shipCountRepository.incrementLikeCount(shipId, 1);
            log.info("User {} liked ship {}", userId, shipId);

            // Notify ship owner asynchronously (skip self-like)
            Ship ship = shipRepository.findById(shipId).orElse(null);
            if (ship != null && !ship.getUserId().equals(userId)) {
                User actor = userRepository.findById(userId).orElse(null);
                notificationDispatchService.dispatch(ship.getUserId(), "LIKE",
                        (actor != null ? actor.getUsername() : "Someone") + " liked your ship",
                        userId,
                        actor != null ? actor.getAvatarUrl() : null,
                        shipId, "/ships/" + shipId);
            }

            return LikeToggleResponse.builder()
                    .liked(true)
                    .likeCount(updated.getLikeCount())
                    .message("Ship liked successfully")
                    .build();
        } catch (DuplicateKeyException e) {
            // Concurrent request already inserted the like — treat as unlike
            log.warn("Race condition on like for ship {} by user {}, treating as unlike", shipId, userId);
            likeRepository.deleteByUserIdAndShipId(userId, shipId);
            Ship updated = shipCountRepository.incrementLikeCount(shipId, -1);
            return LikeToggleResponse.builder()
                    .liked(false)
                    .likeCount(Math.max(0, updated.getLikeCount()))
                    .message("Ship unliked successfully")
                    .build();
        }
    }

    /**
     * Add a comment to a ship.
     * Counter update uses atomic $inc.
     */
    @Transactional
    public CommentResponse addComment(String userId, String shipId, CommentCreateRequest request) {
        log.debug("Adding comment to ship {} by user {}", shipId, userId);

        if (!shipRepository.existsById(shipId)) {
            throw new ResourceNotFoundException("Ship", shipId);
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));

        Comment comment = commentRepository.save(Comment.builder()
                .userId(userId)
                .shipId(shipId)
                .content(inputSanitizer.sanitize(request.getContent(), 1000))
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build());

        // Atomic increment — no read-modify-write
        shipCountRepository.incrementCommentCount(shipId, 1);

        // Notify ship owner asynchronously (skip self-comment)
        Ship ship = shipRepository.findById(shipId).orElse(null);
        if (ship != null && !ship.getUserId().equals(userId)) {
            notificationDispatchService.dispatch(ship.getUserId(), "COMMENT",
                    user.getUsername() + " commented on your ship",
                    userId, user.getAvatarUrl(),
                    shipId, "/ships/" + shipId);
        }

        log.info("Comment {} added to ship {} by user {}", comment.getId(), shipId, userId);
        return buildCommentResponse(comment, user);
    }

    /**
     * Toggle upvote on a comment.
     * Upvote counter uses atomic $inc via MongoTemplate.
     * QUALITY_COMMENT scoring fires only on the transition from unqualified → qualified.
     */
    @Transactional
    public CommentUpvoteToggleResponse toggleCommentUpvote(String userId, String commentId) {
        log.debug("Toggling upvote for user {} on comment {}", userId, commentId);

        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new ResourceNotFoundException("Comment", commentId));
        if (!userRepository.existsById(userId)) {
            throw new ResourceNotFoundException("User", userId);
        }

        Optional<CommentUpvote> existingUpvote =
                commentUpvoteRepository.findByUserIdAndCommentId(userId, commentId);

        if (existingUpvote.isPresent()) {
            long deleted = commentUpvoteRepository.deleteByUserIdAndCommentId(userId, commentId);
            if (deleted == 0) {
                Comment current = commentRepository.findById(commentId)
                        .orElseThrow(() -> new ResourceNotFoundException("Comment", commentId));
                return CommentUpvoteToggleResponse.builder()
                        .upvoted(false)
                        .upvoteCount(Math.max(0, current.getUpvotes()))
                        .qualifiedForScoring(false)
                        .build();
            }
            Comment updated = atomicIncrementUpvotes(commentId, -1);
            log.info("User {} removed upvote from comment {}", userId, commentId);
            return CommentUpvoteToggleResponse.builder()
                    .upvoted(false)
                    .upvoteCount(Math.max(0, updated.getUpvotes()))
                    .qualifiedForScoring(false)
                    .build();
        }

        try {
            commentUpvoteRepository.save(CommentUpvote.builder()
                    .userId(userId)
                    .commentId(commentId)
                    .createdAt(Instant.now())
                    .build());

            // Capture qualification state BEFORE increment
            boolean wasQualified = commentQualificationService.isQualified(comment);

            // Atomic increment — returns updated document
            Comment updated = atomicIncrementUpvotes(commentId, 1);

            boolean isNowQualified = commentQualificationService.isQualified(updated);
            boolean qualifiedForScoring = false;

            if (!wasQualified && isNowQualified) {
                try {
                    qualifiedForScoring = shipScoreService.applyScoreEvent(
                            comment.getUserId(), ScoreEventType.QUALITY_COMMENT, commentId);
                } catch (Exception e) {
                    log.error("Failed to apply QUALITY_COMMENT score for comment {}", commentId, e);
                }
            }

            log.info("User {} upvoted comment {}", userId, commentId);
            return CommentUpvoteToggleResponse.builder()
                    .upvoted(true)
                    .upvoteCount(updated.getUpvotes())
                    .qualifiedForScoring(qualifiedForScoring)
                    .build();

        } catch (DuplicateKeyException e) {
            log.warn("Race condition on upvote for comment {} by user {}, treating as remove", commentId, userId);
            long deleted = commentUpvoteRepository.deleteByUserIdAndCommentId(userId, commentId);
            if (deleted == 0) {
                Comment current = commentRepository.findById(commentId)
                        .orElseThrow(() -> new ResourceNotFoundException("Comment", commentId));
                return CommentUpvoteToggleResponse.builder()
                        .upvoted(false)
                        .upvoteCount(Math.max(0, current.getUpvotes()))
                        .qualifiedForScoring(false)
                        .build();
            }
            Comment updated = atomicIncrementUpvotes(commentId, -1);
            return CommentUpvoteToggleResponse.builder()
                    .upvoted(false)
                    .upvoteCount(Math.max(0, updated.getUpvotes()))
                    .qualifiedForScoring(false)
                    .build();
        }
    }

    /**
     * Get comments for a ship with pagination.
     * Batch-fetches all comment authors in one query instead of N individual lookups.
     */
    public Page<CommentResponse> getShipComments(String shipId, Pageable pageable) {
        return getShipComments(shipId, pageable, null);
    }

    /**
     * Get comments for a ship with optional current-user upvote enrichment.
     */
    public Page<CommentResponse> getShipComments(String shipId, Pageable pageable, String currentUserId) {
        if (!shipRepository.existsById(shipId)) {
            throw new ResourceNotFoundException("Ship", shipId);
        }

        Page<Comment> comments = commentRepository.findByShipId(shipId, pageable);

        // Collect distinct user IDs, fetch all in one query
        Set<String> userIds = comments.getContent().stream()
                .map(Comment::getUserId)
                .collect(Collectors.toSet());
        Map<String, User> userMap = userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));

        Set<String> upvotedCommentIds = Set.of();
        if (currentUserId != null && !currentUserId.isBlank() && !comments.isEmpty()) {
            Set<String> commentIds = comments.getContent().stream()
                    .map(Comment::getId)
                    .collect(Collectors.toSet());
            upvotedCommentIds = commentUpvoteRepository.findByUserIdAndCommentIdIn(currentUserId, commentIds)
                    .stream()
                    .map(CommentUpvote::getCommentId)
                    .collect(Collectors.toSet());
        }

        Set<String> finalUpvotedCommentIds = upvotedCommentIds;
        List<CommentResponse> responses = comments.getContent().stream()
                .map(c -> buildCommentResponse(c, userMap.get(c.getUserId()), finalUpvotedCommentIds.contains(c.getId())))
                .collect(Collectors.toList());

        return new PageImpl<>(responses, pageable, comments.getTotalElements());
    }

    /**
     * Get likes for a ship with pagination.
     * Batch-fetches all liking users in one query instead of N individual lookups.
     */
    public Page<LikeResponse> getShipLikes(String shipId, Pageable pageable) {
        if (!shipRepository.existsById(shipId)) {
            throw new ResourceNotFoundException("Ship", shipId);
        }

        Page<Like> likes = likeRepository.findByShipId(shipId, pageable);

        Set<String> userIds = likes.getContent().stream()
                .map(Like::getUserId)
                .collect(Collectors.toSet());
        Map<String, User> userMap = userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));

        List<LikeResponse> responses = likes.getContent().stream()
                .map(l -> buildLikeResponse(l, userMap.get(l.getUserId())))
                .collect(Collectors.toList());

        return new PageImpl<>(responses, pageable, likes.getTotalElements());
    }

    public boolean hasUserLikedShip(String userId, String shipId) {
        return likeRepository.existsByUserIdAndShipId(userId, shipId);
    }

    /**
     * Returns the set of ship IDs (from the given collection) that the user has liked.
     * Single query — O(1) round trips regardless of page size.
     */
    public java.util.Set<String> getLikedShipIds(String userId, java.util.Collection<String> shipIds) {
        if (shipIds == null || shipIds.isEmpty()) return java.util.Set.of();
        return likeRepository.findByUserIdAndShipIdIn(userId, shipIds)
                .stream()
                .map(com.shipspace.model.Like::getShipId)
                .collect(java.util.stream.Collectors.toSet());
    }

    /**
     * Explicit like — idempotent. Returns current state even if already liked.
     */
    @Transactional
    public LikeToggleResponse likeShip(String userId, String shipId) {
        if (!shipRepository.existsById(shipId)) throw new ResourceNotFoundException("Ship", shipId);
        if (!userRepository.existsById(userId)) throw new ResourceNotFoundException("User", userId);

        if (likeRepository.existsByUserIdAndShipId(userId, shipId)) {
            Ship ship = shipRepository.findById(shipId).orElseThrow();
            return LikeToggleResponse.builder().liked(true)
                    .likeCount(ship.getLikeCount()).message("Already liked").build();
        }
        try {
            likeRepository.save(Like.builder().userId(userId).shipId(shipId)
                    .createdAt(Instant.now()).build());
            Ship updated = shipCountRepository.incrementLikeCount(shipId, 1);
            Ship ship = shipRepository.findById(shipId).orElse(null);
            if (ship != null && !ship.getUserId().equals(userId)) {
                User actor = userRepository.findById(userId).orElse(null);
                notificationDispatchService.dispatch(ship.getUserId(), "LIKE",
                        (actor != null ? actor.getUsername() : "Someone") + " liked your ship",
                        userId, actor != null ? actor.getAvatarUrl() : null,
                        shipId, "/ships/" + shipId);
            }
            return LikeToggleResponse.builder().liked(true)
                    .likeCount(updated.getLikeCount()).message("Ship liked successfully").build();
        } catch (DuplicateKeyException e) {
            Ship ship = shipRepository.findById(shipId).orElseThrow();
            return LikeToggleResponse.builder().liked(true)
                    .likeCount(ship.getLikeCount()).message("Already liked").build();
        }
    }

    /**
     * Explicit unlike — idempotent. Returns current state even if not liked.
     */
    @Transactional
    public LikeToggleResponse unlikeShip(String userId, String shipId) {
        if (!shipRepository.existsById(shipId)) throw new ResourceNotFoundException("Ship", shipId);
        if (!userRepository.existsById(userId)) throw new ResourceNotFoundException("User", userId);

        if (!likeRepository.existsByUserIdAndShipId(userId, shipId)) {
            Ship ship = shipRepository.findById(shipId).orElseThrow();
            return LikeToggleResponse.builder().liked(false)
                    .likeCount(ship.getLikeCount()).message("Not liked").build();
        }
        long deleted = likeRepository.deleteByUserIdAndShipId(userId, shipId);
        if (deleted > 0) {
            Ship updated = shipCountRepository.incrementLikeCount(shipId, -1);
            return LikeToggleResponse.builder().liked(false)
                    .likeCount(updated.getLikeCount()).message("Ship unliked successfully").build();
        }
        // Concurrent request beat us to the delete
        Ship current = shipRepository.findById(shipId).orElseThrow();
        return LikeToggleResponse.builder().liked(false)
                .likeCount(current.getLikeCount()).message("Ship unliked successfully").build();
    }

    /**
     * Explicit upvote — idempotent.
     */
    @Transactional
    public CommentUpvoteToggleResponse addCommentUpvote(String userId, String commentId) {
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new ResourceNotFoundException("Comment", commentId));
        if (!userRepository.existsById(userId)) throw new ResourceNotFoundException("User", userId);

        if (commentUpvoteRepository.findByUserIdAndCommentId(userId, commentId).isPresent()) {
            return CommentUpvoteToggleResponse.builder().upvoted(true)
                    .upvoteCount(comment.getUpvotes()).qualifiedForScoring(false).build();
        }
        try {
            commentUpvoteRepository.save(CommentUpvote.builder().userId(userId)
                    .commentId(commentId).createdAt(Instant.now()).build());
            boolean wasQualified = commentQualificationService.isQualified(comment);
            Comment updated = atomicIncrementUpvotes(commentId, 1);
            boolean isNowQualified = commentQualificationService.isQualified(updated);
            boolean scored = false;
            if (!wasQualified && isNowQualified) {
                try { scored = shipScoreService.applyScoreEvent(
                        comment.getUserId(), ScoreEventType.QUALITY_COMMENT, commentId);
                } catch (Exception e) { log.error("QUALITY_COMMENT score failed for {}", commentId, e); }
            }
            return CommentUpvoteToggleResponse.builder().upvoted(true)
                    .upvoteCount(updated.getUpvotes()).qualifiedForScoring(scored).build();
        } catch (DuplicateKeyException e) {
            return CommentUpvoteToggleResponse.builder().upvoted(true)
                    .upvoteCount(comment.getUpvotes()).qualifiedForScoring(false).build();
        }
    }

    /**
     * Explicit remove upvote — idempotent.
     */
    @Transactional
    public CommentUpvoteToggleResponse removeCommentUpvote(String userId, String commentId) {
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new ResourceNotFoundException("Comment", commentId));
        if (!userRepository.existsById(userId)) throw new ResourceNotFoundException("User", userId);

        if (commentUpvoteRepository.findByUserIdAndCommentId(userId, commentId).isEmpty()) {
            return CommentUpvoteToggleResponse.builder().upvoted(false)
                    .upvoteCount(comment.getUpvotes()).qualifiedForScoring(false).build();
        }
        long deleted = commentUpvoteRepository.deleteByUserIdAndCommentId(userId, commentId);
        if (deleted == 0) {
            return CommentUpvoteToggleResponse.builder().upvoted(false)
                    .upvoteCount(comment.getUpvotes()).qualifiedForScoring(false).build();
        }
        Comment updated = atomicIncrementUpvotes(commentId, -1);
        return CommentUpvoteToggleResponse.builder().upvoted(false)
                .upvoteCount(Math.max(0, updated.getUpvotes())).qualifiedForScoring(false).build();
    }

    // ── private helpers ──────────────────────────────────────────────────────

    /**
     * Atomically increments the upvotes field on a Comment document.
     * Returns the updated Comment. Avoids a separate read after the write.
     */
    private Comment atomicIncrementUpvotes(String commentId, int delta) {
        Query query = new Query(Criteria.where("_id").is(commentId));
        Update update = new Update().inc("upvotes", delta);
        FindAndModifyOptions options = FindAndModifyOptions.options().returnNew(true);
        Comment updated = mongoTemplate.findAndModify(query, update, options, Comment.class);
        if (updated == null) {
            throw new ResourceNotFoundException("Comment", commentId);
        }
        return updated;
    }

    private CommentResponse buildCommentResponse(Comment comment, User user) {
        return buildCommentResponse(comment, user, false);
    }

    private CommentResponse buildCommentResponse(Comment comment, User user, boolean upvotedByCurrentUser) {
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
                .userId(comment.getUserId())
                .content(comment.getContent())
                .upvotes(comment.getUpvotes())
                .upvotedByCurrentUser(upvotedByCurrentUser)
                .commenter(commenter)
                .createdAt(comment.getCreatedAt())
                .updatedAt(comment.getUpdatedAt())
                .build();
    }

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

    /**
     * Deletes a comment. Only the comment author can delete.
     * Decrements the ship's comment count atomically.
     */
    @Transactional
    public void deleteComment(String userId, String commentId) {
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new ResourceNotFoundException("Comment", commentId));
        if (!comment.getUserId().equals(userId)) {
            throw new com.shipspace.exception.ForbiddenException("delete", "comment");
        }
        commentUpvoteRepository.deleteByCommentId(commentId);
        commentRepository.deleteById(commentId);
        shipCountRepository.incrementCommentCount(comment.getShipId(), -1);
        log.info("Deleted comment {} by user {}", commentId, userId);
    }
}
