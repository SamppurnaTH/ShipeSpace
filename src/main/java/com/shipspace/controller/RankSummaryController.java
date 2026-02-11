package com.shipspace.controller;

import com.shipspace.dto.RankSummaryResponse;
import com.shipspace.exception.ResourceNotFoundException;
import com.shipspace.model.User;
import com.shipspace.repository.UserRepository;
import com.shipspace.service.ShipScoreService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.concurrent.TimeUnit;

/**
 * REST controller for rank and level progression summary endpoints (v1).
 * Provides API access to user progression data including ShipScore, level, rank, and next level requirements.
 * 
 * <p>This controller implements the rank summary API endpoint that allows clients to retrieve
 * a user's current progression status in the gamification system.
 * 
 * <p>Implements Requirements 12.1, 12.2, 12.3, 12.5:
 * - GET /v1/users/{id}/rank-summary - Get user's rank and level progression summary
 * 
 * @version v1 - Initial release
 * @see <a href=".kiro/specs/rank-level-progression/requirements.md">Requirements 12.1, 12.2, 12.3, 12.5</a>
 */
@Slf4j
@RestController
@RequestMapping("/v1/users")
@RequiredArgsConstructor
@Tag(name = "Rank Progression", description = "User rank and level progression endpoints")
public class RankSummaryController {

    private final UserRepository userRepository;
    private final ShipScoreService shipScoreService;

    /**
     * Retrieves a user's rank and level progression summary.
     * 
     * <p>Returns comprehensive progression data including:
     * <ul>
     *   <li>shipScore: The user's accumulated ShipScore points</li>
     *   <li>level: The user's current level (1-30)</li>
     *   <li>rank: The user's current rank tier as a string (e.g., "DOCKED", "FLEET_ADMIRAL")</li>
     *   <li>nextLevelScore: The ShipScore required to reach the next level (null if at max level)</li>
     * </ul>
     * 
     * <p>The nextLevelScore is calculated as the minimum ShipScore needed to advance to the next level.
     * For users at level 30 (maximum level), this value will be null.
     * 
     * <p>Cache Policy:
     * <ul>
     *   <li>Cacheable for 60 seconds (short TTL due to frequent updates)</li>
     *   <li>Private cache (must-revalidate)</li>
     *   <li>No-store for highly dynamic user data</li>
     * </ul>
     * 
     * @param id the ID of the user whose rank summary is being requested
     * @return ResponseEntity containing the RankSummaryResponse with progression data
     * @throws ResourceNotFoundException if no user exists with the given ID (results in 404 Not Found)
     * @see <a href=".kiro/specs/rank-level-progression/requirements.md">Requirements 12.1, 12.2, 12.3, 12.4, 12.5, 12.6</a>
     */
    @GetMapping("/{id}/rank-summary")
    @Operation(
        summary = "Get user rank summary",
        description = "Retrieve a user's rank and level progression summary including ShipScore, level, rank tier, and next level requirements"
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "Successfully retrieved rank summary",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = RankSummaryResponse.class)
            ),
            headers = {
                @Header(name = "X-RateLimit-Limit", description = "Maximum requests allowed per minute"),
                @Header(name = "X-RateLimit-Remaining", description = "Remaining requests in current window"),
                @Header(name = "X-RateLimit-Reset", description = "Time until rate limit resets (Unix timestamp)")
            }
        ),
        @ApiResponse(
            responseCode = "404",
            description = "User not found",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = com.shipspace.exception.ErrorResponse.class)
            )
        ),
        @ApiResponse(
            responseCode = "401",
            description = "Unauthorized - Invalid or missing authentication",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = com.shipspace.exception.ErrorResponse.class)
            )
        )
    })
    public ResponseEntity<RankSummaryResponse> getRankSummary(
            @Parameter(description = "ID of the user")
            @PathVariable String id) {
        log.debug("Received request for rank summary for user: {}", id);
        
        // Fetch user by id (throw ResourceNotFoundException if not found)
        User user = userRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("User", id));
        
        // Calculate nextLevelScore using shipScoreService
        Integer nextLevelScore = shipScoreService.calculateNextLevelScore(
            user.getLevel(),
            user.getShipScore()
        );
        
        // Build RankSummaryResponse with user data
        RankSummaryResponse response = RankSummaryResponse.builder()
            .shipScore(user.getShipScore())
            .level(user.getLevel())
            .rank(user.getRank().name())
            .nextLevelScore(nextLevelScore)
            .build();
        
        log.debug("Returning rank summary for user: {} (Level: {}, Rank: {})", 
                id, user.getLevel(), user.getRank());
        
        // Return with cache headers - private, no-store for dynamic data
        return ResponseEntity.ok()
            .cacheControl(CacheControl.noCache().cachePrivate())
            .body(response);
    }

    /**
     * Invalidates the rank summary cache for a specific user.
     * 
     * <p>This endpoint can be called after score updates to ensure
     * the next request returns fresh data.
     * 
     * @param id the ID of the user whose cache should be invalidated
     * @return 204 No Content on success
     */
    @DeleteMapping("/{id}/rank-summary/cache")
    @Operation(
        summary = "Invalidate rank summary cache",
        description = "Invalidate cached rank summary for a specific user (admin operation)"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "204", description = "Cache invalidated successfully"),
        @ApiResponse(responseCode = "404", description = "User not found")
    })
    public ResponseEntity<Void> invalidateCache(
            @Parameter(description = "ID of the user")
            @PathVariable String id) {
        // Verify user exists
        userRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("User", id));
        
        log.info("Cache invalidated for user rank summary: {}", id);
        
        return ResponseEntity.noContent().build();
    }
}
