package com.shipspace.controller;

import com.shipspace.dto.FeedItemResponse;
import com.shipspace.dto.ProjectResponse;
import com.shipspace.dto.ShipResponse;
import com.shipspace.dto.UserProfileResponse;
import com.shipspace.model.Ship;
import com.shipspace.model.User;
import com.shipspace.service.FeedService;
import com.shipspace.service.ProjectService;
import com.shipspace.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for feed generation endpoints.
 * Provides following feed and trending feed functionality.
 */
@RestController
@RequestMapping("/feeds")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Feed", description = "Feed generation and content discovery endpoints")
public class FeedController {

    private final FeedService feedService;
    private final ProjectService projectService;
    private final UserService userService;

    /**
     * Get the chronological following feed for the authenticated user.
     * Returns ships from users that the authenticated user follows.
     * 
     * @param authentication the authenticated user
     * @param page the page number (default: 0)
     * @param size the page size (default: 20)
     * @return Page of feed items with ship, project, and user information
     */
    @GetMapping("/following")
    @Operation(
            summary = "Get following feed",
            description = "Retrieve chronological feed of ships from followed users",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Following feed retrieved successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized - invalid or missing JWT token")
    })
    public ResponseEntity<Page<FeedItemResponse>> getFollowingFeed(
            Authentication authentication,
            @Parameter(description = "Page number (0-indexed)")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size")
            @RequestParam(defaultValue = "20") int size) {
        
        String userId = authentication.getName();
        log.info("Getting following feed for user: {}", userId);
        
        Pageable pageable = PageRequest.of(page, size);
        Page<Ship> ships = feedService.getFollowingFeed(userId, pageable);
        
        // Enrich ships with project and user information
        Page<FeedItemResponse> feedItems = ships.map(this::enrichShipWithMetadata);
        
        return ResponseEntity.ok(feedItems);
    }

    /**
     * Get the trending feed with all-time ranking.
     * Returns ships from all users ranked by engagement score.
     * 
     * @param page the page number (default: 0)
     * @param size the page size (default: 20)
     * @return Page of feed items with ship, project, and user information
     */
    @GetMapping("/trending")
    @Operation(
            summary = "Get trending feed",
            description = "Retrieve trending feed of ships ranked by engagement score (all time)"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Trending feed retrieved successfully")
    })
    public ResponseEntity<Page<FeedItemResponse>> getTrendingFeed(
            @Parameter(description = "Page number (0-indexed)")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size")
            @RequestParam(defaultValue = "20") int size) {
        
        log.info("Getting trending feed (all time)");
        
        Pageable pageable = PageRequest.of(page, size);
        Page<Ship> ships = feedService.getTrendingFeed(pageable);
        
        // Enrich ships with project and user information
        Page<FeedItemResponse> feedItems = ships.map(this::enrichShipWithMetadata);
        
        return ResponseEntity.ok(feedItems);
    }

    /**
     * Get the trending feed with a specific time window.
     * Returns ships from all users within the time window ranked by engagement score.
     * 
     * @param timeWindow the time window (e.g., "24h", "7d", "30d")
     * @param page the page number (default: 0)
     * @param size the page size (default: 20)
     * @return Page of feed items with ship, project, and user information
     */
    @GetMapping("/trending/{timeWindow}")
    @Operation(
            summary = "Get trending feed with time window",
            description = "Retrieve trending feed of ships within a specific time window ranked by engagement score. " +
                    "Time window format: <number><unit> where unit is h (hours), d (days), w (weeks), or m (months). " +
                    "Examples: 24h, 7d, 30d"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Trending feed retrieved successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid time window format")
    })
    public ResponseEntity<Page<FeedItemResponse>> getTrendingFeedWithTimeWindow(
            @Parameter(description = "Time window (e.g., 24h, 7d, 30d)")
            @PathVariable String timeWindow,
            @Parameter(description = "Page number (0-indexed)")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size")
            @RequestParam(defaultValue = "20") int size) {
        
        log.info("Getting trending feed with time window: {}", timeWindow);
        
        Pageable pageable = PageRequest.of(page, size);
        Page<Ship> ships = feedService.getTrendingFeedWithTimeWindow(timeWindow, pageable);
        
        // Enrich ships with project and user information
        Page<FeedItemResponse> feedItems = ships.map(this::enrichShipWithMetadata);
        
        return ResponseEntity.ok(feedItems);
    }

    /**
     * Enrich a ship with associated project and user information.
     * 
     * @param ship the ship to enrich
     * @return FeedItemResponse with ship, project, and user data
     */
    private FeedItemResponse enrichShipWithMetadata(Ship ship) {
        // Get project information (already returns ProjectResponse)
        ProjectResponse projectResponse = projectService.getProjectById(ship.getProjectId());
        
        // Get user information
        User user = userService.getUserById(ship.getUserId());
        UserProfileResponse userResponse = mapToUserProfileResponse(user);
        
        // Map ship to response
        ShipResponse shipResponse = mapToShipResponse(ship);
        
        return FeedItemResponse.builder()
                .ship(shipResponse)
                .project(projectResponse)
                .user(userResponse)
                .build();
    }

    /**
     * Map Ship entity to ShipResponse DTO.
     */
    private ShipResponse mapToShipResponse(Ship ship) {
        return ShipResponse.builder()
                .id(ship.getId())
                .userId(ship.getUserId())
                .projectId(ship.getProjectId())
                .content(ship.getContent())
                .images(ship.getImages())
                .externalLinks(ship.getExternalLinks())
                .likeCount(ship.getLikeCount())
                .commentCount(ship.getCommentCount())
                .trendingScore(ship.getTrendingScore())
                .editWindowOpen(ship.isEditWindowOpen())
                .editWindowExpires(ship.getEditWindowExpires())
                .createdAt(ship.getCreatedAt())
                .updatedAt(ship.getUpdatedAt())
                .build();
    }

    /**
     * Map User entity to UserProfileResponse DTO.
     */
    private UserProfileResponse mapToUserProfileResponse(User user) {
        return UserProfileResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .avatarUrl(user.getAvatarUrl())
                .bio(user.getBio())
                .location(user.getLocation())
                .websiteUrl(user.getWebsiteUrl())
                .followerCount(user.getFollowerCount())
                .followingCount(user.getFollowingCount())
                .projectCount(user.getProjectCount())
                .createdAt(user.getCreatedAt())
                .build();
    }
}
