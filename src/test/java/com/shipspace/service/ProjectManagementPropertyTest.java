package com.shipspace.service;

import com.shipspace.dto.ProjectCreateRequest;
import com.shipspace.dto.ProjectResponse;
import com.shipspace.dto.ProjectUpdateRequest;
import com.shipspace.exception.ForbiddenException;
import com.shipspace.exception.ResourceNotFoundException;
import com.shipspace.model.ExternalLink;
import com.shipspace.model.Project;
import com.shipspace.model.Ship;
import com.shipspace.model.User;
import com.shipspace.repository.ProjectRepository;
import com.shipspace.repository.ShipRepository;
import com.shipspace.util.InputSanitizer;
import net.jqwik.api.*;
import net.jqwik.api.constraints.AlphaChars;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.StringLength;
import net.jqwik.api.lifecycle.BeforeTry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Property-based tests for project management.
 * 
 * **Validates: Requirements 3.1, 3.3, 3.4, 3.5**
 * 
 * These tests verify the correctness properties of the project management system
 * across a wide range of inputs using property-based testing with jqwik.
 */
class ProjectManagementPropertyTest {

    private ProjectService projectService;
    private ProjectRepository projectRepository;
    private ShipRepository shipRepository;
    private UserService userService;
    private org.springframework.data.mongodb.core.MongoTemplate mongoTemplate;
    private ShipScoreService shipScoreService;
    private CascadeDeleteService cascadeDeleteService;
    private InputSanitizer inputSanitizer;

    @BeforeTry
    void setUp() {
        projectRepository = mock(ProjectRepository.class);
        shipRepository = mock(ShipRepository.class);
        userService = mock(UserService.class);
        mongoTemplate = mock(org.springframework.data.mongodb.core.MongoTemplate.class);
        shipScoreService = mock(ShipScoreService.class);
        cascadeDeleteService = mock(CascadeDeleteService.class);
        inputSanitizer = mock(InputSanitizer.class);
        when(inputSanitizer.sanitize(any())).thenAnswer(inv -> inv.getArgument(0));
        projectService = new ProjectService(projectRepository, shipRepository, userService,
                mongoTemplate, shipScoreService, cascadeDeleteService, inputSanitizer);
    }

    /**
     * Property 10: Project creation and storage
     * 
     * For any valid project creation request with authenticated user, a project 
     * document should be stored in MongoDB with all required metadata.
     * 
     * **Validates: Requirements 3.1**
     */
    @Property(tries = 100)
    @Label("Feature: shipspace-mvp, Property 10: Project creation and storage")
    void projectCreationAndStorage(
            @ForAll @StringLength(min = 1, max = 50) @AlphaChars String userId,
            @ForAll @StringLength(min = 1, max = 100) String projectName,
            @ForAll @StringLength(min = 1, max = 500) String projectDescription,
            @ForAll @IntRange(min = 0, max = 5) int techTagCount) {
        
        // Given: A valid user exists
        User mockUser = createMockUser(userId);
        when(userService.getUserById(userId)).thenReturn(mockUser);
        
        // Mock the save operation to return a project with ID
        when(projectRepository.save(any(Project.class))).thenAnswer(invocation -> {
            Project savedProject = invocation.getArgument(0);
            savedProject.setId("project_" + userId);
            return savedProject;
        });
        
        // Mock updateProjectCount
        doNothing().when(userService).updateProjectCount(userId);
        
        // Create tech tags
        List<String> techTags = new ArrayList<>();
        for (int i = 0; i < techTagCount; i++) {
            techTags.add("tech" + i);
        }
        
        // When: Creating a project with valid data
        ProjectCreateRequest request = ProjectCreateRequest.builder()
                .name(projectName)
                .description(projectDescription)
                .techTags(techTags)
                .externalLinks(new ArrayList<>())
                .build();
        
        ProjectResponse response = projectService.createProject(userId, request);
        
        // Then: Project should be stored with all required metadata
        assertThat(response).isNotNull();
        assertThat(response.getId()).isNotNull();
        assertThat(response.getUserId()).isEqualTo(userId);
        assertThat(response.getName()).isEqualTo(projectName);
        assertThat(response.getDescription()).isEqualTo(projectDescription);
        assertThat(response.getTechTags()).hasSize(techTagCount);
        assertThat(response.getShipCount()).isEqualTo(0);
        assertThat(response.getCreatedAt()).isNotNull();
        assertThat(response.getUpdatedAt()).isNotNull();
        
        // Verify repository interactions
        verify(userService).getUserById(userId);
        verify(projectRepository).save(any(Project.class));
        verify(userService).updateProjectCount(userId);
    }

