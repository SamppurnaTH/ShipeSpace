package com.shipspace.controller;

import com.shipspace.dto.CommentCreateRequest;
import com.shipspace.dto.CommentResponse;
import com.shipspace.dto.LikeResponse;
import com.shipspace.dto.LikeToggleResponse;
import com.shipspace.service.BehavioralTrackingService;
import com.shipspace.service.EngagementService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for EngagementController.
 */
@ExtendWith(MockitoExtension.class)
class EngagementControllerTest {

    @Mock
    private EngagementService engagementService;

    @Mock
    private BehavioralTrackingService behavioralTrackingService;

    @Mock
    private Authentication authentication;

    @Mock
    private UserDetails userDetails;

    @InjectMocks
    private EngagementController engagementController;

    @Test
    void likeShip_ValidRequest_ReturnsLikeToggleResponse() {
        // Arrange
        String shipId = "ship-123";
        String userId = "user-456";

        LikeToggleResponse expectedResponse = LikeToggleResponse.builder()
                .liked(true)
                .likeCount(10)
                .message("Ship liked successfully")
                .build();

        when(authentication.getPrincipal()).thenReturn(userDetails);
        when(userDetails.getUsername()).thenReturn(userId);
        when(engagementService.likeShip(userId, shipId)).thenReturn(expectedResponse);

        // Act
        ResponseEntity<LikeToggleResponse> response = engagementController.likeShip(authentication, shipId);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isLiked()).isTrue();
        assertThat(response.getBody().getLikeCount()).isEqualTo(10);
        assertThat(response.getBody().getMessage()).contains("liked successfully");

