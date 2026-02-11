package com.shipspace.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO representing the progress of an image upload operation.
 * Used for tracking asynchronous upload status.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImageUploadProgress {

    /**
     * Unique identifier for this upload operation.
     */
    private String uploadId;

    /**
     * Current status of the upload.
     */
    private UploadStatus status;

    /**
     * Progress percentage (0-100).
     */
    private Integer progressPercentage;

    /**
     * Original filename being uploaded.
     */
    private String filename;

    /**
     * Multibase hash if upload is complete.
     */
    private String multibaseHash;

    /**
     * Error message if upload failed.
     */
    private String errorMessage;

    /**
     * Timestamp when the upload started.
     */
    private Long startedAt;

    /**
     * Timestamp when the upload completed or failed.
     */
    private Long completedAt;

    /**
     * Upload status enumeration.
     */
    public enum UploadStatus {
        PENDING,
        UPLOADING,
        PROCESSING,
        COMPLETED,
        FAILED
    }
}
