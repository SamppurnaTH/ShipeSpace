package com.shipspace.controller;

import com.shipspace.dto.CommentUpvoteToggleResponse;
import com.shipspace.dto.ProjectCreateRequest;
import com.shipspace.dto.ProjectResponse;
import com.shipspace.dto.ProjectUpdateRequest;
import com.shipspace.model.Comment;
import com.shipspace.model.ScoreEventType;
import com.shipspace.repository.CommentRepository;
import com.shipspace.repository.CommentUpvoteRepository;
import com.shipspace.repository.UserRepository;
import com.shipspace.service.*;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Integration tests for scoring hooks across all features.
 * 
 * <p>Tests that scoring events are triggered correctly for:
 * <ul>
 *   <li>PROJECT_CREATED - When a project is created</li>
 *   <li>PROJECT_UPDATED - When a project is updated</li>
 *   <li>COLLAB_ACCEPTED - When a collaboration is accepted (placeholder)</li>
 *   <li>QUALITY_COMMENT - When a comment reaches qualification thresholds</li>
 * </ul>
 * 
 * <p>These tests verify Requirements 10.1, 10.2, 10.3, 10.4 from the rank-level-progression spec.
 * 
 * @see <a href=".kiro/specs/rank-level-progression/requirements.md">Requirements 10.1, 10.2, 10.3, 10.4</a>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Scoring Hooks Integration Tests")
class ScoringHooksIntegrationTest {

    // Project-related mocks
    @Mock
    private ProjectService projectService;

    @Mock
    private Authentication authentication;

    @InjectMocks
    private ProjectController projectController;

    // Comment/Engagement-related mocks
    @Mock
    private CommentRepository commentRepository;

    @Mock
    private CommentUpvoteRepository commentUpvoteRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private CommentQualificationService commentQualificationService;

    @Mock
    private ShipScoreService shipScoreService;

    @InjectMocks
    private EngagementService engagementService;

    // Test data
    private String testUserId;
    private String testProjectId;
    private String testCommentId;
    private String testCollaborationId;
    private ProjectResponse testProjectResponse;
    private ProjectCreateRequest testCreateRequest;
    private ProjectUpdateRequest testUpdateRequest;
    private UserDetails userDetails;
    private Comment testComment;

    @BeforeEach
    void setUp() {
        testUserId = "user123";
        testProjectId = "project123";
        testCommentId = "comment123";
        testCollaborationId = "collab123";

        // Setup project test data
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

        // Setup comment test data
        testComment = Comment.builder()
                .id(testCommentId)
                .userId(testUserId)
                .shipId("ship123")
                .content("This is a test comment with more than fifty characters to qualify for scoring")
                .upvotes(1) // One upvote initially, will reach 2 after toggle
                .createdAt(Instant.now())
                .build();
    }

    // ========================================
    // PROJECT_CREATED Scoring Hook Tests
    // ========================================

    @Test
    @DisplayName("PROJECT_CREATED: Should trigger scoring when project is created")
    void projectCreation_ShouldTriggerProjectCreatedScoring() {
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

        // Verify scoring service was called with correct parameters
        verify(shipScoreService).applyScoreEvent(
                eq(testUserId),
                eq(ScoreEventType.PROJECT_CREATED),
                eq(testProjectId)
        );
    }

    @Test
    @DisplayName("PROJECT_CREATED: Should not fail project creation if scoring fails")
    void projectCreation_ShouldNotFailIfScoringFails() {
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

        // Verify scoring was attempted
        verify(shipScoreService).applyScoreEvent(
                eq(testUserId),
                eq(ScoreEventType.PROJECT_CREATED),
                eq(testProjectId)
        );
    }

    // ========================================
    // PROJECT_UPDATED Scoring Hook Tests
    // ========================================

