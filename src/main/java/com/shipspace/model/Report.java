package com.shipspace.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "reports")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Report {

    @Id
    private String id;

    @Indexed
    private String reporterId;

    @Indexed
    private String targetId;

    private TargetType targetType;

    private Reason reason;

    private String details;

    @Builder.Default
    private Status status = Status.PENDING;

    private String resolvedBy;

    private String resolutionNote;

    @Builder.Default
    private Instant createdAt = Instant.now();

    private Instant resolvedAt;

    public enum TargetType { SHIP, PROJECT, USER, COMMENT }

    public enum Reason { SPAM, HARASSMENT, INAPPROPRIATE_CONTENT, MISINFORMATION, OTHER }

    public enum Status { PENDING, REVIEWED, DISMISSED, ACTION_TAKEN }
}
