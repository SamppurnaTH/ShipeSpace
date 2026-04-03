package com.shipspace.controller;

import com.shipspace.dto.CommentCreateRequest;
import com.shipspace.dto.CommentResponse;
import com.shipspace.dto.CommentUpvoteToggleResponse;
import com.shipspace.dto.LikeResponse;
import com.shipspace.dto.LikeToggleResponse;
import com.shipspace.service.BehavioralTrackingService;
import com.shipspace.service.EngagementService;
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
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for engagement operations (likes and comments).
 * Uses Spring Security Authentication — no raw JWT header parsing.
 */
@RestController
@RequestMapping("/ships")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Engagement", description = "Engagement management endpoints for likes and comments")
public class EngagementController {

    private final EngagementService engagementService;
    private final BehavioralTrackingService behavioralTrackingService;

    @PostMapping("/{shipId}/like")
    @Operation(summary = "Toggle like on a ship", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Like toggled successfully",
                    content = @Content(schema = @Schema(implementation = LikeToggleResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "404", description = "Ship not found")
    })
    public ResponseEntity<LikeToggleResponse> toggleLike(
            Authentication authentication,
            @PathVariable String shipId) {
        String userId = ((UserDetails) authentication.getPrincipal()).getUsername();
        log.info("Toggle like request for ship {} by user {}", shipId, userId);
        LikeToggleResponse response = engagementService.toggleLike(userId, shipId);
        // Fire-and-forget behavioral signal
        if (response.isLiked()) {
            behavioralTrackingService.trackLike(userId, shipId);
        }
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{shipId}/comments")
    @Operation(summary = "Add a comment to a ship", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Comment created successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid comment content"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "404", description = "Ship not found")
    })
    public ResponseEntity<CommentResponse> addComment(
            Authentication authentication,
            @PathVariable String shipId,
            @Valid @RequestBody CommentCreateRequest request) {
        String userId = ((UserDetails) authentication.getPrincipal()).getUsername();
        log.info("Add comment request for ship {} by user {}", shipId, userId);
        CommentResponse response = engagementService.addComment(userId, shipId, request);
        // Fire-and-forget behavioral signal
        behavioralTrackingService.trackComment(userId, shipId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/comments/{commentId}/upvote")
    @Operation(summary = "Toggle upvote on a comment", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Upvote toggled successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "404", description = "Comment not found")
    })
    public ResponseEntity<CommentUpvoteToggleResponse> toggleCommentUpvote(
            Authentication authentication,
            @PathVariable String commentId) {
        String userId = ((UserDetails) authentication.getPrincipal()).getUsername();
        log.info("Toggle upvote request for comment {} by user {}", commentId, userId);
        return ResponseEntity.ok(engagementService.toggleCommentUpvote(userId, commentId));
    }

    @GetMapping("/{shipId}/comments")
    @Operation(summary = "Get comments for a ship")
    public ResponseEntity<Page<CommentResponse>> getShipComments(
            @PathVariable String shipId,
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size (max 100)")
            @RequestParam(defaultValue = "20") int size) {
        size = Math.min(size, 100);
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return ResponseEntity.ok(engagementService.getShipComments(shipId, pageable));
    }

    @GetMapping("/{shipId}/likes")
    @Operation(summary = "Get likes for a ship")
    public ResponseEntity<Page<LikeResponse>> getShipLikes(
            @PathVariable String shipId,
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size (max 100)")
            @RequestParam(defaultValue = "20") int size) {
        size = Math.min(size, 100);
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return ResponseEntity.ok(engagementService.getShipLikes(shipId, pageable));
    }

    @GetMapping("/{shipId}/like/status")
    @Operation(summary = "Check if user has liked a ship", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<Boolean> getLikeStatus(
            Authentication authentication,
            @PathVariable String shipId) {
        String userId = ((UserDetails) authentication.getPrincipal()).getUsername();
        return ResponseEntity.ok(engagementService.hasUserLikedShip(userId, shipId));
    }

    @DeleteMapping("/comments/{commentId}")
    @Operation(summary = "Delete a comment", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Comment deleted successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "403", description = "Forbidden — not the comment owner"),
            @ApiResponse(responseCode = "404", description = "Comment not found")
    })
    public ResponseEntity<Void> deleteComment(
            Authentication authentication,
            @PathVariable String commentId) {
        String userId = ((UserDetails) authentication.getPrincipal()).getUsername();
        log.info("Delete comment request for comment {} by user {}", commentId, userId);
        engagementService.deleteComment(userId, commentId);
        return ResponseEntity.noContent().build();
    }
}