    /**
     * Property 10 (variant): Project creation with external links
     * 
     * For any valid project creation request with external links, all links
     * should be stored correctly.
     * 
     * **Validates: Requirements 3.1**
     */
    @Property(tries = 100)
    @Label("Feature: shipspace-mvp, Property 10: Project creation with external links")
    void projectCreationWithExternalLinks(
            @ForAll @StringLength(min = 1, max = 50) @AlphaChars String userId,
            @ForAll @StringLength(min = 1, max = 100) String projectName,
            @ForAll @StringLength(min = 1, max = 500) String projectDescription,
            @ForAll @IntRange(min = 1, max = 3) int linkCount) {
        
        // Given: A valid user exists
        User mockUser = createMockUser(userId);
        when(userService.getUserById(userId)).thenReturn(mockUser);
        
        when(projectRepository.save(any(Project.class))).thenAnswer(invocation -> {
            Project savedProject = invocation.getArgument(0);
            savedProject.setId("project_" + userId);
            return savedProject;
        });
        
        doNothing().when(userService).updateProjectCount(userId);
        
        // Create external links
        List<ExternalLink> externalLinks = new ArrayList<>();
        for (int i = 0; i < linkCount; i++) {
            externalLinks.add(ExternalLink.builder()
                    .type("github")
                    .url("https://github.com/user/repo" + i)
                    .title("Link " + i)
                    .build());
        }
        
        // When: Creating a project with external links
        ProjectCreateRequest request = ProjectCreateRequest.builder()
                .name(projectName)
                .description(projectDescription)
                .techTags(new ArrayList<>())
                .externalLinks(externalLinks)
                .build();
        
        ProjectResponse response = projectService.createProject(userId, request);
        
        // Then: All external links should be stored
        assertThat(response.getExternalLinks()).hasSize(linkCount);
        for (int i = 0; i < linkCount; i++) {
            assertThat(response.getExternalLinks().get(i).getUrl())
                    .isEqualTo("https://github.com/user/repo" + i);
        }
    }

    /**
     * Property 12: Project data completeness
     * 
     * For any project retrieval request, the response should include project 
     * metadata and accurate associated ships count.
     * 
     * **Validates: Requirements 3.3**
     */
    @Property(tries = 100)
    @Label("Feature: shipspace-mvp, Property 12: Project data completeness")
    void projectDataCompleteness(
            @ForAll @StringLength(min = 1, max = 50) @AlphaChars String projectId,
            @ForAll @StringLength(min = 1, max = 50) @AlphaChars String userId,
            @ForAll @StringLength(min = 1, max = 100) String projectName,
            @ForAll @IntRange(min = 0, max = 10) int shipCount) {
        
        // Given: A project exists with specific ship count
        Project existingProject = createMockProject(projectId, userId, projectName, shipCount);
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(existingProject));
        
        // When: Retrieving the project
        ProjectResponse response = projectService.getProjectById(projectId);
        
        // Then: Response should include all metadata and accurate ship count
        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(projectId);
        assertThat(response.getUserId()).isEqualTo(userId);
        assertThat(response.getName()).isEqualTo(projectName);
        assertThat(response.getDescription()).isNotNull();
        assertThat(response.getTechTags()).isNotNull();
        assertThat(response.getExternalLinks()).isNotNull();
        assertThat(response.getShipCount()).isEqualTo(shipCount);
        assertThat(response.getCreatedAt()).isNotNull();
        assertThat(response.getUpdatedAt()).isNotNull();
        
