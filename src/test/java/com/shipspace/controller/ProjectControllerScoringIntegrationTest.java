package com.shipspace.controller;

import com.shipspace.dto.ProjectCreateRequest;
import com.shipspace.dto.ProjectResponse;
import com.shipspace.dto.ProjectUpdateRequest;
import com.shipspace.model.ScoreEventType;
import com.shipspace.service.ProjectService;
import com.shipspace.service.ShipScoreService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.Instant;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Integration tests for PROJECT_CREATED and PROJECT_UPDATED scoring events in ProjectController.
 * Tests that the scoring service is called correctly when a project is created or updated.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ProjectController Scoring Integration Tests")
class ProjectControllerScoringIntegrationTest {

    @Mock
    private ProjectService projectService;

    @Mock
    private ShipScoreService shipScoreService;

    @Mock
    private Authentication authentication;

    @InjectMocks
    private ProjectController projectController;

    private String testUserId;
    private String testProjectId;
    private ProjectResponse testProjectResponse;
    private ProjectCreateRequest testCreateRequest;
    private ProjectUpdateRequest testUpdateRequest;
    private UserDetails userDetails;

    @BeforeEach
    void setUp() {
        testUserId = "user123";
        testProjectId = "project123";

        testProjectResponse = ProjectResponse.builder()
                .id(testProjectId)
                .userId(testUserId)
                .name("Test Project")
                .description("A test project description")
                .techTags(Arrays.asList("Java", "Spring Boot"))
                .shipCount(0)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        testCreateRequest = ProjectCreateRequest.builder()
                .name("Test Project")
                .description("A test project description")
                .techTags(Arrays.asList("Java", "Spring Boot"))
                .build();

        testUpdateRequest = ProjectUpdateRequest.builder()
                .name("Updated Project Name")
                .description("Updated description")
                .build();

        userDetails = User.builder()
                .username(testUserId)
                .password("password")
                .authorities("ROLE_USER")
                .build();
    }

    @Test
    @DisplayName("Should call ShipScoreService with PROJECT_CREATED event when project is created successfully")
    void createProject_ShouldApplyProjectCreatedScoreEvent() {
        // Arrange
        when(authentication.getPrincipal()).thenReturn(userDetails);
        when(projectService.createProject(eq(testUserId), any(ProjectCreateRequest.class)))
                .thenReturn(testProjectResponse);
        when(shipScoreService.applyScoreEvent(eq(testUserId), eq(ScoreEventType.PROJECT_CREATED), eq(testProjectId)))
                .thenReturn(true);

        // Act
        ResponseEntity<ProjectResponse> response = projectController.createProject(authentication, testCreateRequest);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getId()).isEqualTo(testProjectId);

