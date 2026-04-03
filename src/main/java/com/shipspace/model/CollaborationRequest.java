package com.shipspace.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Collaboration request between two users on a project.
 * The requester asks to collaborate; the project owner accepts or declines.
 * Accepting awards COLLAB_ACCEPTED ShipScore points to the requester.
 */
@Document(collection = "collaboration_requests")
@CompoundIndex(name = "requester_project_idx",
        def = "{'requesterId': 1, 'projectId': 1}", unique = true)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CollaborationRequest {

    @Id
    private String id;

    /** User who wants to collaborate. */
    @Indexed
    private String requesterId;

    /** Project the requester wants to collaborate on. */
    @Indexed
    private String projectId;

    /** Owner of the project — the user who accepts or declines. */
    @Indexed
    private String ownerId;

    /** Optional message from the requester. */
    private String message;

    @Builder.Default
    private Status status = Status.PENDING;

    @Builder.Default
    private Instant createdAt = Instant.now();

    private Instant resolvedAt;

    public enum Status {
        PENDING, ACCEPTED, DECLINED
    }
}
