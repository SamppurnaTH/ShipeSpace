package com.shipspace.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shipspace.dto.ProjectCreateRequest;
import com.shipspace.dto.ProjectResponse;
import com.shipspace.dto.ProjectUpdateRequest;
import com.shipspace.exception.ForbiddenException;
import com.shipspace.exception.ResourceNotFoundException;
import com.shipspace.model.ExternalLink;
import com.shipspace.model.Ship;
import com.shipspace.service.ProjectService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
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
 * Integration tests for ProjectController.
 * Tests all project management endpoints with authentication and authorization.
 */
@WebMvcTest(controllers = ProjectController.class,
    excludeAutoConfiguration = {
        org.springframework.boot.autoconfigure.data.mongo.MongoDataAutoConfiguration.class,
        org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration.class
    },
    excludeFilters = @org.springframework.context.annotation.ComponentScan.Filter(
        type = org.springframework.context.annotation.FilterType.ASSIGNABLE_TYPE,
        classes = com.shipspace.config.MongoAuditingConfig.class))
@AutoConfigureMockMvc
@org.springframework.context.annotation.Import(com.shipspace.config.MockMvcContextPathCustomizer.class)
@DisplayName("ProjectController Integration Tests")
class ProjectControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ProjectService projectService;

    @MockBean
    private com.shipspace.util.JwtUtil jwtUtil;

    @MockBean
    private com.shipspace.service.ShipScoreService shipScoreService;

    @MockBean
    private com.shipspace.security.TokenBlacklistService tokenBlacklistService;

    @MockBean
    private com.shipspace.util.DtoMapper dtoMapper;

    private String testUserId;
    private String testProjectId;
    private ProjectResponse testProjectResponse;
    private ProjectCreateRequest testCreateRequest;
    private ProjectUpdateRequest testUpdateRequest;

    @BeforeEach
    void setUp() {
        testUserId = "user123";
        testProjectId = "project123";

        ExternalLink githubLink = ExternalLink.builder()
                .type("github")
                .url("https://github.com/user/repo")
                .title("GitHub Repository")
                .build();

        testProjectResponse = ProjectResponse.builder()
                .id(testProjectId)
                .userId(testUserId)
                .name("Test Project")
                .description("A test project description")
                .techTags(Arrays.asList("Java", "Spring Boot", "MongoDB"))
                .externalLinks(Arrays.asList(githubLink))
                .shipCount(5)
                .lastShipAt(Instant.now())
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        testCreateRequest = ProjectCreateRequest.builder()
                .name("Test Project")
                .description("A test project description")
                .techTags(Arrays.asList("Java", "Spring Boot", "MongoDB"))
                .externalLinks(Arrays.asList(githubLink))
                .build();

        testUpdateRequest = ProjectUpdateRequest.builder()
                .name("Updated Project Name")
                .description("Updated description")
                .build();
    }

    @Test
    @WithMockUser(username = "user123")
    @DisplayName("POST /api/projects - Should create project successfully")
    void createProject_Success() throws Exception {
        // Arrange
        when(projectService.createProject(eq(testUserId), any(ProjectCreateRequest.class)))
                .thenReturn(testProjectResponse);

        // Act & Assert
        mockMvc.perform(post("/api/projects")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(testCreateRequest)))
                .andExpect(status().isCreated())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(testProjectId))
                .andExpect(jsonPath("$.userId").value(testUserId))
                .andExpect(jsonPath("$.name").value("Test Project"))
                .andExpect(jsonPath("$.description").value("A test project description"))
                .andExpect(jsonPath("$.techTags", hasSize(3)))
                .andExpect(jsonPath("$.techTags[0]").value("Java"))
                .andExpect(jsonPath("$.externalLinks", hasSize(1)))
                .andExpect(jsonPath("$.shipCount").value(5));

        verify(projectService).createProject(eq(testUserId), any(ProjectCreateRequest.class));
    }

    @Test
    @DisplayName("POST /api/projects - Should return 401 when not authenticated")
    void createProject_Unauthorized() throws Exception {
        // Act & Assert
        mockMvc.perform(post("/api/projects")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(testCreateRequest)))
                .andExpect(status().isUnauthorized());

        verify(projectService, never()).createProject(anyString(), any(ProjectCreateRequest.class));
    }

    @Test
    @WithMockUser(username = "user123")
    @DisplayName("POST /api/projects - Should validate required fields")
    void createProject_ValidationError() throws Exception {
        // Arrange - Create request with missing required fields
        ProjectCreateRequest invalidRequest = ProjectCreateRequest.builder()
                .name("") // Empty name
                .description("") // Empty description
                .build();

        // Act & Assert
        mockMvc.perform(post("/api/projects")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        verify(projectService, never()).createProject(anyString(), any(ProjectCreateRequest.class));
    }

    @Test
    @WithMockUser(username = "user123")
    @DisplayName("POST /api/projects - Should validate name length")
    void createProject_NameTooLong() throws Exception {
        // Arrange - Create request with name exceeding max length
        String longName = "a".repeat(101); // Max is 100
        ProjectCreateRequest invalidRequest = ProjectCreateRequest.builder()
                .name(longName)
                .description("Valid description")
                .build();

        // Act & Assert
        mockMvc.perform(post("/api/projects")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        verify(projectService, never()).createProject(anyString(), any(ProjectCreateRequest.class));
    }

    @Test
    @WithMockUser(username = "user123")
    @DisplayName("GET /api/projects/{projectId} - Should return project details")
    void getProject_Success() throws Exception {
        // Arrange
        when(projectService.getProjectById(testProjectId)).thenReturn(testProjectResponse);

        // Act & Assert
        mockMvc.perform(get("/api/projects/{projectId}", testProjectId))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(testProjectId))
                .andExpect(jsonPath("$.name").value("Test Project"))
                .andExpect(jsonPath("$.description").value("A test project description"))
                .andExpect(jsonPath("$.shipCount").value(5));

        verify(projectService).getProjectById(testProjectId);
    }

    @Test
    @WithMockUser(username = "user123")
    @DisplayName("GET /api/projects/{projectId} - Should return 404 when project not found")
    void getProject_NotFound() throws Exception {
        // Arrange
        when(projectService.getProjectById(anyString()))
                .thenThrow(new ResourceNotFoundException("Project", "nonexistent"));

        // Act & Assert
        mockMvc.perform(get("/api/projects/{projectId}", "nonexistent"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Project not found with id: nonexistent"));

        verify(projectService).getProjectById("nonexistent");
    }

    @Test
    @WithMockUser(username = "user123")
    @DisplayName("PUT /api/projects/{projectId} - Should update project successfully")
    void updateProject_Success() throws Exception {
        // Arrange
        ProjectResponse updatedResponse = ProjectResponse.builder()
                .id(testProjectId)
                .userId(testUserId)
                .name("Updated Project Name")
                .description("Updated description")
                .techTags(testProjectResponse.getTechTags())
                .externalLinks(testProjectResponse.getExternalLinks())
                .shipCount(5)
                .createdAt(testProjectResponse.getCreatedAt())
                .updatedAt(Instant.now())
                .build();

        when(projectService.updateProject(eq(testProjectId), eq(testUserId), any(ProjectUpdateRequest.class)))
                .thenReturn(updatedResponse);

        // Act & Assert
        mockMvc.perform(put("/api/projects/{projectId}", testProjectId)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(testUpdateRequest)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(testProjectId))
                .andExpect(jsonPath("$.name").value("Updated Project Name"))
                .andExpect(jsonPath("$.description").value("Updated description"));

        verify(projectService).updateProject(eq(testProjectId), eq(testUserId), any(ProjectUpdateRequest.class));
    }

    @Test
    @DisplayName("PUT /api/projects/{projectId} - Should return 401 when not authenticated")
    void updateProject_Unauthorized() throws Exception {
        // Act & Assert
        mockMvc.perform(put("/api/projects/{projectId}", testProjectId)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(testUpdateRequest)))
                .andExpect(status().isUnauthorized());

        verify(projectService, never()).updateProject(anyString(), anyString(), any(ProjectUpdateRequest.class));
    }

    @Test
    @WithMockUser(username = "user456")
    @DisplayName("PUT /api/projects/{projectId} - Should return 403 when not project owner")
    void updateProject_Forbidden() throws Exception {
        // Arrange
        when(projectService.updateProject(eq(testProjectId), eq("user456"), any(ProjectUpdateRequest.class)))
                .thenThrow(new ForbiddenException("update", "project"));

        // Act & Assert
        mockMvc.perform(put("/api/projects/{projectId}", testProjectId)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(testUpdateRequest)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        verify(projectService).updateProject(eq(testProjectId), eq("user456"), any(ProjectUpdateRequest.class));
    }

    @Test
    @WithMockUser(username = "user123")
    @DisplayName("PUT /api/projects/{projectId} - Should return 404 when project not found")
    void updateProject_NotFound() throws Exception {
        // Arrange
        when(projectService.updateProject(eq("nonexistent"), eq(testUserId), any(ProjectUpdateRequest.class)))
                .thenThrow(new ResourceNotFoundException("Project", "nonexistent"));

        // Act & Assert
        mockMvc.perform(put("/api/projects/{projectId}", "nonexistent")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(testUpdateRequest)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));

        verify(projectService).updateProject(eq("nonexistent"), eq(testUserId), any(ProjectUpdateRequest.class));
    }

    @Test
    @WithMockUser(username = "user123")
    @DisplayName("GET /api/projects/{projectId}/ships - Should return project ships")
    void getProjectShips_Success() throws Exception {
        // Arrange
        List<Ship> ships = Arrays.asList(
                Ship.builder()
                        .id("ship1")
                        .userId(testUserId)
                        .projectId(testProjectId)
                        .content("First ship update")
                        .likeCount(10)
                        .commentCount(5)
                        .createdAt(Instant.now())
                        .build(),
                Ship.builder()
                        .id("ship2")
                        .userId(testUserId)
                        .projectId(testProjectId)
                        .content("Second ship update")
                        .likeCount(15)
                        .commentCount(8)
                        .createdAt(Instant.now().minusSeconds(3600))
                        .build()
        );

        Page<Ship> shipPage = new PageImpl<>(ships);
        when(projectService.getProjectShips(testProjectId, 0, 20)).thenReturn(shipPage);

        // Act & Assert
        mockMvc.perform(get("/api/projects/{projectId}/ships", testProjectId))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[0].id").value("ship1"))
                .andExpect(jsonPath("$.content[0].content").value("First ship update"))
                .andExpect(jsonPath("$.content[0].likeCount").value(10))
                .andExpect(jsonPath("$.content[1].id").value("ship2"))
                .andExpect(jsonPath("$.content[1].content").value("Second ship update"));

        verify(projectService).getProjectShips(testProjectId, 0, 20);
    }

    @Test
    @WithMockUser(username = "user123")
    @DisplayName("GET /api/projects/{projectId}/ships - Should support pagination")
    void getProjectShips_WithPagination() throws Exception {
        // Arrange
        List<Ship> ships = Arrays.asList(
                Ship.builder()
                        .id("ship1")
                        .userId(testUserId)
                        .projectId(testProjectId)
                        .content("Ship update")
                        .createdAt(Instant.now())
                        .build()
        );

        Page<Ship> shipPage = new PageImpl<>(ships);
        when(projectService.getProjectShips(testProjectId, 1, 10)).thenReturn(shipPage);

        // Act & Assert
        mockMvc.perform(get("/api/projects/{projectId}/ships", testProjectId)
                        .param("page", "1")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.content", hasSize(1)));

        verify(projectService).getProjectShips(testProjectId, 1, 10);
    }

    @Test
    @WithMockUser(username = "user123")
    @DisplayName("GET /api/projects/{projectId}/ships - Should return empty page when no ships")
    void getProjectShips_EmptyPage() throws Exception {
        // Arrange
        Page<Ship> emptyPage = new PageImpl<>(Arrays.asList());
        when(projectService.getProjectShips(testProjectId, 0, 20)).thenReturn(emptyPage);

        // Act & Assert
        mockMvc.perform(get("/api/projects/{projectId}/ships", testProjectId))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.content", hasSize(0)))
                .andExpect(jsonPath("$.totalElements").value(0));

        verify(projectService).getProjectShips(testProjectId, 0, 20);
    }

    @Test
    @WithMockUser(username = "user123")
    @DisplayName("GET /api/projects/{projectId}/ships - Should return 404 when project not found")
    void getProjectShips_ProjectNotFound() throws Exception {
        // Arrange
        when(projectService.getProjectShips(anyString(), anyInt(), anyInt()))
                .thenThrow(new ResourceNotFoundException("Project", "nonexistent"));

        // Act & Assert
        mockMvc.perform(get("/api/projects/{projectId}/ships", "nonexistent"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));

        verify(projectService).getProjectShips("nonexistent", 0, 20);
    }

    @Test
    @WithMockUser(username = "user123")
    @DisplayName("POST /api/projects - Should handle minimal project creation")
    void createProject_MinimalData() throws Exception {
        // Arrange
        ProjectCreateRequest minimalRequest = ProjectCreateRequest.builder()
                .name("Minimal Project")
                .description("Minimal description")
                .build();

        ProjectResponse minimalResponse = ProjectResponse.builder()
                .id("proj456")
                .userId(testUserId)
                .name("Minimal Project")
                .description("Minimal description")
                .techTags(Arrays.asList())
                .externalLinks(Arrays.asList())
                .shipCount(0)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        when(projectService.createProject(eq(testUserId), any(ProjectCreateRequest.class)))
                .thenReturn(minimalResponse);

        // Act & Assert
        mockMvc.perform(post("/api/projects")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(minimalRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Minimal Project"))
                .andExpect(jsonPath("$.techTags", hasSize(0)))
                .andExpect(jsonPath("$.externalLinks", hasSize(0)))
                .andExpect(jsonPath("$.shipCount").value(0));

        verify(projectService).createProject(eq(testUserId), any(ProjectCreateRequest.class));
    }

    @Test
    @WithMockUser(username = "user123")
    @DisplayName("PUT /api/projects/{projectId} - Should update only provided fields")
    void updateProject_PartialUpdate() throws Exception {
        // Arrange - Only update description
        ProjectUpdateRequest partialUpdate = ProjectUpdateRequest.builder()
                .description("Only description updated")
                .build();

        ProjectResponse updatedResponse = ProjectResponse.builder()
                .id(testProjectId)
                .userId(testUserId)
                .name("Test Project") // Name unchanged
                .description("Only description updated")
                .techTags(testProjectResponse.getTechTags())
                .externalLinks(testProjectResponse.getExternalLinks())
                .shipCount(5)
                .createdAt(testProjectResponse.getCreatedAt())
                .updatedAt(Instant.now())
                .build();

        when(projectService.updateProject(eq(testProjectId), eq(testUserId), any(ProjectUpdateRequest.class)))
                .thenReturn(updatedResponse);

        // Act & Assert
        mockMvc.perform(put("/api/projects/{projectId}", testProjectId)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(partialUpdate)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Test Project"))
                .andExpect(jsonPath("$.description").value("Only description updated"));

        verify(projectService).updateProject(eq(testProjectId), eq(testUserId), any(ProjectUpdateRequest.class));
    }
}
