package com.shipspace.service;

import com.shipspace.dto.ProjectCreateRequest;
import com.shipspace.dto.ProjectResponse;
import com.shipspace.dto.ProjectUpdateRequest;
import com.shipspace.exception.ForbiddenException;
import com.shipspace.exception.ResourceNotFoundException;
import com.shipspace.model.Project;
import com.shipspace.model.ScoreEventType;
import com.shipspace.model.Ship;
import com.shipspace.repository.ProjectRepository;
import com.shipspace.repository.ShipRepository;
import com.shipspace.util.InputSanitizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Service for project management operations.
 * Handles project CRUD operations, ownership validation, and metadata aggregation.
 * 
 * Implements Requirements 3.1, 3.2, 3.3, 3.4, 3.5:
 * - Project creation with validation
 * - Project retrieval with metadata
 * - Project updates with ownership validation
 * - Project ship history in chronological order
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final ShipRepository shipRepository;
    private final UserService userService;
    private final MongoTemplate mongoTemplate;
    private final ShipScoreService shipScoreService;
    private final CascadeDeleteService cascadeDeleteService;
    private final InputSanitizer inputSanitizer;

    /**
     * Creates a new project for the authenticated user.
     * Validates input data and initializes project metadata.
     * 
     * @param userId the ID of the user creating the project
     * @param request the project creation request with metadata
     * @return the created project response
     */
    @Transactional
    public ProjectResponse createProject(String userId, ProjectCreateRequest request) {
        log.info("Creating project for user ID: {}", userId);
        
        // Verify user exists
        userService.getUserById(userId);
        
        // Create project entity
        Project project = Project.builder()
                .userId(userId)
                .name(inputSanitizer.sanitize(request.getName(), 100))
                .description(inputSanitizer.sanitize(request.getDescription(), 2000))
                .techTags(request.getTechTags())
                .externalLinks(request.getExternalLinks())
                .shipCount(0)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        
        // Save project
        Project savedProject = projectRepository.save(project);
        log.info("Successfully created project with ID: {} for user: {}", savedProject.getId(), userId);
        
        // Update user's project count
        userService.updateProjectCount(userId);

        // Apply score event — non-fatal if it fails
        try {
            shipScoreService.applyScoreEvent(userId, ScoreEventType.PROJECT_CREATED, savedProject.getId());
        } catch (Exception e) {
            log.error("Failed to apply PROJECT_CREATED score for user {}: {}", userId, e.getMessage());
        }
        
        return mapToResponse(savedProject);
    }

    /**
     * Retrieves a project by its ID.
     * Includes project metadata and ship count.
     * 
     * @param projectId the ID of the project to retrieve
     * @return the project response with metadata
     * @throws ResourceNotFoundException if project is not found
     */
    public ProjectResponse getProjectById(String projectId) {
        log.debug("Retrieving project with ID: {}", projectId);
        
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project", projectId));
        
        return mapToResponse(project);
    }

    /**
     * Updates a project's metadata.
     * Only the project owner can update the project.
     * Only provided fields will be updated (null fields are ignored).
     * 
     * @param projectId the ID of the project to update
     * @param userId the ID of the user attempting the update
     * @param request the update request with new metadata
     * @return the updated project response
     * @throws ResourceNotFoundException if project is not found
     * @throws ForbiddenException if user is not the project owner
     */
    @Transactional
    public ProjectResponse updateProject(String projectId, String userId, ProjectUpdateRequest request) {
        log.info("Updating project ID: {} by user ID: {}", projectId, userId);
        
        // Retrieve project
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project", projectId));
        
        // Verify ownership
        if (!project.getUserId().equals(userId)) {
            log.warn("User {} attempted to update project {} owned by {}", 
                    userId, projectId, project.getUserId());
            throw new ForbiddenException("update", "project");
        }
        
        // Update only provided fields — sanitize free-text
        if (request.getName() != null) {
            project.setName(inputSanitizer.sanitize(request.getName(), 100));
        }
        if (request.getDescription() != null) {
            project.setDescription(inputSanitizer.sanitize(request.getDescription(), 2000));
        }
        if (request.getTechTags() != null) {
            project.setTechTags(request.getTechTags());
        }
        if (request.getExternalLinks() != null) {
            project.setExternalLinks(request.getExternalLinks());
        }
        
        project.setUpdatedAt(Instant.now());
        
        // Save updated project
        Project updatedProject = projectRepository.save(project);
        log.info("Successfully updated project ID: {}", projectId);

        // Apply score event — non-fatal if it fails
        try {
            shipScoreService.applyScoreEvent(userId, ScoreEventType.PROJECT_UPDATED, projectId);
        } catch (Exception e) {
            log.error("Failed to apply PROJECT_UPDATED score for user {}: {}", userId, e.getMessage());
        }
        
        return mapToResponse(updatedProject);
    }

    /**
     * Retrieves all ships associated with a project in chronological order.
     * Returns ships sorted by creation date (most recent first).
     * 
     * @param projectId the ID of the project
     * @param page the page number (0-indexed)
     * @param size the page size
     * @return page of ships for the project
     * @throws ResourceNotFoundException if project is not found
     */
    public Page<Ship> getProjectShips(String projectId, int page, int size) {
        log.debug("Retrieving ships for project ID: {} (page: {}, size: {})", projectId, page, size);
        
        // Verify project exists
        projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project", projectId));
        
        // Get ships sorted by creation date (most recent first)
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Ship> ships = shipRepository.findByProjectId(projectId, pageable);
        
        log.debug("Found {} ships for project ID: {}", ships.getTotalElements(), projectId);
        
        return ships;
    }

    /**
     * Retrieves all ships associated with a project with default pagination.
     * 
     * @param projectId the ID of the project
     * @return page of ships for the project (first 20 ships)
     * @throws ResourceNotFoundException if project is not found
     */
    public Page<Ship> getProjectShips(String projectId) {
        return getProjectShips(projectId, 0, 20);
    }

    /**
     * Updates project metadata when a new ship is created.
     * Uses atomic $inc — no read-modify-write race condition.
     */
    @Transactional
    public void updateProjectShipMetadata(String projectId) {
        log.debug("Updating ship metadata for project ID: {}", projectId);
        Query query = new Query(Criteria.where("_id").is(projectId));
        Update update = new Update()
                .inc("shipCount", 1)
                .set("lastShipAt", java.time.Instant.now())
                .set("updatedAt", java.time.Instant.now());
        mongoTemplate.updateFirst(query, update, Project.class);
        log.debug("Atomically incremented shipCount for project {}", projectId);
    }

    /**
     * Deletes a project and all its ships. Only the owner can delete.
     */
    @Transactional
    public void deleteProject(String projectId, String userId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project", projectId));
        if (!project.getUserId().equals(userId)) {
            throw new ForbiddenException("delete", "project");
        }
        // Collect ship IDs before deletion for cascade cleanup — projection only, no full doc load
        List<String> shipIds = shipRepository.findIdsByProjectId(projectId)
                .stream()
                .map(com.shipspace.model.Ship::getId)
                .collect(java.util.stream.Collectors.toList());

        // Delete all ships belonging to this project
        shipRepository.deleteByProjectId(projectId);
        projectRepository.deleteById(projectId);
        userService.decrementProjectCount(userId);

        // Cascade: clean up all orphaned documents
        cascadeDeleteService.deleteProjectCascade(projectId, shipIds);
        log.info("Deleted project {} and its ships by user {}", projectId, userId);
    }

    /**
     * Decrements project ship count when a ship is deleted.
     * Uses atomic $inc — no read-modify-write race condition.
     */
    @Transactional
    public void decrementProjectShipCount(String projectId) {
        Query query = new Query(Criteria.where("_id").is(projectId));
        Update update = new Update()
                .inc("shipCount", -1)
                .set("updatedAt", Instant.now());
        mongoTemplate.updateFirst(query, update, Project.class);
        log.debug("Atomically decremented shipCount for project {}", projectId);
    }

    /**
     * Verifies that a project exists and is owned by the specified user.
     * 
     * @param projectId the ID of the project
     * @param userId the ID of the user
     * @throws ResourceNotFoundException if project is not found
     * @throws ForbiddenException if user is not the project owner
     */
    public void verifyProjectOwnership(String projectId, String userId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project", projectId));
        
        if (!project.getUserId().equals(userId)) {
            throw new ForbiddenException("access", "project");
        }
    }

    /**
     * Batch-fetch projects by IDs. Used by FeedController for enrichment.
     */
    public List<Project> findAllByIds(java.util.Collection<String> ids) {
        return projectRepository.findAllById(ids);
    }

    /**
     * Maps a Project entity to a ProjectResponse DTO.
     * 
     * @param project the project entity
     * @return the project response DTO
     */
    private ProjectResponse mapToResponse(Project project) {
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
}
