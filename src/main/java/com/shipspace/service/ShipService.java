package com.shipspace.service;

import com.shipspace.dto.ShipCreateRequest;
import com.shipspace.dto.ShipResponse;
import com.shipspace.dto.ShipUpdateRequest;
import com.shipspace.exception.ForbiddenException;
import com.shipspace.exception.ResourceNotFoundException;
import com.shipspace.model.ImageReference;
import com.shipspace.model.Ship;
import com.shipspace.repository.ShipRepository;
import com.shipspace.util.InputSanitizer;
import io.micrometer.core.instrument.Counter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * Service for ship content management operations.
 * Handles ship creation, retrieval, updates with edit window enforcement,
 * project association validation, and content processing.
 * 
 * Implements Requirements 4.1, 4.2, 4.4, 4.5:
 * - 4.1: Ship creation with project association validation
 * - 4.2: Accept text content, image references, and external links
 * - 4.4: Edit window enforcement (10 minutes)
 * - 4.5: Reject edits after edit window expires
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ShipService {

    private final ShipRepository shipRepository;
    private final ProjectService projectService;
    private final MultibaseService multibaseService;
    private final CascadeDeleteService cascadeDeleteService;
    private final InputSanitizer inputSanitizer;
    private final Counter shipCreatedCounter;

    /**
     * Edit window duration in minutes.
     */
    private static final int EDIT_WINDOW_MINUTES = 10;

    /**
     * Creates a new ship for the authenticated user.
     * Validates project association and initializes edit window.
     * 
     * Requirement 4.1: Ship must be associated with an existing project owned by the user.
     * Requirement 4.2: Accept text content and external links in the ship payload.
     * Requirement 4.4: Set edit window to 10 minutes from creation.
     * 
     * @param userId the ID of the user creating the ship
     * @param request the ship creation request with content and metadata
     * @return the created ship response
     * @throws ResourceNotFoundException if project is not found
     * @throws ForbiddenException if user does not own the project
     */
    @Transactional
    public ShipResponse createShip(String userId, ShipCreateRequest request) {
        log.info("Creating ship for user ID: {} on project ID: {}", userId, request.getProjectId());
        
        // Validate project exists and user owns it (Requirement 4.1)
        projectService.verifyProjectOwnership(request.getProjectId(), userId);
        
        // Calculate edit window expiration (Requirement 4.4)
        Instant now = Instant.now();
        Instant editWindowExpires = now.plus(EDIT_WINDOW_MINUTES, ChronoUnit.MINUTES);
        
        // Create ship entity (Requirement 4.2)
        Ship ship = Ship.builder()
                .userId(userId)
                .projectId(request.getProjectId())
                .content(inputSanitizer.sanitize(request.getContent(), 5000))
                .externalLinks(request.getExternalLinks() != null ?
                        request.getExternalLinks() : new ArrayList<>())
                .images(new ArrayList<>())
                .likeCount(0)
                .commentCount(0)
                .trendingScore(0.0)
                .editWindowExpires(editWindowExpires)
                .createdAt(now)
                .updatedAt(now)
                .build();
        
        // Save ship
        Ship savedShip = shipRepository.save(ship);
        log.info("Successfully created ship with ID: {} for user: {}", savedShip.getId(), userId);

        // Increment Prometheus counter
        shipCreatedCounter.increment();
        
        // Update project metadata (ship count and last ship timestamp)
        projectService.updateProjectShipMetadata(request.getProjectId());
        
        return mapToResponse(savedShip);
    }

    /**
     * Retrieves a ship by its ID.
     * Includes ship content, engagement metrics, and edit window status.
     * 
     * @param shipId the ID of the ship to retrieve
     * @return the ship response with all metadata
     * @throws ResourceNotFoundException if ship is not found
     */
    public ShipResponse getShipById(String shipId) {
        log.debug("Retrieving ship with ID: {}", shipId);
        
        Ship ship = shipRepository.findById(shipId)
                .orElseThrow(() -> new ResourceNotFoundException("Ship", shipId));
        
        return mapToResponse(ship);
    }

    /**
     * Updates a ship's content within the edit window.
     * Only the ship owner can update the ship.
     * Only provided fields will be updated (null fields are ignored).
     * 
     * Requirement 4.4: Updates only allowed within 10 minutes of creation.
     * Requirement 4.5: Reject edits after edit window expires.
     * 
     * @param shipId the ID of the ship to update
     * @param userId the ID of the user attempting the update
     * @param request the update request with new content
     * @return the updated ship response
     * @throws ResourceNotFoundException if ship is not found
     * @throws ForbiddenException if user is not the ship owner or edit window expired
     */
    @Transactional
    public ShipResponse updateShip(String shipId, String userId, ShipUpdateRequest request) {
        log.info("Updating ship ID: {} by user ID: {}", shipId, userId);
        
        // Retrieve ship
        Ship ship = shipRepository.findById(shipId)
                .orElseThrow(() -> new ResourceNotFoundException("Ship", shipId));
        
        // Verify ownership
        if (!ship.getUserId().equals(userId)) {
            log.warn("User {} attempted to update ship {} owned by {}", 
                    userId, shipId, ship.getUserId());
            throw new ForbiddenException("update", "ship");
        }
        
        // Verify edit window is still open (Requirement 4.4, 4.5)
        if (!ship.isEditWindowOpen()) {
            log.warn("User {} attempted to update ship {} after edit window expired at {}", 
                    userId, shipId, ship.getEditWindowExpires());
            throw new ForbiddenException("Edit window has expired. Ships can only be edited within " + 
                    EDIT_WINDOW_MINUTES + " minutes of creation.");
        }
        
        // Update only provided fields
        if (request.getContent() != null) {
            ship.setContent(inputSanitizer.sanitize(request.getContent(), 5000));
        }
        if (request.getExternalLinks() != null) {
            ship.setExternalLinks(request.getExternalLinks());
        }
        
        ship.setUpdatedAt(Instant.now());
        
        // Save updated ship
        Ship updatedShip = shipRepository.save(ship);
        log.info("Successfully updated ship ID: {}", shipId);
        
        return mapToResponse(updatedShip);
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
     * @param shipId the ID of the ship
     * @param userId the ID of the user uploading images
     * @param files the image files to upload
     * @return the updated ship response with image references
     * @throws ResourceNotFoundException if ship is not found
     * @throws ForbiddenException if user is not the ship owner or edit window expired
     */
    @Transactional
    public ShipResponse uploadShipImages(String shipId, String userId, List<MultipartFile> files) {
        log.info("Uploading {} images to ship ID: {} by user ID: {}", files.size(), shipId, userId);
        
        // Retrieve ship
        Ship ship = shipRepository.findById(shipId)
                .orElseThrow(() -> new ResourceNotFoundException("Ship", shipId));
        
        // Verify ownership
        if (!ship.getUserId().equals(userId)) {
            log.warn("User {} attempted to upload images to ship {} owned by {}", 
                    userId, shipId, ship.getUserId());
            throw new ForbiddenException("upload images to", "ship");
        }
        
        // Verify edit window is still open
        if (!ship.isEditWindowOpen()) {
            log.warn("User {} attempted to upload images to ship {} after edit window expired at {}", 
                    userId, shipId, ship.getEditWindowExpires());
            throw new ForbiddenException("Edit window has expired. Images can only be added within " + 
                    EDIT_WINDOW_MINUTES + " minutes of ship creation.");
        }
        
        // Upload images to Multibase (Requirement 4.3)
        List<ImageReference> imageReferences = new ArrayList<>();
        List<String> failedFiles = new ArrayList<>();
        for (MultipartFile file : files) {
            try {
                ImageReference imageRef = multibaseService.uploadImage(file);
                imageReferences.add(imageRef);
                log.debug("Successfully uploaded image {} with hash: {}",
                        file.getOriginalFilename(), imageRef.getMultibaseHash());
            } catch (Exception e) {
                log.error("Failed to upload image {} for ship {}", file.getOriginalFilename(), shipId, e);
                failedFiles.add(file.getOriginalFilename());
            }
        }

        if (!failedFiles.isEmpty() && imageReferences.isEmpty()) {
            // All uploads failed — don't save partial state, surface the error
            throw new com.shipspace.exception.MultibaseException(
                    "All image uploads failed: " + String.join(", ", failedFiles));
        }
        if (!failedFiles.isEmpty()) {
            log.warn("Partial upload failure for ship {}: {} files failed: {}",
                    shipId, failedFiles.size(), failedFiles);
        }
        
        // Add image references to ship
        if (ship.getImages() == null) {
            ship.setImages(new ArrayList<>());
        }
        ship.getImages().addAll(imageReferences);
        ship.setUpdatedAt(Instant.now());

        Ship updatedShip = shipRepository.save(ship);
        log.info("Uploaded {}/{} images to ship ID: {} (failed: {})",
                imageReferences.size(), files.size(), shipId, failedFiles.size());

        return mapToResponse(updatedShip);
    }

    /**
     * Deletes a ship. Only the owner can delete.
     * Also decrements the project's ship count.
     */
    @Transactional
    public void deleteShip(String shipId, String userId) {
        Ship ship = shipRepository.findById(shipId)
                .orElseThrow(() -> new ResourceNotFoundException("Ship", shipId));
        if (!ship.getUserId().equals(userId)) {
            throw new ForbiddenException("delete", "ship");
        }
        shipRepository.deleteById(shipId);
        cascadeDeleteService.deleteShipCascade(shipId);
        projectService.decrementProjectShipCount(ship.getProjectId());
        log.info("Deleted ship {} by user {}", shipId, userId);
    }

    /**
     * Verifies that a ship exists and is owned by the specified user.
     * 
     * @param shipId the ID of the ship
     * @param userId the ID of the user
     * @throws ResourceNotFoundException if ship is not found
     * @throws ForbiddenException if user is not the ship owner
     */
    public void verifyShipOwnership(String shipId, String userId) {
        Ship ship = shipRepository.findById(shipId)
                .orElseThrow(() -> new ResourceNotFoundException("Ship", shipId));
        
        if (!ship.getUserId().equals(userId)) {
            throw new ForbiddenException("access", "ship");
        }
    }

    /**
     * Maps a Ship entity to a ShipResponse DTO.
     * 
     * @param ship the ship entity
     * @return the ship response DTO
     */
    private ShipResponse mapToResponse(Ship ship) {
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
}
