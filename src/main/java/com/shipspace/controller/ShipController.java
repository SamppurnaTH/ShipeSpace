package com.shipspace.controller;

import com.shipspace.dto.ShipCreateRequest;
import com.shipspace.dto.ShipResponse;
import com.shipspace.dto.ShipUpdateRequest;
import com.shipspace.service.ShipService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * REST controller for ship content management endpoints.
 * Handles ship creation, retrieval, updates, and image uploads with proper authentication and authorization.
 * 
 * Implements Requirements 4.1, 4.2, 4.3, 4.4, 4.5:
 * - POST /api/ships - Create new ship with project association
 * - GET /api/ships/{shipId} - Get ship details
 * - PUT /api/ships/{shipId} - Update ship within edit window
 * - POST /api/ships/{shipId}/images - Upload images to ship
 */
@Slf4j
@RestController
@RequestMapping("/api/ships")
@RequiredArgsConstructor
@Tag(name = "Ship Content Management", description = "Ship creation, updates, and image upload endpoints")
public class ShipController {

    private final ShipService shipService;

    /**
     * Creates a new ship for the authenticated user.
     * Validates project association and initializes edit window.
     * 
     * Requirement 4.1: Ship must be associated with an existing project owned by the user.
     * Requirement 4.2: Accept text content and external links in the ship payload.
     * Requirement 4.4: Set edit window to 10 minutes from creation.
     *
     * @param authentication the Spring Security authentication object
     * @param request the ship creation request with content and metadata
     * @return the created ship response with 201 Created status
     */
    @PostMapping
    @Operation(
        summary = "Create new ship",
        description = "Create a new ship (project update) for the authenticated user. Ship must be associated with an existing project owned by the user. Edit window is set to 10 minutes from creation.",
        security = @SecurityRequirement(name = "bearerAuth")
    )
    public ResponseEntity<ShipResponse> createShip(
            Authentication authentication,
            @Valid @RequestBody ShipCreateRequest request) {
        log.info("Received ship creation request for project: {}", request.getProjectId());
        
        // Extract user ID from authentication
        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
        String userId = userDetails.getUsername(); // We store userId in username field
        
        ShipResponse ship = shipService.createShip(userId, request);
        
        log.info("Successfully created ship with ID: {} for user: {}", ship.getId(), userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(ship);
    }

    /**
     * Retrieves a ship by its ID.
     * Returns ship content, engagement metrics, and edit window status.
     *
     * @param shipId the ID of the ship to retrieve
     * @return the ship response with all metadata
     */
    @GetMapping("/{shipId}")
    @Operation(
        summary = "Get ship details",
        description = "Retrieve ship information including content, images, engagement metrics, and edit window status"
    )
    public ResponseEntity<ShipResponse> getShip(
            @Parameter(description = "ID of the ship to retrieve")
            @PathVariable String shipId) {
        log.debug("Received request for ship: {}", shipId);
        
        ShipResponse ship = shipService.getShipById(shipId);
        
        log.debug("Returning ship: {}", ship.getId());
        return ResponseEntity.ok(ship);
    }

    /**
     * Updates a ship's content within the edit window.
     * Only the ship owner can update the ship.
     * Only provided fields will be updated (null fields are ignored).
     * 
     * Requirement 4.4: Updates only allowed within 10 minutes of creation.
     * Requirement 4.5: Reject edits after edit window expires.
     *
     * @param authentication the Spring Security authentication object
     * @param shipId the ID of the ship to update
     * @param request the update request with new content
     * @return the updated ship response
     */
    @PutMapping("/{shipId}")
    @Operation(
        summary = "Update ship",
        description = "Update ship content within the 10-minute edit window. Only the ship owner can perform this operation. Only provided fields will be updated.",
        security = @SecurityRequirement(name = "bearerAuth")
    )
    public ResponseEntity<ShipResponse> updateShip(
            Authentication authentication,
            @Parameter(description = "ID of the ship to update")
            @PathVariable String shipId,
            @Valid @RequestBody ShipUpdateRequest request) {
        log.info("Received ship update request for ship: {}", shipId);
        
        // Extract user ID from authentication
        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
        String userId = userDetails.getUsername(); // We store userId in username field
        
        ShipResponse ship = shipService.updateShip(shipId, userId, request);
        
        log.info("Successfully updated ship: {}", shipId);
        return ResponseEntity.ok(ship);
    }

    /**
     * Uploads images to a ship using Multibase.
     * Images are uploaded to Multibase and references are added to the ship.
     * Only the ship owner can upload images.
     * Images can only be added within the edit window.
     * 
     * Requirement 4.3: Integrate with Multibase_System for content addressing and storage.
     * Requirement 4.4: Image uploads only allowed within edit window.
     *
     * @param authentication the Spring Security authentication object
     * @param shipId the ID of the ship
     * @param files the image files to upload
     * @return the updated ship response with image references
     */
    @PostMapping(value = "/{shipId}/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
        summary = "Upload images to ship",
        description = "Upload images to a ship using Multibase content addressing. Only the ship owner can upload images, and only within the 10-minute edit window.",
        security = @SecurityRequirement(name = "bearerAuth")
    )
    public ResponseEntity<ShipResponse> uploadShipImages(
            Authentication authentication,
            @Parameter(description = "ID of the ship")
            @PathVariable String shipId,
            @Parameter(description = "Image files to upload")
            @RequestParam("files") List<MultipartFile> files) {
        log.info("Received image upload request for ship: {} with {} files", shipId, files.size());
        
        // Extract user ID from authentication
        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
        String userId = userDetails.getUsername(); // We store userId in username field
        
        ShipResponse ship = shipService.uploadShipImages(shipId, userId, files);
        
        log.info("Successfully uploaded {} images to ship: {}", files.size(), shipId);
        return ResponseEntity.ok(ship);
    }
}
