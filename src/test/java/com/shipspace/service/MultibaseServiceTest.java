package com.shipspace.service;

import com.shipspace.dto.ImageUploadProgress;
import com.shipspace.dto.MultibaseUploadResponse;
import com.shipspace.exception.MultibaseException;
import com.shipspace.model.ImageReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for MultibaseService.
 * Tests image upload, content addressing, and progress tracking functionality.
 */
class MultibaseServiceTest {

    private MockWebServer mockWebServer;
    private MultibaseService multibaseService;

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();
        
        String baseUrl = mockWebServer.url("/").toString();
        WebClient.Builder webClientBuilder = WebClient.builder();

        @SuppressWarnings("unchecked")
        RedisTemplate<String, String> redisTemplate = Mockito.mock(RedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOps = Mockito.mock(ValueOperations.class);
        Map<String, String> redisStore = new ConcurrentHashMap<>();
        Mockito.when(redisTemplate.opsForValue()).thenReturn(valueOps);
        Mockito.when(valueOps.get(Mockito.anyString()))
                .thenAnswer(invocation -> redisStore.get(invocation.getArgument(0)));
        Mockito.doAnswer(invocation -> {
            redisStore.put(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(valueOps).set(Mockito.anyString(), Mockito.anyString(), Mockito.any());
        Mockito.when(redisTemplate.delete(Mockito.anyString()))
                .thenAnswer(invocation -> redisStore.remove(invocation.getArgument(0)) != null);

        multibaseService = new MultibaseService(
                webClientBuilder,
                baseUrl,
                "test-api-key",
                redisTemplate,
                new ObjectMapper()
        );
    }

    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    @Test
    void testUploadImage_Success() {
        // Arrange
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test.jpg",
                "image/jpeg",
                jpegBytes()
        );
        
        String mockResponse = """
                {
                    "hash": "QmTestHash123",
                    "size": 18,
                    "contentType": "image/jpeg",
                    "status": "success"
                }
                """;
        
        mockWebServer.enqueue(new MockResponse()
                .setBody(mockResponse)
                .addHeader("Content-Type", "application/json"));
        
        // Act
        ImageReference result = multibaseService.uploadImage(file);
        
        // Assert
        assertNotNull(result);
        assertEquals("QmTestHash123", result.getMultibaseHash());
        assertEquals("test.jpg", result.getFilename());
        assertEquals("image/jpeg", result.getContentType());
        assertEquals(18L, result.getSize());
    }

    @Test
    void testUploadImage_EmptyFile() {
        // Arrange
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test.jpg",
                "image/jpeg",
                new byte[0]
        );
        
        // Act & Assert
        MultibaseException exception = assertThrows(
                MultibaseException.class,
                () -> multibaseService.uploadImage(file)
        );
        assertTrue(exception.getMessage().contains("empty"));
    }

    @Test
    void testUploadImage_InvalidContentType() {
        // Arrange
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test.txt",
                "text/plain",
                "test content".getBytes()
        );
        
        // Act & Assert
        MultibaseException exception = assertThrows(
                MultibaseException.class,
                () -> multibaseService.uploadImage(file)
        );
        assertTrue(exception.getMessage().contains("content type"));
    }

    @Test
    void testUploadImage_FileTooLarge() {
        // Arrange - Create a file larger than 10MB
        byte[] largeContent = new byte[11 * 1024 * 1024]; // 11MB
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "large.jpg",
                "image/jpeg",
                largeContent
        );
        