        // Verify repository was called
        verify(projectRepository).findById(projectId);
    }

    /**
     * Property 12 (variant): Project retrieval for non-existent project
     * 
     * For any non-existent project ID, retrieval should throw ResourceNotFoundException.
     * 
     * **Validates: Requirements 3.3**
     */
    @Property(tries = 100)
    @Label("Feature: shipspace-mvp, Property 12: Non-existent project handling")
    void nonExistentProjectHandling(
            @ForAll @StringLength(min = 1, max = 50) @AlphaChars String nonExistentProjectId) {
        
        // Given: A project does not exist
        when(projectRepository.findById(nonExistentProjectId)).thenReturn(Optional.empty());
        
        // When/Then: Retrieving non-existent project should throw exception
        assertThatThrownBy(() -> projectService.getProjectById(nonExistentProjectId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Project");
        
        verify(projectRepository).findById(nonExistentProjectId);
    }

    /**
     * Property 13: Project ownership authorization
     * 
     * For any project update request, only the project owner should be able 
     * to modify the project metadata.
     * 
     * **Validates: Requirements 3.4**
     */
    @Property(tries = 100)
    @Label("Feature: shipspace-mvp, Property 13: Project ownership authorization")
    void projectOwnershipAuthorization(
            @ForAll @StringLength(min = 1, max = 50) @AlphaChars String projectId,
            @ForAll @StringLength(min = 1, max = 50) @AlphaChars String ownerId,
            @ForAll @StringLength(min = 1, max = 50) @AlphaChars String otherUserId,
            @ForAll @StringLength(min = 1, max = 100) String newName) {
        
        // Ensure owner and other user are different
        Assume.that(!ownerId.equals(otherUserId));
        
        // Given: A project exists owned by ownerId
        Project existingProject = createMockProject(projectId, ownerId, "Original Name", 0);
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(existingProject));
        
        // When: A non-owner attempts to update the project
        ProjectUpdateRequest updateRequest = ProjectUpdateRequest.builder()
                .name(newName)
                .build();
        
        // Then: Should throw ForbiddenException
        assertThatThrownBy(() -> projectService.updateProject(projectId, otherUserId, updateRequest))
                .isInstanceOf(ForbiddenException.class);
        
        // Verify save was never called
        verify(projectRepository, never()).save(any(Project.class));
    }

    /**
     * Property 13 (variant): Project owner can update successfully
     * 
     * For any project update request by the owner, the update should succeed
     * and changes should be persisted.
     * 
     * **Validates: Requirements 3.4**
     */
    @Property(tries = 100)
    @Label("Feature: shipspace-mvp, Property 13: Project owner update success")
    void projectOwnerUpdateSuccess(
            @ForAll @StringLength(min = 1, max = 50) @AlphaChars String projectId,
            @ForAll @StringLength(min = 1, max = 50) @AlphaChars String ownerId,
            @ForAll @StringLength(min = 1, max = 100) String newName,
            @ForAll @StringLength(min = 1, max = 500) String newDescription) {
        
        // Given: A project exists owned by ownerId
        Project existingProject = createMockProject(projectId, ownerId, "Original Name", 0);
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(existingProject));
        
        when(projectRepository.save(any(Project.class))).thenAnswer(invocation -> invocation.getArgument(0));
        
        // When: The owner updates the project
        ProjectUpdateRequest updateRequest = ProjectUpdateRequest.builder()
                .name(newName)
                .description(newDescription)
                .build();
        
        ProjectResponse response = projectService.updateProject(projectId, ownerId, updateRequest);
        
        // Then: Update should succeed and changes should be persisted
        assertThat(response).isNotNull();
        assertThat(response.getName()).isEqualTo(newName);
        assertThat(response.getDescription()).isEqualTo(newDescription);
        assertThat(response.getUpdatedAt()).isNotNull();
        
        // Verify save was called
        verify(projectRepository).save(any(Project.class));
    }

    /**
     * Property 13 (variant): Partial project updates preserve unchanged fields
     * 
     * For any partial project update, only provided fields should be modified,
     * leaving other fields unchanged.
     * 
     * **Validates: Requirements 3.4**
     */
    @Property(tries = 100)
    @Label("Feature: shipspace-mvp, Property 13: Partial project update")
    void partialProjectUpdate(
            @ForAll @StringLength(min = 1, max = 50) @AlphaChars String projectId,
            @ForAll @StringLength(min = 1, max = 50) @AlphaChars String ownerId,
            @ForAll @StringLength(min = 1, max = 100) String originalName,
            @ForAll @StringLength(min = 1, max = 500) String originalDescription,
            @ForAll @StringLength(min = 1, max = 500) String newDescription) {
        
        // Given: A project exists with original data
        Project existingProject = Project.builder()
                .id(projectId)
                .userId(ownerId)
                .name(originalName)
                .description(originalDescription)
                .techTags(List.of("java", "spring"))
                .externalLinks(new ArrayList<>())
                .shipCount(0)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(existingProject));
        when(projectRepository.save(any(Project.class))).thenAnswer(invocation -> invocation.getArgument(0));
        
        // When: Updating only the description (not name or tech tags)
        ProjectUpdateRequest updateRequest = ProjectUpdateRequest.builder()
                .description(newDescription)
                .build();
        
        ProjectResponse response = projectService.updateProject(projectId, ownerId, updateRequest);
        
        // Then: Only description should be updated, other fields unchanged
        assertThat(response.getDescription()).isEqualTo(newDescription);
        assertThat(response.getName()).isEqualTo(originalName);
        assertThat(response.getTechTags()).containsExactly("java", "spring");
    }

    /**
     * Property 14: Project ship chronological ordering
     * 
     * For any project with multiple ships, retrieving project history should 
     * return all ships in reverse chronological order (most recent first).
     * 
     * **Validates: Requirements 3.5**
     */
    @Property(tries = 100)
    @Label("Feature: shipspace-mvp, Property 14: Project ship chronological ordering")
    void projectShipChronologicalOrdering(
            @ForAll @StringLength(min = 1, max = 50) @AlphaChars String projectId,
            @ForAll @StringLength(min = 1, max = 50) @AlphaChars String userId,
            @ForAll @IntRange(min = 2, max = 10) int shipCount) {
        
        // Given: A project exists
        Project existingProject = createMockProject(projectId, userId, "Test Project", shipCount);
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(existingProject));
        
        // Create ships with different timestamps (older to newer)
        List<Ship> ships = new ArrayList<>();
        Instant baseTime = Instant.now().minusSeconds(shipCount * 3600L); // Start from hours ago
        
        for (int i = 0; i < shipCount; i++) {
            Ship ship = Ship.builder()
                    .id("ship_" + i)
                    .userId(userId)
                    .projectId(projectId)
                    .content("Ship content " + i)
                    .createdAt(baseTime.plusSeconds(i * 3600L)) // Each ship 1 hour apart
                    .build();
            ships.add(ship);
        }
        
        // Reverse the list to simulate most recent first (as the query would return)
        List<Ship> reversedShips = new ArrayList<>(ships);
        reversedShips.sort((s1, s2) -> s2.getCreatedAt().compareTo(s1.getCreatedAt()));
        
        // Mock the repository to return ships in reverse chronological order
        Page<Ship> shipPage = new PageImpl<>(reversedShips, 
                PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdAt")), 
                shipCount);
        when(shipRepository.findByProjectId(eq(projectId), any())).thenReturn(shipPage);
        
        // When: Retrieving project ships
        Page<Ship> result = projectService.getProjectShips(projectId);
        
        // Then: Ships should be in reverse chronological order (most recent first)
        assertThat(result.getContent()).hasSize(shipCount);
        
        // Verify ordering: each ship should be newer than or equal to the next
        List<Ship> resultShips = result.getContent();
        for (int i = 0; i < resultShips.size() - 1; i++) {
            Instant currentTime = resultShips.get(i).getCreatedAt();
            Instant nextTime = resultShips.get(i + 1).getCreatedAt();
            assertThat(currentTime).isAfterOrEqualTo(nextTime);
        }
        
        // Verify the most recent ship is first
        assertThat(resultShips.get(0).getCreatedAt())
                .isEqualTo(ships.get(shipCount - 1).getCreatedAt());
        
        // Verify the oldest ship is last
        assertThat(resultShips.get(shipCount - 1).getCreatedAt())
                .isEqualTo(ships.get(0).getCreatedAt());
        
        verify(projectRepository).findById(projectId);
        verify(shipRepository).findByProjectId(eq(projectId), any());
    }

    /**
     * Property 14 (variant): Empty project returns empty ship list
     * 
     * For any project with no ships, retrieving project history should 
     * return an empty list.
     * 
     * **Validates: Requirements 3.5**
     */
    @Property(tries = 100)
    @Label("Feature: shipspace-mvp, Property 14: Empty project ship list")
    void emptyProjectShipList(
            @ForAll @StringLength(min = 1, max = 50) @AlphaChars String projectId,
            @ForAll @StringLength(min = 1, max = 50) @AlphaChars String userId) {
        
        // Given: A project exists with no ships
        Project existingProject = createMockProject(projectId, userId, "Test Project", 0);
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(existingProject));
        
        // Mock empty ship list
        Page<Ship> emptyPage = new PageImpl<>(new ArrayList<>(), 
                PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdAt")), 
                0);
        when(shipRepository.findByProjectId(eq(projectId), any())).thenReturn(emptyPage);
        
        // When: Retrieving project ships
        Page<Ship> result = projectService.getProjectShips(projectId);
        
        // Then: Should return empty list
        assertThat(result.getContent()).isEmpty();
        assertThat(result.getTotalElements()).isEqualTo(0);
        
        verify(projectRepository).findById(projectId);
        verify(shipRepository).findByProjectId(eq(projectId), any());
    }

    /**
     * Property 14 (variant): Project ship pagination works correctly
     * 
     * For any project with ships, pagination should work correctly and
     * maintain chronological ordering across pages.
     * 
     * **Validates: Requirements 3.5**
     */
    @Property(tries = 100)
    @Label("Feature: shipspace-mvp, Property 14: Project ship pagination")
    void projectShipPagination(
            @ForAll @StringLength(min = 1, max = 50) @AlphaChars String projectId,
            @ForAll @StringLength(min = 1, max = 50) @AlphaChars String userId,
            @ForAll @IntRange(min = 5, max = 15) int totalShips,
            @ForAll @IntRange(min = 2, max = 5) int pageSize) {
        
        // Given: A project exists with multiple ships
        Project existingProject = createMockProject(projectId, userId, "Test Project", totalShips);
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(existingProject));
        
        // Create ships with timestamps
        List<Ship> allShips = new ArrayList<>();
        Instant baseTime = Instant.now().minusSeconds(totalShips * 3600L);
        
        for (int i = 0; i < totalShips; i++) {
            Ship ship = Ship.builder()
                    .id("ship_" + i)
                    .userId(userId)
                    .projectId(projectId)
                    .content("Ship content " + i)
                    .createdAt(baseTime.plusSeconds(i * 3600L))
                    .build();
            allShips.add(ship);
        }
        
        // Sort in reverse chronological order
        allShips.sort((s1, s2) -> s2.getCreatedAt().compareTo(s1.getCreatedAt()));
        
        // Get first page
        int actualPageSize = Math.min(pageSize, totalShips);
        List<Ship> firstPageShips = allShips.subList(0, actualPageSize);
        Page<Ship> firstPage = new PageImpl<>(firstPageShips, 
                PageRequest.of(0, pageSize, Sort.by(Sort.Direction.DESC, "createdAt")), 
                totalShips);
        
        when(shipRepository.findByProjectId(eq(projectId), any())).thenReturn(firstPage);
        
        // When: Retrieving first page of project ships
        Page<Ship> result = projectService.getProjectShips(projectId, 0, pageSize);
        
        // Then: Should return correct page with proper ordering
        assertThat(result.getContent()).hasSize(actualPageSize);
        assertThat(result.getTotalElements()).isEqualTo(totalShips);
        
        // Verify chronological ordering within the page
        List<Ship> pageShips = result.getContent();
        for (int i = 0; i < pageShips.size() - 1; i++) {
            assertThat(pageShips.get(i).getCreatedAt())
                    .isAfterOrEqualTo(pageShips.get(i + 1).getCreatedAt());
        }
    }

    // Helper methods

    private User createMockUser(String userId) {
        return User.builder()
                .id(userId)
                .githubId("gh_" + userId)
                .username("user_" + userId)
                .email(userId + "@example.com")
                .followerCount(0)
                .followingCount(0)
                .projectCount(0)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    private Project createMockProject(String projectId, String userId, String name, int shipCount) {
        return Project.builder()
                .id(projectId)
                .userId(userId)
                .name(name)
                .description("Test project description")
                .techTags(List.of("java", "spring", "mongodb"))
                .externalLinks(new ArrayList<>())
                .shipCount(shipCount)
                .lastShipAt(shipCount > 0 ? Instant.now() : null)
                .createdAt(Instant.now().minusSeconds(86400))
                .updatedAt(Instant.now())
                .build();
    }
}
