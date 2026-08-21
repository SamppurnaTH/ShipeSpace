package com.shipspace.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shipspace.dto.ShipCreateRequest;
import com.shipspace.dto.ShipResponse;
import com.shipspace.dto.ShipUpdateRequest;
import com.shipspace.exception.ForbiddenException;
import com.shipspace.exception.ResourceNotFoundException;
import com.shipspace.model.ExternalLink;
import com.shipspace.model.ImageReference;
import com.shipspace.service.ShipService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for ShipController.
 * Tests all ship content management endpoints with authentication and authorization.
 * 
 * Tests Requirements 4.1, 4.2, 4.3, 4.4, 4.5:
 * - POST /api/ships - Create new ship with project association
 * - GET /api/ships/{shipId} - Get ship details
 * - PUT /api/ships/{shipId} - Update ship within edit window
 * - POST /api/ships/{shipId}/images - Upload images to ship
 */
@WebMvcTest(controllers = ShipController.class,
    excludeAutoConfiguration = {
        org.springframework.boot.autoconfigure.data.mongo.MongoDataAutoConfiguration.class,
        org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration.class
    },
    excludeFilters = @org.springframework.context.annotation.ComponentScan.Filter(
        type = org.springframework.context.annotation.FilterType.ASSIGNABLE_TYPE,
        classes = com.shipspace.config.MongoAuditingConfig.class))
