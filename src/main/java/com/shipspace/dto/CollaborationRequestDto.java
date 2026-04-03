package com.shipspace.dto;

import com.shipspace.model.CollaborationRequest;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;

/** Request body for sending a collaboration request. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CollaborationRequestDto {

    @NotBlank(message = "Project ID is required")
    private String projectId;

    @Size(max = 500, message = "Message must not exceed 500 characters")
    private String message;

    /** Response DTO returned to clients. */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Response {
        private String id;
        private String requesterId;
        private String projectId;
        private String ownerId;
        private String message;
        private CollaborationRequest.Status status;
        private Instant createdAt;
        private Instant resolvedAt;
    }
}
