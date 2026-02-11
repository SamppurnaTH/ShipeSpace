package com.shipspace.service;

import com.shipspace.dto.ShipCreateRequest;
import com.shipspace.dto.ShipResponse;
import com.shipspace.dto.ShipUpdateRequest;
import com.shipspace.exception.ForbiddenException;
import com.shipspace.exception.ResourceNotFoundException;
import com.shipspace.model.ExternalLink;
import com.shipspace.model.ImageReference;
import com.shipspace.model.Project;
import com.shipspace.model.Ship;
import com.shipspace.repository.ShipRepository;
import net.jqwik.api.*;
import net.jqwik.api.constraints.AlphaChars;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.StringLength;
import net.jqwik.api.lifecycle.BeforeTry;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Property-based tests for ship content management.
 * 
 * **Validates: Requirements 4.1, 4.2, 4.3, 4.4, 4.5**
 * 
 * These tests verify the correctness properties of the ship management system
 * across a wide range of inputs using property-based testing with jqwik.
 */
class ShipManagementPropertyTest {

    private ShipService shipService;
    private ShipRepository shipRepository;
    private ProjectService projectService;
    private MultibaseService multibaseService;

    @BeforeTry
    void setUp() {
        shipRepository = mock(ShipRepository.class);
        projectService = mock(ProjectService.class);
        multibaseService = mock(MultibaseService.class);
        shipService = new ShipService(shipRepository, projectService, multibaseService);
    }

    /**
     * Property 15: Ship project association validation
     * 
     * For any ship creation request, the ship should only be created if associated 
     * with an existing project owned by the authenticated user.
     * 
     * **Validates: Requirements 4.1**
     */
    @Property(tries = 100)
    @Label("Feature: shipspace-mvp, Property 15: Ship project association validation")
    void shipProjectAssociationValidation(
            @ForAll @StringLength(min = 1, max = 50) @AlphaChars String userId,
            @ForAll @StringLength(min = 1, max = 50) @AlphaChars String projectId,
            @ForAll @StringLength(min = 1, max = 500) String content) {
        
        // Given: A valid project owned by the user
        doNothing().when(projectService).verifyProjectOwnership(projectId, userId);
        doNothing().when(projectService).updateProjectShipMetadata(projectId);
        
        // Mock the save operation to return a ship with ID
        when(shipRepository.save(any(Ship.class))).thenAnswer(invocation -> {
            Ship savedShip = invocation.getArgument(0);
            savedShip.setId("ship_" + userId);
            return savedShip;
        });
        
        // When: Creating a ship with valid project association
        ShipCreateRequest request = ShipCreateRequest.builder()
                .projectId(projectId)
                .content(content)
                .externalLinks(new ArrayList<>())
                .build();
        
        ShipResponse response = shipService.createShip(userId, request);
        
        // Then: Ship should be created and associated with the project
        assertThat(response).isNotNull();
        assertThat(response.getId()).isNotNull();
        assertThat(response.getUserId()).isEqualTo(userId);
        assertThat(response.getProjectId()).isEqualTo(projectId);
        assertThat(response.getContent()).isEqualTo(content);
        assertThat(response.getEditWindowOpen()).isTrue();
        assertThat(response.getEditWindowExpires()).isNotNull();
        
        // Verify project ownership was checked
        verify(projectService).verifyProjectOwnership(projectId, userId);
        verify(shipRepository).save(any(Ship.class));
        verify(projectService).updateProjectShipMetadata(projectId);
    }

