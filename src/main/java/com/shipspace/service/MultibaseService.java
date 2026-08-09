package com.shipspace.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shipspace.dto.ImageUploadProgress;
import com.shipspace.dto.MultibaseUploadRequest;
import com.shipspace.dto.MultibaseUploadResponse;
import com.shipspace.exception.MultibaseException;
import com.shipspace.model.ImageReference;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.time.Duration;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Service for integrating with Multibase content addressing system.
 * Upload progress is stored in Redis with a 1-hour TTL â€” safe for multi-instance deployments.
 */
@Service
@Slf4j
public class MultibaseService {

    private static final String PROGRESS_KEY_PREFIX = "upload:progress:";
    private static final Duration PROGRESS_TTL = Duration.ofHours(1);

    private final WebClient webClient;
    private final String multibaseApiUrl;
    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    public MultibaseService(
            WebClient.Builder webClientBuilder,
            @Value("${multibase.api-url}") String multibaseApiUrl,
            @Value("${multibase.api-key}") String multibaseApiKey,
            RedisTemplate<String, String> redisTemplate,
            ObjectMapper objectMapper) {
        this.multibaseApiUrl = multibaseApiUrl;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.webClient = webClientBuilder
                .baseUrl(multibaseApiUrl)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + multibaseApiKey)
                .build();
        log.info("MultibaseService initialized with API URL: {}", multibaseApiUrl);
    }

    @Async("taskExecutor")
    public CompletableFuture<String> uploadImageAsync(MultipartFile file) {
        String uploadId = UUID.randomUUID().toString();
        log.info("Starting async upload for file: {} with uploadId: {}",
                file == null ? "<null>" : file.getOriginalFilename(), uploadId);

        saveProgress(uploadId, ImageUploadProgress.builder()
                .uploadId(uploadId)
                .status(ImageUploadProgress.UploadStatus.PENDING)
                .progressPercentage(0)
                .filename(file == null ? null : file.getOriginalFilename())
                .startedAt(System.currentTimeMillis())
                .build());

        try {
            updateProgress(uploadId, ImageUploadProgress.UploadStatus.UPLOADING, 25, null, null);
            validateImageFile(file);

            byte[] fileBytes = file.getBytes();
            String base64Data = Base64.getEncoder().encodeToString(fileBytes);

            updateProgress(uploadId, ImageUploadProgress.UploadStatus.PROCESSING, 50, null, null);

            MultibaseUploadResponse response = uploadToMultibase(MultibaseUploadRequest.builder()
                    .data(base64Data)
                    .filename(file.getOriginalFilename())
                    .contentType(file.getContentType())
                    .build());

            updateProgress(uploadId, ImageUploadProgress.UploadStatus.COMPLETED, 100, response.getHash(), null);
            log.info("Async upload completed for uploadId: {} hash: {}", uploadId, response.getHash());
            return CompletableFuture.completedFuture(uploadId);

        } catch (IOException e) {
            log.error("IO error during async upload for uploadId: {}", uploadId, e);
            updateProgress(uploadId, ImageUploadProgress.UploadStatus.FAILED, null, null, "Failed to read file: " + e.getMessage());
            throw new MultibaseException("Failed to read file for upload", e);
        } catch (Exception e) {
            log.error("Async upload failed for uploadId: {}", uploadId, e);
            updateProgress(uploadId, ImageUploadProgress.UploadStatus.FAILED, null, null, "Upload failed: " + e.getMessage());
            throw new MultibaseException("Failed to upload image to Multibase", e);
        }
    }

    public ImageReference uploadImage(MultipartFile file) {
        log.info("Starting synchronous upload for file: {}",
                file == null ? "<null>" : file.getOriginalFilename());
        try {
            validateImageFile(file);
            byte[] fileBytes = file.getBytes();
            String base64Data = Base64.getEncoder().encodeToString(fileBytes);

            MultibaseUploadResponse response = uploadToMultibase(MultibaseUploadRequest.builder()
                    .data(base64Data)
                    .filename(file.getOriginalFilename())
                    .contentType(file.getContentType())
                    .build());

            log.info("Synchronous upload completed with hash: {}", response.getHash());
            return ImageReference.builder()
                    .multibaseHash(response.getHash())
                    .filename(file.getOriginalFilename())
                    .contentType(file.getContentType())
                    .size(file.getSize())
                    .build();
        } catch (IOException e) {
            throw new MultibaseException("Failed to read file for upload", e);
        } catch (MultibaseException e) {
            throw e;
        } catch (Exception e) {
            throw new MultibaseException("Failed to upload image to Multibase", e);
        }
    }

    public ImageUploadProgress getUploadProgress(String uploadId) {
        String key = PROGRESS_KEY_PREFIX + uploadId;
        String json = redisTemplate.opsForValue().get(key);
        if (json == null) {
            throw new MultibaseException("Upload ID not found: " + uploadId);
        }
        try {
            return objectMapper.readValue(json, ImageUploadProgress.class);
        } catch (JsonProcessingException e) {
            throw new MultibaseException("Failed to deserialize upload progress for: " + uploadId, e);
        }
    }

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

    public void cleanupUploadProgress(String uploadId) {
        redisTemplate.delete(PROGRESS_KEY_PREFIX + uploadId);
        log.debug("Cleaned up upload progress for uploadId: {}", uploadId);
    }

    // â”€â”€ private helpers â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    private void saveProgress(String uploadId, ImageUploadProgress progress) {
        try {
            String json = objectMapper.writeValueAsString(progress);
            redisTemplate.opsForValue().set(PROGRESS_KEY_PREFIX + uploadId, json, PROGRESS_TTL);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize upload progress for uploadId: {}", uploadId, e);
        }
    }

    private void updateProgress(String uploadId, ImageUploadProgress.UploadStatus status,
                                Integer progressPercentage, String multibaseHash, String errorMessage) {
        try {
            ImageUploadProgress progress = getUploadProgress(uploadId);
            progress.setStatus(status);
            if (progressPercentage != null) progress.setProgressPercentage(progressPercentage);
            if (multibaseHash != null) progress.setMultibaseHash(multibaseHash);
            if (errorMessage != null) progress.setErrorMessage(errorMessage);
            if (status == ImageUploadProgress.UploadStatus.COMPLETED
                    || status == ImageUploadProgress.UploadStatus.FAILED) {
                progress.setCompletedAt(System.currentTimeMillis());
            }
            saveProgress(uploadId, progress);
        } catch (Exception e) {
            log.warn("Failed to update progress for uploadId {}: {}", uploadId, e.getMessage());
        }
    }

    private void validateImageFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new MultibaseException("File is empty or null");
        }
        String contentType = file.getContentType();
        if (!isSupportedImageContentType(contentType)) {
            throw new MultibaseException("File content type must be one of JPEG, PNG, GIF, or WebP");
        }
        long maxSize = 10 * 1024 * 1024L;
        if (file.getSize() > maxSize) {
            throw new MultibaseException("File size exceeds maximum allowed size of 10MB");
        }
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
    }

    private boolean isSupportedImageContentType(String contentType) {
        return "image/jpeg".equalsIgnoreCase(contentType)
                || "image/png".equalsIgnoreCase(contentType)
                || "image/gif".equalsIgnoreCase(contentType)
                || "image/webp".equalsIgnoreCase(contentType);
    }

    private boolean isValidImageMagicBytes(byte[] header) {
        if (header[0] == (byte) 0xFF && header[1] == (byte) 0xD8 && header[2] == (byte) 0xFF) return true;
        if (header[0] == (byte) 0x89 && header[1] == 0x50 && header[2] == 0x4E && header[3] == 0x47) return true;
        if (header[0] == 0x47 && header[1] == 0x49 && header[2] == 0x46 && header[3] == 0x38) return true;
        return header.length >= 12
                && header[0] == 0x52 && header[1] == 0x49 && header[2] == 0x46 && header[3] == 0x46
                && header[8] == 0x57 && header[9] == 0x45 && header[10] == 0x42 && header[11] == 0x50;
    }

    private MultibaseUploadResponse uploadToMultibase(MultibaseUploadRequest request) {
        try {
            MultibaseUploadResponse response = webClient.post()
                    .uri("/upload")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(MultibaseUploadResponse.class)
                    .onErrorResume(WebClientResponseException.class, ex -> {
                        log.error("Multibase API error: {} - {}", ex.getStatusCode(), ex.getResponseBodyAsString());
                        return Mono.error(new MultibaseException(
                                "Multibase API error: " + ex.getStatusCode(), ex));
                    })
                    .block();
            if (response == null || response.getHash() == null) {
                throw new MultibaseException("Invalid response from Multibase API");
            }
            return response;
        } catch (MultibaseException e) {
            throw e;
        } catch (Exception e) {
            throw new MultibaseException("Failed to communicate with Multibase API", e);
        }
    }
}
