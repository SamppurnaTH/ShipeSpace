# ShipSpace Integration Documentation

This directory contains integration guides for connecting various features with the ShipScore rank and level progression system.

## Available Integration Guides

### [Collaboration and ShipScore Integration](collaboration-shipscore-integration.md)

**Status**: Pending collaboration feature implementation  
**Task**: 7.3 Integrate with collaboration acceptance  
**Spec**: rank-level-progression

This guide provides comprehensive documentation for integrating the collaboration acceptance feature with the ShipScore system. It includes:

- Implementation examples with code snippets
- Error handling best practices
- Testing strategies (unit and integration tests)
- Key design decisions and rationale
- Verification checklist

**Quick Reference**: When a collaboration is accepted, award 10 points to the **collaborator** (not the accepter) using:

```java
shipScoreService.applyScoreEvent(
    collaboratorId,
    ScoreEventType.COLLAB_ACCEPTED,
    collaborationId
);
```

## Integration Points Overview

The ShipScore system integrates with the following platform features:

| Feature | Event Type | Points | Daily Cap | Status |
|---------|-----------|--------|-----------|--------|
| Project Creation | `PROJECT_CREATED` | 50 | None | ✅ Implemented |
| Project Update | `PROJECT_UPDATED` | 20 | 5 per day | ✅ Implemented |
| Collaboration Acceptance | `COLLAB_ACCEPTED` | 10 | None | 📝 Documented (pending feature) |
| Quality Comment | `QUALITY_COMMENT` | 5 | None | 🚧 Partially implemented |

## General Integration Pattern

All ShipScore integrations follow this pattern:

```java
// After successful operation
try {
    boolean scoreApplied = shipScoreService.applyScoreEvent(
        userId,           // User receiving the points
        eventType,        // Type of event (PROJECT_CREATED, etc.)
        entityId          // Unique identifier for idempotency
    );
    
    if (scoreApplied) {
        log.debug("Successfully applied {} score event for user: {}", 
                 eventType, userId);
    } else {
        log.debug("{} score event was not applied (duplicate or cap reached) for user: {}", 
                 eventType, userId);
    }
} catch (Exception e) {
    // Log error but don't fail the primary operation
    log.error("Failed to apply {} score event for user: {}", 
             eventType, userId, e);
}
```

### Key Principles

1. **Non-blocking**: Score application should never fail the primary operation
2. **Idempotent**: Use unique entityId to prevent duplicate scoring
3. **Logged**: Always log success/failure for debugging
4. **Async-friendly**: Wrap in try-catch to handle errors gracefully

## Testing Integration Points

When implementing or testing integrations:

1. **Unit Tests**: Verify the score service is called with correct parameters
2. **Integration Tests**: Verify end-to-end score application and persistence
3. **Error Tests**: Verify primary operation succeeds even if scoring fails
4. **Idempotency Tests**: Verify duplicate operations don't award points twice

## Related Documentation

- [Rank and Level Progression Requirements](../.kiro/specs/rank-level-progression/requirements.md)
- [Rank and Level Progression Design](../.kiro/specs/rank-level-progression/design.md)
- [ShipScoreService API Documentation](../api/ShipScoreService.md) *(to be created)*
- [Score Event Types](../api/ScoreEventType.md) *(to be created)*

## Contributing

When adding new integration points:

1. Create a detailed integration guide in this directory
2. Follow the established pattern for score application
3. Include comprehensive test examples
4. Update this README with the new integration
5. Link to relevant requirements and design documents

## Questions?

For questions about ShipScore integrations:

1. Review the relevant integration guide
2. Check existing implementations in `ProjectController`
3. Consult the ShipScore specification documents
4. Reach out to the team for clarification