@AutoConfigureMockMvc
@org.springframework.context.annotation.Import(com.shipspace.config.MockMvcContextPathCustomizer.class)
@DisplayName("ShipController Integration Tests")
class ShipControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ShipService shipService;

    @MockBean
    private com.shipspace.util.JwtUtil jwtUtil;

    @MockBean
    private com.shipspace.security.TokenBlacklistService tokenBlacklistService;

    private String testUserId;
    private String testProjectId;
    private String testShipId;
    private ShipResponse testShipResponse;
    private ShipCreateRequest testCreateRequest;
    private ShipUpdateRequest testUpdateRequest;

    @BeforeEach
    void setUp() {
        testUserId = "user123";
        testProjectId = "project123";
        testShipId = "ship123";

        Instant now = Instant.now();
        Instant editWindowExpires = now.plus(10, ChronoUnit.MINUTES);

        ExternalLink link = ExternalLink.builder()
                .url("https://example.com")
                .title("Example Link")
                .description("Test link")
                .build();

        testShipResponse = ShipResponse.builder()
                .id(testShipId)
                .userId(testUserId)
                .projectId(testProjectId)
                .content("Test ship content")
                .images(new ArrayList<>())
                .externalLinks(Arrays.asList(link))
                .likeCount(0)
                .commentCount(0)
                .trendingScore(0.0)
                .editWindowOpen(true)
                .editWindowExpires(editWindowExpires)
                .createdAt(now)
                .updatedAt(now)
                .build();

        testCreateRequest = ShipCreateRequest.builder()
                .projectId(testProjectId)
                .content("Test ship content")
                .externalLinks(Arrays.asList(link))
                .build();

        testUpdateRequest = ShipUpdateRequest.builder()
                .content("Updated ship content")
                .build();
    }

    // ========== Ship Creation Tests (Requirement 4.1, 4.2, 4.4) ==========

    @Test
    @WithMockUser(username = "user123")
    @DisplayName("POST /api/ships - Should create ship successfully")
    void createShip_Success() throws Exception {
        // Arrange
        when(shipService.createShip(eq(testUserId), any(ShipCreateRequest.class)))
                .thenReturn(testShipResponse);

        // Act & Assert
        mockMvc.perform(post("/api/ships")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(testCreateRequest)))
                .andExpect(status().isCreated())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(testShipId))
                .andExpect(jsonPath("$.userId").value(testUserId))
                .andExpect(jsonPath("$.projectId").value(testProjectId))
                .andExpect(jsonPath("$.content").value("Test ship content"))
                .andExpect(jsonPath("$.externalLinks", hasSize(1)))
                .andExpect(jsonPath("$.externalLinks[0].url").value("https://example.com"))
                .andExpect(jsonPath("$.editWindowOpen").value(true))
                .andExpect(jsonPath("$.editWindowExpires").exists())
                .andExpect(jsonPath("$.likeCount").value(0))
                .andExpect(jsonPath("$.commentCount").value(0));

        verify(shipService).createShip(eq(testUserId), any(ShipCreateRequest.class));
    }

    @Test
    @DisplayName("POST /api/ships - Should return 401 when not authenticated")
    void createShip_Unauthorized() throws Exception {
        // Act & Assert
        mockMvc.perform(post("/api/ships")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(testCreateRequest)))
                .andExpect(status().isUnauthorized());

        verify(shipService, never()).createShip(anyString(), any(ShipCreateRequest.class));
    }

    @Test
    @WithMockUser(username = "user123")
    @DisplayName("POST /api/ships - Should validate required fields")
    void createShip_ValidationError() throws Exception {
        // Arrange - Create request with missing required fields
        ShipCreateRequest invalidRequest = ShipCreateRequest.builder()
                .projectId("") // Empty project ID
                .content("") // Empty content
                .build();

        // Act & Assert
        mockMvc.perform(post("/api/ships")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        verify(shipService, never()).createShip(anyString(), any(ShipCreateRequest.class));
    }

    @Test
    @WithMockUser(username = "user123")
    @DisplayName("POST /api/ships - Should validate content length")
    void createShip_ContentTooLong() throws Exception {
        // Arrange - Create request with content exceeding max length
        String longContent = "a".repeat(5001); // Max is 5000
        ShipCreateRequest invalidRequest = ShipCreateRequest.builder()
                .projectId(testProjectId)
                .content(longContent)
                .build();

        // Act & Assert
        mockMvc.perform(post("/api/ships")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        verify(shipService, never()).createShip(anyString(), any(ShipCreateRequest.class));
    }

    @Test
    @WithMockUser(username = "user123")
    @DisplayName("POST /api/ships - Should throw exception when project not owned by user")
    void createShip_ProjectNotOwned() throws Exception {
        // Arrange - Requirement 4.1: Project association validation
        when(shipService.createShip(eq(testUserId), any(ShipCreateRequest.class)))
                .thenThrow(new ForbiddenException("access", "project"));

        // Act & Assert
        mockMvc.perform(post("/api/ships")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(testCreateRequest)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        verify(shipService).createShip(eq(testUserId), any(ShipCreateRequest.class));
    }

    @Test
    @WithMockUser(username = "user123")
    @DisplayName("POST /api/ships - Should throw exception when project not found")
    void createShip_ProjectNotFound() throws Exception {
        // Arrange - Requirement 4.1: Project must exist
        when(shipService.createShip(eq(testUserId), any(ShipCreateRequest.class)))
                .thenThrow(new ResourceNotFoundException("Project", testProjectId));

        // Act & Assert
        mockMvc.perform(post("/api/ships")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(testCreateRequest)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));

        verify(shipService).createShip(eq(testUserId), any(ShipCreateRequest.class));
    }

    @Test
    @WithMockUser(username = "user123")
    @DisplayName("POST /api/ships - Should create ship with minimal data")
    void createShip_MinimalData() throws Exception {
        // Arrange
        ShipCreateRequest minimalRequest = ShipCreateRequest.builder()
                .projectId(testProjectId)
                .content("Minimal ship content")
                .build();

        ShipResponse minimalResponse = ShipResponse.builder()
                .id("ship456")
                .userId(testUserId)
                .projectId(testProjectId)
                .content("Minimal ship content")
                .images(new ArrayList<>())
                .externalLinks(new ArrayList<>())
                .likeCount(0)
                .commentCount(0)
                .trendingScore(0.0)
                .editWindowOpen(true)
                .editWindowExpires(Instant.now().plus(10, ChronoUnit.MINUTES))
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        when(shipService.createShip(eq(testUserId), any(ShipCreateRequest.class)))
                .thenReturn(minimalResponse);

        // Act & Assert
        mockMvc.perform(post("/api/ships")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(minimalRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.content").value("Minimal ship content"))
                .andExpect(jsonPath("$.externalLinks", hasSize(0)))
                .andExpect(jsonPath("$.images", hasSize(0)));

        verify(shipService).createShip(eq(testUserId), any(ShipCreateRequest.class));
    }

    // ========== Ship Retrieval Tests ==========

    @Test
    @WithMockUser(username = "user123")
    @DisplayName("GET /api/ships/{shipId} - Should return ship details")
    void getShip_Success() throws Exception {
        // Arrange
        when(shipService.getShipById(testShipId)).thenReturn(testShipResponse);

        // Act & Assert
        mockMvc.perform(get("/api/ships/{shipId}", testShipId))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(testShipId))
                .andExpect(jsonPath("$.userId").value(testUserId))
                .andExpect(jsonPath("$.projectId").value(testProjectId))
                .andExpect(jsonPath("$.content").value("Test ship content"))
                .andExpect(jsonPath("$.editWindowOpen").value(true))
                .andExpect(jsonPath("$.likeCount").value(0))
                .andExpect(jsonPath("$.commentCount").value(0));

        verify(shipService).getShipById(testShipId);
    }

    @Test
    @WithMockUser(username = "user123")
    @DisplayName("GET /api/ships/{shipId} - Should return 404 when ship not found")
    void getShip_NotFound() throws Exception {
        // Arrange
        when(shipService.getShipById(anyString()))
                .thenThrow(new ResourceNotFoundException("Ship", "nonexistent"));

        // Act & Assert
        mockMvc.perform(get("/api/ships/{shipId}", "nonexistent"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Ship not found with id: nonexistent"));

        verify(shipService).getShipById("nonexistent");
    }

    @Test
    @WithMockUser(username = "user123")
    @DisplayName("GET /api/ships/{shipId} - Should return ship with images")
    void getShip_WithImages() throws Exception {
        // Arrange
        ImageReference image1 = ImageReference.builder()
                .multibaseHash("hash1")
                .filename("image1.jpg")
                .contentType("image/jpeg")
                .size(1024L)
                .build();

        ImageReference image2 = ImageReference.builder()
                .multibaseHash("hash2")
                .filename("image2.jpg")
                .contentType("image/jpeg")
                .size(2048L)
                .build();

        ShipResponse shipWithImages = ShipResponse.builder()
                .id(testShipId)
                .userId(testUserId)
                .projectId(testProjectId)
                .content("Test ship content")
                .images(Arrays.asList(image1, image2))
                .externalLinks(new ArrayList<>())
                .likeCount(5)
                .commentCount(3)
                .trendingScore(10.5)
                .editWindowOpen(false)
                .editWindowExpires(Instant.now().minus(5, ChronoUnit.MINUTES))
                .createdAt(Instant.now().minus(15, ChronoUnit.MINUTES))
                .updatedAt(Instant.now().minus(15, ChronoUnit.MINUTES))
                .build();

        when(shipService.getShipById(testShipId)).thenReturn(shipWithImages);

        // Act & Assert
        mockMvc.perform(get("/api/ships/{shipId}", testShipId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.images", hasSize(2)))
                .andExpect(jsonPath("$.images[0].multibaseHash").value("hash1"))
                .andExpect(jsonPath("$.images[0].filename").value("image1.jpg"))
                .andExpect(jsonPath("$.images[1].multibaseHash").value("hash2"))
                .andExpect(jsonPath("$.editWindowOpen").value(false))
                .andExpect(jsonPath("$.likeCount").value(5))
                .andExpect(jsonPath("$.commentCount").value(3))
                .andExpect(jsonPath("$.trendingScore").value(10.5));

        verify(shipService).getShipById(testShipId);
    }

    // ========== Ship Update Tests (Requirement 4.4, 4.5) ==========

    @Test
    @WithMockUser(username = "user123")
    @DisplayName("PUT /api/ships/{shipId} - Should update ship within edit window")
    void updateShip_Success() throws Exception {
        // Arrange - Requirement 4.4: Updates allowed within 10 minutes
        ShipResponse updatedResponse = ShipResponse.builder()
                .id(testShipId)
                .userId(testUserId)
                .projectId(testProjectId)
                .content("Updated ship content")
                .images(testShipResponse.getImages())
                .externalLinks(testShipResponse.getExternalLinks())
                .likeCount(0)
                .commentCount(0)
                .trendingScore(0.0)
                .editWindowOpen(true)
                .editWindowExpires(testShipResponse.getEditWindowExpires())
                .createdAt(testShipResponse.getCreatedAt())
                .updatedAt(Instant.now())
                .build();

        when(shipService.updateShip(eq(testShipId), eq(testUserId), any(ShipUpdateRequest.class)))
                .thenReturn(updatedResponse);

        // Act & Assert
        mockMvc.perform(put("/api/ships/{shipId}", testShipId)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(testUpdateRequest)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(testShipId))
                .andExpect(jsonPath("$.content").value("Updated ship content"))
                .andExpect(jsonPath("$.editWindowOpen").value(true));

        verify(shipService).updateShip(eq(testShipId), eq(testUserId), any(ShipUpdateRequest.class));
    }

    @Test
    @DisplayName("PUT /api/ships/{shipId} - Should return 401 when not authenticated")
    void updateShip_Unauthorized() throws Exception {
        // Act & Assert
        mockMvc.perform(put("/api/ships/{shipId}", testShipId)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(testUpdateRequest)))
                .andExpect(status().isUnauthorized());

        verify(shipService, never()).updateShip(anyString(), anyString(), any(ShipUpdateRequest.class));
    }

    @Test
    @WithMockUser(username = "user456")
    @DisplayName("PUT /api/ships/{shipId} - Should return 403 when not ship owner")
    void updateShip_Forbidden() throws Exception {
        // Arrange
        when(shipService.updateShip(eq(testShipId), eq("user456"), any(ShipUpdateRequest.class)))
                .thenThrow(new ForbiddenException("update", "ship"));

        // Act & Assert
        mockMvc.perform(put("/api/ships/{shipId}", testShipId)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(testUpdateRequest)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        verify(shipService).updateShip(eq(testShipId), eq("user456"), any(ShipUpdateRequest.class));
    }

    @Test
    @WithMockUser(username = "user123")
    @DisplayName("PUT /api/ships/{shipId} - Should return 403 when edit window expired")
    void updateShip_EditWindowExpired() throws Exception {
        // Arrange - Requirement 4.5: Reject edits after edit window expires
        when(shipService.updateShip(eq(testShipId), eq(testUserId), any(ShipUpdateRequest.class)))
                .thenThrow(new ForbiddenException("Edit window has expired. Ships can only be edited within 10 minutes of creation."));

        // Act & Assert
        mockMvc.perform(put("/api/ships/{shipId}", testShipId)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(testUpdateRequest)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.message").value(containsString("Edit window has expired")));

        verify(shipService).updateShip(eq(testShipId), eq(testUserId), any(ShipUpdateRequest.class));
    }

    @Test
    @WithMockUser(username = "user123")
    @DisplayName("PUT /api/ships/{shipId} - Should return 404 when ship not found")
    void updateShip_NotFound() throws Exception {
        // Arrange
        when(shipService.updateShip(eq("nonexistent"), eq(testUserId), any(ShipUpdateRequest.class)))
                .thenThrow(new ResourceNotFoundException("Ship", "nonexistent"));

        // Act & Assert
        mockMvc.perform(put("/api/ships/{shipId}", "nonexistent")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(testUpdateRequest)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));

        verify(shipService).updateShip(eq("nonexistent"), eq(testUserId), any(ShipUpdateRequest.class));
    }

    @Test
    @WithMockUser(username = "user123")
    @DisplayName("PUT /api/ships/{shipId} - Should update only provided fields")
    void updateShip_PartialUpdate() throws Exception {
        // Arrange - Only update external links
        ExternalLink newLink = ExternalLink.builder()
                .url("https://newlink.com")
                .title("New Link")
                .build();

        ShipUpdateRequest partialUpdate = ShipUpdateRequest.builder()
                .externalLinks(Arrays.asList(newLink))
                .build(); // content is null, should not be updated

        ShipResponse updatedResponse = ShipResponse.builder()
                .id(testShipId)
                .userId(testUserId)
                .projectId(testProjectId)
                .content("Test ship content") // Content unchanged
                .images(testShipResponse.getImages())
                .externalLinks(Arrays.asList(newLink))
                .likeCount(0)
                .commentCount(0)
                .trendingScore(0.0)
                .editWindowOpen(true)
                .editWindowExpires(testShipResponse.getEditWindowExpires())
                .createdAt(testShipResponse.getCreatedAt())
                .updatedAt(Instant.now())
                .build();

        when(shipService.updateShip(eq(testShipId), eq(testUserId), any(ShipUpdateRequest.class)))
                .thenReturn(updatedResponse);

        // Act & Assert
        mockMvc.perform(put("/api/ships/{shipId}", testShipId)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(partialUpdate)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("Test ship content"))
                .andExpect(jsonPath("$.externalLinks", hasSize(1)))
                .andExpect(jsonPath("$.externalLinks[0].url").value("https://newlink.com"));

        verify(shipService).updateShip(eq(testShipId), eq(testUserId), any(ShipUpdateRequest.class));
    }

    @Test
    @WithMockUser(username = "user123")
    @DisplayName("PUT /api/ships/{shipId} - Should validate content length on update")
    void updateShip_ContentTooLong() throws Exception {
        // Arrange
        String longContent = "a".repeat(5001); // Max is 5000
        ShipUpdateRequest invalidUpdate = ShipUpdateRequest.builder()
                .content(longContent)
                .build();

        // Act & Assert
        mockMvc.perform(put("/api/ships/{shipId}", testShipId)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidUpdate)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        verify(shipService, never()).updateShip(anyString(), anyString(), any(ShipUpdateRequest.class));
    }

    // ========== Image Upload Tests (Requirement 4.3) ==========

    @Test
    @WithMockUser(username = "user123")
    @DisplayName("POST /api/ships/{shipId}/images - Should upload images successfully")
    void uploadImages_Success() throws Exception {
        // Arrange - Requirement 4.3: Multibase integration
        MockMultipartFile file1 = new MockMultipartFile(
                "files",
                "image1.jpg",
                "image/jpeg",
                "test image content 1".getBytes()
        );

        MockMultipartFile file2 = new MockMultipartFile(
                "files",
                "image2.jpg",
                "image/jpeg",
                "test image content 2".getBytes()
        );

        ImageReference imageRef1 = ImageReference.builder()
                .multibaseHash("hash1")
                .filename("image1.jpg")
                .contentType("image/jpeg")
                .size(1024L)
                .build();

        ImageReference imageRef2 = ImageReference.builder()
                .multibaseHash("hash2")
                .filename("image2.jpg")
                .contentType("image/jpeg")
                .size(2048L)
                .build();

        ShipResponse responseWithImages = ShipResponse.builder()
                .id(testShipId)
                .userId(testUserId)
                .projectId(testProjectId)
                .content("Test ship content")
                .images(Arrays.asList(imageRef1, imageRef2))
                .externalLinks(testShipResponse.getExternalLinks())
                .likeCount(0)
                .commentCount(0)
                .trendingScore(0.0)
                .editWindowOpen(true)
                .editWindowExpires(testShipResponse.getEditWindowExpires())
                .createdAt(testShipResponse.getCreatedAt())
                .updatedAt(Instant.now())
                .build();

        when(shipService.uploadShipImages(eq(testShipId), eq(testUserId), anyList()))
                .thenReturn(responseWithImages);

        // Act & Assert
        mockMvc.perform(multipart("/api/ships/{shipId}/images", testShipId)
                        .file(file1)
                        .file(file2)
                        .with(csrf())
                        .with(request -> {
                            request.setMethod("POST");
                            return request;
                        }))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(testShipId))
                .andExpect(jsonPath("$.images", hasSize(2)))
                .andExpect(jsonPath("$.images[0].multibaseHash").value("hash1"))
                .andExpect(jsonPath("$.images[0].filename").value("image1.jpg"))
                .andExpect(jsonPath("$.images[1].multibaseHash").value("hash2"))
                .andExpect(jsonPath("$.images[1].filename").value("image2.jpg"));

        verify(shipService).uploadShipImages(eq(testShipId), eq(testUserId), anyList());
    }

    @Test
    @DisplayName("POST /api/ships/{shipId}/images - Should return 401 when not authenticated")
    void uploadImages_Unauthorized() throws Exception {
        // Arrange
        MockMultipartFile file = new MockMultipartFile(
                "files",
                "image.jpg",
                "image/jpeg",
                "test image content".getBytes()
        );

        // Act & Assert
        mockMvc.perform(multipart("/api/ships/{shipId}/images", testShipId)
                        .file(file)
                        .with(csrf()))
                .andExpect(status().isUnauthorized());

        verify(shipService, never()).uploadShipImages(anyString(), anyString(), anyList());
    }

    @Test
    @WithMockUser(username = "user456")
    @DisplayName("POST /api/ships/{shipId}/images - Should return 403 when not ship owner")
    void uploadImages_Forbidden() throws Exception {
        // Arrange
        MockMultipartFile file = new MockMultipartFile(
                "files",
                "image.jpg",
                "image/jpeg",
                "test image content".getBytes()
        );

        when(shipService.uploadShipImages(eq(testShipId), eq("user456"), anyList()))
                .thenThrow(new ForbiddenException("upload images to", "ship"));

        // Act & Assert
        mockMvc.perform(multipart("/api/ships/{shipId}/images", testShipId)
                        .file(file)
                        .with(csrf())
                        .with(request -> {
                            request.setMethod("POST");
                            return request;
                        }))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        verify(shipService).uploadShipImages(eq(testShipId), eq("user456"), anyList());
    }

    @Test
    @WithMockUser(username = "user123")
    @DisplayName("POST /api/ships/{shipId}/images - Should return 403 when edit window expired")
    void uploadImages_EditWindowExpired() throws Exception {
        // Arrange - Requirement 4.4: Image uploads only within edit window
        MockMultipartFile file = new MockMultipartFile(
                "files",
                "image.jpg",
                "image/jpeg",
                "test image content".getBytes()
        );

        when(shipService.uploadShipImages(eq(testShipId), eq(testUserId), anyList()))
                .thenThrow(new ForbiddenException("Edit window has expired. Images can only be added within 10 minutes of ship creation."));

        // Act & Assert
        mockMvc.perform(multipart("/api/ships/{shipId}/images", testShipId)
                        .file(file)
                        .with(csrf())
                        .with(request -> {
                            request.setMethod("POST");
                            return request;
                        }))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.message").value(containsString("Edit window has expired")));

        verify(shipService).uploadShipImages(eq(testShipId), eq(testUserId), anyList());
    }

    @Test
    @WithMockUser(username = "user123")
    @DisplayName("POST /api/ships/{shipId}/images - Should return 404 when ship not found")
    void uploadImages_ShipNotFound() throws Exception {
        // Arrange
        MockMultipartFile file = new MockMultipartFile(
                "files",
                "image.jpg",
                "image/jpeg",
                "test image content".getBytes()
        );

        when(shipService.uploadShipImages(eq("nonexistent"), eq(testUserId), anyList()))
                .thenThrow(new ResourceNotFoundException("Ship", "nonexistent"));

        // Act & Assert
        mockMvc.perform(multipart("/api/ships/{shipId}/images", "nonexistent")
                        .file(file)
                        .with(csrf())
                        .with(request -> {
                            request.setMethod("POST");
                            return request;
                        }))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));

        verify(shipService).uploadShipImages(eq("nonexistent"), eq(testUserId), anyList());
    }

    @Test
    @WithMockUser(username = "user123")
    @DisplayName("POST /api/ships/{shipId}/images - Should handle single image upload")
    void uploadImages_SingleImage() throws Exception {
        // Arrange
        MockMultipartFile file = new MockMultipartFile(
                "files",
                "single.jpg",
                "image/jpeg",
                "single image content".getBytes()
        );

        ImageReference imageRef = ImageReference.builder()
                .multibaseHash("singlehash")
                .filename("single.jpg")
                .contentType("image/jpeg")
                .size(512L)
                .build();

        ShipResponse responseWithImage = ShipResponse.builder()
                .id(testShipId)
                .userId(testUserId)
                .projectId(testProjectId)
                .content("Test ship content")
                .images(Arrays.asList(imageRef))
                .externalLinks(testShipResponse.getExternalLinks())
                .likeCount(0)
                .commentCount(0)
                .trendingScore(0.0)
                .editWindowOpen(true)
                .editWindowExpires(testShipResponse.getEditWindowExpires())
                .createdAt(testShipResponse.getCreatedAt())
                .updatedAt(Instant.now())
                .build();

        when(shipService.uploadShipImages(eq(testShipId), eq(testUserId), anyList()))
                .thenReturn(responseWithImage);

        // Act & Assert
        mockMvc.perform(multipart("/api/ships/{shipId}/images", testShipId)
                        .file(file)
                        .with(csrf())
                        .with(request -> {
                            request.setMethod("POST");
                            return request;
                        }))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.images", hasSize(1)))
                .andExpect(jsonPath("$.images[0].multibaseHash").value("singlehash"))
                .andExpect(jsonPath("$.images[0].filename").value("single.jpg"));

        verify(shipService).uploadShipImages(eq(testShipId), eq(testUserId), anyList());
    }

    @Test
    @WithMockUser(username = "user123")
    @DisplayName("DELETE /api/ships/{shipId} - Should delete owned ship")
    void deleteShip_Success() throws Exception {
        mockMvc.perform(delete("/api/ships/{shipId}", testShipId)
                        .with(csrf()))
                .andExpect(status().isNoContent());

        verify(shipService).deleteShip(testShipId, testUserId);
    }

    @Test
    @DisplayName("DELETE /api/ships/{shipId} - Should return 401 when not authenticated")
    void deleteShip_Unauthorized() throws Exception {
        mockMvc.perform(delete("/api/ships/{shipId}", testShipId)
                        .with(csrf()))
                .andExpect(status().isUnauthorized());

        verify(shipService, never()).deleteShip(anyString(), anyString());
    }

    @Test
    @WithMockUser(username = "user456")
    @DisplayName("DELETE /api/ships/{shipId} - Should return 403 when not ship owner")
    void deleteShip_Forbidden() throws Exception {
        doThrow(new ForbiddenException("delete", "ship"))
                .when(shipService).deleteShip(testShipId, "user456");

        mockMvc.perform(delete("/api/ships/{shipId}", testShipId)
                        .with(csrf()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        verify(shipService).deleteShip(testShipId, "user456");
    }
}