    @Test
    @DisplayName("PROJECT_UPDATED: Should trigger scoring when project is updated")
    void projectUpdate_ShouldTriggerProjectUpdatedScoring() {
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

        // Verify scoring service was called with correct parameters
        verify(shipScoreService).applyScoreEvent(
                eq(testUserId),
                eq(ScoreEventType.PROJECT_UPDATED),
                eq(testProjectId)
        );
    }

    @Test
    @DisplayName("PROJECT_UPDATED: Should handle daily cap rejection gracefully")
    void projectUpdate_ShouldHandleDailyCapRejection() {
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

        // Verify scoring was attempted
        verify(shipScoreService).applyScoreEvent(
                eq(testUserId),
                eq(ScoreEventType.PROJECT_UPDATED),
                eq(testProjectId)
        );
    }

    @Test
    @DisplayName("PROJECT_UPDATED: Should not fail project update if scoring fails")
    void projectUpdate_ShouldNotFailIfScoringFails() {
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

        // Verify scoring was attempted
        verify(shipScoreService).applyScoreEvent(
                eq(testUserId),
                eq(ScoreEventType.PROJECT_UPDATED),
                eq(testProjectId)
        );
    }

    // ========================================
    // COLLAB_ACCEPTED Scoring Hook Tests
    // ========================================

    @Test
    @DisplayName("COLLAB_ACCEPTED: Placeholder test - Collaboration feature not yet implemented")
    void collaborationAcceptance_PlaceholderTest() {
        // This test serves as a placeholder for when the collaboration feature is implemented.
        // 
        // When implementing the collaboration acceptance endpoint, ensure:
        // 1. ShipScoreService.applyScoreEvent is called with COLLAB_ACCEPTED event type
        // 2. The collaboratorId (user being added as collaborator) is passed as userId
        // 3. The collaborationId is passed as entityId for idempotency
        // 4. Scoring failures do not fail the collaboration acceptance
        //
        // Expected implementation pattern:
        //
        // when(collaborationService.acceptCollaboration(...)).thenReturn(collaborationResponse);
        // when(shipScoreService.applyScoreEvent(
        //     eq(collaboratorId), 
        //     eq(ScoreEventType.COLLAB_ACCEPTED), 
        //     eq(collaborationId)
        // )).thenReturn(true);
        //
        // ResponseEntity<?> response = collaborationController.acceptCollaboration(...);
        //
        // verify(shipScoreService).applyScoreEvent(
        //     eq(collaboratorId),
        //     eq(ScoreEventType.COLLAB_ACCEPTED),
        //     eq(collaborationId)
        // );
        
        // For now, just verify the event type exists and has correct properties
        assertThat(ScoreEventType.COLLAB_ACCEPTED).isNotNull();
        assertThat(ScoreEventType.COLLAB_ACCEPTED.getPoints()).isEqualTo(10);
        assertThat(ScoreEventType.COLLAB_ACCEPTED.hasDailyCap()).isFalse();
    }

    // ========================================
    // QUALITY_COMMENT Scoring Hook Tests
    // ========================================

    @Test
    @DisplayName("QUALITY_COMMENT: Should trigger scoring when comment reaches qualification thresholds")
    void commentQualification_ShouldTriggerQualityCommentScoring() {
        // Arrange
        when(commentRepository.findById(testCommentId)).thenReturn(Optional.of(testComment));
        when(userRepository.findById(anyString())).thenReturn(Optional.of(new com.shipspace.model.User()));
        when(commentUpvoteRepository.findByUserIdAndCommentId(anyString(), eq(testCommentId)))
                .thenReturn(Optional.empty()); // No existing upvote
        
        // Comment was not qualified before (1 upvote), but will be after (2 upvotes)
        when(commentQualificationService.isQualified(any(Comment.class)))
                .thenAnswer(invocation -> {
                    Comment c = invocation.getArgument(0);
                    return c != null && c.getUpvotes() >= 2;
                });
        
        when(shipScoreService.applyScoreEvent(eq(testUserId), eq(ScoreEventType.QUALITY_COMMENT), eq(testCommentId)))
                .thenReturn(true);

        // Act
        CommentUpvoteToggleResponse response = engagementService.toggleCommentUpvote("upvoter123", testCommentId);

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.isUpvoted()).isTrue();
        assertThat(response.getUpvoteCount()).isEqualTo(2);
        assertThat(response.isQualifiedForScoring()).isTrue();

