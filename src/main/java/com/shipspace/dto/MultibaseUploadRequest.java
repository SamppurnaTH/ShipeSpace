package com.shipspace.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for Multibase upload API request.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MultibaseUploadRequest {

    /**
     * Base64 encoded image data.
     */
    private String data;

    /**
     * Original filename.
     */
    private String filename;

    /**
     * MIME content type.
     */
    private String contentType;
}
