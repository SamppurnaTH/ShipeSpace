package com.shipspace.controller;

import com.shipspace.dto.BuilderDiscoveryResponse;
import com.shipspace.service.BuilderDiscoveryService;
import com.shipspace.service.BuilderDiscoveryService.DiscoverySort;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for the People discovery page.
 * Public endpoint — anonymous users can browse builders.
 * Authenticated users get isFollowing enrichment.
 *
 * <p>Endpoints:
 * <ul>
 *   <li>GET /api/users/discover — paginated builder discovery with sort modes</li>
 *   <li>GET /api/users/discover/search — builder-specific search</li>
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping("/users/discover")
@RequiredArgsConstructor
@Validated
@Tag(name = "People Discovery", description = "Public builder discovery and people search endpoints")
public class PeopleController {

    private final BuilderDiscoveryService discoveryService;

    /**
     * Discovers builders with pagination and sort modes.
     * Public — anonymous users see builders without isFollowing data.
     * Authenticated users get isFollowing enrichment.
     *
     * @param page page number (0-indexed)
     * @param size page size (max 50)
     * @param sort discovery sort mode: discover, top, new
     * @param authentication optional authentication context
     * @return paginated builder discovery responses
     */
    @GetMapping
    @Operation(
            summary = "Discover builders",
            description = "Paginated builder discovery. Public endpoint — anonymous users can browse builders. " +
                    "Authenticated users get isFollowing enrichment. " +
                    "Sort modes: discover (recently active), top (highest ShipScore), new (recently joined)."
    )
    public ResponseEntity<Page<BuilderDiscoveryResponse>> discoverBuilders(
            @Parameter(description = "Page number (0-indexed)")
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @Parameter(description = "Page size (max 50)")
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size,
            @Parameter(description = "Sort mode: discover, top, new")
            @RequestParam(defaultValue = "discover") String sort,
            Authentication authentication) {

        log.debug("People discovery request: page={}, size={}, sort={}", page, size, sort);

        DiscoverySort sortMode = parseSort(sort);
        String viewerUserId = extractUserId(authentication);

        Page<BuilderDiscoveryResponse> result = discoveryService.discoverBuilders(page, size, sortMode, viewerUserId);

        log.debug("Returning {} builders (page {} of {})", result.getNumberOfElements(), page, result.getTotalPages());
        return ResponseEntity.ok(result);
    }

    /**
     * Searches builders by username or bio. People page-specific.
     * Public — anonymous users can search.
     *
     * @param q search query (required, min 1 char)
     * @param page page number
     * @param size page size
     * @param authentication optional authentication context
     * @return paginated search results
     */
    @GetMapping("/search")
    @Operation(
            summary = "Search builders",
            description = "Search builders by username or bio. People page-specific endpoint. " +
                    "Public — anonymous users can search builders."
    )
    public ResponseEntity<Page<BuilderDiscoveryResponse>> searchBuilders(
            @Parameter(description = "Search query")
            @RequestParam String q,
            @Parameter(description = "Page number (0-indexed)")
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @Parameter(description = "Page size (max 50)")
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size,
            Authentication authentication) {

        if (q == null || q.isBlank()) {
            return ResponseEntity.ok(Page.empty());
        }

        // Cap query length to avoid regex abuse
        String sanitizedQuery = q.trim();
        if (sanitizedQuery.length() > 100) {
            sanitizedQuery = sanitizedQuery.substring(0, 100);
        }

        log.debug("People search: q='{}', page={}, size={}", sanitizedQuery, page, size);

        String viewerUserId = extractUserId(authentication);
        Page<BuilderDiscoveryResponse> result = discoveryService.searchBuilders(sanitizedQuery, page, size, viewerUserId);

        log.debug("People search returned {} results", result.getNumberOfElements());
        return ResponseEntity.ok(result);
    }

    private DiscoverySort parseSort(String sort) {
        try {
            return DiscoverySort.valueOf(sort.toUpperCase());
        } catch (IllegalArgumentException e) {
            return DiscoverySort.DISCOVER;
        }
    }

    private String extractUserId(Authentication authentication) {
        if (authentication != null && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof UserDetails userDetails) {
            return userDetails.getUsername();
        }
        return null;
    }
}
