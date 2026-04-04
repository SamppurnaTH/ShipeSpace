package com.shipspace.controller;

import com.shipspace.dto.ProjectCreateRequest;
import com.shipspace.dto.ProjectResponse;
import com.shipspace.dto.ProjectUpdateRequest;
import com.shipspace.dto.ShipResponse;
import com.shipspace.model.Ship;
import com.shipspace.service.ProjectService;
import com.shipspace.util.DtoMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for project management endpoints.
 * Handles project CRUD operations with proper authentication and authorization.
 * 
 * Implements Requirements 3.1, 3.2, 3.3, 3.4, 3.5:
 * - POST /api/projects - Create new project
 * - GET /api/projects/{projectId} - Get project details
 * - PUT /api/projects/{projectId} - Update project metadata
 * - GET /api/projects/{projectId}/ships - Get project's ships chronologically
 */
@Slf4j
@RestController
@RequestMapping("/projects")
@RequiredArgsConstructor
@Validated
@Tag(name = "Project Management", description = "Project CRUD and ship history endpoints")
public class ProjectController {

    private final ProjectService projectService;
    private final DtoMapper dtoMapper;

    /**
     * Creates a new project for the authenticated user.
     * Validates input data and initializes project metadata.
     *
     * @param authentication the Spring Security authentication object
     * @param request the project creation request with metadata
     * @return the created project response with 201 Created status
     */
    @PostMapping
    @Operation(
        summary = "Create new project",
        description = "Create a new project for the authenticated user with metadata including name, description, tech tags, and external links",
        security = @SecurityRequirement(name = "bearerAuth")
    )
    public ResponseEntity<ProjectResponse> createProject(
            Authentication authentication,
            @Valid @RequestBody ProjectCreateRequest request) {
        log.info("Received project creation request");
        
        // Extract user ID from authentication
        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
        String userId = userDetails.getUsername(); // We store userId in username field
        
        ProjectResponse project = projectService.createProject(userId, request);
        
        log.info("Successfully created project with ID: {} for user: {}", project.getId(), userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(project);
    }

    /**
     * Retrieves a project by its ID.
     * Returns project metadata including ship count and last ship timestamp.
     *
     * @param projectId the ID of the project to retrieve
     * @return the project response with metadata
     */
    @GetMapping("/{projectId}")
    @Operation(
        summary = "Get project details",
        description = "Retrieve project information including metadata and associated ships count"
    )
    public ResponseEntity<ProjectResponse> getProject(
            @Parameter(description = "ID of the project to retrieve")
            @PathVariable String projectId) {
        log.debug("Received request for project: {}", projectId);
        
        ProjectResponse project = projectService.getProjectById(projectId);
        
        log.debug("Returning project: {}", project.getName());
        return ResponseEntity.ok(project);
    }

    /**
     * Updates a project's metadata.
     * Only the project owner can update the project.
     * Only provided fields will be updated (null fields are ignored).
     *
     * @param authentication the Spring Security authentication object
     * @param projectId the ID of the project to update
     * @param request the update request with new metadata
     * @return the updated project response
     */
    @PutMapping("/{projectId}")
    @Operation(
        summary = "Update project",
        description = "Update project metadata. Only the project owner can perform this operation. Only provided fields will be updated.",
        security = @SecurityRequirement(name = "bearerAuth")
    )
    public ResponseEntity<ProjectResponse> updateProject(
            Authentication authentication,
            @Parameter(description = "ID of the project to update")
            @PathVariable String projectId,
            @Valid @RequestBody ProjectUpdateRequest request) {
        log.info("Received project update request for project: {}", projectId);
        
        // Extract user ID from authentication
        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
        String userId = userDetails.getUsername(); // We store userId in username field
        
        ProjectResponse project = projectService.updateProject(projectId, userId, request);
        
        log.info("Successfully updated project: {}", projectId);
        return ResponseEntity.ok(project);
    }

    /**
     * Retrieves all ships associated with a project in chronological order.
     * Returns ships sorted by creation date (most recent first) with pagination.
     *
     * @param projectId the ID of the project
     * @param page the page number (0-indexed, default: 0)
     * @param size the page size (default: 20)
     * @return page of ships for the project
     */
    @GetMapping("/{projectId}/ships")
    @Operation(
        summary = "Get project ships",
        description = "Retrieve all ships (updates) associated with a project in reverse chronological order"
    )
    public ResponseEntity<Page<ShipResponse>> getProjectShips(
            @Parameter(description = "ID of the project")
            @PathVariable String projectId,
            @Parameter(description = "Page number (0-indexed)")
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @Parameter(description = "Page size (max 100)")
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        log.debug("Received request for ships of project: {} (page: {}, size: {})", projectId, page, size);
        
        Page<Ship> ships = projectService.getProjectShips(projectId, page, size);
        Page<ShipResponse> shipResponses = ships.map(dtoMapper::toShipResponse);
        
        log.debug("Returning {} ships for project: {}", shipResponses.getNumberOfElements(), projectId);
        return ResponseEntity.ok(shipResponses);
    }

    /**
     * Deletes a project and all its associated ships.
     * Only the project owner can delete.
     */
    @DeleteMapping("/{projectId}")
    @Operation(
        summary = "Delete project",
        description = "Permanently delete a project and all its ships. Only the project owner can perform this operation.",
        security = @SecurityRequirement(name = "bearerAuth")
    )
    public ResponseEntity<Void> deleteProject(
            Authentication authentication,
            @Parameter(description = "ID of the project to delete")
            @PathVariable String projectId) {
        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
        String userId = userDetails.getUsername();
        log.info("Delete project request for project: {} by user: {}", projectId, userId);
        projectService.deleteProject(projectId, userId);
        return ResponseEntity.noContent().build();
    }
}
