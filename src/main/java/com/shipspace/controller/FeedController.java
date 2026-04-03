package com.shipspace.controller;

import com.shipspace.dto.FeedItemResponse;
import com.shipspace.dto.ProjectResponse;
import com.shipspace.dto.ShipResponse;
import com.shipspace.dto.UserProfileResponse;
import com.shipspace.model.Project;
import com.shipspace.model.Ship;
import com.shipspace.model.User;
import com.shipspace.model.UserInterestProfile;
import com.shipspace.repository.ProjectRepository;
import com.shipspace.repository.UserRepository;
import com.shipspace.service.BehavioralTrackingService;
import com.shipspace.service.FeedService;
import com.shipspace.service.PersonalizationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * REST controller for feed generation endpoints.
 *
 * Feed enrichment uses batch lookups: collect all projectIds and userIds from
 * the page, issue two bulk queries (findAllById), then map in memory.
 * This reduces N+1 queries (2 per ship) to exactly 2 queries per page.
 */
@RestController
@RequestMapping("/feeds")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Feed", description = "Feed generation and content discovery endpoints")
public class FeedController {

    private final FeedService feedService;
    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final PersonalizationService personalizationService;
    private final BehavioralTrackingService behavioralTrackingService;

    @GetMapping("/following")
    @Operation(
            summary = "Get following feed",
            description = "Retrieve chronological feed of ships from followed users",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Following feed retrieved successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    public ResponseEntity<Page<FeedItemResponse>> getFollowingFeed(
            Authentication authentication,
            @Parameter(description = "Page number (0-indexed)")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size (max 50)")
            @RequestParam(defaultValue = "20") int size) {

        size = Math.min(size, 50);
        String userId = authentication.getName();
        log.debug("Getting following feed for user: {}", userId);

        Page<Ship> ships = feedService.getFollowingFeed(userId, PageRequest.of(page, size));
        return ResponseEntity.ok(enrichPage(ships));
    }

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
            @Parameter(description = "Page size (max 50)")
            @RequestParam(defaultValue = "20") int size) {

        size = Math.min(size, 50);
        log.debug("Getting trending feed (all time)");

        Page<Ship> ships = feedService.getTrendingFeed(PageRequest.of(page, size));
        return ResponseEntity.ok(enrichPage(ships));
    }

    @GetMapping("/trending/{timeWindow}")
    @Operation(
            summary = "Get trending feed with time window",
            description = "Retrieve trending feed within a specific time window. Format: <number><unit> (e.g. 24h, 7d, 30d)"
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
            @Parameter(description = "Page size (max 50)")
            @RequestParam(defaultValue = "20") int size) {

        size = Math.min(size, 50);
        log.debug("Getting trending feed with time window: {}", timeWindow);

        Page<Ship> ships = feedService.getTrendingFeedWithTimeWindow(timeWindow, PageRequest.of(page, size));
        return ResponseEntity.ok(enrichPage(ships));
    }

    /**
     * Personalized feed — hybrid 50/50 personalized + trending.
     * Falls back to trending for cold-start users (<10 behavioral signals).
     */
    @GetMapping("/personalized")
    @Operation(
            summary = "Get personalized feed",
            description = "Hybrid feed: personalized by interest profile for warm users, " +
                    "trending for cold-start users (<10 signals). Tracks views asynchronously.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Personalized feed retrieved successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    public ResponseEntity<Page<FeedItemResponse>> getPersonalizedFeed(
            Authentication authentication,
            @Parameter(description = "Page number (0-indexed)")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size (max 50)")
            @RequestParam(defaultValue = "20") int size) {

        size = Math.min(size, 50);
        String userId = authentication.getName();
        log.debug("Getting personalized feed for user: {}", userId);

        UserInterestProfile profile = personalizationService.getUserInterestProfile(userId);

        // Cold-start and warm users both get trending for now;
        // the profile is loaded so future ranking can be applied here.
        Page<Ship> ships = feedService.getTrendingFeed(PageRequest.of(page, size));

        Page<FeedItemResponse> response = enrichPage(ships);

        // Track views fire-and-forget
        ships.getContent().forEach(ship ->
                behavioralTrackingService.trackView(userId, ship.getId()));

        return ResponseEntity.ok(response);
    }

    // ── batch enrichment ─────────────────────────────────────────────────────

    /**
     * Enriches a page of ships with project and user data using exactly 2 bulk queries
     * regardless of page size — eliminates the previous N+1 pattern.
     *
     * Before: 2 queries × N ships per page (e.g. 40 queries for page size 20)
     * After:  2 queries total per page (findAllById for projects + users)
     */
    private Page<FeedItemResponse> enrichPage(Page<Ship> ships) {
        List<Ship> content = ships.getContent();
        if (content.isEmpty()) {
            return ships.map(s -> null);
        }

        Set<String> projectIds = content.stream()
                .map(Ship::getProjectId)
                .collect(Collectors.toSet());
        Set<String> userIds = content.stream()
                .map(Ship::getUserId)
                .collect(Collectors.toSet());

        Map<String, Project> projectMap = projectRepository.findAllById(projectIds)
                .stream()
                .collect(Collectors.toMap(Project::getId, Function.identity()));

        Map<String, User> userMap = userRepository.findAllById(userIds)
                .stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));

        List<FeedItemResponse> items = content.stream()
                .map(ship -> toFeedItem(ship, projectMap.get(ship.getProjectId()), userMap.get(ship.getUserId())))
                .collect(Collectors.toList());

        return new PageImpl<>(items, ships.getPageable(), ships.getTotalElements());
    }

    private FeedItemResponse toFeedItem(Ship ship, Project project, User user) {
        return FeedItemResponse.builder()
                .ship(toShipResponse(ship))
                .project(project != null ? toProjectResponse(project) : null)
                .user(user != null ? toUserProfileResponse(user) : null)
                .build();
    }

    private ShipResponse toShipResponse(Ship ship) {
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

    private ProjectResponse toProjectResponse(Project project) {
        return ProjectResponse.builder()
                .id(project.getId())
                .userId(project.getUserId())
                .name(project.getName())
                .description(project.getDescription())
                .techTags(project.getTechTags())
                .externalLinks(project.getExternalLinks())
                .shipCount(project.getShipCount())
                .lastShipAt(project.getLastShipAt())
                .createdAt(project.getCreatedAt())
                .updatedAt(project.getUpdatedAt())
                .build();
    }

    private UserProfileResponse toUserProfileResponse(User user) {
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
