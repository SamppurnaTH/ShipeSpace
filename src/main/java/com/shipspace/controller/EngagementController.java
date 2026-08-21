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
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for engagement operations (likes and comments).
 * Uses Spring Security Authentication — no raw JWT header parsing.
 */
@RestController
@RequestMapping("/ships")
@RequiredArgsConstructor
@Slf4j
@Validated
@Tag(name = "Engagement", description = "Engagement management endpoints for likes and comments")
public class EngagementController {

    private final EngagementService engagementService;
    private final BehavioralTrackingService behavioralTrackingService;

    @PutMapping("/{shipId}/like")
    @Operation(summary = "Like a ship", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Ship liked successfully",
                    content = @Content(schema = @Schema(implementation = LikeToggleResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "404", description = "Ship not found")
    })
    public ResponseEntity<LikeToggleResponse> likeShip(
            Authentication authentication,
            @PathVariable String shipId) {
        String userId = ((UserDetails) authentication.getPrincipal()).getUsername();
        LikeToggleResponse response = engagementService.likeShip(userId, shipId);
        if (response.isLiked()) {
            behavioralTrackingService.trackLike(userId, shipId);
        }
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{shipId}/like")
    @Operation(summary = "Unlike a ship", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<LikeToggleResponse> unlikeShip(
            Authentication authentication,
            @PathVariable String shipId) {
        String userId = ((UserDetails) authentication.getPrincipal()).getUsername();
        return ResponseEntity.ok(engagementService.unlikeShip(userId, shipId));
    }

    @PutMapping("/comments/{commentId}/upvote")
    @Operation(summary = "Upvote a comment", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Upvote added successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "404", description = "Comment not found")
    })
    public ResponseEntity<CommentUpvoteToggleResponse> upvoteComment(
            Authentication authentication,
            @PathVariable String commentId) {
        String userId = ((UserDetails) authentication.getPrincipal()).getUsername();
        return ResponseEntity.ok(engagementService.addCommentUpvote(userId, commentId));
    }

    @DeleteMapping("/comments/{commentId}/upvote")
    @Operation(summary = "Remove upvote from a comment", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<CommentUpvoteToggleResponse> removeCommentUpvote(
            Authentication authentication,
            @PathVariable String commentId) {
        String userId = ((UserDetails) authentication.getPrincipal()).getUsername();
        return ResponseEntity.ok(engagementService.removeCommentUpvote(userId, commentId));
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
        behavioralTrackingService.trackComment(userId, shipId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{shipId}/comments")
    @Operation(summary = "Get comments for a ship")
    public ResponseEntity<Page<CommentResponse>> getShipComments(
            Authentication authentication,
            @PathVariable String shipId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @Parameter(description = "Page size (max 100)")
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        String currentUserId = null;
        if (authentication != null && authentication.getPrincipal() instanceof UserDetails userDetails) {
            currentUserId = userDetails.getUsername();
        }
        return ResponseEntity.ok(engagementService.getShipComments(shipId, pageable, currentUserId));
    }

    @GetMapping("/{shipId}/likes")
    @Operation(summary = "Get likes for a ship")
    public ResponseEntity<Page<LikeResponse>> getShipLikes(
            @PathVariable String shipId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @Parameter(description = "Page size (max 100)")
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
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
