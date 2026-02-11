package com.shipspace.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for Multibase upload API response.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MultibaseUploadResponse {

    /**
     * Content hash returned by Multibase.
     */
    private String hash;

    /**
     * Size of the uploaded content in bytes.
     */
    private Long size;

    /**
     * Content type of the uploaded file.
     */
    private String contentType;

    /**
     * Status message from Multibase.
     */
    private String status;
}