    /**
     * Property 15 (variant): Ship creation fails for non-owned project
     * 
     * For any ship creation request with a project not owned by the user,
     * the creation should fail with ForbiddenException.
     * 
     * **Validates: Requirements 4.1**
     */
    @Property(tries = 100)
    @Label("Feature: shipspace-mvp, Property 15: Ship creation fails for non-owned project")
    void shipCreationFailsForNonOwnedProject(
            @ForAll @StringLength(min = 1, max = 50) @AlphaChars String userId,
            @ForAll @StringLength(min = 1, max = 50) @AlphaChars String projectId,
            @ForAll @StringLength(min = 1, max = 500) String content) {
        
        // Given: A project not owned by the user
        doThrow(new ForbiddenException("access", "project"))
                .when(projectService).verifyProjectOwnership(projectId, userId);
        
        // When: Attempting to create a ship
        ShipCreateRequest request = ShipCreateRequest.builder()
                .projectId(projectId)
                .content(content)
                .externalLinks(new ArrayList<>())
                .build();
        
        // Then: Should throw ForbiddenException
        assertThatThrownBy(() -> shipService.createShip(userId, request))
                .isInstanceOf(ForbiddenException.class);
        
        // Verify save was never called
        verify(shipRepository, never()).save(any(Ship.class));
    }