        // Act & Assert
        MultibaseException exception = assertThrows(
                MultibaseException.class,
                () -> multibaseService.uploadImage(file)
        );
        assertTrue(exception.getMessage().contains("exceeds maximum"));
    }

    @Test
    void testUploadImage_ApiError() {
        // Arrange
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test.jpg",
                "image/jpeg",
                jpegBytes()
        );
        
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(500)
                .setBody("Internal Server Error"));
        
        // Act & Assert
        MultibaseException exception = assertThrows(
                MultibaseException.class,
                () -> multibaseService.uploadImage(file)
        );
        assertTrue(exception.getMessage().contains("Multibase API"));
    }

    @Test
    void testUploadImageAsync_Success() throws Exception {
        // Arrange
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test.jpg",
                "image/jpeg",
                jpegBytes()
        );
        
        String mockResponse = """
                {
                    "hash": "QmTestHash456",
                    "size": 18,
                    "contentType": "image/jpeg",
                    "status": "success"
                }
                """;
        
        mockWebServer.enqueue(new MockResponse()
                .setBody(mockResponse)
                .addHeader("Content-Type", "application/json"));
        
        // Act - Call async method but wait for completion
        CompletableFuture<String> future = multibaseService.uploadImageAsync(file);
        
        // Wait for completion with timeout
        String uploadId = future.get(10, java.util.concurrent.TimeUnit.SECONDS);
        
        // Assert
        assertNotNull(uploadId);
        
        // Give async processing time to update progress
        Thread.sleep(2000);
        
        ImageUploadProgress progress = multibaseService.getUploadProgress(uploadId);
        assertNotNull(progress);
        assertEquals(ImageUploadProgress.UploadStatus.COMPLETED, progress.getStatus());
        assertEquals("QmTestHash456", progress.getMultibaseHash());
        assertEquals(100, progress.getProgressPercentage());
    }

    @Test
    void testGetUploadProgress_NotFound() {
        // Act & Assert
        MultibaseException exception = assertThrows(
                MultibaseException.class,
                () -> multibaseService.getUploadProgress("non-existent-id")
        );
        assertTrue(exception.getMessage().contains("not found"));
    }

    @Test
    void testGetImageReferenceFromUpload_Success() throws Exception {
        // Arrange
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test.jpg",
                "image/jpeg",
                jpegBytes()
        );
        
        String mockResponse = """
                {
                    "hash": "QmTestHash789",
                    "size": 18,
                    "contentType": "image/jpeg",
                    "status": "success"
                }
                """;
        
        mockWebServer.enqueue(new MockResponse()
                .setBody(mockResponse)
                .addHeader("Content-Type", "application/json"));
        
        CompletableFuture<String> future = multibaseService.uploadImageAsync(file);
        String uploadId = future.get(10, java.util.concurrent.TimeUnit.SECONDS);
        
        // Wait for async processing
        Thread.sleep(2000);
        
        // Act
        ImageReference result = multibaseService.getImageReferenceFromUpload(uploadId, file);
        
        // Assert
        assertNotNull(result);
        assertEquals("QmTestHash789", result.getMultibaseHash());
        assertEquals("test.jpg", result.getFilename());
        assertEquals("image/jpeg", result.getContentType());
    }

    @Test
    void testGetImageReferenceFromUpload_NotComplete() {
        // Arrange - Create a pending upload manually
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test.jpg",
                "image/jpeg",
                jpegBytes()
        );
        
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(500)
                .setBody("Internal Server Error"));

        MultibaseException exception = assertThrows(
                MultibaseException.class,
                () -> multibaseService.uploadImageAsync(file)
        );
        assertTrue(exception.getMessage().contains("Failed to upload image to Multibase"));
    }

    @Test
    void testCleanupUploadProgress() throws Exception {
        // Arrange
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test.jpg",
                "image/jpeg",
                jpegBytes()
        );
        
        String mockResponse = """
                {
                    "hash": "QmTestHashCleanup",
                    "size": 18,
                    "contentType": "image/jpeg",
                    "status": "success"
                }
                """;
        
        mockWebServer.enqueue(new MockResponse()
                .setBody(mockResponse)
                .addHeader("Content-Type", "application/json"));
        
        CompletableFuture<String> future = multibaseService.uploadImageAsync(file);
        String uploadId = future.get(10, java.util.concurrent.TimeUnit.SECONDS);
        
        // Wait for async processing
        Thread.sleep(2000);
        
        // Verify progress exists
        ImageUploadProgress progress = multibaseService.getUploadProgress(uploadId);
        assertNotNull(progress);
        
        // Act
        multibaseService.cleanupUploadProgress(uploadId);
        
        // Assert
        MultibaseException exception = assertThrows(
                MultibaseException.class,
                () -> multibaseService.getUploadProgress(uploadId)
        );
        assertTrue(exception.getMessage().contains("not found"));
    }

    @Test
    void testUploadImage_ValidatesJpegImage() {
        // Arrange
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "photo.jpg",
                "image/jpeg",
                jpegBytes()
        );
        
        String mockResponse = """
                {
                    "hash": "QmJpegHash",
                    "size": 12,
                    "contentType": "image/jpeg",
                    "status": "success"
                }
                """;
        
        mockWebServer.enqueue(new MockResponse()
                .setBody(mockResponse)
                .addHeader("Content-Type", "application/json"));
        
        // Act
        ImageReference result = multibaseService.uploadImage(file);
        
        // Assert
        assertNotNull(result);
        assertEquals("image/jpeg", result.getContentType());
    }

    @Test
    void testUploadImage_ValidatesPngImage() {
        // Arrange
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "graphic.png",
                "image/png",
                pngBytes()
        );
        
        String mockResponse = """
                {
                    "hash": "QmPngHash",
                    "size": 11,
                    "contentType": "image/png",
                    "status": "success"
                }
                """;
        
        mockWebServer.enqueue(new MockResponse()
                .setBody(mockResponse)
                .addHeader("Content-Type", "application/json"));
        
        // Act
        ImageReference result = multibaseService.uploadImage(file);
        
        // Assert
        assertNotNull(result);
        assertEquals("image/png", result.getContentType());
    }

    @Test
    void testUploadImage_NullFile() {
        // Act & Assert
        MultibaseException exception = assertThrows(
                MultibaseException.class,
                () -> multibaseService.uploadImage(null)
        );
        assertTrue(exception.getMessage().contains("empty or null"));
    }

    @Test
    void testUploadImage_RenamedExecutableRejectedByMagicBytes() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "malware.png",
                "image/png",
                new byte[] {0x4D, 0x5A, 0x00, 0x00, 0x01, 0x02}
        );

        MultibaseException exception = assertThrows(
                MultibaseException.class,
                () -> multibaseService.uploadImage(file)
        );
        assertTrue(exception.getMessage().contains("supported image format"));
    }

    private static byte[] jpegBytes() {
        return new byte[] {
                (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0,
                0x00, 0x10, 0x4A, 0x46, 0x49, 0x46, 0x00, 0x01,
                0x01, 0x01, 0x00, 0x48, 0x00, 0x48
        };
    }

    private static byte[] pngBytes() {
        return new byte[] {
                (byte) 0x89, 0x50, 0x4E, 0x47,
                0x0D, 0x0A, 0x1A, 0x0A,
                0x00, 0x00, 0x00, 0x0D
        };
    }
}
