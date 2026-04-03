package com.shipspace.service;

import com.shipspace.dto.ImageUploadProgress;
import com.shipspace.dto.MultibaseUploadRequest;
import com.shipspace.dto.MultibaseUploadResponse;
import com.shipspace.exception.MultibaseException;
import com.shipspace.model.ImageReference;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for integrating with Multibase content addressing system.
 * Handles image uploads, content addressing, and asynchronous processing with progress tracking.
 * 
 * Validates Requirements 4.3 and 11.2:
 * - 4.3: Integration with Multibase_System for content addressing and storage
 * - 11.2: Asynchronous image upload processing with progress tracking
 */
@Service
@Slf4j
public class MultibaseService {

    private final WebClient webClient;
    private final String multibaseApiUrl;
    private final String multibaseApiKey;

    // In-memory storage for upload progress tracking
    // In production, this should be replaced with Redis or a database
    private final Map<String, ImageUploadProgress> uploadProgressMap = new ConcurrentHashMap<>();

    public MultibaseService(
            WebClient.Builder webClientBuilder,
            @Value("${multibase.api-url}") String multibaseApiUrl,
            @Value("${multibase.api-key}") String multibaseApiKey) {
        this.multibaseApiUrl = multibaseApiUrl;
        this.multibaseApiKey = multibaseApiKey;
        this.webClient = webClientBuilder
                .baseUrl(multibaseApiUrl)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + multibaseApiKey)
                .build();
        
