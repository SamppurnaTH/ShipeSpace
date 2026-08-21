package com.shipspace.dto;

import com.shipspace.model.AuditLog;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * DTO for admin audit log reads.
 * Metadata is limited to known moderation payloads written by AuditLogService.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminAuditLogResponse {

    private String id;
    private String eventType;
    private String userId;
    private String entityId;
    private String previousState;
    private String newState;
    private String metadata;
    private Instant timestamp;

    public static AdminAuditLogResponse from(AuditLog auditLog) {
        return AdminAuditLogResponse.builder()
                .id(auditLog.getId())
                .eventType(auditLog.getEventType())
                .userId(auditLog.getUserId())
                .entityId(auditLog.getEntityId())
                .previousState(auditLog.getPreviousState())
                .newState(auditLog.getNewState())
                .metadata(auditLog.getMetadata())
                .timestamp(auditLog.getTimestamp())
                .build();
    }
}
