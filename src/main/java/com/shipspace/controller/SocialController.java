package com.shipspace.controller;

import com.shipspace.dto.UserProfileResponse;
import com.shipspace.service.SocialService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
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
 * REST controller for social following system endpoints.
 * Handles follow/unfollow operations and retrieval of follower/following lists.
 * 
 * Implements Requirements 5.1, 5.2, 5.4, 5.5:
 * - POST /api/social/follow/{userId} - Follow a user
 * - DELETE /api/social/follow/{userId} - Unfollow a user
 * - GET /api/social/followers - Get current user's followers
 * - GET /api/social/following - Get current user's following list
 */
@Slf4j
@RestController
@RequestMapping("/social")
@RequiredArgsConstructor
@Tag(name = "Social Following", description = "Social following and relationship management endpoints")
public class SocialController {

    private final SocialService socialService;

    /**
     * Follows a user. Creates a following relationship between the authenticated user
     * and the target user. Prevents self-follows and duplicate follows.
     *
     * @param authentication the Spring Security authentication object
     * @param userId the ID of the user to follow
     * @return 204 No Content on success
     */
    @PostMapping("/follow/{userId}")
    @Operation(
        summary = "Follow a user",
        description = "Create a following relationship with another user. Prevents self-follows and duplicate follows.",
        security = @SecurityRequirement(name = "bearerAuth")
    )
    public ResponseEntity<Void> followUser(
            Authentication authentication,
            @Parameter(description = "ID of the user to follow") @PathVariable String userId) {
        log.info("Received follow request for user: {}", userId);
        
        // Extract current user ID from authentication
        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
        String currentUserId = userDetails.getUsername(); // We store userId in username field
        
        socialService.followUser(currentUserId, userId);
        
        log.info("User {} successfully followed user {}", currentUserId, userId);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    /**
     * Unfollows a user. Removes the following relationship between the authenticated user
     * and the target user. Idempotent operation - succeeds even if not following.
     *
     * @param authentication the Spring Security authentication object
     * @param userId the ID of the user to unfollow
     * @return 204 No Content on success
     */
    @DeleteMapping("/follow/{userId}")
    @Operation(
        summary = "Unfollow a user",
        description = "Remove a following relationship with another user. Idempotent operation.",
        security = @SecurityRequirement(name = "bearerAuth")
    )
    public ResponseEntity<Void> unfollowUser(
            Authentication authentication,
            @Parameter(description = "ID of the user to unfollow") @PathVariable String userId) {
        log.info("Received unfollow request for user: {}", userId);
        
        // Extract current user ID from authentication
        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
        String currentUserId = userDetails.getUsername(); // We store userId in username field
        
        socialService.unfollowUser(currentUserId, userId);
        
        log.info("User {} successfully unfollowed user {}", currentUserId, userId);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    /**
     * Gets the current authenticated user's followers with pagination.
     * Returns user profile summaries for each follower.
     *
     * @param authentication the Spring Security authentication object
     * @param page the page number (0-indexed, default: 0)
     * @param size the page size (default: 20)
     * @param sort the sort field (default: createdAt)
     * @param direction the sort direction (default: DESC)
     * @return paginated list of follower profiles
     */
    @GetMapping("/followers")
    @Operation(
        summary = "Get current user's followers",
        description = "Retrieve a paginated list of users following the authenticated user",
        security = @SecurityRequirement(name = "bearerAuth")
    )
    public ResponseEntity<Page<UserProfileResponse>> getFollowers(
            Authentication authentication,
            @Parameter(description = "Page number (0-indexed)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "20") int size,
            @Parameter(description = "Sort field") @RequestParam(defaultValue = "createdAt") String sort,
            @Parameter(description = "Sort direction (ASC or DESC)") @RequestParam(defaultValue = "DESC") String direction) {
        log.debug("Received request for current user's followers - page: {}, size: {}", page, size);
        
        // Extract current user ID from authentication
        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
        String currentUserId = userDetails.getUsername(); // We store userId in username field
        
        // Create pageable with sorting
        Sort.Direction sortDirection = Sort.Direction.fromString(direction);
        Pageable pageable = PageRequest.of(page, size, Sort.by(sortDirection, sort));
        
        Page<UserProfileResponse> followers = socialService.getFollowers(currentUserId, pageable);
        
        log.debug("Returning {} followers for current user (page {} of {})", 
                followers.getNumberOfElements(), followers.getNumber() + 1, followers.getTotalPages());
        return ResponseEntity.ok(followers);
    }

    /**
     * Gets the list of users that the current authenticated user is following with pagination.
     * Returns user profile summaries for each followed user.
     *
     * @param authentication the Spring Security authentication object
     * @param page the page number (0-indexed, default: 0)
     * @param size the page size (default: 20)
     * @param sort the sort field (default: createdAt)
     * @param direction the sort direction (default: DESC)
     * @return paginated list of followed user profiles
     */
    @GetMapping("/following")
    @Operation(
        summary = "Get current user's following list",
        description = "Retrieve a paginated list of users that the authenticated user is following",
        security = @SecurityRequirement(name = "bearerAuth")
    )
    public ResponseEntity<Page<UserProfileResponse>> getFollowing(
            Authentication authentication,
            @Parameter(description = "Page number (0-indexed)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "20") int size,
            @Parameter(description = "Sort field") @RequestParam(defaultValue = "createdAt") String sort,
            @Parameter(description = "Sort direction (ASC or DESC)") @RequestParam(defaultValue = "DESC") String direction) {
        log.debug("Received request for current user's following list - page: {}, size: {}", page, size);
        
        // Extract current user ID from authentication
        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
        String currentUserId = userDetails.getUsername(); // We store userId in username field
        
        // Create pageable with sorting
        Sort.Direction sortDirection = Sort.Direction.fromString(direction);
        Pageable pageable = PageRequest.of(page, size, Sort.by(sortDirection, sort));
        
        Page<UserProfileResponse> following = socialService.getFollowing(currentUserId, pageable);
        
        log.debug("Returning {} following for current user (page {} of {})", 
                following.getNumberOfElements(), following.getNumber() + 1, following.getTotalPages());
        return ResponseEntity.ok(following);
    }
}