    /**
     * Property 15 (variant): Ship creation fails for non-existent project
     * 
     * For any ship creation request with a non-existent project,
     * the creation should fail with ResourceNotFoundException.
     * 
     * **Validates: Requirements 4.1**
     */
    @Property(tries = 100)
    @Label("Feature: shipspace-mvp, Property 15: Ship creation fails for non-existent project")
    void shipCreationFailsForNonExistentProject(
            @ForAll @StringLength(min = 1, max = 50) @AlphaChars String userId,
            @ForAll @StringLength(min = 1, max = 50) @AlphaChars String projectId,
            @ForAll @StringLength(min = 1, max = 500) String content) {
        
        // Given: A non-existent project
        doThrow(new ResourceNotFoundException("Project", projectId))
                .when(projectService).verifyProjectOwnership(projectId, userId);
        
        // When: Attempting to create a ship
        ShipCreateRequest request = ShipCreateRequest.builder()
                .projectId(projectId)
                .content(content)
                .externalLinks(new ArrayList<>())
                .build();
        
        // Then: Should throw ResourceNotFoundException
        assertThatThrownBy(() -> shipService.createShip(userId, request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Project");
        
        // Verify save was never called
        verify(shipRepository, never()).save(any(Ship.class));
    }

    /**
     * Property 16: Ship content acceptance
     * 
     * For any ship creation request, the API should accept and store text content, 
     * image references, and external links in the ship payload.
     * 
     * **Validates: Requirements 4.2**
     */
    @Property(tries = 100)
    @Label("Feature: shipspace-mvp, Property 16: Ship content acceptance")
    void shipContentAcceptance(
            @ForAll @StringLength(min = 1, max = 50) @AlphaChars String userId,
            @ForAll @StringLength(min = 1, max = 50) @AlphaChars String projectId,
            @ForAll @StringLength(min = 1, max = 500) String content,
            @ForAll @IntRange(min = 0, max = 5) int linkCount) {
        
        // Given: A valid project and external links
        doNothing().when(projectService).verifyProjectOwnership(projectId, userId);
        doNothing().when(projectService).updateProjectShipMetadata(projectId);
        
        when(shipRepository.save(any(Ship.class))).thenAnswer(invocation -> {
            Ship savedShip = invocation.getArgument(0);
            savedShip.setId("ship_" + userId);
            return savedShip;
        });
        
        // Create external links
        List<ExternalLink> externalLinks = new ArrayList<>();
        for (int i = 0; i < linkCount; i++) {
            externalLinks.add(ExternalLink.builder()
                    .type("github")
                    .url("https://github.com/user/repo" + i)
                    .title("Link " + i)
                    .build());
        }
        
        // When: Creating a ship with content and links
        ShipCreateRequest request = ShipCreateRequest.builder()
                .projectId(projectId)
                .content(content)
                .externalLinks(externalLinks)
                .build();
        
        ShipResponse response = shipService.createShip(userId, request);
        
        // Then: All content should be stored correctly
        assertThat(response).isNotNull();
        assertThat(response.getContent()).isEqualTo(content);
        assertThat(response.getExternalLinks()).hasSize(linkCount);
        
        // Verify external links are stored correctly
        for (int i = 0; i < linkCount; i++) {
            assertThat(response.getExternalLinks().get(i).getUrl())
                    .isEqualTo("https://github.com/user/repo" + i);
            assertThat(response.getExternalLinks().get(i).getTitle())
                    .isEqualTo("Link " + i);
        }
        
        // Verify images list is initialized (empty initially)
        assertThat(response.getImages()).isNotNull();
        
        verify(shipRepository).save(any(Ship.class));
    }

    /**
     * Property 16 (variant): Ship content update preserves unchanged fields
     * 
     * For any ship update request, only provided fields should be modified,
     * leaving other fields unchanged.
     * 
     * **Validates: Requirements 4.2**
     */
    @Property(tries = 100)
    @Label("Feature: shipspace-mvp, Property 16: Ship content update preserves unchanged fields")
    void shipContentUpdatePreservesUnchangedFields(
            @ForAll @StringLength(min = 1, max = 50) @AlphaChars String shipId,
            @ForAll @StringLength(min = 1, max = 50) @AlphaChars String userId,
            @ForAll @StringLength(min = 1, max = 500) String originalContent,
            @ForAll @StringLength(min = 1, max = 500) String newContent) {
        
        // Given: An existing ship within edit window
        Ship existingShip = createMockShip(shipId, userId, originalContent, true);
        when(shipRepository.findById(shipId)).thenReturn(Optional.of(existingShip));
        when(shipRepository.save(any(Ship.class))).thenAnswer(invocation -> invocation.getArgument(0));
        
        // When: Updating only the content (not external links)
        ShipUpdateRequest updateRequest = ShipUpdateRequest.builder()
                .content(newContent)
                .build();
        
        ShipResponse response = shipService.updateShip(shipId, userId, updateRequest);
        
        // Then: Only content should be updated, other fields unchanged
        assertThat(response.getContent()).isEqualTo(newContent);
        assertThat(response.getExternalLinks()).isEqualTo(existingShip.getExternalLinks());
        assertThat(response.getImages()).isEqualTo(existingShip.getImages());
        
        verify(shipRepository).save(any(Ship.class));
    }

    /**
     * Property 17: Multibase image integration
     * 
     * For any image upload to a ship, the image should be stored in Multibase system 
     * and the ship should contain the correct multibase hash reference.
     * 
     * **Validates: Requirements 4.3**
     */
    @Property(tries = 100)
    @Label("Feature: shipspace-mvp, Property 17: Multibase image integration")
    void multibaseImageIntegration(
            @ForAll @StringLength(min = 1, max = 50) @AlphaChars String shipId,
            @ForAll @StringLength(min = 1, max = 50) @AlphaChars String userId,
            @ForAll @StringLength(min = 1, max = 50) @AlphaChars String multibaseHash,
            @ForAll @IntRange(min = 1, max = 3) int imageCount) {
        
        // Given: An existing ship within edit window
        Ship existingShip = createMockShip(shipId, userId, "Test content", true);
        when(shipRepository.findById(shipId)).thenReturn(Optional.of(existingShip));
        when(shipRepository.save(any(Ship.class))).thenAnswer(invocation -> invocation.getArgument(0));
        
        // Mock image uploads to Multibase
        List<MultipartFile> mockFiles = new ArrayList<>();
        for (int i = 0; i < imageCount; i++) {
            MultipartFile mockFile = mock(MultipartFile.class);
            when(mockFile.getOriginalFilename()).thenReturn("image" + i + ".png");
            when(mockFile.getContentType()).thenReturn("image/png");
            when(mockFile.getSize()).thenReturn(1024L * (i + 1));
            
            ImageReference imageRef = ImageReference.builder()
                    .multibaseHash(multibaseHash + "_" + i)
                    .filename("image" + i + ".png")
                    .contentType("image/png")
                    .size(1024L * (i + 1))
                    .build();
            
            when(multibaseService.uploadImage(mockFile)).thenReturn(imageRef);
            mockFiles.add(mockFile);
        }
        
        // When: Uploading images to the ship
        ShipResponse response = shipService.uploadShipImages(shipId, userId, mockFiles);
        
        // Then: Ship should contain all image references with correct multibase hashes
        assertThat(response).isNotNull();
        assertThat(response.getImages()).hasSize(imageCount);
        
        for (int i = 0; i < imageCount; i++) {
            ImageReference imageRef = response.getImages().get(i);
            assertThat(imageRef.getMultibaseHash()).isEqualTo(multibaseHash + "_" + i);
            assertThat(imageRef.getFilename()).isEqualTo("image" + i + ".png");
            assertThat(imageRef.getContentType()).isEqualTo("image/png");
            assertThat(imageRef.getSize()).isEqualTo(1024L * (i + 1));
        }
        
        // Verify Multibase service was called for each image
        verify(multibaseService, times(imageCount)).uploadImage(any(MultipartFile.class));
        verify(shipRepository).save(any(Ship.class));
    }

    /**
     * Property 17 (variant): Image upload fails outside edit window
     * 
     * For any image upload attempt after the edit window expires,
     * the upload should fail with ForbiddenException.
     * 
     * **Validates: Requirements 4.3, 4.4**
     */
    @Property(tries = 100)
    @Label("Feature: shipspace-mvp, Property 17: Image upload fails outside edit window")
    void imageUploadFailsOutsideEditWindow(
            @ForAll @StringLength(min = 1, max = 50) @AlphaChars String shipId,
            @ForAll @StringLength(min = 1, max = 50) @AlphaChars String userId) {
        
        // Given: An existing ship with expired edit window
        Ship existingShip = createMockShip(shipId, userId, "Test content", false);
        when(shipRepository.findById(shipId)).thenReturn(Optional.of(existingShip));
        
        // Mock image file
        MultipartFile mockFile = mock(MultipartFile.class);
        when(mockFile.getOriginalFilename()).thenReturn("image.png");
        List<MultipartFile> mockFiles = List.of(mockFile);
        
        // When: Attempting to upload images after edit window expires
        // Then: Should throw ForbiddenException
        assertThatThrownBy(() -> shipService.uploadShipImages(shipId, userId, mockFiles))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("Edit window has expired");
        
        // Verify Multibase service was never called
        verify(multibaseService, never()).uploadImage(any(MultipartFile.class));
        verify(shipRepository, never()).save(any(Ship.class));
    }

    /**
     * Property 17 (variant): Image upload fails for non-owner
     * 
     * For any image upload attempt by a non-owner,
     * the upload should fail with ForbiddenException.
     * 
     * **Validates: Requirements 4.3**
     */
    @Property(tries = 100)
    @Label("Feature: shipspace-mvp, Property 17: Image upload fails for non-owner")
    void imageUploadFailsForNonOwner(
            @ForAll @StringLength(min = 1, max = 50) @AlphaChars String shipId,
            @ForAll @StringLength(min = 1, max = 50) @AlphaChars String ownerId,
            @ForAll @StringLength(min = 1, max = 50) @AlphaChars String otherUserId) {
        
        // Ensure owner and other user are different
        Assume.that(!ownerId.equals(otherUserId));
        
        // Given: An existing ship owned by ownerId
        Ship existingShip = createMockShip(shipId, ownerId, "Test content", true);
        when(shipRepository.findById(shipId)).thenReturn(Optional.of(existingShip));
        
        // Mock image file
        MultipartFile mockFile = mock(MultipartFile.class);
        when(mockFile.getOriginalFilename()).thenReturn("image.png");
        List<MultipartFile> mockFiles = List.of(mockFile);
        
        // When: A non-owner attempts to upload images
        // Then: Should throw ForbiddenException
        assertThatThrownBy(() -> shipService.uploadShipImages(shipId, otherUserId, mockFiles))
                .isInstanceOf(ForbiddenException.class);
        
        // Verify Multibase service was never called
        verify(multibaseService, never()).uploadImage(any(MultipartFile.class));
        verify(shipRepository, never()).save(any(Ship.class));
    }

    /**
     * Property 18: Edit window enforcement
     * 
     * For any ship, updates should be allowed only within 10 minutes of creation, 
     * and rejected afterward.
     * 
     * **Validates: Requirements 4.4, 4.5**
     */
    @Property(tries = 100)
    @Label("Feature: shipspace-mvp, Property 18: Edit window enforcement")
    void editWindowEnforcement(
            @ForAll @StringLength(min = 1, max = 50) @AlphaChars String shipId,
            @ForAll @StringLength(min = 1, max = 50) @AlphaChars String userId,
            @ForAll @StringLength(min = 1, max = 500) String newContent,
            @ForAll boolean withinEditWindow) {
        
        // Given: An existing ship with edit window status
        Ship existingShip = createMockShip(shipId, userId, "Original content", withinEditWindow);
        when(shipRepository.findById(shipId)).thenReturn(Optional.of(existingShip));
        when(shipRepository.save(any(Ship.class))).thenAnswer(invocation -> invocation.getArgument(0));
        
        // When: Attempting to update the ship
        ShipUpdateRequest updateRequest = ShipUpdateRequest.builder()
                .content(newContent)
                .build();
        
        if (withinEditWindow) {
            // Then: Update should succeed within edit window
            ShipResponse response = shipService.updateShip(shipId, userId, updateRequest);
            
            assertThat(response).isNotNull();
            assertThat(response.getContent()).isEqualTo(newContent);
            
            verify(shipRepository).save(any(Ship.class));
        } else {
            // Then: Update should fail after edit window expires
            assertThatThrownBy(() -> shipService.updateShip(shipId, userId, updateRequest))
                    .isInstanceOf(ForbiddenException.class)
                    .hasMessageContaining("Edit window has expired");
            
            verify(shipRepository, never()).save(any(Ship.class));
        }
    }

    /**
     * Property 18 (variant): Edit window is exactly 10 minutes
     * 
     * For any ship, the edit window should expire exactly 10 minutes after creation.
     * 
     * **Validates: Requirements 4.4**
     */
    @Property(tries = 100)
    @Label("Feature: shipspace-mvp, Property 18: Edit window is exactly 10 minutes")
    void editWindowIsExactlyTenMinutes(
            @ForAll @StringLength(min = 1, max = 50) @AlphaChars String userId,
            @ForAll @StringLength(min = 1, max = 50) @AlphaChars String projectId,
            @ForAll @StringLength(min = 1, max = 500) String content) {
        
        // Given: A valid project
        doNothing().when(projectService).verifyProjectOwnership(projectId, userId);
        doNothing().when(projectService).updateProjectShipMetadata(projectId);
        
        Instant beforeCreation = Instant.now();
        
        when(shipRepository.save(any(Ship.class))).thenAnswer(invocation -> {
            Ship savedShip = invocation.getArgument(0);
            savedShip.setId("ship_" + userId);
            return savedShip;
        });
        
        // When: Creating a ship
        ShipCreateRequest request = ShipCreateRequest.builder()
                .projectId(projectId)
                .content(content)
                .externalLinks(new ArrayList<>())
                .build();
        
        ShipResponse response = shipService.createShip(userId, request);
        
        Instant afterCreation = Instant.now();
        
        // Then: Edit window should expire exactly 10 minutes after creation
        assertThat(response.getEditWindowExpires()).isNotNull();
        
        // The edit window should be approximately 10 minutes from creation time
        // Allow for small timing variations in test execution
        Instant expectedMinExpiry = beforeCreation.plus(10, ChronoUnit.MINUTES);
        Instant expectedMaxExpiry = afterCreation.plus(10, ChronoUnit.MINUTES);
        
        assertThat(response.getEditWindowExpires())
                .isAfterOrEqualTo(expectedMinExpiry)
                .isBeforeOrEqualTo(expectedMaxExpiry);
        
        // Verify edit window is initially open
        assertThat(response.getEditWindowOpen()).isTrue();
    }

    /**
     * Property 18 (variant): Ship update fails for non-owner
     * 
     * For any ship update attempt by a non-owner,
     * the update should fail with ForbiddenException regardless of edit window.
     * 
     * **Validates: Requirements 4.4, 4.5**
     */
    @Property(tries = 100)
    @Label("Feature: shipspace-mvp, Property 18: Ship update fails for non-owner")
    void shipUpdateFailsForNonOwner(
            @ForAll @StringLength(min = 1, max = 50) @AlphaChars String shipId,
            @ForAll @StringLength(min = 1, max = 50) @AlphaChars String ownerId,
            @ForAll @StringLength(min = 1, max = 50) @AlphaChars String otherUserId,
            @ForAll @StringLength(min = 1, max = 500) String newContent) {
        
        // Ensure owner and other user are different
        Assume.that(!ownerId.equals(otherUserId));
        
        // Given: An existing ship owned by ownerId (within edit window)
        Ship existingShip = createMockShip(shipId, ownerId, "Original content", true);
        when(shipRepository.findById(shipId)).thenReturn(Optional.of(existingShip));
        
        // When: A non-owner attempts to update the ship
        ShipUpdateRequest updateRequest = ShipUpdateRequest.builder()
                .content(newContent)
                .build();
        
        // Then: Should throw ForbiddenException
        assertThatThrownBy(() -> shipService.updateShip(shipId, otherUserId, updateRequest))
                .isInstanceOf(ForbiddenException.class);
        
        // Verify save was never called
        verify(shipRepository, never()).save(any(Ship.class));
    }

    /**
     * Property 18 (variant): Ship retrieval works regardless of edit window
     * 
     * For any ship, retrieval should work regardless of whether the edit window
     * is open or expired.
     * 
     * **Validates: Requirements 4.4, 4.5**
     */
    @Property(tries = 100)
    @Label("Feature: shipspace-mvp, Property 18: Ship retrieval works regardless of edit window")
    void shipRetrievalWorksRegardlessOfEditWindow(
            @ForAll @StringLength(min = 1, max = 50) @AlphaChars String shipId,
            @ForAll @StringLength(min = 1, max = 50) @AlphaChars String userId,
            @ForAll boolean withinEditWindow) {
        
        // Given: An existing ship with any edit window status
        Ship existingShip = createMockShip(shipId, userId, "Test content", withinEditWindow);
        when(shipRepository.findById(shipId)).thenReturn(Optional.of(existingShip));
        
        // When: Retrieving the ship
        ShipResponse response = shipService.getShipById(shipId);
        
        // Then: Retrieval should succeed and return correct edit window status
        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(shipId);
        assertThat(response.getEditWindowOpen()).isEqualTo(withinEditWindow);
        
        verify(shipRepository).findById(shipId);
    }

    // Helper methods

    private Ship createMockShip(String shipId, String userId, String content, boolean withinEditWindow) {
        Instant now = Instant.now();
        Instant editWindowExpires;
        
        if (withinEditWindow) {
            // Edit window expires in the future (5 minutes from now)
            editWindowExpires = now.plus(5, ChronoUnit.MINUTES);
        } else {
            // Edit window expired in the past (5 minutes ago)
            editWindowExpires = now.minus(5, ChronoUnit.MINUTES);
        }
        
        return Ship.builder()
                .id(shipId)
                .userId(userId)
                .projectId("project_123")
                .content(content)
                .images(new ArrayList<>())
                .externalLinks(new ArrayList<>())
                .likeCount(0)
                .commentCount(0)
                .trendingScore(0.0)
                .editWindowExpires(editWindowExpires)
                .createdAt(now.minus(10, ChronoUnit.MINUTES))
                .updatedAt(now)
                .build();
    }
}
