package com.shipspace.service;

import com.shipspace.dto.CollaborationRequestDto;
import com.shipspace.exception.ForbiddenException;
import com.shipspace.exception.ResourceNotFoundException;
import com.shipspace.model.CollaborationRequest;
import com.shipspace.model.Project;
import com.shipspace.model.ScoreEventType;
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
 * Minimal collaboration service.
 * Handles send / accept / decline of collaboration requests.
 * Accepting awards COLLAB_ACCEPTED ShipScore points to the requester.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CollaborationService {

    private final CollaborationRequestRepository collaborationRequestRepository;
    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final ShipScoreService shipScoreService;

    /**
     * Send a collaboration request to a project owner.
     * Idempotent — silently succeeds if a pending request already exists.
     */
    @Transactional
    public CollaborationRequestDto.Response sendRequest(String requesterId, CollaborationRequestDto dto) {
        if (!userRepository.existsById(requesterId)) {
            throw new ResourceNotFoundException("User", requesterId);
        }

        Project project = projectRepository.findById(dto.getProjectId())
                .orElseThrow(() -> new ResourceNotFoundException("Project", dto.getProjectId()));

        if (project.getUserId().equals(requesterId)) {
            throw new ForbiddenException("You cannot request to collaborate on your own project");
        }

        // Idempotency — return existing pending request if present
        if (collaborationRequestRepository.existsByRequesterIdAndProjectId(requesterId, dto.getProjectId())) {
            log.debug("Collaboration request already exists for requester {} on project {}",
                    requesterId, dto.getProjectId());
            // Return a minimal response — the client already has the data
            return CollaborationRequestDto.Response.builder()
                    .requesterId(requesterId)
                    .projectId(dto.getProjectId())
                    .ownerId(project.getUserId())
                    .message(dto.getMessage())
                    .status(CollaborationRequest.Status.PENDING)
                    .build();
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

            log.info("Collaboration request {} sent by {} for project {}", request.getId(), requesterId, dto.getProjectId());
            return toResponse(request);
        } catch (DuplicateKeyException e) {
            // Concurrent duplicate — treat as idempotent success
            log.debug("Concurrent duplicate collaboration request ignored");
            return CollaborationRequestDto.Response.builder()
                    .requesterId(requesterId)
                    .projectId(dto.getProjectId())
                    .ownerId(project.getUserId())
                    .status(CollaborationRequest.Status.PENDING)
                    .build();
        }
    }

    /**
     * Accept a collaboration request.
     * Only the project owner can accept.
     * Awards COLLAB_ACCEPTED ShipScore points to the requester.
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

        // Award score to the requester — fire-and-forget, never fail the acceptance
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
     * Decline a collaboration request.
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

        log.info("Collaboration request {} declined by owner {}", collaborationId, ownerId);
        return toResponse(request);
    }

    /** Get pending requests for the authenticated project owner. */
    public Page<CollaborationRequestDto.Response> getIncomingRequests(String ownerId, Pageable pageable) {
        return collaborationRequestRepository
                .findByOwnerIdAndStatus(ownerId, CollaborationRequest.Status.PENDING, pageable)
                .map(this::toResponse);
    }

    /** Get requests sent by the authenticated user. */
    public Page<CollaborationRequestDto.Response> getSentRequests(String requesterId, Pageable pageable) {
        return collaborationRequestRepository
                .findByRequesterId(requesterId, pageable)
                .map(this::toResponse);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private CollaborationRequest getAndValidateRequest(String collaborationId, String ownerId) {
        CollaborationRequest request = collaborationRequestRepository.findById(collaborationId)
                .orElseThrow(() -> new ResourceNotFoundException("CollaborationRequest", collaborationId));
        if (!request.getOwnerId().equals(ownerId)) {
            throw new ForbiddenException("Only the project owner can manage this collaboration request");
        }
        return request;
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
                .build();
    }
}
