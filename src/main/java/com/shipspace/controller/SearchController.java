package com.shipspace.controller;

import com.shipspace.dto.ShipResponse;
import com.shipspace.dto.UserProfileResponse;
import com.shipspace.exception.TooManyRequestsException;
import com.shipspace.service.RateLimitingService;
import com.shipspace.service.SearchService;
import com.shipspace.util.InputSanitizer;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST controller for search endpoints.
 *
 * Delegates to SearchService which uses MongoDB $text queries (not regex).
 * Requires text indexes on users (username, bio) and ships (content) —
 * created at startup by MongoIndexInitializer.
 *
 * Rate limiting strategy:
 *   - Authenticated users: 60 req/min by userId (generous, trusted)
 *   - Anonymous users:     20 req/min by IP     (tighter, untrusted)
 * This prevents IP-spoofing abuse while not penalising logged-in users.
 */
@RestController
@RequestMapping("/search")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Search", description = "Search for users and ships")
public class SearchController {

    private final SearchService searchService;
    private final RateLimitingService rateLimitingService;
    private final InputSanitizer inputSanitizer;

    @Value("${search.debug-headers-enabled:false}")
    private boolean debugHeadersEnabled;

    private static final int MAX_RESULTS      = 20;
    private static final int MAX_QUERY_LENGTH = 100;
    private static final int AUTH_RATE_LIMIT  = 60;
    private static final int ANON_RATE_LIMIT  = 20;
    private static final int RATE_WINDOW_SECS = 60;

    @GetMapping
    @Operation(summary = "Search users and ships")
    public ResponseEntity<Map<String, Object>> search(
            @RequestParam String q,
            @RequestParam(defaultValue = "10") int size,
            Authentication authentication,
            HttpServletRequest request,
            HttpServletResponse response) {

        // Dual rate limiting: userId when authenticated, IP when anonymous
        if (authentication != null && authentication.isAuthenticated()) {
            String userId = authentication.getName();
            if (rateLimitingService.isRateLimited(userId, "search", AUTH_RATE_LIMIT, RATE_WINDOW_SECS, response)) {
                throw new TooManyRequestsException("Too many search requests. Please slow down.");
            }
        } else {
            String clientIp = getClientIp(request);
            if (rateLimitingService.isRateLimited(clientIp, "search:anon", ANON_RATE_LIMIT, RATE_WINDOW_SECS, response)) {
                throw new TooManyRequestsException("Too many search requests. Please slow down.");
            }
        }

        // Sanitize before any processing — strips HTML tags, control chars, bidi overrides
        String sanitized = inputSanitizer.sanitize(q, MAX_QUERY_LENGTH);
        if (sanitized == null || sanitized.isBlank()) {
            return ResponseEntity.ok(Map.of("users", List.of(), "ships", List.of()));
        }

        size = Math.min(size, MAX_RESULTS);

        SearchService.SearchResult<UserProfileResponse> users = searchService.searchUsers(sanitized, size);
        SearchService.SearchResult<ShipResponse> ships = searchService.searchShips(sanitized, size);

        // X-Search-Path is emitted when:
        //   a) search.debug-headers-enabled=true (all requests, for staging/load-test environments)
        //   b) authenticated user sends X-Debug: true (per-request, targeted debugging only)
        // Unauthenticated callers cannot trigger the debug header — prevents behavior probing.
        boolean isAuthenticated = authentication != null && authentication.isAuthenticated();
        boolean perRequestDebug = isAuthenticated && "true".equalsIgnoreCase(request.getHeader("X-Debug"));
        boolean emitDebugHeader = debugHeadersEnabled || perRequestDebug;

        ResponseEntity.BodyBuilder builder = ResponseEntity.ok();
        if (emitDebugHeader) {
            String searchPath = (SearchService.SearchResult.PATH_FALLBACK.equals(users.path())
                    || SearchService.SearchResult.PATH_FALLBACK.equals(ships.path()))
                    ? SearchService.SearchResult.PATH_FALLBACK
                    : SearchService.SearchResult.PATH_TEXT;
            builder = builder.header("X-Search-Path", searchPath);
        }

        return builder.body(Map.of("users", users.items(), "ships", ships.items()));
    }

    private String getClientIp(HttpServletRequest request) {
        String remoteAddr = request.getRemoteAddr();
        if (isTrustedProxy(remoteAddr)) {
            String forwarded = request.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                return forwarded.split(",")[0].trim();
            }
            String realIp = request.getHeader("X-Real-IP");
            if (realIp != null && !realIp.isBlank()) {
                return realIp;
            }
        }
        return remoteAddr;
    }

    private boolean isTrustedProxy(String remoteAddr) {
        if (remoteAddr == null) return false;
        return remoteAddr.equals("127.0.0.1")
                || remoteAddr.equals("::1")
                || remoteAddr.startsWith("10.")
                || remoteAddr.startsWith("172.16.")
                || remoteAddr.startsWith("172.17.")
                || remoteAddr.startsWith("172.18.")
                || remoteAddr.startsWith("172.19.")
                || remoteAddr.startsWith("172.2")
                || remoteAddr.startsWith("172.30.")
                || remoteAddr.startsWith("172.31.")
                || remoteAddr.startsWith("192.168.");
    }
}
