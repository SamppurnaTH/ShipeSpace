package com.shipspace.controller;

import com.shipspace.dto.FeedItemResponse;
import com.shipspace.dto.ProjectResponse;
import com.shipspace.dto.ShipResponse;
import com.shipspace.dto.UserProfileResponse;
import com.shipspace.model.Project;
import com.shipspace.model.Ship;
import com.shipspace.model.User;
import com.shipspace.service.BehavioralTrackingService;
import com.shipspace.service.EngagementService;
import com.shipspace.service.FeedService;
import com.shipspace.service.PersonalizationService;
import com.shipspace.service.ProjectService;
import com.shipspace.service.UserService;
import com.shipspace.util.DtoMapper;
import com.shipspace.config.SloEnforcementService;
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
 * All data access goes through service layer — no direct repository injection.
 * Feed enrichment uses batch lookups: 2 queries per page regardless of page size.
 */
@RestController
@RequestMapping("/feeds")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Feed", description = "Feed generation and content discovery endpoints")
public class FeedController {

    private final FeedService feedService;
    private final PersonalizationService personalizationService;
    private final BehavioralTrackingService behavioralTrackingService;
    private final ProjectService projectService;
    private final UserService userService;
    private final DtoMapper dtoMapper;
    private final SloEnforcementService sloEnforcementService;
    private final EngagementService engagementService;

    @GetMapping("/latest")
    @Operation(summary = "Get latest feed", description = "Ships ordered by newest publication time")
    public ResponseEntity<Page<FeedItemResponse>> getLatestFeed(
            Authentication authentication,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        size = Math.min(size, 50);
        String userId = authentication != null ? authentication.getName() : null;
        Page<Ship> ships = feedService.getLatestFeed(PageRequest.of(page, size));
        return ResponseEntity.ok(enrichPage(ships, userId));
    }

    @GetMapping("/following")
    @Operation(
            summary = "Get following feed",
            description = "Chronological feed of ships from followed users",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Feed retrieved"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    public ResponseEntity<Page<FeedItemResponse>> getFollowingFeed(
            Authentication authentication,
            @Parameter(description = "Page number (0-indexed)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size (max 50)") @RequestParam(defaultValue = "20") int size) {

        size = Math.min(size, 50);
        String userId = authentication.getName();
        Page<Ship> ships = feedService.getFollowingFeed(userId, PageRequest.of(page, size));
        return ResponseEntity.ok(enrichPage(ships, userId));
    }

    @GetMapping("/trending")
    @Operation(summary = "Get trending feed", description = "Ships ranked by engagement score (all time)")
    public ResponseEntity<Page<FeedItemResponse>> getTrendingFeed(
            Authentication authentication,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        size = Math.min(size, 50);
        String userId = authentication != null ? authentication.getName() : null;
        Page<Ship> ships = feedService.getTrendingFeed(PageRequest.of(page, size));
        return ResponseEntity.ok(enrichPage(ships, userId));
    }

    @GetMapping("/trending/{timeWindow}")
    @Operation(summary = "Get trending feed with time window",
            description = "Trending feed within a time window. Format: <number><unit> e.g. 24h, 7d, 30d")
    public ResponseEntity<Page<FeedItemResponse>> getTrendingFeedWithTimeWindow(
            Authentication authentication,
            @PathVariable String timeWindow,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        size = Math.min(size, 50);
        String userId = authentication != null ? authentication.getName() : null;
        Page<Ship> ships = feedService.getTrendingFeedWithTimeWindow(timeWindow, PageRequest.of(page, size));
        return ResponseEntity.ok(enrichPage(ships, userId));
    }

    /**
     * Personalized feed — hybrid 50/50 personalized + trending for warm users,
     * pure trending for cold-start users (<10 behavioral signals).
     * Tracks views asynchronously.
     */
    @GetMapping("/personalized")
    @Operation(
            summary = "Get personalized feed",
            description = "Hybrid personalized + trending feed. Falls back to trending for cold-start users.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Feed retrieved"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    public ResponseEntity<Page<FeedItemResponse>> getPersonalizedFeed(
            Authentication authentication,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        size = Math.min(size, 50);
        String userId = authentication.getName();
        log.debug("Getting personalized feed for user: {}", userId);

        Page<Ship> ships;
        boolean degraded = sloEnforcementService.isPersonalizationDisabled();
        if (degraded) {
            log.info("Personalization disabled (SLO degradation level {}), serving trending for user {}",
                    sloEnforcementService.getEffectiveLevel(), userId);
            ships = feedService.getTrendingFeed(PageRequest.of(page, size));
        } else {
            ships = personalizationService.getPersonalizedFeed(userId, PageRequest.of(page, size));
        }
        Page<FeedItemResponse> response = enrichPage(ships, userId);

        ships.getContent().forEach(ship -> behavioralTrackingService.trackView(userId, ship.getId()));

        return ResponseEntity.ok()
                .header("X-Feed-Mode", degraded ? "trending-fallback" : "personalized")
                .body(response);
    }

    // ── Batch enrichment — 2 queries per page regardless of page size ─────────

    private Page<FeedItemResponse> enrichPage(Page<Ship> ships, String userId) {
        List<Ship> content = ships.getContent();
        if (content.isEmpty()) {
            return new PageImpl<>(List.of(), ships.getPageable(), ships.getTotalElements());
        }

        Set<String> projectIds = content.stream().map(Ship::getProjectId).collect(Collectors.toSet());
        Set<String> userIds = content.stream().map(Ship::getUserId).collect(Collectors.toSet());
        Set<String> shipIds = content.stream().map(Ship::getId).collect(Collectors.toSet());

        Map<String, Project> projectMap = projectService.findAllByIds(projectIds)
                .stream().collect(Collectors.toMap(Project::getId, Function.identity()));
        Map<String, User> userMap = userService.findAllByIds(userIds)
                .stream().collect(Collectors.toMap(User::getId, Function.identity()));

        // Single bulk query for like status — only when user is authenticated
        Set<String> likedIds = (userId != null)
                ? engagementService.getLikedShipIds(userId, shipIds)
                : Set.of();

        List<FeedItemResponse> items = content.stream()
                .map(ship -> toFeedItem(ship, projectMap.get(ship.getProjectId()),
                        userMap.get(ship.getUserId()), userId != null ? likedIds.contains(ship.getId()) : null))
                .collect(Collectors.toList());

        return new PageImpl<>(items, ships.getPageable(), ships.getTotalElements());
    }

    private FeedItemResponse toFeedItem(Ship ship, Project project, User user, Boolean liked) {
        return FeedItemResponse.builder()
                .ship(dtoMapper.toShipResponse(ship))
                .project(project != null ? dtoMapper.toProjectResponse(project) : null)
                .user(user != null ? toUserProfileResponse(user) : null)
                .liked(liked)
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
