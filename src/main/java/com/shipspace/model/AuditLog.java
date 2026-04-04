package com.shipspace.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * Persistent audit log document.
 * TTL index auto-expires entries after 365 days.
 */
@Document(collection = "audit_logs")
@CompoundIndex(name = "userId_eventType_idx", def = "{'userId': 1, 'eventType': 1}")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLog {

    @Id
    private String id;

    private String eventType;
    private String userId;
    private String entityId;
    private String previousState;
    private String newState;
    private String metadata;

    /** TTL: auto-delete after 365 days. */
    @Indexed(expireAfterSeconds = 31536000)
    @Builder.Default
    private Instant timestamp = Instant.now();
}
