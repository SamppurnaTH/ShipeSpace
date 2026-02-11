package com.shipspace.service;

import com.shipspace.dto.ShipCreateRequest;
import com.shipspace.dto.ShipResponse;
import com.shipspace.dto.ShipUpdateRequest;
import com.shipspace.exception.ForbiddenException;
import com.shipspace.exception.ResourceNotFoundException;
import com.shipspace.model.ExternalLink;
import com.shipspace.model.ImageReference;
import com.shipspace.model.Ship;
import com.shipspace.repository.ShipRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ShipService.
 * Tests ship CRUD operations, edit window enforcement, project association validation,
 * and image upload functionality.
 * 
 * Tests Requirements 4.1, 4.2, 4.4, 4.5:
 * - 4.1: Ship creation with project association validation
 * - 4.2: Accept text content, image references, and external links
 * - 4.4: Edit window enforcement (10 minutes)
 * - 4.5: Reject edits after edit window expires
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ShipService Tests")
class ShipServiceTest {

    @Mock
    private ShipRepository shipRepository;

    @Mock
    private ProjectService projectService;

    @Mock
    private MultibaseService multibaseService;

    @InjectMocks
    private ShipService shipService;

    private Ship testShip;
    private ShipCreateRequest createRequest;
    private ShipUpdateRequest updateRequest;

    @BeforeEach
    void setUp() {
        Instant now = Instant.now();
        Instant editWindowExpires = now.plus(10, ChronoUnit.MINUTES);

        testShip = Ship.builder()
                .id("ship123")
                .userId("user123")
                .projectId("project123")
                .content("Test ship content")
                .externalLinks(new ArrayList<>())
                .images(new ArrayList<>())
                .likeCount(0)
                .commentCount(0)
                .trendingScore(0.0)
                .editWindowExpires(editWindowExpires)
                .createdAt(now)
                .updatedAt(now)
                .build();

        createRequest = ShipCreateRequest.builder()
                .projectId("project123")
                .content("Test ship content")
                .externalLinks(new ArrayList<>())
                .build();

        updateRequest = ShipUpdateRequest.builder()
                .content("Updated ship content")
                .build();
    }

    // ========== Ship Creation Tests (Requirement 4.1, 4.2, 4.4) ==========

    @Test
    @DisplayName("Should create ship successfully with valid project association")
    void shouldCreateShipSuccessfullyWithValidProjectAssociation() {
        // Given
        doNothing().when(projectService).verifyProjectOwnership("project123", "user123");
        when(shipRepository.save(any(Ship.class))).thenReturn(testShip);
        doNothing().when(projectService).updateProjectShipMetadata("project123");

        // When
        ShipResponse response = shipService.createShip("user123", createRequest);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo("ship123");
        assertThat(response.getUserId()).isEqualTo("user123");
        assertThat(response.getProjectId()).isEqualTo("project123");
        assertThat(response.getContent()).isEqualTo("Test ship content");
        assertThat(response.getEditWindowOpen()).isTrue();
        assertThat(response.getEditWindowExpires()).isNotNull();

        verify(projectService).verifyProjectOwnership("project123", "user123");
        verify(shipRepository).save(any(Ship.class));
        verify(projectService).updateProjectShipMetadata("project123");

        // Verify edit window is set to 10 minutes (Requirement 4.4)
        ArgumentCaptor<Ship> shipCaptor = ArgumentCaptor.forClass(Ship.class);
        verify(shipRepository).save(shipCaptor.capture());
        Ship savedShip = shipCaptor.getValue();
        long minutesDiff = ChronoUnit.MINUTES.between(savedShip.getCreatedAt(), savedShip.getEditWindowExpires());
        assertThat(minutesDiff).isEqualTo(10);
    }

    @Test
    @DisplayName("Should throw exception when creating ship for non-owned project")
    void shouldThrowExceptionWhenCreatingShipForNonOwnedProject() {
        // Given - Requirement 4.1: Project association validation
        doThrow(new ForbiddenException("access", "project"))
                .when(projectService).verifyProjectOwnership("project123", "otherUser");

        // When & Then
        assertThatThrownBy(() -> shipService.createShip("otherUser", createRequest))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("project");

        verify(projectService).verifyProjectOwnership("project123", "otherUser");
        verify(shipRepository, never()).save(any(Ship.class));
        verify(projectService, never()).updateProjectShipMetadata(anyString());
    }

