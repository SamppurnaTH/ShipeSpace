package com.shipspace.controller;

import com.shipspace.dto.CommentCreateRequest;
import com.shipspace.dto.CommentResponse;
import com.shipspace.dto.CommentUpvoteToggleResponse;
import com.shipspace.dto.LikeResponse;
import com.shipspace.dto.LikeToggleResponse;
import com.shipspace.service.EngagementService;
import com.shipspace.util.JwtUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for engagement operations (likes and comments).
 * Provides endpoints for toggling likes, creating comments, and retrieving engagement data.
 */
@RestController
@RequestMapping("/api/ships")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Engagement", description = "Engagement management endpoints for likes and comments")
public class EngagementController {

    private final EngagementService engagementService;
    private final JwtUtil jwtUtil;

    /**
     * Toggle like on a ship.
     * If the user has already liked the ship, the like is removed.
     * If the user has not liked the ship, a like is added.
     *
     * @param shipId the ID of the ship to like/unlike
     * @param authHeader the Authorization header containing the JWT token
     * @return LikeToggleResponse indicating the new state
     */
    @PostMapping("/{shipId}/like")
    @Operation(
            summary = "Toggle like on a ship",
            description = "Adds or removes a like on a ship. If the user has already liked the ship, the like is removed. Otherwise, a like is added.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Like toggled successfully",
                    content = @Content(schema = @Schema(implementation = LikeToggleResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized - Invalid or missing JWT token"),
            @ApiResponse(responseCode = "404", description = "Ship or user not found")
    })
    public ResponseEntity<LikeToggleResponse> toggleLike(
            @Parameter(description = "ID of the ship to like/unlike", required = true)
            @PathVariable String shipId,
            @RequestHeader("Authorization") String authHeader) {

        String userId = jwtUtil.extractUserId(authHeader.substring(7));
        log.info("Toggle like request for ship {} by user {}", shipId, userId);

        LikeToggleResponse response = engagementService.toggleLike(userId, shipId);
        return ResponseEntity.ok(response);
    }

    /**
     * Add a comment to a ship.
     *
     * @param shipId the ID of the ship to comment on
     * @param request the comment creation request
     * @param authHeader the Authorization header containing the JWT token
     * @return CommentResponse with commenter profile information
     */
    @PostMapping("/{shipId}/comments")
    @Operation(
            summary = "Add a comment to a ship",
            description = "Creates a new comment on a ship with validation and updates the ship's comment count.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Comment created successfully",
                    content = @Content(schema = @Schema(implementation = CommentResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid comment content"),
            @ApiResponse(responseCode = "401", description = "Unauthorized - Invalid or missing JWT token"),
            @ApiResponse(responseCode = "404", description = "Ship or user not found")
    })
    public ResponseEntity<CommentResponse> addComment(
            @Parameter(description = "ID of the ship to comment on", required = true)
            @PathVariable String shipId,
            @Valid @RequestBody CommentCreateRequest request,
            @RequestHeader("Authorization") String authHeader) {

        String userId = jwtUtil.extractUserId(authHeader.substring(7));
        log.info("Add comment request for ship {} by user {}", shipId, userId);

        CommentResponse response = engagementService.addComment(userId, shipId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Toggle upvote on a comment.
     * If the user has already upvoted the comment, the upvote is removed.
     * If the user has not upvoted the comment, an upvote is added.
     * When a comment reaches qualification thresholds, triggers QUALITY_COMMENT scoring.
     *
     * @param commentId the ID of the comment to upvote/remove upvote
     * @param authHeader the Authorization header containing the JWT token
     * @return CommentUpvoteToggleResponse indicating the new state
     */
    @PostMapping("/comments/{commentId}/upvote")
    @Operation(
            summary = "Toggle upvote on a comment",
            description = "Adds or removes an upvote on a comment. If the user has already upvoted the comment, the upvote is removed. Otherwise, an upvote is added. When a comment reaches qualification thresholds (>= 50 chars and >= 2 upvotes), triggers QUALITY_COMMENT scoring event.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Upvote toggled successfully",
                    content = @Content(schema = @Schema(implementation = CommentUpvoteToggleResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized - Invalid or missing JWT token"),
            @ApiResponse(responseCode = "404", description = "Comment or user not found")
    })
    public ResponseEntity<CommentUpvoteToggleResponse> toggleCommentUpvote(
            @Parameter(description = "ID of the comment to upvote/remove upvote", required = true)
            @PathVariable String commentId,
            @RequestHeader("Authorization") String authHeader) {

        String userId = jwtUtil.extractUserId(authHeader.substring(7));
        log.info("Toggle upvote request for comment {} by user {}", commentId, userId);

        CommentUpvoteToggleResponse response = engagementService.toggleCommentUpvote(userId, commentId);
        return ResponseEntity.ok(response);
    }

    /**
     * Get comments for a ship with pagination.
     *
     * @param shipId the ID of the ship
     * @param page the page number (0-indexed)
     * @param size the page size
     * @return Page of CommentResponse objects
     */
    @GetMapping("/{shipId}/comments")
    @Operation(
            summary = "Get comments for a ship",
            description = "Retrieves paginated comments for a ship, including commenter profile information and timestamps."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Comments retrieved successfully",
                    content = @Content(schema = @Schema(implementation = Page.class))),
            @ApiResponse(responseCode = "404", description = "Ship not found")
    })
    public ResponseEntity<Page<CommentResponse>> getShipComments(
            @Parameter(description = "ID of the ship", required = true)
            @PathVariable String shipId,
            @Parameter(description = "Page number (0-indexed)")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size")
            @RequestParam(defaultValue = "20") int size) {

        log.info("Get comments request for ship {} (page: {}, size: {})", shipId, page, size);

        // Sort by creation time descending (most recent first)
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<CommentResponse> comments = engagementService.getShipComments(shipId, pageable);

        return ResponseEntity.ok(comments);
    }

    /**
     * Get likes for a ship with pagination.
     *
     * @param shipId the ID of the ship
     * @param page the page number (0-indexed)
     * @param size the page size
     * @return Page of LikeResponse objects
     */
    @GetMapping("/{shipId}/likes")
    @Operation(
            summary = "Get likes for a ship",
            description = "Retrieves paginated likes for a ship, including user profile information and timestamps."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Likes retrieved successfully",
                    content = @Content(schema = @Schema(implementation = Page.class))),
            @ApiResponse(responseCode = "404", description = "Ship not found")
    })
    public ResponseEntity<Page<LikeResponse>> getShipLikes(
            @Parameter(description = "ID of the ship", required = true)
            @PathVariable String shipId,
            @Parameter(description = "Page number (0-indexed)")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size")
            @RequestParam(defaultValue = "20") int size) {

        log.info("Get likes request for ship {} (page: {}, size: {})", shipId, page, size);

        // Sort by creation time descending (most recent first)
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<LikeResponse> likes = engagementService.getShipLikes(shipId, pageable);

        return ResponseEntity.ok(likes);
    }

    /**
     * Check if the authenticated user has liked a specific ship.
     *
     * @param shipId the ID of the ship
     * @param authHeader the Authorization header containing the JWT token
     * @return true if the user has liked the ship
     */
    @GetMapping("/{shipId}/like/status")
    @Operation(
            summary = "Check if user has liked a ship",
            description = "Returns whether the authenticated user has liked the specified ship.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Like status retrieved successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized - Invalid or missing JWT token")
    })
    public ResponseEntity<Boolean> getLikeStatus(
            @Parameter(description = "ID of the ship", required = true)
            @PathVariable String shipId,
            @RequestHeader("Authorization") String authHeader) {

        String userId = jwtUtil.extractUserId(authHeader.substring(7));
        log.debug("Get like status for ship {} by user {}", shipId, userId);

        boolean hasLiked = engagementService.hasUserLikedShip(userId, shipId);
        return ResponseEntity.ok(hasLiked);
    }
}
