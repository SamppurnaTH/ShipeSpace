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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ProjectService.
 * Tests project CRUD operations, ownership validation, and metadata aggregation.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ProjectService Tests")
class ProjectServiceTest {

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private ShipRepository shipRepository;

    @Mock
    private UserService userService;

    @Mock
    private MongoTemplate mongoTemplate;

    @Mock
    private ShipScoreService shipScoreService;

    @Mock
    private CascadeDeleteService cascadeDeleteService;

    @Mock
    private InputSanitizer inputSanitizer;

    @InjectMocks
    private ProjectService projectService;

    private User testUser;
    private Project testProject;
    private ProjectCreateRequest createRequest;
    private ProjectUpdateRequest updateRequest;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .id("user123")
                .username("testuser")
                .email("test@example.com")
                .projectCount(0)
                .build();

        testProject = Project.builder()
                .id("project123")
                .userId("user123")
                .name("Test Project")
                .description("A test project description")
                .techTags(Arrays.asList("Java", "Spring Boot"))
                .externalLinks(new ArrayList<>())
                .shipCount(0)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        createRequest = ProjectCreateRequest.builder()
                .name("Test Project")
                .description("A test project description")
                .techTags(Arrays.asList("Java", "Spring Boot"))
                .externalLinks(new ArrayList<>())
                .build();

        updateRequest = ProjectUpdateRequest.builder()
                .name("Updated Project")
                .description("Updated description")
                .build();

        lenient().when(inputSanitizer.sanitize(anyString(), anyInt()))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    @DisplayName("Should create project successfully")
    void shouldCreateProjectSuccessfully() {
        // Given
        when(userService.getUserById("user123")).thenReturn(testUser);
        when(projectRepository.save(any(Project.class))).thenReturn(testProject);
        doNothing().when(userService).updateProjectCount("user123");

        // When
        ProjectResponse response = projectService.createProject("user123", createRequest);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo("project123");
        assertThat(response.getName()).isEqualTo("Test Project");
        assertThat(response.getDescription()).isEqualTo("A test project description");
        assertThat(response.getTechTags()).containsExactly("Java", "Spring Boot");
        assertThat(response.getShipCount()).isEqualTo(0);

        verify(userService).getUserById("user123");
        verify(projectRepository).save(any(Project.class));
        verify(userService).updateProjectCount("user123");
    }

    @Test
    @DisplayName("Should throw exception when creating project for non-existent user")
    void shouldThrowExceptionWhenCreatingProjectForNonExistentUser() {
        // Given
        when(userService.getUserById("nonexistent")).thenThrow(new ResourceNotFoundException("User", "nonexistent"));

        // When & Then
        assertThatThrownBy(() -> projectService.createProject("nonexistent", createRequest))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("User not found");

        verify(userService).getUserById("nonexistent");
        verify(projectRepository, never()).save(any(Project.class));
    }

    @Test
    @DisplayName("Should retrieve project by ID successfully")
    void shouldRetrieveProjectByIdSuccessfully() {
        // Given
        when(projectRepository.findById("project123")).thenReturn(Optional.of(testProject));

        // When
        ProjectResponse response = projectService.getProjectById("project123");

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo("project123");
        assertThat(response.getName()).isEqualTo("Test Project");
        assertThat(response.getUserId()).isEqualTo("user123");

        verify(projectRepository).findById("project123");
    }

