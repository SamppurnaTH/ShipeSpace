package com.shipspace.controller;

import com.shipspace.dto.NotificationResponse;
import com.shipspace.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST controller for notification endpoints.
 */
@Slf4j
@RestController
@RequestMapping("/notifications")
@RequiredArgsConstructor
@Tag(name = "Notifications", description = "Notification management endpoints")
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    @Operation(
        summary = "Get notifications",
        description = "Retrieve paginated notifications for the authenticated user",
        security = @SecurityRequirement(name = "bearerAuth")
    )
    public ResponseEntity<Page<NotificationResponse>> getNotifications(
            Authentication authentication,
            @Parameter(description = "Page number (0-indexed)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "20") int size) {
        String userId = ((UserDetails) authentication.getPrincipal()).getUsername();
        return ResponseEntity.ok(notificationService.getNotifications(userId, page, size));
    }

    @GetMapping("/unread-count")
    @Operation(
        summary = "Get unread count",
        description = "Get the count of unread notifications",
        security = @SecurityRequirement(name = "bearerAuth")
    )
    public ResponseEntity<Map<String, Long>> getUnreadCount(Authentication authentication) {
        String userId = ((UserDetails) authentication.getPrincipal()).getUsername();
        long count = notificationService.getUnreadCount(userId);
        return ResponseEntity.ok(Map.of("count", count));
    }

    @PutMapping("/{notificationId}/read")
    @Operation(
        summary = "Mark notification as read",
        description = "Mark a specific notification as read",
        security = @SecurityRequirement(name = "bearerAuth")
    )
    public ResponseEntity<NotificationResponse> markAsRead(
            Authentication authentication,
            @PathVariable String notificationId) {
        String userId = ((UserDetails) authentication.getPrincipal()).getUsername();
        return ResponseEntity.ok(notificationService.markAsRead(notificationId, userId));
    }

    @PutMapping("/read-all")
    @Operation(
        summary = "Mark all as read",
        description = "Mark all notifications as read for the authenticated user",
        security = @SecurityRequirement(name = "bearerAuth")
    )
    public ResponseEntity<Void> markAllAsRead(Authentication authentication) {
        String userId = ((UserDetails) authentication.getPrincipal()).getUsername();
        notificationService.markAllAsRead(userId);
        return ResponseEntity.ok().build();
    }
}
