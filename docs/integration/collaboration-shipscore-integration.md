# Collaboration and ShipScore Integration Guide

## Overview

This document provides guidance for integrating the collaboration acceptance feature with the ShipScore rank and level progression system. This integration is part of **Task 7.3** from the rank-level-progression specification.

## Requirements

- **Requirement 10.3**: When a collaboration request is accepted, THE System SHALL invoke ShipScoreService with COLLAB_ACCEPTED event type for the collaborator
- **Requirement 10.5**: THE System SHALL pass the relevant entity identifier as entityId to ensure idempotency

## Integration Point

The integration should occur in the `CollaborationController.acceptCollaboration` method, immediately after a collaboration request is successfully accepted.

## Implementation Example

```java
@PostMapping("/{collaborationId}/accept")
@Operation(
    summary = "Accept collaboration request",
    description = "Accept a pending collaboration request and add the collaborator to the project",
    security = @SecurityRequirement(name = "bearerAuth")
)
public ResponseEntity<CollaborationResponse> acceptCollaboration(
        Authentication authentication,
        @PathVariable String collaborationId) {
    
    log.info("Received collaboration acceptance request for collaboration: {}", collaborationId);
    
    // Extract current user ID from authentication
    UserDetails userDetails = (UserDetails) authentication.getPrincipal();
    String currentUserId = userDetails.getUsername();
    
    // Accept the collaboration (existing logic)
    CollaborationResponse response = collaborationService.acceptCollaboration(
        collaborationId, 
        currentUserId
    );
    
    // ========== SHIPSCORE INTEGRATION START ==========
    // Apply COLLAB_ACCEPTED score event for the collaborator
    // Note: Award points to the collaborator (the user being added), not the accepter
    String collaboratorId = response.getCollaboratorId();
    
    try {
        boolean scoreApplied = shipScoreService.applyScoreEvent(
            collaboratorId,                    // User receiving the score
            ScoreEventType.COLLAB_ACCEPTED,    // Event type (10 points, no daily cap)
            collaborationId                     // Entity ID for idempotency
        );
        
        if (scoreApplied) {
            log.debug("Successfully applied COLLAB_ACCEPTED score event for collaborator: {}", 
                     collaboratorId);
        } else {
            log.debug("COLLAB_ACCEPTED score event was not applied (duplicate or cap reached) " +
                     "for collaborator: {}", collaboratorId);
        }
    } catch (Exception e) {
        // Log the error but don't fail the collaboration acceptance
        // Score application is a secondary concern - the collaboration should succeed
        log.error("Failed to apply COLLAB_ACCEPTED score event for collaborator: {}", 
                 collaboratorId, e);
    }
    // ========== SHIPSCORE INTEGRATION END ==========
    
    log.info("Successfully accepted collaboration: {}", collaborationId);
    return ResponseEntity.ok(response);
}
```

## Key Points

### 1. Who Gets the Points?

**The collaborator** (the user being added to the project) receives the points, **not** the user who accepts the request.

**Rationale**: The collaboration acceptance rewards the collaborator for being invited and accepted to contribute to a project, recognizing their value to the community.

### 2. Entity ID for Idempotency

Use the `collaborationId` as the entityId parameter. This ensures that:
- The same collaboration acceptance cannot award points multiple times
- Retries or duplicate requests are handled gracefully
- The score event log maintains referential integrity

### 3. Error Handling

Score event application should **never fail the collaboration acceptance**:
- Wrap the score application in a try-catch block
- Log errors but continue with the response
- The collaboration acceptance is the primary operation
- Score tracking is a secondary concern

### 4. Score Event Details

- **Event Type**: `ScoreEventType.COLLAB_ACCEPTED`
- **Points Awarded**: 10 points
- **Daily Cap**: None (unlimited collaborations per day)
- **Idempotency**: Protected by (userId, eventType, entityId) unique constraint

## Testing

When implementing this integration, ensure the following test cases are covered:

### Unit Tests

