package com.shipspace.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * DTO for notification responses.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationResponse {

    private String id;
    private String type;
    private String text;
    private String actorId;
    private String actorAvatarUrl;
    private String targetId;
    private String targetView;
    private boolean read;
    private Instant createdAt;
}