        // Verify scoring service was called with correct parameters
        verify(shipScoreService).applyScoreEvent(
                eq(testUserId),
                eq(ScoreEventType.QUALITY_COMMENT),
                eq(testCommentId)
        );
    }

    @Test
    @DisplayName("QUALITY_COMMENT: Should not trigger scoring if comment was already qualified")
    void commentQualification_ShouldNotTriggerIfAlreadyQualified() {
        // Arrange
        testComment.setUpvotes(5); // Already has many upvotes
        when(commentRepository.findById(testCommentId)).thenReturn(Optional.of(testComment));
        when(userRepository.findById(anyString())).thenReturn(Optional.of(new com.shipspace.model.User()));
        when(commentUpvoteRepository.findByUserIdAndCommentId(anyString(), eq(testCommentId)))
                .thenReturn(Optional.empty()); // No existing upvote
        
        // Comment was already qualified before and after
        when(commentQualificationService.isQualified(any(Comment.class)))
                .thenReturn(true);

        // Act
        CommentUpvoteToggleResponse response = engagementService.toggleCommentUpvote("upvoter123", testCommentId);

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.isUpvoted()).isTrue();
        assertThat(response.isQualifiedForScoring()).isFalse(); // Should be false because it was already qualified

        // Verify scoring service was NOT called (comment was already qualified)
        verify(shipScoreService, never()).applyScoreEvent(anyString(), any(ScoreEventType.class), anyString());
    }

    @Test
    @DisplayName("QUALITY_COMMENT: Should not trigger scoring if comment doesn't meet length requirement")
    void commentQualification_ShouldNotTriggerIfLengthTooShort() {
        // Arrange
        testComment.setContent("Short comment"); // Less than 50 characters
        testComment.setUpvotes(1);
        when(commentRepository.findById(testCommentId)).thenReturn(Optional.of(testComment));
        when(userRepository.findById(anyString())).thenReturn(Optional.of(new com.shipspace.model.User()));
        when(commentUpvoteRepository.findByUserIdAndCommentId(anyString(), eq(testCommentId)))
                .thenReturn(Optional.empty()); // No existing upvote
        
        // Comment doesn't qualify (too short)
        when(commentQualificationService.isQualified(any(Comment.class)))
                .thenReturn(false);

        // Act
        CommentUpvoteToggleResponse response = engagementService.toggleCommentUpvote("upvoter123", testCommentId);

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.isUpvoted()).isTrue();
        assertThat(response.isQualifiedForScoring()).isFalse();

        // Verify scoring service was NOT called
        verify(shipScoreService, never()).applyScoreEvent(anyString(), any(ScoreEventType.class), anyString());
    }

    @Test
    @DisplayName("QUALITY_COMMENT: Should not fail upvote if scoring fails")
    void commentQualification_ShouldNotFailUpvoteIfScoringFails() {
        // Arrange
        when(commentRepository.findById(testCommentId)).thenReturn(Optional.of(testComment));
        when(userRepository.findById(anyString())).thenReturn(Optional.of(new com.shipspace.model.User()));
        when(commentUpvoteRepository.findByUserIdAndCommentId(anyString(), eq(testCommentId)))
                .thenReturn(Optional.empty()); // No existing upvote
        
        // Comment becomes qualified
        when(commentQualificationService.isQualified(any(Comment.class)))
                .thenAnswer(invocation -> {
                    Comment c = invocation.getArgument(0);
                    return c != null && c.getUpvotes() >= 2;
                });
        
        // Scoring service throws exception
        when(shipScoreService.applyScoreEvent(eq(testUserId), eq(ScoreEventType.QUALITY_COMMENT), eq(testCommentId)))
                .thenThrow(new RuntimeException("Scoring service error"));

        // Act
        CommentUpvoteToggleResponse response = engagementService.toggleCommentUpvote("upvoter123", testCommentId);

        // Assert - Upvote should still succeed
        assertThat(response).isNotNull();
        assertThat(response.isUpvoted()).isTrue();
        assertThat(response.getUpvoteCount()).isEqualTo(2);
        assertThat(response.isQualifiedForScoring()).isFalse(); // False because scoring failed

        // Verify scoring was attempted
        verify(shipScoreService).applyScoreEvent(
                eq(testUserId),
                eq(ScoreEventType.QUALITY_COMMENT),
                eq(testCommentId)
        );
    }

    @Test
    @DisplayName("QUALITY_COMMENT: Should handle scoring rejection gracefully")
    void commentQualification_ShouldHandleScoringRejection() {
        // Arrange
        when(commentRepository.findById(testCommentId)).thenReturn(Optional.of(testComment));
        when(userRepository.findById(anyString())).thenReturn(Optional.of(new com.shipspace.model.User()));
        when(commentUpvoteRepository.findByUserIdAndCommentId(anyString(), eq(testCommentId)))
                .thenReturn(Optional.empty()); // No existing upvote
        
        // Comment becomes qualified
        when(commentQualificationService.isQualified(any(Comment.class)))
                .thenAnswer(invocation -> {
                    Comment c = invocation.getArgument(0);
                    return c != null && c.getUpvotes() >= 2;
                });
        
        // Scoring service returns false (duplicate or cap reached)
        when(shipScoreService.applyScoreEvent(eq(testUserId), eq(ScoreEventType.QUALITY_COMMENT), eq(testCommentId)))
                .thenReturn(false);

        // Act
        CommentUpvoteToggleResponse response = engagementService.toggleCommentUpvote("upvoter123", testCommentId);

        // Assert - Upvote should still succeed
        assertThat(response).isNotNull();
        assertThat(response.isUpvoted()).isTrue();
        assertThat(response.getUpvoteCount()).isEqualTo(2);
        assertThat(response.isQualifiedForScoring()).isFalse(); // False because score was not applied

        // Verify scoring was attempted
        verify(shipScoreService).applyScoreEvent(
                eq(testUserId),
                eq(ScoreEventType.QUALITY_COMMENT),
                eq(testCommentId)
        );
    }

    @Test
    @DisplayName("QUALITY_COMMENT: Should use correct entityId for idempotency")
    void commentQualification_ShouldUseCommentIdAsEntityId() {
        // Arrange
        when(commentRepository.findById(testCommentId)).thenReturn(Optional.of(testComment));
        when(userRepository.findById(anyString())).thenReturn(Optional.of(new com.shipspace.model.User()));
        when(commentUpvoteRepository.findByUserIdAndCommentId(anyString(), eq(testCommentId)))
                .thenReturn(Optional.empty());
        
        when(commentQualificationService.isQualified(any(Comment.class)))
                .thenAnswer(invocation -> {
                    Comment c = invocation.getArgument(0);
                    return c != null && c.getUpvotes() >= 2;
                });
        
        when(shipScoreService.applyScoreEvent(anyString(), any(ScoreEventType.class), anyString()))
                .thenReturn(true);

        // Act
        engagementService.toggleCommentUpvote("upvoter123", testCommentId);

        // Assert - Verify commentId is used as entityId for idempotency
        verify(shipScoreService).applyScoreEvent(
                eq(testUserId),
                eq(ScoreEventType.QUALITY_COMMENT),
                eq(testCommentId) // commentId should be used as entityId
        );
    }
}