    @Test
    @DisplayName("Should throw exception when project not found")
    void shouldThrowExceptionWhenProjectNotFound() {
        // Given
        when(projectRepository.findById("nonexistent")).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> projectService.getProjectById("nonexistent"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Project not found");

        verify(projectRepository).findById("nonexistent");
    }

    @Test
    @DisplayName("Should update project successfully when user is owner")
    void shouldUpdateProjectSuccessfullyWhenUserIsOwner() {
        // Given
        when(projectRepository.findById("project123")).thenReturn(Optional.of(testProject));
        when(projectRepository.save(any(Project.class))).thenReturn(testProject);

        // When
        ProjectResponse response = projectService.updateProject("project123", "user123", updateRequest);

        // Then
        assertThat(response).isNotNull();
        verify(projectRepository).findById("project123");
        verify(projectRepository).save(any(Project.class));

        ArgumentCaptor<Project> projectCaptor = ArgumentCaptor.forClass(Project.class);
        verify(projectRepository).save(projectCaptor.capture());
        Project savedProject = projectCaptor.getValue();
        assertThat(savedProject.getName()).isEqualTo("Updated Project");
        assertThat(savedProject.getDescription()).isEqualTo("Updated description");
    }

    @Test
    @DisplayName("Should throw ForbiddenException when non-owner tries to update project")
    void shouldThrowForbiddenExceptionWhenNonOwnerTriesToUpdate() {
        // Given
        when(projectRepository.findById("project123")).thenReturn(Optional.of(testProject));

        // When & Then
        assertThatThrownBy(() -> projectService.updateProject("project123", "otherUser", updateRequest))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("don't have permission");

        verify(projectRepository).findById("project123");
        verify(projectRepository, never()).save(any(Project.class));
    }

    @Test
    @DisplayName("Should update only provided fields")
    void shouldUpdateOnlyProvidedFields() {
        // Given
        ProjectUpdateRequest partialUpdate = ProjectUpdateRequest.builder()
                .name("New Name Only")
                .build();

        when(projectRepository.findById("project123")).thenReturn(Optional.of(testProject));
        when(projectRepository.save(any(Project.class))).thenReturn(testProject);

        // When
        projectService.updateProject("project123", "user123", partialUpdate);

        // Then
        ArgumentCaptor<Project> projectCaptor = ArgumentCaptor.forClass(Project.class);
        verify(projectRepository).save(projectCaptor.capture());
        Project savedProject = projectCaptor.getValue();
        
        assertThat(savedProject.getName()).isEqualTo("New Name Only");
        assertThat(savedProject.getDescription()).isEqualTo("A test project description"); // Unchanged
    }

    @Test
    @DisplayName("Should retrieve project ships in chronological order")
    void shouldRetrieveProjectShipsInChronologicalOrder() {
        // Given
        Ship ship1 = Ship.builder()
                .id("ship1")
                .projectId("project123")
                .userId("user123")
                .content("First ship")
                .createdAt(Instant.now().minusSeconds(3600))
                .build();

        Ship ship2 = Ship.builder()
                .id("ship2")
                .projectId("project123")
                .userId("user123")
                .content("Second ship")
                .createdAt(Instant.now())
                .build();

        List<Ship> ships = Arrays.asList(ship2, ship1); // Most recent first
        Page<Ship> shipPage = new PageImpl<>(ships);

        when(projectRepository.findById("project123")).thenReturn(Optional.of(testProject));
        when(shipRepository.findByProjectId(eq("project123"), any(Pageable.class))).thenReturn(shipPage);

        // When
        Page<Ship> result = projectService.getProjectShips("project123", 0, 20);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getContent().get(0).getId()).isEqualTo("ship2"); // Most recent first
        assertThat(result.getContent().get(1).getId()).isEqualTo("ship1");

        verify(projectRepository).findById("project123");
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(shipRepository).findByProjectId(eq("project123"), pageableCaptor.capture());
        assertThat(pageableCaptor.getValue().getSort().getOrderFor("createdAt")).isNotNull();
        assertThat(pageableCaptor.getValue().getSort().getOrderFor("id")).isNotNull();
    }

