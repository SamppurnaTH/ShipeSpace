package com.shipspace.service;

import com.shipspace.dto.CommentCreateRequest;
import com.shipspace.dto.CommentResponse;
import com.shipspace.dto.CommentUpvoteToggleResponse;
import com.shipspace.dto.LikeResponse;
import com.shipspace.dto.LikeToggleResponse;
import com.shipspace.exception.ResourceNotFoundException;
import com.shipspace.model.Comment;
import com.shipspace.model.CommentUpvote;
import com.shipspace.model.Like;
import com.shipspace.model.ScoreEventType;
import com.shipspace.model.Ship;
import com.shipspace.model.User;
import com.shipspace.repository.CommentRepository;
import com.shipspace.repository.CommentUpvoteRepository;
import com.shipspace.repository.LikeRepository;
import com.shipspace.repository.ShipCountRepository;
import com.shipspace.repository.ShipRepository;
import com.shipspace.repository.UserRepository;
import com.shipspace.util.InputSanitizer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.time.Instant;
import java.util.Arrays;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for EngagementService.
 * Tests like toggling, comment creation, and engagement data retrieval.
 * 
 * Requirements tested: 8.1, 8.2, 8.3, 8.4, 8.5
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EngagementServiceTest {

    @Mock
    private LikeRepository likeRepository;

    @Mock
    private CommentRepository commentRepository;

    @Mock
    private CommentUpvoteRepository commentUpvoteRepository;

    @Mock
    private ShipRepository shipRepository;

    @Mock
    private ShipCountRepository shipCountRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private CommentQualificationService commentQualificationService;

    @Mock
    private ShipScoreService shipScoreService;

    @Mock
    private MongoTemplate mongoTemplate;

    @Mock
    private NotificationDispatchService notificationDispatchService;

    @Mock
    private InputSanitizer inputSanitizer;

    @InjectMocks
    private EngagementService engagementService;

    @BeforeEach
    void setUp() {
        lenient().when(shipRepository.existsById("ship-456")).thenReturn(true);
        lenient().when(userRepository.existsById("user-123")).thenReturn(true);
        lenient().when(inputSanitizer.sanitize(any(String.class), anyInt()))
                .thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(shipCountRepository.incrementLikeCount(eq("ship-456"), eq(1)))
                .thenReturn(Ship.builder().id("ship-456").likeCount(6).build());
        lenient().when(shipCountRepository.incrementLikeCount(eq("ship-456"), eq(-1)))
                .thenReturn(Ship.builder().id("ship-456").likeCount(4).build());
        lenient().when(shipCountRepository.incrementCommentCount(eq("ship-456"), eq(1)))
                .thenReturn(Ship.builder().id("ship-456").commentCount(4).build());
        lenient().when(mongoTemplate.findAndModify(any(), any(), any(), eq(Comment.class)))
                .thenAnswer(invocation -> Comment.builder().id("comment-456").upvotes(1).build());
        lenient().when(likeRepository.deleteByUserIdAndShipId("user-123", "ship-456")).thenReturn(1L);
        lenient().when(commentUpvoteRepository.deleteByUserIdAndCommentId("user-123", "comment-456")).thenReturn(1L);
    }

    @Test
    void toggleLike_WhenNotLiked_AddsLike() {
        // Arrange
        String userId = "user-123";
        String shipId = "ship-456";

        Ship ship = Ship.builder()
                .id(shipId)
                .userId("owner-id")
                .projectId("project-id")
                .content("Test ship")
                .likeCount(5)
                .commentCount(0)
                .build();

        User user = User.builder()
                .id(userId)
                .username("testuser")
                .build();

        when(shipRepository.findById(shipId)).thenReturn(Optional.of(ship));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(likeRepository.findByUserIdAndShipId(userId, shipId)).thenReturn(Optional.empty());
        when(likeRepository.save(any(Like.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(shipRepository.save(any(Ship.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        LikeToggleResponse response = engagementService.toggleLike(userId, shipId);

        // Assert
        assertThat(response.isLiked()).isTrue();
        assertThat(response.getLikeCount()).isEqualTo(6);
        assertThat(response.getMessage()).contains("liked successfully");

        verify(likeRepository).save(any(Like.class));
        verify(shipCountRepository).incrementLikeCount(shipId, 1);
    }

    @Test
    void toggleLike_WhenAlreadyLiked_RemovesLike() {
        // Arrange
        String userId = "user-123";
        String shipId = "ship-456";

        Ship ship = Ship.builder()
                .id(shipId)
                .userId("owner-id")
                .projectId("project-id")
                .content("Test ship")
                .likeCount(5)
                .commentCount(0)
                .build();

        User user = User.builder()
                .id(userId)
                .username("testuser")
                .build();

        Like existingLike = Like.builder()
                .id("like-id")
                .userId(userId)
                .shipId(shipId)
                .createdAt(Instant.now())
                .build();

        when(shipRepository.findById(shipId)).thenReturn(Optional.of(ship));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(likeRepository.findByUserIdAndShipId(userId, shipId)).thenReturn(Optional.of(existingLike));
        when(shipRepository.save(any(Ship.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        LikeToggleResponse response = engagementService.toggleLike(userId, shipId);

        // Assert
        assertThat(response.isLiked()).isFalse();
        assertThat(response.getLikeCount()).isEqualTo(4);
        assertThat(response.getMessage()).contains("unliked successfully");

        verify(likeRepository).deleteByUserIdAndShipId(userId, shipId);
        verify(shipCountRepository).incrementLikeCount(shipId, -1);
    }

    @Test
    void toggleLike_WhenRaceConditionOccurs_HandlesGracefully() {
        // Arrange
        String userId = "user-123";
        String shipId = "ship-456";

        Ship ship = Ship.builder()
                .id(shipId)
                .userId("owner-id")
                .projectId("project-id")
                .content("Test ship")
                .likeCount(5)
                .commentCount(0)
                .build();

        User user = User.builder()
                .id(userId)
                .username("testuser")
                .build();

        when(shipRepository.findById(shipId)).thenReturn(Optional.of(ship));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(likeRepository.findByUserIdAndShipId(userId, shipId)).thenReturn(Optional.empty());
        when(likeRepository.save(any(Like.class))).thenThrow(new DuplicateKeyException("Duplicate key"));
        when(shipRepository.save(any(Ship.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        LikeToggleResponse response = engagementService.toggleLike(userId, shipId);

        // Assert - Race condition should result in unlike
        assertThat(response.isLiked()).isFalse();
        assertThat(response.getLikeCount()).isEqualTo(4);

        verify(likeRepository).deleteByUserIdAndShipId(userId, shipId);
        verify(shipCountRepository).incrementLikeCount(shipId, -1);
    }

    @Test
    void toggleLike_WhenShipNotFound_ThrowsException() {
        // Arrange
        String userId = "user-123";
        String shipId = "nonexistent-ship";

        when(shipRepository.findById(shipId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> engagementService.toggleLike(userId, shipId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Ship not found");

        verify(likeRepository, never()).save(any());
        verify(likeRepository, never()).deleteByUserIdAndShipId(any(), any());
    }

    @Test
    void toggleLike_WhenUserNotFound_ThrowsException() {
        // Arrange
        String userId = "nonexistent-user";
        String shipId = "ship-456";

        Ship ship = Ship.builder()
                .id(shipId)
                .userId("owner-id")
                .projectId("project-id")
                .content("Test ship")
                .likeCount(5)
                .build();

        when(shipRepository.findById(shipId)).thenReturn(Optional.of(ship));
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> engagementService.toggleLike(userId, shipId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("User not found");

        verify(likeRepository, never()).save(any());
    }

    @Test
    void toggleLike_WhenLikeCountIsZero_DoesNotGoNegative() {
        // Arrange
        String userId = "user-123";
        String shipId = "ship-456";

        Ship ship = Ship.builder()
                .id(shipId)
                .userId("owner-id")
                .projectId("project-id")
                .content("Test ship")
                .likeCount(0)
                .commentCount(0)
                .build();

        User user = User.builder()
                .id(userId)
                .username("testuser")
                .build();

        Like existingLike = Like.builder()
                .id("like-id")
                .userId(userId)
                .shipId(shipId)
                .createdAt(Instant.now())
                .build();

        when(shipRepository.findById(shipId)).thenReturn(Optional.of(ship));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(likeRepository.findByUserIdAndShipId(userId, shipId)).thenReturn(Optional.of(existingLike));
        when(shipCountRepository.incrementLikeCount(shipId, -1))
                .thenReturn(Ship.builder().id(shipId).likeCount(0).build());
        when(shipRepository.save(any(Ship.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        LikeToggleResponse response = engagementService.toggleLike(userId, shipId);

        // Assert
        assertThat(response.getLikeCount()).isEqualTo(0);
        verify(shipCountRepository).incrementLikeCount(shipId, -1);
    }

    @Test
    void addComment_ValidRequest_CreatesComment() {
        // Arrange
        String userId = "user-123";
        String shipId = "ship-456";
        String content = "Great work on this project!";

        CommentCreateRequest request = CommentCreateRequest.builder()
                .content(content)
                .build();

        Ship ship = Ship.builder()
                .id(shipId)
                .userId("owner-id")
                .projectId("project-id")
                .content("Test ship")
                .likeCount(5)
                .commentCount(3)
                .build();

        User user = User.builder()
                .id(userId)
                .username("testuser")
                .avatarUrl("https://example.com/avatar.png")
                .bio("Test bio")
                .build();

        Comment savedComment = Comment.builder()
                .id("comment-id")
                .userId(userId)
                .shipId(shipId)
                .content(content)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        when(shipRepository.findById(shipId)).thenReturn(Optional.of(ship));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(commentRepository.save(any(Comment.class))).thenReturn(savedComment);
        when(shipRepository.save(any(Ship.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        CommentResponse response = engagementService.addComment(userId, shipId, request);

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo("comment-id");
        assertThat(response.getShipId()).isEqualTo(shipId);
        assertThat(response.getUserId()).isEqualTo(userId);
        assertThat(response.getContent()).isEqualTo(content);
        assertThat(response.getUpvotes()).isEqualTo(0);
        assertThat(response.getUpvotedByCurrentUser()).isFalse();
        assertThat(response.getCommenter()).isNotNull();
        assertThat(response.getCommenter().getId()).isEqualTo(userId);
        assertThat(response.getCommenter().getUsername()).isEqualTo("testuser");

        verify(commentRepository).save(any(Comment.class));
        verify(shipCountRepository).incrementCommentCount(shipId, 1);
    }

    @Test
    void addComment_WhenShipNotFound_ThrowsException() {
        // Arrange
        String userId = "user-123";
        String shipId = "nonexistent-ship";

        CommentCreateRequest request = CommentCreateRequest.builder()
                .content("Test comment")
                .build();

        when(shipRepository.findById(shipId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> engagementService.addComment(userId, shipId, request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Ship not found");

        verify(commentRepository, never()).save(any());
    }

    @Test
    void addComment_WhenUserNotFound_ThrowsException() {
        // Arrange
        String userId = "nonexistent-user";
        String shipId = "ship-456";

        CommentCreateRequest request = CommentCreateRequest.builder()
                .content("Test comment")
                .build();

        Ship ship = Ship.builder()
                .id(shipId)
                .userId("owner-id")
                .projectId("project-id")
                .content("Test ship")
                .build();

        when(shipRepository.findById(shipId)).thenReturn(Optional.of(ship));
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> engagementService.addComment(userId, shipId, request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("User not found");

        verify(commentRepository, never()).save(any());
    }

    @Test
    void getShipComments_ValidShip_ReturnsPagedComments() {
        // Arrange
        String shipId = "ship-456";
        Pageable pageable = PageRequest.of(0, 20);

        Ship ship = Ship.builder()
                .id(shipId)
                .userId("owner-id")
                .projectId("project-id")
                .content("Test ship")
                .build();

        User user1 = User.builder()
                .id("user-1")
                .username("user1")
                .avatarUrl("https://example.com/avatar1.png")
                .bio("User 1 bio")
                .build();

        User user2 = User.builder()
                .id("user-2")
                .username("user2")
                .avatarUrl("https://example.com/avatar2.png")
                .bio("User 2 bio")
                .build();

        Comment comment1 = Comment.builder()
                .id("comment-1")
                .userId("user-1")
                .shipId(shipId)
                .content("First comment")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        Comment comment2 = Comment.builder()
                .id("comment-2")
                .userId("user-2")
                .shipId(shipId)
                .content("Second comment")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        Page<Comment> commentsPage = new PageImpl<>(Arrays.asList(comment1, comment2));

        when(shipRepository.findById(shipId)).thenReturn(Optional.of(ship));
        when(commentRepository.findByShipId(shipId, pageable)).thenReturn(commentsPage);
        when(userRepository.findAllById(any())).thenReturn(Arrays.asList(user1, user2));

        // Act
        Page<CommentResponse> response = engagementService.getShipComments(shipId, pageable);

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.getContent()).hasSize(2);
        assertThat(response.getContent().get(0).getId()).isEqualTo("comment-1");
        assertThat(response.getContent().get(0).getUserId()).isEqualTo("user-1");
        assertThat(response.getContent().get(0).getUpvotes()).isEqualTo(0);
        assertThat(response.getContent().get(0).getUpvotedByCurrentUser()).isFalse();
        assertThat(response.getContent().get(0).getCommenter().getUsername()).isEqualTo("user1");
        assertThat(response.getContent().get(1).getId()).isEqualTo("comment-2");
        assertThat(response.getContent().get(1).getCommenter().getUsername()).isEqualTo("user2");

        verify(commentRepository).findByShipId(shipId, pageable);
    }

    @Test
    void getShipComments_WithCurrentUser_EnrichesUpvotedState() {
        String shipId = "ship-456";
        Pageable pageable = PageRequest.of(0, 20);
        Comment comment = Comment.builder()
                .id("comment-1")
                .userId("user-1")
                .shipId(shipId)
                .content("First comment")
                .upvotes(2)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        User user = User.builder().id("user-1").username("user1").build();
        CommentUpvote upvote = CommentUpvote.builder()
                .id("upvote-1")
                .userId("viewer-1")
                .commentId("comment-1")
                .build();

        when(commentRepository.findByShipId(shipId, pageable))
                .thenReturn(new PageImpl<>(java.util.List.of(comment), pageable, 1));
        when(userRepository.findAllById(any())).thenReturn(java.util.List.of(user));
        when(commentUpvoteRepository.findByUserIdAndCommentIdIn(eq("viewer-1"), any()))
                .thenReturn(java.util.List.of(upvote));

        Page<CommentResponse> response = engagementService.getShipComments(shipId, pageable, "viewer-1");

        assertThat(response.getContent()).hasSize(1);
        assertThat(response.getContent().get(0).getUpvotes()).isEqualTo(2);
        assertThat(response.getContent().get(0).getUpvotedByCurrentUser()).isTrue();
    }

    @Test
    void getShipComments_WhenShipNotFound_ThrowsException() {
        // Arrange
        String shipId = "nonexistent-ship";
        Pageable pageable = PageRequest.of(0, 20);

        when(shipRepository.findById(shipId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> engagementService.getShipComments(shipId, pageable))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Ship not found");

        verify(commentRepository, never()).findByShipId(any(), any());
    }

    @Test
    void getShipLikes_ValidShip_ReturnsPagedLikes() {
        // Arrange
        String shipId = "ship-456";
        Pageable pageable = PageRequest.of(0, 20);

        Ship ship = Ship.builder()
                .id(shipId)
                .userId("owner-id")
                .projectId("project-id")
                .content("Test ship")
                .build();

        User user1 = User.builder()
                .id("user-1")
                .username("user1")
                .avatarUrl("https://example.com/avatar1.png")
                .bio("User 1 bio")
                .build();

        User user2 = User.builder()
                .id("user-2")
                .username("user2")
                .avatarUrl("https://example.com/avatar2.png")
                .bio("User 2 bio")
                .build();

        Like like1 = Like.builder()
                .id("like-1")
                .userId("user-1")
                .shipId(shipId)
                .createdAt(Instant.now())
                .build();

        Like like2 = Like.builder()
                .id("like-2")
                .userId("user-2")
                .shipId(shipId)
                .createdAt(Instant.now())
                .build();

        Page<Like> likesPage = new PageImpl<>(Arrays.asList(like1, like2));

        when(shipRepository.findById(shipId)).thenReturn(Optional.of(ship));
        when(likeRepository.findByShipId(shipId, pageable)).thenReturn(likesPage);
        when(userRepository.findAllById(any())).thenReturn(Arrays.asList(user1, user2));

        // Act
        Page<LikeResponse> response = engagementService.getShipLikes(shipId, pageable);

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.getContent()).hasSize(2);
        assertThat(response.getContent().get(0).getId()).isEqualTo("like-1");
        assertThat(response.getContent().get(0).getUser().getUsername()).isEqualTo("user1");
        assertThat(response.getContent().get(1).getId()).isEqualTo("like-2");
        assertThat(response.getContent().get(1).getUser().getUsername()).isEqualTo("user2");

        verify(likeRepository).findByShipId(shipId, pageable);
    }

    @Test
    void getShipLikes_WhenShipNotFound_ThrowsException() {
        // Arrange
        String shipId = "nonexistent-ship";
        Pageable pageable = PageRequest.of(0, 20);

        when(shipRepository.findById(shipId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> engagementService.getShipLikes(shipId, pageable))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Ship not found");

        verify(likeRepository, never()).findByShipId(any(), any());
    }

    @Test
    void hasUserLikedShip_WhenLiked_ReturnsTrue() {
        // Arrange
        String userId = "user-123";
        String shipId = "ship-456";

        when(likeRepository.existsByUserIdAndShipId(userId, shipId)).thenReturn(true);

        // Act
        boolean result = engagementService.hasUserLikedShip(userId, shipId);

        // Assert
        assertThat(result).isTrue();
        verify(likeRepository).existsByUserIdAndShipId(userId, shipId);
    }

    @Test
    void hasUserLikedShip_WhenNotLiked_ReturnsFalse() {
        // Arrange
        String userId = "user-123";
        String shipId = "ship-456";

        when(likeRepository.existsByUserIdAndShipId(userId, shipId)).thenReturn(false);

        // Act
        boolean result = engagementService.hasUserLikedShip(userId, shipId);

        // Assert
        assertThat(result).isFalse();
        verify(likeRepository).existsByUserIdAndShipId(userId, shipId);
    }

    @Test
    void getShipComments_WhenUserDeleted_HandlesGracefully() {
        // Arrange
        String shipId = "ship-456";
        Pageable pageable = PageRequest.of(0, 20);

        Ship ship = Ship.builder()
                .id(shipId)
                .userId("owner-id")
                .projectId("project-id")
                .content("Test ship")
                .build();

        Comment comment = Comment.builder()
                .id("comment-1")
                .userId("deleted-user")
                .shipId(shipId)
                .content("Comment from deleted user")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        Page<Comment> commentsPage = new PageImpl<>(Arrays.asList(comment));

        when(shipRepository.findById(shipId)).thenReturn(Optional.of(ship));
        when(commentRepository.findByShipId(shipId, pageable)).thenReturn(commentsPage);
        when(userRepository.findAllById(any())).thenReturn(Arrays.asList());

        // Act
        Page<CommentResponse> response = engagementService.getShipComments(shipId, pageable);

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.getContent()).hasSize(1);
        assertThat(response.getContent().get(0).getCommenter()).isNull();
    }

    @Test
    void getShipLikes_WhenUserDeleted_HandlesGracefully() {
        // Arrange
        String shipId = "ship-456";
        Pageable pageable = PageRequest.of(0, 20);

        Ship ship = Ship.builder()
                .id(shipId)
                .userId("owner-id")
                .projectId("project-id")
                .content("Test ship")
                .build();

        Like like = Like.builder()
                .id("like-1")
                .userId("deleted-user")
                .shipId(shipId)
                .createdAt(Instant.now())
                .build();

        Page<Like> likesPage = new PageImpl<>(Arrays.asList(like));

        when(shipRepository.findById(shipId)).thenReturn(Optional.of(ship));
        when(likeRepository.findByShipId(shipId, pageable)).thenReturn(likesPage);
        when(userRepository.findAllById(any())).thenReturn(Arrays.asList());

        // Act
        Page<LikeResponse> response = engagementService.getShipLikes(shipId, pageable);

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.getContent()).hasSize(1);
        assertThat(response.getContent().get(0).getUser()).isNull();
    }

    // ========== Comment Upvote Tests ==========

    @Test
    void toggleCommentUpvote_WhenNotUpvoted_AddsUpvote() {
        // Arrange
        String userId = "user-123";
        String commentId = "comment-456";

        Comment comment = Comment.builder()
                .id(commentId)
                .userId("commenter-id")
                .shipId("ship-id")
                .content("This is a test comment")
                .upvotes(0)
                .build();

        User user = User.builder()
                .id(userId)
                .username("testuser")
                .build();

        when(commentRepository.findById(commentId)).thenReturn(Optional.of(comment));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(commentUpvoteRepository.findByUserIdAndCommentId(userId, commentId)).thenReturn(Optional.empty());
        when(commentUpvoteRepository.save(any(CommentUpvote.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(commentRepository.save(any(Comment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(commentQualificationService.isQualified(any(Comment.class))).thenReturn(false);

        // Act
        CommentUpvoteToggleResponse response = engagementService.toggleCommentUpvote(userId, commentId);

        // Assert
        assertThat(response.isUpvoted()).isTrue();
        assertThat(response.getUpvoteCount()).isEqualTo(1);
        assertThat(response.isQualifiedForScoring()).isFalse();

        verify(commentUpvoteRepository).save(any(CommentUpvote.class));
        verify(mongoTemplate).findAndModify(any(), any(), any(), eq(Comment.class));
        verify(shipScoreService, never()).applyScoreEvent(any(), any(), any());
    }

    @Test
    void toggleCommentUpvote_WhenAlreadyUpvoted_RemovesUpvote() {
        // Arrange
        String userId = "user-123";
        String commentId = "comment-456";

        Comment comment = Comment.builder()
                .id(commentId)
                .userId("commenter-id")
                .shipId("ship-id")
                .content("This is a test comment")
                .upvotes(3)
                .build();

        User user = User.builder()
                .id(userId)
                .username("testuser")
                .build();

        CommentUpvote existingUpvote = CommentUpvote.builder()
                .id("upvote-id")
                .userId(userId)
                .commentId(commentId)
                .build();

        when(commentRepository.findById(commentId)).thenReturn(Optional.of(comment));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(commentUpvoteRepository.findByUserIdAndCommentId(userId, commentId)).thenReturn(Optional.of(existingUpvote));
        when(mongoTemplate.findAndModify(any(), any(), any(), eq(Comment.class)))
                .thenReturn(Comment.builder().id(commentId).upvotes(2).build());
        when(commentRepository.save(any(Comment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        CommentUpvoteToggleResponse response = engagementService.toggleCommentUpvote(userId, commentId);

        // Assert
        assertThat(response.isUpvoted()).isFalse();
        assertThat(response.getUpvoteCount()).isEqualTo(2);
        assertThat(response.isQualifiedForScoring()).isFalse();

        verify(commentUpvoteRepository).deleteByUserIdAndCommentId(userId, commentId);
        verify(mongoTemplate).findAndModify(any(), any(), any(), eq(Comment.class));
        verify(shipScoreService, never()).applyScoreEvent(any(), any(), any());
    }

    @Test
    void removeCommentUpvote_WhenConcurrentDeleteAlreadyRemoved_DoesNotDecrement() {
        String userId = "user-123";
        String commentId = "comment-456";
        Comment comment = Comment.builder()
                .id(commentId)
                .userId("commenter-id")
                .shipId("ship-id")
                .content("Test comment")
                .upvotes(3)
                .build();
        CommentUpvote existingUpvote = CommentUpvote.builder()
                .id("upvote-id")
                .userId(userId)
                .commentId(commentId)
                .build();

        when(commentRepository.findById(commentId)).thenReturn(Optional.of(comment));
        when(commentUpvoteRepository.findByUserIdAndCommentId(userId, commentId)).thenReturn(Optional.of(existingUpvote));
        when(commentUpvoteRepository.deleteByUserIdAndCommentId(userId, commentId)).thenReturn(0L);

        CommentUpvoteToggleResponse response = engagementService.removeCommentUpvote(userId, commentId);

        assertThat(response.isUpvoted()).isFalse();
        assertThat(response.getUpvoteCount()).isEqualTo(3);
        verify(mongoTemplate, never()).findAndModify(any(), any(), any(), eq(Comment.class));
    }

    @Test
    void toggleCommentUpvote_WhenCommentQualifies_TriggersScoring() {
        // Arrange
        String userId = "user-123";
        String commentId = "comment-456";
        String commenterId = "commenter-id";

        // Comment with 50+ chars and 1 upvote (will become 2 after this upvote)
        Comment comment = Comment.builder()
                .id(commentId)
                .userId(commenterId)
                .shipId("ship-id")
                .content("This is a long comment with more than fifty characters to qualify for scoring")
                .upvotes(1)
                .build();

        User user = User.builder()
                .id(userId)
                .username("testuser")
                .build();

        when(commentRepository.findById(commentId)).thenReturn(Optional.of(comment));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(commentUpvoteRepository.findByUserIdAndCommentId(userId, commentId)).thenReturn(Optional.empty());
        when(commentUpvoteRepository.save(any(CommentUpvote.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(mongoTemplate.findAndModify(any(), any(), any(), eq(Comment.class)))
                .thenReturn(Comment.builder()
                        .id(commentId)
                        .userId(commenterId)
                        .shipId("ship-id")
                        .content(comment.getContent())
                        .upvotes(2)
                        .build());
        when(commentRepository.save(any(Comment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        
        // First call (before upvote): not qualified (only 1 upvote)
        // Second call (after upvote): qualified (2 upvotes)
        when(commentQualificationService.isQualified(any(Comment.class)))
                .thenReturn(false)  // Before upvote
                .thenReturn(true);  // After upvote
        
        when(shipScoreService.applyScoreEvent(commenterId, ScoreEventType.QUALITY_COMMENT, commentId))
                .thenReturn(true);

        // Act
        CommentUpvoteToggleResponse response = engagementService.toggleCommentUpvote(userId, commentId);

        // Assert
        assertThat(response.isUpvoted()).isTrue();
        assertThat(response.getUpvoteCount()).isEqualTo(2);
        assertThat(response.isQualifiedForScoring()).isTrue();

        verify(commentUpvoteRepository).save(any(CommentUpvote.class));
        verify(mongoTemplate).findAndModify(any(), any(), any(), eq(Comment.class));
        verify(shipScoreService).applyScoreEvent(commenterId, ScoreEventType.QUALITY_COMMENT, commentId);
    }

    @Test
    void toggleCommentUpvote_WhenCommentAlreadyQualified_DoesNotTriggerScoring() {
        // Arrange
        String userId = "user-123";
        String commentId = "comment-456";
        String commenterId = "commenter-id";

        // Comment already qualified (50+ chars and 2+ upvotes)
        Comment comment = Comment.builder()
                .id(commentId)
                .userId(commenterId)
                .shipId("ship-id")
                .content("This is a long comment with more than fifty characters to qualify for scoring")
                .upvotes(3)
                .build();

        User user = User.builder()
                .id(userId)
                .username("testuser")
                .build();

        when(commentRepository.findById(commentId)).thenReturn(Optional.of(comment));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(commentUpvoteRepository.findByUserIdAndCommentId(userId, commentId)).thenReturn(Optional.empty());
        when(commentUpvoteRepository.save(any(CommentUpvote.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(mongoTemplate.findAndModify(any(), any(), any(), eq(Comment.class)))
                .thenReturn(Comment.builder()
                        .id(commentId)
                        .userId(commenterId)
                        .shipId("ship-id")
                        .content(comment.getContent())
                        .upvotes(4)
                        .build());
        when(commentRepository.save(any(Comment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        
        // Already qualified before and after upvote
        when(commentQualificationService.isQualified(any(Comment.class)))
                .thenReturn(true)   // Before upvote
                .thenReturn(true);  // After upvote

        // Act
        CommentUpvoteToggleResponse response = engagementService.toggleCommentUpvote(userId, commentId);

        // Assert
        assertThat(response.isUpvoted()).isTrue();
        assertThat(response.getUpvoteCount()).isEqualTo(4);
        assertThat(response.isQualifiedForScoring()).isFalse(); // Not newly qualified

        verify(commentUpvoteRepository).save(any(CommentUpvote.class));
        verify(mongoTemplate).findAndModify(any(), any(), any(), eq(Comment.class));
        verify(shipScoreService, never()).applyScoreEvent(any(), any(), any());
    }

    @Test
    void toggleCommentUpvote_WhenScoringFails_ContinuesWithUpvote() {
        // Arrange
        String userId = "user-123";
        String commentId = "comment-456";
        String commenterId = "commenter-id";

        Comment comment = Comment.builder()
                .id(commentId)
                .userId(commenterId)
                .shipId("ship-id")
                .content("This is a long comment with more than fifty characters to qualify for scoring")
                .upvotes(1)
                .build();

        User user = User.builder()
                .id(userId)
                .username("testuser")
                .build();

        when(commentRepository.findById(commentId)).thenReturn(Optional.of(comment));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(commentUpvoteRepository.findByUserIdAndCommentId(userId, commentId)).thenReturn(Optional.empty());
        when(commentUpvoteRepository.save(any(CommentUpvote.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(mongoTemplate.findAndModify(any(), any(), any(), eq(Comment.class)))
                .thenReturn(Comment.builder()
                        .id(commentId)
                        .userId(commenterId)
                        .shipId("ship-id")
                        .content(comment.getContent())
                        .upvotes(2)
                        .build());
        when(commentRepository.save(any(Comment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        
        when(commentQualificationService.isQualified(any(Comment.class)))
                .thenReturn(false)  // Before upvote
                .thenReturn(true);  // After upvote
        
        // Scoring service throws exception
        when(shipScoreService.applyScoreEvent(commenterId, ScoreEventType.QUALITY_COMMENT, commentId))
                .thenThrow(new RuntimeException("Database error"));

        // Act
        CommentUpvoteToggleResponse response = engagementService.toggleCommentUpvote(userId, commentId);

        // Assert - Upvote should still succeed even if scoring fails
        assertThat(response.isUpvoted()).isTrue();
        assertThat(response.getUpvoteCount()).isEqualTo(2);
        assertThat(response.isQualifiedForScoring()).isFalse(); // Scoring failed

        verify(commentUpvoteRepository).save(any(CommentUpvote.class));
        verify(mongoTemplate).findAndModify(any(), any(), any(), eq(Comment.class));
        verify(shipScoreService).applyScoreEvent(commenterId, ScoreEventType.QUALITY_COMMENT, commentId);
    }

    @Test
    void toggleCommentUpvote_WhenScoringReturnsFlase_DoesNotSetQualifiedFlag() {
        // Arrange
        String userId = "user-123";
        String commentId = "comment-456";
        String commenterId = "commenter-id";

        Comment comment = Comment.builder()
                .id(commentId)
                .userId(commenterId)
                .shipId("ship-id")
                .content("This is a long comment with more than fifty characters to qualify for scoring")
                .upvotes(1)
                .build();

        User user = User.builder()
                .id(userId)
                .username("testuser")
                .build();

        when(commentRepository.findById(commentId)).thenReturn(Optional.of(comment));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(commentUpvoteRepository.findByUserIdAndCommentId(userId, commentId)).thenReturn(Optional.empty());
        when(commentUpvoteRepository.save(any(CommentUpvote.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(mongoTemplate.findAndModify(any(), any(), any(), eq(Comment.class)))
                .thenReturn(Comment.builder()
                        .id(commentId)
                        .userId(commenterId)
                        .shipId("ship-id")
                        .content(comment.getContent())
                        .upvotes(2)
                        .build());
        when(commentRepository.save(any(Comment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        
        when(commentQualificationService.isQualified(any(Comment.class)))
                .thenReturn(false)  // Before upvote
                .thenReturn(true);  // After upvote
        
        // Scoring service returns false (duplicate or cap reached)
        when(shipScoreService.applyScoreEvent(commenterId, ScoreEventType.QUALITY_COMMENT, commentId))
                .thenReturn(false);

        // Act
        CommentUpvoteToggleResponse response = engagementService.toggleCommentUpvote(userId, commentId);

        // Assert
        assertThat(response.isUpvoted()).isTrue();
        assertThat(response.getUpvoteCount()).isEqualTo(2);
        assertThat(response.isQualifiedForScoring()).isFalse(); // Score not applied

        verify(shipScoreService).applyScoreEvent(commenterId, ScoreEventType.QUALITY_COMMENT, commentId);
    }

    @Test
    void toggleCommentUpvote_WhenCommentNotFound_ThrowsException() {
        // Arrange
        String userId = "user-123";
        String commentId = "nonexistent-comment";

        when(commentRepository.findById(commentId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> engagementService.toggleCommentUpvote(userId, commentId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Comment not found");

        verify(commentUpvoteRepository, never()).save(any());
        verify(shipScoreService, never()).applyScoreEvent(any(), any(), any());
    }

    @Test
    void toggleCommentUpvote_WhenUserNotFound_ThrowsException() {
        // Arrange
        String userId = "nonexistent-user";
        String commentId = "comment-456";

        Comment comment = Comment.builder()
                .id(commentId)
                .userId("commenter-id")
                .shipId("ship-id")
                .content("Test comment")
                .upvotes(0)
                .build();

        when(commentRepository.findById(commentId)).thenReturn(Optional.of(comment));
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> engagementService.toggleCommentUpvote(userId, commentId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("User not found");

        verify(commentUpvoteRepository, never()).save(any());
        verify(shipScoreService, never()).applyScoreEvent(any(), any(), any());
    }

    @Test
    void toggleCommentUpvote_WhenRaceConditionOccurs_HandlesGracefully() {
        // Arrange
        String userId = "user-123";
        String commentId = "comment-456";

        Comment comment = Comment.builder()
                .id(commentId)
                .userId("commenter-id")
                .shipId("ship-id")
                .content("Test comment")
                .upvotes(2)
                .build();

        User user = User.builder()
                .id(userId)
                .username("testuser")
                .build();

        when(commentRepository.findById(commentId)).thenReturn(Optional.of(comment));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(commentUpvoteRepository.findByUserIdAndCommentId(userId, commentId)).thenReturn(Optional.empty());
        when(commentUpvoteRepository.save(any(CommentUpvote.class))).thenThrow(new DuplicateKeyException("Duplicate key"));
        when(mongoTemplate.findAndModify(any(), any(), any(), eq(Comment.class)))
                .thenReturn(Comment.builder().id(commentId).upvotes(1).build());
        when(commentRepository.save(any(Comment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        CommentUpvoteToggleResponse response = engagementService.toggleCommentUpvote(userId, commentId);

        // Assert - Race condition should result in remove upvote
        assertThat(response.isUpvoted()).isFalse();
        assertThat(response.getUpvoteCount()).isEqualTo(1);
        assertThat(response.isQualifiedForScoring()).isFalse();

        verify(commentUpvoteRepository).deleteByUserIdAndCommentId(userId, commentId);
        verify(mongoTemplate).findAndModify(any(), any(), any(), eq(Comment.class));
        verify(shipScoreService, never()).applyScoreEvent(any(), any(), any());
    }

    @Test
    void toggleCommentUpvote_WhenUpvoteCountIsZero_DoesNotGoNegative() {
        // Arrange
        String userId = "user-123";
        String commentId = "comment-456";

        Comment comment = Comment.builder()
                .id(commentId)
                .userId("commenter-id")
                .shipId("ship-id")
                .content("Test comment")
                .upvotes(0)
                .build();

        User user = User.builder()
                .id(userId)
                .username("testuser")
                .build();

        CommentUpvote existingUpvote = CommentUpvote.builder()
                .id("upvote-id")
                .userId(userId)
                .commentId(commentId)
                .build();

        when(commentRepository.findById(commentId)).thenReturn(Optional.of(comment));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(commentUpvoteRepository.findByUserIdAndCommentId(userId, commentId)).thenReturn(Optional.of(existingUpvote));
        when(mongoTemplate.findAndModify(any(), any(), any(), eq(Comment.class)))
                .thenReturn(Comment.builder().id(commentId).upvotes(0).build());
        when(commentRepository.save(any(Comment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        CommentUpvoteToggleResponse response = engagementService.toggleCommentUpvote(userId, commentId);

        // Assert
        assertThat(response.getUpvoteCount()).isEqualTo(0);
        verify(mongoTemplate).findAndModify(any(), any(), any(), eq(Comment.class));
    }
}