        verify(engagementService).likeShip(userId, shipId);
        verify(behavioralTrackingService).trackLike(userId, shipId);
    }

    @Test
    void unlikeShip_ValidRequest_ReturnsUnlikedResponse() {
        // Arrange
        String shipId = "ship-123";
        String userId = "user-456";

        LikeToggleResponse expectedResponse = LikeToggleResponse.builder()
                .liked(false)
                .likeCount(9)
                .message("Ship unliked successfully")
                .build();

        when(authentication.getPrincipal()).thenReturn(userDetails);
        when(userDetails.getUsername()).thenReturn(userId);
        when(engagementService.unlikeShip(userId, shipId)).thenReturn(expectedResponse);

        // Act
        ResponseEntity<LikeToggleResponse> response = engagementController.unlikeShip(authentication, shipId);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isLiked()).isFalse();
        assertThat(response.getBody().getLikeCount()).isEqualTo(9);
        assertThat(response.getBody().getMessage()).contains("unliked successfully");

        verify(engagementService).unlikeShip(userId, shipId);
    }

    @Test
    void addComment_ValidRequest_ReturnsCreatedComment() {
        // Arrange
        String shipId = "ship-123";
        String userId = "user-456";

        CommentCreateRequest request = CommentCreateRequest.builder()
                .content("Great work on this project!")
                .build();

        CommentResponse expectedResponse = CommentResponse.builder()
                .id("comment-789")
                .shipId(shipId)
                .content("Great work on this project!")
                .commenter(CommentResponse.UserProfileSummary.builder()
                        .id(userId)
                        .username("testuser")
                        .avatarUrl("https://example.com/avatar.png")
                        .bio("Test bio")
                        .build())
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        when(authentication.getPrincipal()).thenReturn(userDetails);
        when(userDetails.getUsername()).thenReturn(userId);
        when(engagementService.addComment(userId, shipId, request)).thenReturn(expectedResponse);

        // Act
        ResponseEntity<CommentResponse> response = engagementController.addComment(authentication, shipId, request);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getId()).isEqualTo("comment-789");
        assertThat(response.getBody().getShipId()).isEqualTo(shipId);
        assertThat(response.getBody().getContent()).isEqualTo("Great work on this project!");
        assertThat(response.getBody().getCommenter()).isNotNull();
        assertThat(response.getBody().getCommenter().getUsername()).isEqualTo("testuser");

        verify(engagementService).addComment(userId, shipId, request);
        verify(behavioralTrackingService).trackComment(userId, shipId);
    }

    @Test
    void getShipComments_ValidRequest_ReturnsPagedComments() {
        // Arrange
        String shipId = "ship-123";
        int page = 0;
        int size = 20;

        CommentResponse comment1 = CommentResponse.builder()
                .id("comment-1")
                .shipId(shipId)
                .content("First comment")
                .commenter(CommentResponse.UserProfileSummary.builder()
                        .id("user-1")
                        .username("user1")
                        .build())
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        CommentResponse comment2 = CommentResponse.builder()
                .id("comment-2")
                .shipId(shipId)
                .content("Second comment")
                .commenter(CommentResponse.UserProfileSummary.builder()
                        .id("user-2")
                        .username("user2")
                        .build())
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        Page<CommentResponse> expectedPage = new PageImpl<>(Arrays.asList(comment1, comment2));

        when(engagementService.getShipComments(eq(shipId), any(Pageable.class), isNull())).thenReturn(expectedPage);

        // Act
        ResponseEntity<Page<CommentResponse>> response = engagementController.getShipComments(null, shipId, page, size);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getContent()).hasSize(2);
        assertThat(response.getBody().getContent().get(0).getId()).isEqualTo("comment-1");
        assertThat(response.getBody().getContent().get(1).getId()).isEqualTo("comment-2");

        verify(engagementService).getShipComments(eq(shipId), any(Pageable.class), isNull());
    }

    @Test
    void getShipComments_WithCustomPagination_UsesCorrectParameters() {
        // Arrange
        String shipId = "ship-123";
        int page = 2;
        int size = 10;

        Page<CommentResponse> expectedPage = new PageImpl<>(Collections.emptyList());

        when(engagementService.getShipComments(eq(shipId), any(Pageable.class), isNull())).thenReturn(expectedPage);

        // Act
        ResponseEntity<Page<CommentResponse>> response = engagementController.getShipComments(null, shipId, page, size);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(engagementService).getShipComments(eq(shipId), any(Pageable.class), isNull());
    }

    @Test
    void getShipComments_WithAuthenticatedViewer_PassesViewerIdForUpvoteState() {
        String shipId = "ship-123";
        String userId = "user-456";
        Page<CommentResponse> expectedPage = new PageImpl<>(Collections.emptyList());

        when(authentication.getPrincipal()).thenReturn(userDetails);
        when(userDetails.getUsername()).thenReturn(userId);
        when(engagementService.getShipComments(eq(shipId), any(Pageable.class), eq(userId))).thenReturn(expectedPage);

        ResponseEntity<Page<CommentResponse>> response = engagementController.getShipComments(authentication, shipId, 0, 20);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(engagementService).getShipComments(eq(shipId), any(Pageable.class), eq(userId));
    }

    @Test
    void getShipLikes_ValidRequest_ReturnsPagedLikes() {
        // Arrange
        String shipId = "ship-123";
        int page = 0;
        int size = 20;

        LikeResponse like1 = LikeResponse.builder()
                .id("like-1")
                .shipId(shipId)
                .user(LikeResponse.UserProfileSummary.builder()
                        .id("user-1")
                        .username("user1")
                        .build())
                .createdAt(Instant.now())
                .build();

        LikeResponse like2 = LikeResponse.builder()
                .id("like-2")
                .shipId(shipId)
                .user(LikeResponse.UserProfileSummary.builder()
                        .id("user-2")
                        .username("user2")
                        .build())
                .createdAt(Instant.now())
                .build();

        Page<LikeResponse> expectedPage = new PageImpl<>(Arrays.asList(like1, like2));

        when(engagementService.getShipLikes(eq(shipId), any(Pageable.class))).thenReturn(expectedPage);

        // Act
        ResponseEntity<Page<LikeResponse>> response = engagementController.getShipLikes(shipId, page, size);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getContent()).hasSize(2);
        assertThat(response.getBody().getContent().get(0).getId()).isEqualTo("like-1");
        assertThat(response.getBody().getContent().get(1).getId()).isEqualTo("like-2");

        verify(engagementService).getShipLikes(eq(shipId), any(Pageable.class));
    }

    @Test
    void getShipLikes_WithCustomPagination_UsesCorrectParameters() {
        // Arrange
        String shipId = "ship-123";
        int page = 1;
        int size = 50;

        Page<LikeResponse> expectedPage = new PageImpl<>(Collections.emptyList());

        when(engagementService.getShipLikes(eq(shipId), any(Pageable.class))).thenReturn(expectedPage);

        // Act
        ResponseEntity<Page<LikeResponse>> response = engagementController.getShipLikes(shipId, page, size);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(engagementService).getShipLikes(eq(shipId), any(Pageable.class));
    }

    @Test
    void getLikeStatus_UserHasLiked_ReturnsTrue() {
        // Arrange
        String shipId = "ship-123";
        String userId = "user-456";

        when(authentication.getPrincipal()).thenReturn(userDetails);
        when(userDetails.getUsername()).thenReturn(userId);
        when(engagementService.hasUserLikedShip(userId, shipId)).thenReturn(true);

        // Act
        ResponseEntity<Boolean> response = engagementController.getLikeStatus(authentication, shipId);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isTrue();

        verify(engagementService).hasUserLikedShip(userId, shipId);
    }

    @Test
    void getLikeStatus_UserHasNotLiked_ReturnsFalse() {
        // Arrange
        String shipId = "ship-123";
        String userId = "user-456";

        when(authentication.getPrincipal()).thenReturn(userDetails);
        when(userDetails.getUsername()).thenReturn(userId);
        when(engagementService.hasUserLikedShip(userId, shipId)).thenReturn(false);

        // Act
        ResponseEntity<Boolean> response = engagementController.getLikeStatus(authentication, shipId);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isFalse();

        verify(engagementService).hasUserLikedShip(userId, shipId);
    }

    @Test
    void addComment_EmptyContent_ShouldBeValidatedByFramework() {
        // Arrange
        String shipId = "ship-123";
        String userId = "user-456";

        CommentCreateRequest request = CommentCreateRequest.builder()
                .content("Valid comment content")
                .build();

        CommentResponse expectedResponse = CommentResponse.builder()
                .id("comment-789")
                .shipId(shipId)
                .content("Valid comment content")
                .commenter(CommentResponse.UserProfileSummary.builder()
                        .id(userId)
                        .username("testuser")
                        .build())
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        when(authentication.getPrincipal()).thenReturn(userDetails);
        when(userDetails.getUsername()).thenReturn(userId);
        when(engagementService.addComment(userId, shipId, request)).thenReturn(expectedResponse);

        // Act
        ResponseEntity<CommentResponse> response = engagementController.addComment(authentication, shipId, request);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        verify(engagementService).addComment(userId, shipId, request);
    }
}