    @Test
    @DisplayName("Should throw exception when retrieving ships for non-existent project")
    void shouldThrowExceptionWhenRetrievingShipsForNonExistentProject() {
        // Given
        when(projectRepository.findById("nonexistent")).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> projectService.getProjectShips("nonexistent"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Project not found");

        verify(projectRepository).findById("nonexistent");
        verify(shipRepository, never()).findByProjectId(anyString(), any(Pageable.class));
    }

    @Test
    @DisplayName("Should update project ship metadata")
    void shouldUpdateProjectShipMetadata() {
        // Given
        // When
        projectService.updateProjectShipMetadata("project123");

        // Then
        verify(mongoTemplate).updateFirst(any(), any(), eq(Project.class));
        verify(projectRepository, never()).save(any(Project.class));
    }

    @Test
    @DisplayName("Should verify project ownership successfully")
    void shouldVerifyProjectOwnershipSuccessfully() {
        // Given
        when(projectRepository.findById("project123")).thenReturn(Optional.of(testProject));

        // When & Then - should not throw exception
        projectService.verifyProjectOwnership("project123", "user123");

        verify(projectRepository).findById("project123");
    }

    @Test
    @DisplayName("Should throw ForbiddenException when verifying ownership for non-owner")
    void shouldThrowForbiddenExceptionWhenVerifyingOwnershipForNonOwner() {
        // Given
        when(projectRepository.findById("project123")).thenReturn(Optional.of(testProject));

        // When & Then
        assertThatThrownBy(() -> projectService.verifyProjectOwnership("project123", "otherUser"))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("don't have permission");

        verify(projectRepository).findById("project123");
    }

    @Test
    @DisplayName("Should create project with external links")
    void shouldCreateProjectWithExternalLinks() {
        // Given
        ExternalLink githubLink = ExternalLink.builder()
                .type("github")
                .url("https://github.com/user/repo")
                .title("GitHub Repository")
                .build();

        ProjectCreateRequest requestWithLinks = ProjectCreateRequest.builder()
                .name("Project with Links")
                .description("Description")
                .techTags(Arrays.asList("Java"))
                .externalLinks(Arrays.asList(githubLink))
                .build();

        Project projectWithLinks = Project.builder()
                .id("project456")
                .userId("user123")
                .name("Project with Links")
                .description("Description")
                .techTags(Arrays.asList("Java"))
                .externalLinks(Arrays.asList(githubLink))
                .shipCount(0)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        when(userService.getUserById("user123")).thenReturn(testUser);
        when(projectRepository.save(any(Project.class))).thenReturn(projectWithLinks);
        doNothing().when(userService).updateProjectCount("user123");

        // When
        ProjectResponse response = projectService.createProject("user123", requestWithLinks);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getExternalLinks()).hasSize(1);
        assertThat(response.getExternalLinks().get(0).getType()).isEqualTo("github");
        assertThat(response.getExternalLinks().get(0).getUrl()).isEqualTo("https://github.com/user/repo");
    }

    @Test
    @DisplayName("Should delete owned project with associated ships and cascade references")
    void shouldDeleteOwnedProjectWithAssociatedShipsAndCascadeReferences() {
        // Given
        Ship ship1 = Ship.builder().id("ship1").projectId("project123").userId("user123").build();
        Ship ship2 = Ship.builder().id("ship2").projectId("project123").userId("user123").build();
        when(projectRepository.findById("project123")).thenReturn(Optional.of(testProject));
        when(shipRepository.findIdsByProjectId("project123")).thenReturn(List.of(ship1, ship2));
        doNothing().when(userService).decrementProjectCount("user123");

        // When
        projectService.deleteProject("project123", "user123");

        // Then
        verify(shipRepository).deleteByProjectId("project123");
        verify(projectRepository).deleteById("project123");
        verify(userService).decrementProjectCount("user123");
        verify(cascadeDeleteService).deleteProjectCascade("project123", List.of("ship1", "ship2"));
    }

    @Test
    @DisplayName("Should deny project deletion by non-owner")
    void shouldDenyProjectDeletionByNonOwner() {
        // Given
        when(projectRepository.findById("project123")).thenReturn(Optional.of(testProject));

        // When & Then
        assertThatThrownBy(() -> projectService.deleteProject("project123", "otherUser"))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("project");

        verify(shipRepository, never()).deleteByProjectId(anyString());
        verify(projectRepository, never()).deleteById(anyString());
        verify(cascadeDeleteService, never()).deleteProjectCascade(anyString(), any());
    }
}
