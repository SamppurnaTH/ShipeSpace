package com.shipspace.controller;

import com.shipspace.dto.CurrentUserResponse;
import com.shipspace.dto.ProjectResponse;
import com.shipspace.dto.UserActivityResponse;
import com.shipspace.dto.UserProfileResponse;
import com.shipspace.dto.UserUpdateRequest;
import com.shipspace.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for user profile management endpoints.
 * Handles user profile retrieval, updates, and activity aggregation.
 * 
 * Implements Requirements 2.1, 2.3, 2.4, 2.5:
 * - GET /api/users/{userId} - Get public user profile by ID
 * - PUT /api/users/me - Update current user's profile
 * - GET /api/users/me/projects - Get current user's projects
 * - GET /api/users/me/activity - Get current user's activity history
 */
@Slf4j
@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
@Tag(name = "User Management", description = "User profile and activity endpoints")
public class UserController {

    private final UserService userService;

    /**
     * Gets a user's public profile by user ID.
     * Returns publicly visible information for any user.
     *
     * @param userId the ID of the user to retrieve
     * @return public profile information
     */
    @GetMapping("/{userId}")
    @Operation(
        summary = "Get user profile",
        description = "Retrieve public profile information for any user by their ID"
    )
    public ResponseEntity<UserProfileResponse> getUserProfile(@PathVariable String userId) {
        log.debug("Received request for user profile: {}", userId);
        
        UserProfileResponse profile = userService.getPublicProfile(userId);
        
        log.debug("Returning profile for user: {}", profile.getUsername());
        return ResponseEntity.ok(profile);
    }

    /**
     * Updates the current authenticated user's profile.
     * Only updates fields that are provided in the request.
     *
     * @param authentication the Spring Security authentication object
     * @param updateRequest the profile update request
     * @return the updated user information
     */
    @PutMapping("/me")
    @Operation(
        summary = "Update current user profile",
        description = "Update the authenticated user's profile information",
        security = @SecurityRequirement(name = "bearerAuth")
    )
    public ResponseEntity<CurrentUserResponse> updateCurrentUserProfile(
            Authentication authentication,
            @Valid @RequestBody UserUpdateRequest updateRequest) {
        log.info("Received profile update request");
        
        // Extract user ID from authentication
        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
        String userId = userDetails.getUsername(); // We store userId in username field
        
        CurrentUserResponse updatedUser = userService.updateUserProfile(userId, updateRequest);
        
        log.info("Successfully updated profile for user: {}", updatedUser.getUsername());
        return ResponseEntity.ok(updatedUser);
    }

    /**
     * Gets the current authenticated user's projects.
     *
     * @param authentication the Spring Security authentication object
     * @return list of projects owned by the current user
     */
    @GetMapping("/me/projects")
    @Operation(
        summary = "Get current user's projects",
        description = "Retrieve all projects owned by the authenticated user",
        security = @SecurityRequirement(name = "bearerAuth")
    )
    public ResponseEntity<List<ProjectResponse>> getCurrentUserProjects(Authentication authentication) {
        log.debug("Received request for current user's projects");
        
        // Extract user ID from authentication
        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
        String userId = userDetails.getUsername(); // We store userId in username field
        
        List<ProjectResponse> projects = userService.getUserProjects(userId);
        
        log.debug("Returning {} projects for current user", projects.size());
        return ResponseEntity.ok(projects);
    }

    /**
     * Gets the current authenticated user's activity history.
     * Returns recent ships and engagement metrics.
     *
     * @param authentication the Spring Security authentication object
     * @return user activity with recent ships and metrics
     */
    @GetMapping("/me/activity")
    @Operation(
        summary = "Get current user's activity",
        description = "Retrieve recent ships and engagement metrics for the authenticated user",
        security = @SecurityRequirement(name = "bearerAuth")
    )
    public ResponseEntity<UserActivityResponse> getCurrentUserActivity(Authentication authentication) {
        log.debug("Received request for current user's activity");
        
        // Extract user ID from authentication
        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
        String userId = userDetails.getUsername(); // We store userId in username field
        
        UserActivityResponse activity = userService.getUserActivity(userId);
        
        log.debug("Returning activity for current user with {} recent ships", 
                activity.getRecentShips().size());
        return ResponseEntity.ok(activity);
    }
}