```java
@Test
@DisplayName("acceptCollaboration: should apply COLLAB_ACCEPTED score event to collaborator")
void acceptCollaboration_Success_AppliesScoreEvent() {
    // Arrange
    String collaborationId = "collab123";
    String collaboratorId = "user456";
    String accepterId = "user789";
    
    CollaborationResponse mockResponse = new CollaborationResponse();
    mockResponse.setCollaboratorId(collaboratorId);
    
    when(collaborationService.acceptCollaboration(collaborationId, accepterId))
        .thenReturn(mockResponse);
    when(shipScoreService.applyScoreEvent(
        collaboratorId, 
        ScoreEventType.COLLAB_ACCEPTED, 
        collaborationId
    )).thenReturn(true);
    
    // Act
    ResponseEntity<CollaborationResponse> response = 
        collaborationController.acceptCollaboration(authentication, collaborationId);
    
    // Assert
    assertEquals(HttpStatus.OK, response.getStatusCode());
    verify(shipScoreService).applyScoreEvent(
        collaboratorId, 
        ScoreEventType.COLLAB_ACCEPTED, 
        collaborationId
    );
}

@Test
@DisplayName("acceptCollaboration: should succeed even if score event fails")
void acceptCollaboration_ScoreEventFails_StillSucceeds() {
    // Arrange
    String collaborationId = "collab123";
    String collaboratorId = "user456";
    String accepterId = "user789";
    
    CollaborationResponse mockResponse = new CollaborationResponse();
    mockResponse.setCollaboratorId(collaboratorId);
    
    when(collaborationService.acceptCollaboration(collaborationId, accepterId))
        .thenReturn(mockResponse);
    when(shipScoreService.applyScoreEvent(any(), any(), any()))
        .thenThrow(new RuntimeException("Database error"));
    
    // Act
    ResponseEntity<CollaborationResponse> response = 
        collaborationController.acceptCollaboration(authentication, collaborationId);
    
    // Assert
    assertEquals(HttpStatus.OK, response.getStatusCode());
    // Collaboration should succeed despite score event failure
}
```

### Integration Tests

```java
@Test
@DisplayName("Integration: accepting collaboration increases collaborator's shipScore")
void acceptCollaboration_Integration_IncreasesCollaboratorScore() {
    // Arrange
    User collaborator = createTestUser();
    int initialScore = collaborator.getShipScore();
    
    Collaboration collaboration = createTestCollaboration(collaborator.getId());
    
    // Act
    acceptCollaborationRequest(collaboration.getId());
    
    // Assert
    User updatedCollaborator = userRepository.findById(collaborator.getId()).orElseThrow();
    assertEquals(initialScore + 10, updatedCollaborator.getShipScore());
    
    // Verify event log entry exists
    boolean eventLogged = scoreEventLogRepository.existsByUserIdAndEventTypeAndEntityId(
        collaborator.getId(),
        ScoreEventType.COLLAB_ACCEPTED,
        collaboration.getId()
    );
    assertTrue(eventLogged);
}
```

## Dependencies

Ensure the following dependencies are injected into `CollaborationController`:

```java
@Slf4j
@RestController
@RequestMapping("/api/collaborations")
@RequiredArgsConstructor
public class CollaborationController {

    private final CollaborationService collaborationService;
    private final ShipScoreService shipScoreService;  // Add this dependency
    
    // ... controller methods ...
}
```

## Verification Checklist

Before marking Task 7.3 as complete, verify:

- [ ] `ShipScoreService` is injected into `CollaborationController`
- [ ] `applyScoreEvent` is called after successful collaboration acceptance
- [ ] The **collaborator** (not the accepter) receives the points
- [ ] The `collaborationId` is used as the entityId
- [ ] Score event failures are caught and logged without failing the acceptance
- [ ] Unit tests verify the integration
- [ ] Integration tests verify end-to-end score application
- [ ] Logs include appropriate debug/error messages

## Related Documentation

- [Rank and Level Progression Requirements](.kiro/specs/rank-level-progression/requirements.md)
- [Rank and Level Progression Design](.kiro/specs/rank-level-progression/design.md)
- [ShipScoreService API Documentation](../api/ShipScoreService.md)
- [Score Event Types](../api/ScoreEventType.md)

## Questions?

If you have questions about this integration:

1. Review the requirements (10.3, 10.5) in the specification
2. Check the existing integrations in `ProjectController` for reference
3. Consult the ShipScoreService documentation
4. Reach out to the team for clarification

---

**Status**: Pending collaboration feature implementation  
**Task**: 7.3 Integrate with collaboration acceptance  
**Spec**: rank-level-progression
