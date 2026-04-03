package com.shipspace.controller;

import com.shipspace.dto.CollaborationRequestDto;
import com.shipspace.service.CollaborationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for collaboration requests.
 *
 * POST   /collaborations              — send a request
 * POST   /collaborations/{id}/accept  — owner accepts
 * POST   /collaborations/{id}/decline — owner declines
 * GET    /collaborations/incoming     — owner views pending requests
 * GET    /collaborations/sent         — requester views their requests
 */
@Slf4j
@RestController
@RequestMapping("/collaborations")
@RequiredArgsConstructor
@Tag(name = "Collaborations", description = "Collaboration request management")
public class CollaborationController {

    private final CollaborationService collaborationService;

    @PostMapping
    @Operation(summary = "Send a collaboration request",
            security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<CollaborationRequestDto.Response> sendRequest(
            Authentication authentication,
            @Valid @RequestBody CollaborationRequestDto dto) {
        String userId = userId(authentication);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(collaborationService.sendRequest(userId, dto));
    }

    @PostMapping("/{id}/accept")
    @Operation(summary = "Accept a collaboration request (project owner only)",
            security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<CollaborationRequestDto.Response> acceptRequest(
            Authentication authentication,
            @PathVariable String id) {
        return ResponseEntity.ok(collaborationService.acceptRequest(userId(authentication), id));
    }

    @PostMapping("/{id}/decline")
    @Operation(summary = "Decline a collaboration request (project owner only)",
            security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<CollaborationRequestDto.Response> declineRequest(
            Authentication authentication,
            @PathVariable String id) {
        return ResponseEntity.ok(collaborationService.declineRequest(userId(authentication), id));
    }

    @GetMapping("/incoming")
    @Operation(summary = "Get pending collaboration requests for your projects",
            security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<Page<CollaborationRequestDto.Response>> getIncoming(
            Authentication authentication,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        size = Math.min(size, 50);
        return ResponseEntity.ok(collaborationService.getIncomingRequests(
                userId(authentication),
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))));
    }

    @GetMapping("/sent")
    @Operation(summary = "Get collaboration requests you have sent",
            security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<Page<CollaborationRequestDto.Response>> getSent(
            Authentication authentication,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        size = Math.min(size, 50);
        return ResponseEntity.ok(collaborationService.getSentRequests(
                userId(authentication),
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))));
    }

    private String userId(Authentication auth) {
        return ((UserDetails) auth.getPrincipal()).getUsername();
    }
}