        // Verify that the scoring service was called with correct parameters
        verify(shipScoreService).applyScoreEvent(
                eq(testUserId),
                eq(ScoreEventType.PROJECT_CREATED),
                eq(testProjectId)
        );
    }

    @Test
    @DisplayName("Should still create project successfully even if scoring fails")
    void createProject_ShouldSucceedEvenIfScoringFails() {
        // Arrange
        when(authentication.getPrincipal()).thenReturn(userDetails);
        when(projectService.createProject(eq(testUserId), any(ProjectCreateRequest.class)))
                .thenReturn(testProjectResponse);
        when(shipScoreService.applyScoreEvent(eq(testUserId), eq(ScoreEventType.PROJECT_CREATED), eq(testProjectId)))
                .thenThrow(new RuntimeException("Scoring service error"));

        // Act
        ResponseEntity<ProjectResponse> response = projectController.createProject(authentication, testCreateRequest);

        // Assert - Project creation should still succeed
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getId()).isEqualTo(testProjectId);

        // Verify that the scoring service was called
        verify(shipScoreService).applyScoreEvent(
                eq(testUserId),
                eq(ScoreEventType.PROJECT_CREATED),
                eq(testProjectId)
        );
    }

    @Test
    @DisplayName("Should handle case when score event is rejected (duplicate or cap)")
    void createProject_ShouldHandleScoreEventRejection() {
        // Arrange
        when(authentication.getPrincipal()).thenReturn(userDetails);
        when(projectService.createProject(eq(testUserId), any(ProjectCreateRequest.class)))
                .thenReturn(testProjectResponse);
        when(shipScoreService.applyScoreEvent(eq(testUserId), eq(ScoreEventType.PROJECT_CREATED), eq(testProjectId)))
                .thenReturn(false); // Score event rejected

        // Act
        ResponseEntity<ProjectResponse> response = projectController.createProject(authentication, testCreateRequest);

        // Assert - Project creation should still succeed
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getId()).isEqualTo(testProjectId);

        // Verify that the scoring service was called
        verify(shipScoreService).applyScoreEvent(
                eq(testUserId),
                eq(ScoreEventType.PROJECT_CREATED),
                eq(testProjectId)
        );
    }

    @Test
    @DisplayName("Should pass correct parameters to scoring service")
    void createProject_ShouldPassCorrectParametersToScoringService() {
        // Arrange
        when(authentication.getPrincipal()).thenReturn(userDetails);
        when(projectService.createProject(eq(testUserId), any(ProjectCreateRequest.class)))
                .thenReturn(testProjectResponse);
        when(shipScoreService.applyScoreEvent(anyString(), any(ScoreEventType.class), anyString()))
                .thenReturn(true);

        // Act
        projectController.createProject(authentication, testCreateRequest);

        // Assert - Verify exact parameters
        verify(shipScoreService).applyScoreEvent(
                eq(testUserId),           // userId from authentication
                eq(ScoreEventType.PROJECT_CREATED),  // Correct event type
                eq(testProjectId)         // projectId from created project
        );
    }

    @Test
    @DisplayName("Should call ShipScoreService with PROJECT_UPDATED event when project is updated successfully")
    void updateProject_ShouldApplyProjectUpdatedScoreEvent() {
        // Arrange
        when(authentication.getPrincipal()).thenReturn(userDetails);
        when(projectService.updateProject(eq(testProjectId), eq(testUserId), any(ProjectUpdateRequest.class)))
                .thenReturn(testProjectResponse);
        when(shipScoreService.applyScoreEvent(eq(testUserId), eq(ScoreEventType.PROJECT_UPDATED), eq(testProjectId)))
                .thenReturn(true);

        // Act
        ResponseEntity<ProjectResponse> response = projectController.updateProject(authentication, testProjectId, testUpdateRequest);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getId()).isEqualTo(testProjectId);

        // Verify that the scoring service was called with correct parameters
        verify(shipScoreService).applyScoreEvent(
                eq(testUserId),
                eq(ScoreEventType.PROJECT_UPDATED),
                eq(testProjectId)
        );
    }

    @Test
    @DisplayName("Should still update project successfully even if scoring fails")
    void updateProject_ShouldSucceedEvenIfScoringFails() {
        // Arrange
        when(authentication.getPrincipal()).thenReturn(userDetails);
        when(projectService.updateProject(eq(testProjectId), eq(testUserId), any(ProjectUpdateRequest.class)))
                .thenReturn(testProjectResponse);
        when(shipScoreService.applyScoreEvent(eq(testUserId), eq(ScoreEventType.PROJECT_UPDATED), eq(testProjectId)))
                .thenThrow(new RuntimeException("Scoring service error"));

        // Act
        ResponseEntity<ProjectResponse> response = projectController.updateProject(authentication, testProjectId, testUpdateRequest);

        // Assert - Project update should still succeed
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getId()).isEqualTo(testProjectId);

        // Verify that the scoring service was called
        verify(shipScoreService).applyScoreEvent(
                eq(testUserId),
                eq(ScoreEventType.PROJECT_UPDATED),
                eq(testProjectId)
        );
    }

    @Test
    @DisplayName("Should handle case when PROJECT_UPDATED score event is rejected (duplicate or cap)")
    void updateProject_ShouldHandleScoreEventRejection() {
        // Arrange
        when(authentication.getPrincipal()).thenReturn(userDetails);
        when(projectService.updateProject(eq(testProjectId), eq(testUserId), any(ProjectUpdateRequest.class)))
                .thenReturn(testProjectResponse);
        when(shipScoreService.applyScoreEvent(eq(testUserId), eq(ScoreEventType.PROJECT_UPDATED), eq(testProjectId)))
                .thenReturn(false); // Score event rejected (e.g., daily cap reached)

        // Act
        ResponseEntity<ProjectResponse> response = projectController.updateProject(authentication, testProjectId, testUpdateRequest);

        // Assert - Project update should still succeed
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getId()).isEqualTo(testProjectId);

        // Verify that the scoring service was called
        verify(shipScoreService).applyScoreEvent(
                eq(testUserId),
                eq(ScoreEventType.PROJECT_UPDATED),
                eq(testProjectId)
        );
    }

    @Test
    @DisplayName("Should pass correct parameters to scoring service for PROJECT_UPDATED")
    void updateProject_ShouldPassCorrectParametersToScoringService() {
        // Arrange
        when(authentication.getPrincipal()).thenReturn(userDetails);
        when(projectService.updateProject(eq(testProjectId), eq(testUserId), any(ProjectUpdateRequest.class)))
                .thenReturn(testProjectResponse);
        when(shipScoreService.applyScoreEvent(anyString(), any(ScoreEventType.class), anyString()))
                .thenReturn(true);

        // Act
        projectController.updateProject(authentication, testProjectId, testUpdateRequest);

        // Assert - Verify exact parameters
        verify(shipScoreService).applyScoreEvent(
                eq(testUserId),           // userId from authentication
                eq(ScoreEventType.PROJECT_UPDATED),  // Correct event type
                eq(testProjectId)         // projectId being updated
        );
    }
}
