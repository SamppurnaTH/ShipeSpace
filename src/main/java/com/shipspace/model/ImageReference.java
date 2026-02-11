package com.shipspace.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Embedded document representing an image stored in Multibase.
 * Contains content addressing information and metadata.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImageReference {

    /**
     * Multibase content hash for the image.
     */
    @NotBlank(message = "Multibase hash is required")
    private String multibaseHash;

    /**
     * Original filename of the uploaded image.
     */
    @NotBlank(message = "Filename is required")
    private String filename;

    /**
     * MIME content type of the image.
     */
    @NotBlank(message = "Content type is required")
    private String contentType;

    /**
     * Size of the image in bytes.
     */
    @Positive(message = "Size must be positive")
    private Long size;
}
