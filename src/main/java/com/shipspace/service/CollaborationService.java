package com.shipspace.service;

import com.shipspace.dto.CollaborationRequestDto;
import com.shipspace.exception.ForbiddenException;
import com.shipspace.exception.ResourceNotFoundException;
import com.shipspace.model.CollaborationRequest;
import com.shipspace.model.Project;
import com.shipspace.model.ScoreEventType;
import com.shipspace.model.User;
import com.shipspace.repository.CollaborationRequestRepository;
import com.shipspace.repository.ProjectRepository;
import com.shipspace.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Handles project-scoped collaboration requests.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CollaborationService {

    private final CollaborationRequestRepository collaborationRequestRepository;
    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final ShipScoreService shipScoreService;
    private final NotificationDispatchService notificationDispatchService;

    /**
     * Send a collaboration request to a project owner.
     * Duplicate requester/project requests return the existing persisted request.
     */
    @Transactional
    public CollaborationRequestDto.Response sendRequest(String requesterId, CollaborationRequestDto dto) {
        if (!userRepository.existsById(requesterId)) {
            throw new ResourceNotFoundException("User", requesterId);
        }

        Project project = projectRepository.findById(dto.getProjectId())
                .orElseThrow(() -> new ResourceNotFoundException("Project", dto.getProjectId()));

        if (!userRepository.existsById(project.getUserId())) {
            throw new ResourceNotFoundException("User", project.getUserId());
        }

        if (project.getUserId().equals(requesterId)) {
            throw new ForbiddenException("You cannot request to collaborate on your own project");
        }

        if (collaborationRequestRepository.existsByRequesterIdAndProjectId(requesterId, dto.getProjectId())) {
            log.debug("Collaboration request already exists for requester {} on project {}",
                    requesterId, dto.getProjectId());
            return existingOrPendingFallback(requesterId, dto.getProjectId(), project.getUserId());
        }

        try {
            CollaborationRequest request = collaborationRequestRepository.save(
                    CollaborationRequest.builder()
                            .requesterId(requesterId)
                            .projectId(dto.getProjectId())
                            .ownerId(project.getUserId())
                            .message(dto.getMessage())
                            .status(CollaborationRequest.Status.PENDING)
                            .build());

            dispatchRequestNotification(request, project);
            log.info("Collaboration request {} sent by {} for project {}", request.getId(), requesterId, dto.getProjectId());
            return toResponse(request);
        } catch (DuplicateKeyException e) {
            log.debug("Concurrent duplicate collaboration request ignored");
            return existingOrPendingFallback(requesterId, dto.getProjectId(), project.getUserId());
        }
    }

    /**
     * Accept a pending collaboration request.
     * Only the project owner can accept.
     */
    @Transactional
    public CollaborationRequestDto.Response acceptRequest(String ownerId, String collaborationId) {
        CollaborationRequest request = getAndValidateRequest(collaborationId, ownerId);

        if (request.getStatus() != CollaborationRequest.Status.PENDING) {
            throw new ForbiddenException("Collaboration request is no longer pending");
        }

        request.setStatus(CollaborationRequest.Status.ACCEPTED);
        request.setResolvedAt(Instant.now());
        collaborationRequestRepository.save(request);
        dispatchResolvedNotification(request, "COLLABORATION_ACCEPTED");

        try {
            boolean scored = shipScoreService.applyScoreEvent(
                    request.getRequesterId(), ScoreEventType.COLLAB_ACCEPTED, collaborationId);
            log.debug("COLLAB_ACCEPTED score event applied={} for requester {}", scored, request.getRequesterId());
        } catch (Exception e) {
            log.error("Failed to apply COLLAB_ACCEPTED score for requester {}: {}",
                    request.getRequesterId(), e.getMessage());
        }

        log.info("Collaboration request {} accepted by owner {}", collaborationId, ownerId);
        return toResponse(request);
    }

    /**
     * Decline a pending collaboration request.
     * Only the project owner can decline.
     */
    @Transactional
    public CollaborationRequestDto.Response declineRequest(String ownerId, String collaborationId) {
        CollaborationRequest request = getAndValidateRequest(collaborationId, ownerId);

        if (request.getStatus() != CollaborationRequest.Status.PENDING) {
            throw new ForbiddenException("Collaboration request is no longer pending");
        }

        request.setStatus(CollaborationRequest.Status.DECLINED);
        request.setResolvedAt(Instant.now());
        collaborationRequestRepository.save(request);
        dispatchResolvedNotification(request, "COLLABORATION_DECLINED");

        log.info("Collaboration request {} declined by owner {}", collaborationId, ownerId);
        return toResponse(request);
    }

    /** Get collaboration requests addressed to the authenticated project owner. */
    public Page<CollaborationRequestDto.Response> getIncomingRequests(String ownerId, Pageable pageable) {
        return collaborationRequestRepository
                .findByOwnerId(ownerId, pageable)
                .map(this::toResponse);
    }

    /** Get requests sent by the authenticated user. */
    public Page<CollaborationRequestDto.Response> getSentRequests(String requesterId, Pageable pageable) {
        return collaborationRequestRepository
                .findByRequesterId(requesterId, pageable)
                .map(this::toResponse);
    }

    private CollaborationRequest getAndValidateRequest(String collaborationId, String ownerId) {
        CollaborationRequest request = collaborationRequestRepository.findById(collaborationId)
                .orElseThrow(() -> new ResourceNotFoundException("CollaborationRequest", collaborationId));
        if (!request.getOwnerId().equals(ownerId)) {
            throw new ForbiddenException("Only the project owner can manage this collaboration request");
        }
        return request;
    }

    private CollaborationRequestDto.Response existingOrPendingFallback(String requesterId, String projectId, String ownerId) {
        return collaborationRequestRepository.findByRequesterIdAndProjectId(requesterId, projectId)
                .map(this::toResponse)
                .orElseGet(() -> CollaborationRequestDto.Response.builder()
                        .requesterId(requesterId)
                        .projectId(projectId)
                        .ownerId(ownerId)
                        .status(CollaborationRequest.Status.PENDING)
                        .build());
    }

    private CollaborationRequestDto.Response toResponse(CollaborationRequest r) {
        return CollaborationRequestDto.Response.builder()
                .id(r.getId())
                .requesterId(r.getRequesterId())
                .projectId(r.getProjectId())
                .ownerId(r.getOwnerId())
                .message(r.getMessage())
                .status(r.getStatus())
                .createdAt(r.getCreatedAt())
                .resolvedAt(r.getResolvedAt())
                .requester(userSummary(r.getRequesterId()))
                .owner(userSummary(r.getOwnerId()))
                .project(projectSummary(r.getProjectId()))
                .build();
    }

    private CollaborationRequestDto.UserSummary userSummary(String userId) {
        return userRepository.findById(userId)
                .map(user -> CollaborationRequestDto.UserSummary.builder()
                        .id(user.getId())
                        .username(user.getUsername())
                        .avatarUrl(user.getAvatarUrl())
                        .build())
                .orElse(null);
    }

    private CollaborationRequestDto.ProjectSummary projectSummary(String projectId) {
        return projectRepository.findById(projectId)
                .map(project -> CollaborationRequestDto.ProjectSummary.builder()
                        .id(project.getId())
                        .name(project.getName())
                        .userId(project.getUserId())
                        .build())
                .orElse(null);
    }

    private void dispatchRequestNotification(CollaborationRequest request, Project project) {
        User requester = userRepository.findById(request.getRequesterId()).orElse(null);
        String requesterName = requester != null ? requester.getUsername() : "A builder";
        notificationDispatchService.dispatch(
                request.getOwnerId(),
                "COLLABORATION_REQUESTED",
                requesterName + " requested to collaborate on " + project.getName(),
                request.getRequesterId(),
                requester != null ? requester.getAvatarUrl() : null,
                request.getId(),
                "/collaborations");
    }

    private void dispatchResolvedNotification(CollaborationRequest request, String type) {
        Project project = projectRepository.findById(request.getProjectId()).orElse(null);
        User owner = userRepository.findById(request.getOwnerId()).orElse(null);
        String projectName = project != null ? project.getName() : "a project";
        String action = "COLLABORATION_ACCEPTED".equals(type) ? "accepted" : "declined";
        notificationDispatchService.dispatch(
                request.getRequesterId(),
                type,
                "Your collaboration request for " + projectName + " was " + action + ".",
                request.getOwnerId(),
                owner != null ? owner.getAvatarUrl() : null,
                request.getId(),
                "/collaborations");
    }
}