    @Test
    @DisplayName("Should throw exception when creating ship for non-existent project")
    void shouldThrowExceptionWhenCreatingShipForNonExistentProject() {
        // Given - Requirement 4.1: Project must exist
        doThrow(new ResourceNotFoundException("Project", "nonexistent"))
                .when(projectService).verifyProjectOwnership("nonexistent", "user123");

        ShipCreateRequest invalidRequest = ShipCreateRequest.builder()
                .projectId("nonexistent")
                .content("Test content")
                .build();

        // When & Then
        assertThatThrownBy(() -> shipService.createShip("user123", invalidRequest))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Project");

        verify(projectService).verifyProjectOwnership("nonexistent", "user123");
        verify(shipRepository, never()).save(any(Ship.class));
    }

    @Test
    @DisplayName("Should create ship with external links")
    void shouldCreateShipWithExternalLinks() {
        // Given - Requirement 4.2: Accept external links
        ExternalLink link = ExternalLink.builder()
                .url("https://example.com")
                .title("Example Link")
                .description("Test link")
                .build();
        
        ShipCreateRequest requestWithLinks = ShipCreateRequest.builder()
                .projectId("project123")
                .content("Test ship content")
                .externalLinks(Arrays.asList(link))
                .build();

        Ship shipWithLinks = Ship.builder()
                .id("ship123")
                .userId("user123")
                .projectId("project123")
                .content("Test ship content")
                .externalLinks(Arrays.asList(link))
                .images(new ArrayList<>())
                .likeCount(0)
                .commentCount(0)
                .trendingScore(0.0)
                .editWindowExpires(Instant.now().plus(10, ChronoUnit.MINUTES))
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        doNothing().when(projectService).verifyProjectOwnership("project123", "user123");
        when(shipRepository.save(any(Ship.class))).thenReturn(shipWithLinks);
        doNothing().when(projectService).updateProjectShipMetadata("project123");

        // When
        ShipResponse response = shipService.createShip("user123", requestWithLinks);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getExternalLinks()).hasSize(1);
        assertThat(response.getExternalLinks().get(0).getUrl()).isEqualTo("https://example.com");
        assertThat(response.getExternalLinks().get(0).getTitle()).isEqualTo("Example Link");
    }

    // ========== Ship Retrieval Tests ==========

    @Test
    @DisplayName("Should retrieve ship by ID successfully")
    void shouldRetrieveShipByIdSuccessfully() {
        // Given
        when(shipRepository.findById("ship123")).thenReturn(Optional.of(testShip));

        // When
        ShipResponse response = shipService.getShipById("ship123");

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo("ship123");
        assertThat(response.getUserId()).isEqualTo("user123");
        assertThat(response.getProjectId()).isEqualTo("project123");
        assertThat(response.getContent()).isEqualTo("Test ship content");

        verify(shipRepository).findById("ship123");
    }

    @Test
    @DisplayName("Should throw exception when ship not found")
    void shouldThrowExceptionWhenShipNotFound() {
        // Given
        when(shipRepository.findById("nonexistent")).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> shipService.getShipById("nonexistent"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Ship");

        verify(shipRepository).findById("nonexistent");
    }

    // ========== Ship Update Tests (Requirement 4.4, 4.5) ==========

    @Test
    @DisplayName("Should update ship within edit window")
    void shouldUpdateShipWithinEditWindow() {
        // Given - Requirement 4.4: Updates allowed within 10 minutes
        when(shipRepository.findById("ship123")).thenReturn(Optional.of(testShip));
        when(shipRepository.save(any(Ship.class))).thenReturn(testShip);

        // When
        ShipResponse response = shipService.updateShip("ship123", "user123", updateRequest);

        // Then
        assertThat(response).isNotNull();
        verify(shipRepository).findById("ship123");
        verify(shipRepository).save(any(Ship.class));

        ArgumentCaptor<Ship> shipCaptor = ArgumentCaptor.forClass(Ship.class);
        verify(shipRepository).save(shipCaptor.capture());
        Ship updatedShip = shipCaptor.getValue();
        assertThat(updatedShip.getContent()).isEqualTo("Updated ship content");
    }

    @Test
    @DisplayName("Should throw exception when updating ship after edit window expires")
    void shouldThrowExceptionWhenUpdatingShipAfterEditWindowExpires() {
        // Given - Requirement 4.5: Reject edits after edit window expires
        Instant pastTime = Instant.now().minus(15, ChronoUnit.MINUTES);
        Instant expiredEditWindow = pastTime.plus(10, ChronoUnit.MINUTES);
        
        Ship expiredShip = Ship.builder()
                .id("ship123")
                .userId("user123")
                .projectId("project123")
                .content("Test ship content")
                .externalLinks(new ArrayList<>())
                .images(new ArrayList<>())
                .likeCount(0)
                .commentCount(0)
                .trendingScore(0.0)
                .editWindowExpires(expiredEditWindow)
                .createdAt(pastTime)
                .updatedAt(pastTime)
                .build();

        when(shipRepository.findById("ship123")).thenReturn(Optional.of(expiredShip));

        // When & Then
        assertThatThrownBy(() -> shipService.updateShip("ship123", "user123", updateRequest))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("Edit window has expired");

        verify(shipRepository).findById("ship123");
        verify(shipRepository, never()).save(any(Ship.class));
    }

    @Test
    @DisplayName("Should throw exception when non-owner tries to update ship")
    void shouldThrowExceptionWhenNonOwnerTriesToUpdateShip() {
        // Given
        when(shipRepository.findById("ship123")).thenReturn(Optional.of(testShip));

        // When & Then
        assertThatThrownBy(() -> shipService.updateShip("ship123", "otherUser", updateRequest))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("ship");

        verify(shipRepository).findById("ship123");
        verify(shipRepository, never()).save(any(Ship.class));
    }

    @Test
    @DisplayName("Should update only provided fields")
    void shouldUpdateOnlyProvidedFields() {
        // Given
        ExternalLink newLink = ExternalLink.builder()
                .url("https://newlink.com")
                .title("New Link")
                .build();

        ShipUpdateRequest partialUpdate = ShipUpdateRequest.builder()
                .externalLinks(Arrays.asList(newLink))
                .build(); // content is null, should not be updated

        when(shipRepository.findById("ship123")).thenReturn(Optional.of(testShip));
        when(shipRepository.save(any(Ship.class))).thenReturn(testShip);

        // When
        shipService.updateShip("ship123", "user123", partialUpdate);

        // Then
        ArgumentCaptor<Ship> shipCaptor = ArgumentCaptor.forClass(Ship.class);
        verify(shipRepository).save(shipCaptor.capture());
        Ship updatedShip = shipCaptor.getValue();
        
        // Content should remain unchanged
        assertThat(updatedShip.getContent()).isEqualTo("Test ship content");
        // External links should be updated
        assertThat(updatedShip.getExternalLinks()).hasSize(1);
        assertThat(updatedShip.getExternalLinks().get(0).getUrl()).isEqualTo("https://newlink.com");
    }

    // ========== Image Upload Tests (Requirement 4.3) ==========

    @Test
    @DisplayName("Should upload images to ship successfully")
    void shouldUploadImagesToShipSuccessfully() {
        // Given - Requirement 4.3: Multibase integration
        MultipartFile mockFile1 = mock(MultipartFile.class);
        MultipartFile mockFile2 = mock(MultipartFile.class);
        List<MultipartFile> files = Arrays.asList(mockFile1, mockFile2);

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

        when(shipRepository.findById("ship123")).thenReturn(Optional.of(testShip));
        when(multibaseService.uploadImage(mockFile1)).thenReturn(imageRef1);
        when(multibaseService.uploadImage(mockFile2)).thenReturn(imageRef2);
        when(shipRepository.save(any(Ship.class))).thenReturn(testShip);

        // When
        ShipResponse response = shipService.uploadShipImages("ship123", "user123", files);

        // Then
        assertThat(response).isNotNull();
        verify(shipRepository).findById("ship123");
        verify(multibaseService).uploadImage(mockFile1);
        verify(multibaseService).uploadImage(mockFile2);
        verify(shipRepository).save(any(Ship.class));

        ArgumentCaptor<Ship> shipCaptor = ArgumentCaptor.forClass(Ship.class);
        verify(shipRepository).save(shipCaptor.capture());
        Ship updatedShip = shipCaptor.getValue();
        assertThat(updatedShip.getImages()).hasSize(2);
        assertThat(updatedShip.getImages().get(0).getMultibaseHash()).isEqualTo("hash1");
        assertThat(updatedShip.getImages().get(1).getMultibaseHash()).isEqualTo("hash2");
    }

    @Test
    @DisplayName("Should throw exception when non-owner tries to upload images")
    void shouldThrowExceptionWhenNonOwnerTriesToUploadImages() {
        // Given
        MultipartFile mockFile = mock(MultipartFile.class);
        List<MultipartFile> files = Arrays.asList(mockFile);

        when(shipRepository.findById("ship123")).thenReturn(Optional.of(testShip));

        // When & Then
        assertThatThrownBy(() -> shipService.uploadShipImages("ship123", "otherUser", files))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("ship");

        verify(shipRepository).findById("ship123");
        verify(multibaseService, never()).uploadImage(any());
        verify(shipRepository, never()).save(any(Ship.class));
    }

    @Test
    @DisplayName("Should throw exception when uploading images after edit window expires")
    void shouldThrowExceptionWhenUploadingImagesAfterEditWindowExpires() {
        // Given - Requirement 4.4: Image uploads only within edit window
        Instant pastTime = Instant.now().minus(15, ChronoUnit.MINUTES);
        Instant expiredEditWindow = pastTime.plus(10, ChronoUnit.MINUTES);
        
        Ship expiredShip = Ship.builder()
                .id("ship123")
                .userId("user123")
                .projectId("project123")
                .content("Test ship content")
                .externalLinks(new ArrayList<>())
                .images(new ArrayList<>())
                .likeCount(0)
                .commentCount(0)
                .trendingScore(0.0)
                .editWindowExpires(expiredEditWindow)
                .createdAt(pastTime)
                .updatedAt(pastTime)
                .build();

        MultipartFile mockFile = mock(MultipartFile.class);
        List<MultipartFile> files = Arrays.asList(mockFile);

        when(shipRepository.findById("ship123")).thenReturn(Optional.of(expiredShip));

        // When & Then
        assertThatThrownBy(() -> shipService.uploadShipImages("ship123", "user123", files))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("Edit window has expired");

        verify(shipRepository).findById("ship123");
        verify(multibaseService, never()).uploadImage(any());
        verify(shipRepository, never()).save(any(Ship.class));
    }

    @Test
    @DisplayName("Should continue uploading other images if one fails")
    void shouldContinueUploadingOtherImagesIfOneFails() {
        // Given
        MultipartFile mockFile1 = mock(MultipartFile.class);
        MultipartFile mockFile2 = mock(MultipartFile.class);
        MultipartFile mockFile3 = mock(MultipartFile.class);
        List<MultipartFile> files = Arrays.asList(mockFile1, mockFile2, mockFile3);

        ImageReference imageRef1 = ImageReference.builder()
                .multibaseHash("hash1")
                .filename("image1.jpg")
                .contentType("image/jpeg")
                .size(1024L)
                .build();

        ImageReference imageRef3 = ImageReference.builder()
                .multibaseHash("hash3")
                .filename("image3.jpg")
                .contentType("image/jpeg")
                .size(3072L)
                .build();

        when(shipRepository.findById("ship123")).thenReturn(Optional.of(testShip));
        when(multibaseService.uploadImage(mockFile1)).thenReturn(imageRef1);
        when(multibaseService.uploadImage(mockFile2)).thenThrow(new RuntimeException("Upload failed"));
        when(multibaseService.uploadImage(mockFile3)).thenReturn(imageRef3);
        when(shipRepository.save(any(Ship.class))).thenReturn(testShip);

        // When
        ShipResponse response = shipService.uploadShipImages("ship123", "user123", files);

        // Then
        assertThat(response).isNotNull();
        verify(multibaseService).uploadImage(mockFile1);
        verify(multibaseService).uploadImage(mockFile2);
        verify(multibaseService).uploadImage(mockFile3);
        verify(shipRepository).save(any(Ship.class));

        ArgumentCaptor<Ship> shipCaptor = ArgumentCaptor.forClass(Ship.class);
        verify(shipRepository).save(shipCaptor.capture());
        Ship updatedShip = shipCaptor.getValue();
        // Should have 2 images (file1 and file3), file2 failed
        assertThat(updatedShip.getImages()).hasSize(2);
    }

    // ========== Ownership Verification Tests ==========

    @Test
    @DisplayName("Should verify ship ownership successfully")
    void shouldVerifyShipOwnershipSuccessfully() {
        // Given
        when(shipRepository.findById("ship123")).thenReturn(Optional.of(testShip));

        // When & Then - should not throw exception
        shipService.verifyShipOwnership("ship123", "user123");

        verify(shipRepository).findById("ship123");
    }

    @Test
    @DisplayName("Should throw exception when verifying ownership for non-existent ship")
    void shouldThrowExceptionWhenVerifyingOwnershipForNonExistentShip() {
        // Given
        when(shipRepository.findById("nonexistent")).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> shipService.verifyShipOwnership("nonexistent", "user123"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Ship");

        verify(shipRepository).findById("nonexistent");
    }

    @Test
    @DisplayName("Should throw exception when verifying ownership for non-owner")
    void shouldThrowExceptionWhenVerifyingOwnershipForNonOwner() {
        // Given
        when(shipRepository.findById("ship123")).thenReturn(Optional.of(testShip));

        // When & Then
        assertThatThrownBy(() -> shipService.verifyShipOwnership("ship123", "otherUser"))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("ship");

        verify(shipRepository).findById("ship123");
    }
}