        log.info("MultibaseService initialized with API URL: {}", multibaseApiUrl);
    }

    /**
     * Upload an image to Multibase asynchronously with progress tracking.
     * 
     * @param file the image file to upload
     * @return CompletableFuture containing the upload ID for progress tracking
     */
    @Async
    public CompletableFuture<String> uploadImageAsync(MultipartFile file) {
        String uploadId = UUID.randomUUID().toString();
        
        log.info("Starting async upload for file: {} with uploadId: {}", file.getOriginalFilename(), uploadId);
        
        // Initialize progress tracking
        ImageUploadProgress progress = ImageUploadProgress.builder()
                .uploadId(uploadId)
                .status(ImageUploadProgress.UploadStatus.PENDING)
                .progressPercentage(0)
                .filename(file.getOriginalFilename())
                .startedAt(System.currentTimeMillis())
                .build();
        uploadProgressMap.put(uploadId, progress);

        try {
            // Update status to uploading
            updateProgress(uploadId, ImageUploadProgress.UploadStatus.UPLOADING, 25, null, null);
            
            // Validate file
            validateImageFile(file);
            
            // Convert file to base64 for API transmission
            byte[] fileBytes = file.getBytes();
            String base64Data = Base64.getEncoder().encodeToString(fileBytes);
            
            // Update status to processing
            updateProgress(uploadId, ImageUploadProgress.UploadStatus.PROCESSING, 50, null, null);
            
            // Upload to Multibase
            MultibaseUploadRequest request = MultibaseUploadRequest.builder()
                    .data(base64Data)
                    .filename(file.getOriginalFilename())
                    .contentType(file.getContentType())
                    .build();
            
            MultibaseUploadResponse response = uploadToMultibase(request);
            
            // Update status to completed
            updateProgress(uploadId, ImageUploadProgress.UploadStatus.COMPLETED, 100, 
                    response.getHash(), null);
            
            log.info("Upload completed successfully for uploadId: {} with hash: {}", 
                    uploadId, response.getHash());
            
            return CompletableFuture.completedFuture(uploadId);
            
        } catch (IOException e) {
            log.error("IO error during upload for uploadId: {}", uploadId, e);
            updateProgress(uploadId, ImageUploadProgress.UploadStatus.FAILED, null, null, 
                    "Failed to read file: " + e.getMessage());
            throw new MultibaseException("Failed to read file for upload", e);
        } catch (Exception e) {
            log.error("Upload failed for uploadId: {}", uploadId, e);
            updateProgress(uploadId, ImageUploadProgress.UploadStatus.FAILED, null, null, 
                    "Upload failed: " + e.getMessage());
            throw new MultibaseException("Failed to upload image to Multibase", e);
        }
    }

    /**
     * Upload an image to Multibase synchronously.
     * 
     * @param file the image file to upload
     * @return ImageReference containing the Multibase hash and metadata
     */
    public ImageReference uploadImage(MultipartFile file) {
        log.info("Starting synchronous upload for file: {}", file.getOriginalFilename());
        
        try {
            // Validate file
            validateImageFile(file);
            
            // Convert file to base64 for API transmission
            byte[] fileBytes = file.getBytes();
            String base64Data = Base64.getEncoder().encodeToString(fileBytes);
            
            // Upload to Multibase
            MultibaseUploadRequest request = MultibaseUploadRequest.builder()
                    .data(base64Data)
                    .filename(file.getOriginalFilename())
                    .contentType(file.getContentType())
                    .build();
            
            MultibaseUploadResponse response = uploadToMultibase(request);
            
            // Create ImageReference
            ImageReference imageRef = ImageReference.builder()
                    .multibaseHash(response.getHash())
                    .filename(file.getOriginalFilename())
                    .contentType(file.getContentType())
                    .size(file.getSize())
                    .build();
            
            log.info("Synchronous upload completed successfully with hash: {}", response.getHash());
            
            return imageRef;
            
        } catch (IOException e) {
            log.error("IO error during synchronous upload", e);
            throw new MultibaseException("Failed to read file for upload", e);
        } catch (Exception e) {
            log.error("Synchronous upload failed", e);
            throw new MultibaseException("Failed to upload image to Multibase", e);
        }
    }

    /**
     * Get the progress of an asynchronous upload.
     * 
     * @param uploadId the upload ID
     * @return ImageUploadProgress containing current status and progress
     */
    public ImageUploadProgress getUploadProgress(String uploadId) {
        ImageUploadProgress progress = uploadProgressMap.get(uploadId);
        if (progress == null) {
            throw new MultibaseException("Upload ID not found: " + uploadId);
        }
        return progress;
    }

    /**
     * Get the ImageReference for a completed upload.
     * 
     * @param uploadId the upload ID
     * @param file the original file (for metadata)
     * @return ImageReference if upload is complete
     * @throws MultibaseException if upload is not complete or failed
     */
    public ImageReference getImageReferenceFromUpload(String uploadId, MultipartFile file) {
        ImageUploadProgress progress = getUploadProgress(uploadId);
        
        if (progress.getStatus() != ImageUploadProgress.UploadStatus.COMPLETED) {
            throw new MultibaseException("Upload is not complete. Current status: " + progress.getStatus());
        }
        
        return ImageReference.builder()
                .multibaseHash(progress.getMultibaseHash())
                .filename(progress.getFilename())
                .contentType(file.getContentType())
                .size(file.getSize())
                .build();
    }

    /**
     * Clean up completed or failed upload progress records.
     * Should be called periodically to prevent memory leaks.
     * 
     * @param uploadId the upload ID to clean up
     */
    public void cleanupUploadProgress(String uploadId) {
        uploadProgressMap.remove(uploadId);
        log.debug("Cleaned up upload progress for uploadId: {}", uploadId);
    }

    /**
     * Validates that the file is a genuine image by inspecting magic bytes.
     * Content-Type header alone is not trusted — it can be spoofed.
     *
     * Supported formats: JPEG, PNG, GIF, WebP
     */
    private void validateImageFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new MultibaseException("File is empty or null");
        }

        // Size check first — avoid reading a huge file just to reject it
        long maxSize = 10 * 1024 * 1024L; // 10 MB
        if (file.getSize() > maxSize) {
            throw new MultibaseException("File size exceeds maximum allowed size of 10MB");
        }

        // Read the first 12 bytes for magic number detection
        byte[] header;
        try {
            byte[] allBytes = file.getBytes();
            if (allBytes.length < 4) {
                throw new MultibaseException("File is too small to be a valid image");
            }
            header = java.util.Arrays.copyOf(allBytes, Math.min(12, allBytes.length));
        } catch (java.io.IOException e) {
            throw new MultibaseException("Failed to read file for validation", e);
        }

        if (!isValidImageMagicBytes(header)) {
            throw new MultibaseException(
                    "File content does not match a supported image format (JPEG, PNG, GIF, WebP)");
        }

        log.debug("File validation passed for: {}", file.getOriginalFilename());
    }

    /**
     * Checks magic bytes for JPEG, PNG, GIF87a, GIF89a, and WebP.
     */
    private boolean isValidImageMagicBytes(byte[] header) {
        // JPEG: FF D8 FF
        if (header[0] == (byte) 0xFF && header[1] == (byte) 0xD8 && header[2] == (byte) 0xFF) {
            return true;
        }
        // PNG: 89 50 4E 47 0D 0A 1A 0A
        if (header[0] == (byte) 0x89 && header[1] == 0x50 && header[2] == 0x4E && header[3] == 0x47) {
            return true;
        }
        // GIF87a / GIF89a: 47 49 46 38
        if (header[0] == 0x47 && header[1] == 0x49 && header[2] == 0x46 && header[3] == 0x38) {
            return true;
        }
        // WebP: 52 49 46 46 ?? ?? ?? ?? 57 45 42 50
        if (header.length >= 12
                && header[0] == 0x52 && header[1] == 0x49 && header[2] == 0x46 && header[3] == 0x46
                && header[8] == 0x57 && header[9] == 0x45 && header[10] == 0x42 && header[11] == 0x50) {
            return true;
        }
        return false;
    }

    /**
     * Upload data to Multibase API.
     * 
     * @param request the upload request
     * @return MultibaseUploadResponse from the API
     * @throws MultibaseException if upload fails
     */
    private MultibaseUploadResponse uploadToMultibase(MultibaseUploadRequest request) {
        try {
            log.debug("Sending upload request to Multibase API");
            
            MultibaseUploadResponse response = webClient.post()
                    .uri("/upload")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(MultibaseUploadResponse.class)
                    .onErrorResume(WebClientResponseException.class, ex -> {
                        log.error("Multibase API error: {} - {}", ex.getStatusCode(), ex.getResponseBodyAsString());
                        return Mono.error(new MultibaseException(
                                "Multibase API error: " + ex.getStatusCode() + " - " + ex.getResponseBodyAsString(), 
                                ex));
                    })
                    .block();
            
            if (response == null || response.getHash() == null) {
                throw new MultibaseException("Invalid response from Multibase API");
            }
            
            log.debug("Received response from Multibase API with hash: {}", response.getHash());
            
            return response;
            
        } catch (Exception e) {
            log.error("Failed to upload to Multibase", e);
            throw new MultibaseException("Failed to communicate with Multibase API", e);
        }
    }

    /**
     * Update the progress of an upload.
     * 
     * @param uploadId the upload ID
     * @param status the new status
     * @param progressPercentage the progress percentage (can be null)
     * @param multibaseHash the Multibase hash (can be null)
     * @param errorMessage the error message (can be null)
     */
    private void updateProgress(String uploadId, ImageUploadProgress.UploadStatus status, 
                                Integer progressPercentage, String multibaseHash, String errorMessage) {
        ImageUploadProgress progress = uploadProgressMap.get(uploadId);
        if (progress != null) {
            progress.setStatus(status);
            if (progressPercentage != null) {
                progress.setProgressPercentage(progressPercentage);
            }
            if (multibaseHash != null) {
                progress.setMultibaseHash(multibaseHash);
            }
            if (errorMessage != null) {
                progress.setErrorMessage(errorMessage);
            }
            if (status == ImageUploadProgress.UploadStatus.COMPLETED || 
                status == ImageUploadProgress.UploadStatus.FAILED) {
                progress.setCompletedAt(System.currentTimeMillis());
            }
            
            log.debug("Updated progress for uploadId: {} - Status: {}, Progress: {}%", 
                    uploadId, status, progressPercentage);
        }
    }
}
