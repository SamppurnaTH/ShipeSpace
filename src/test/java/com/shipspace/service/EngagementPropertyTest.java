package com.shipspace.service;

import com.shipspace.dto.CommentCreateRequest;
import com.shipspace.dto.CommentResponse;
import com.shipspace.dto.LikeResponse;
import com.shipspace.dto.LikeToggleResponse;
import com.shipspace.exception.ResourceNotFoundException;
import com.shipspace.model.Comment;
import com.shipspace.model.Like;
import com.shipspace.model.Ship;
import com.shipspace.model.User;
import com.shipspace.repository.CommentRepository;
import com.shipspace.repository.CommentUpvoteRepository;
import com.shipspace.repository.LikeRepository;
import com.shipspace.repository.ShipCountRepository;
import com.shipspace.repository.ShipRepository;
import com.shipspace.repository.UserRepository;
import com.shipspace.service.NotificationDispatchService;
import com.shipspace.util.InputSanitizer;
import net.jqwik.api.*;
import net.jqwik.api.constraints.AlphaChars;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.StringLength;
import net.jqwik.api.lifecycle.BeforeTry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Property-based tests for engagement features (likes and comments).
 * 
 * **Validates: Requirements 8.1, 8.2, 8.3, 8.4, 8.5**
 * 
 * These tests verify the correctness properties of the engagement system
 * across a wide range of inputs using property-based testing with jqwik.
 */
class EngagementPropertyTest {

    private EngagementService engagementService;
    private LikeRepository likeRepository;
    private CommentRepository commentRepository;
    private ShipRepository shipRepository;
    private UserRepository userRepository;
    private CommentUpvoteRepository commentUpvoteRepository;
    private CommentQualificationService commentQualificationService;
    private ShipScoreService shipScoreService;
    private ShipCountRepository shipCountRepository;

    @BeforeTry
    void setUp() {
        likeRepository = mock(LikeRepository.class);
        commentRepository = mock(CommentRepository.class);
        commentUpvoteRepository = mock(CommentUpvoteRepository.class);
        shipRepository = mock(ShipRepository.class);
        userRepository = mock(UserRepository.class);
        commentQualificationService = mock(CommentQualificationService.class);
        shipScoreService = mock(ShipScoreService.class);
        shipCountRepository = mock(ShipCountRepository.class);
        org.springframework.data.mongodb.core.MongoTemplate mongoTemplate = mock(org.springframework.data.mongodb.core.MongoTemplate.class);
        NotificationDispatchService notificationDispatchService = mock(NotificationDispatchService.class);
        InputSanitizer inputSanitizer = mock(InputSanitizer.class);
        when(inputSanitizer.sanitize(any())).thenAnswer(inv -> inv.getArgument(0));
        when(inputSanitizer.sanitize(any(), anyInt())).thenAnswer(inv -> inv.getArgument(0));
        when(shipRepository.existsById(anyString())).thenReturn(true);
        when(userRepository.existsById(anyString())).thenReturn(true);
        engagementService = new EngagementService(likeRepository, commentRepository, commentUpvoteRepository,
                shipRepository, shipCountRepository, userRepository, commentQualificationService, shipScoreService,
                mongoTemplate, notificationDispatchService, inputSanitizer);
    }

    /**
     * Property 28: Like state toggling
     * 
     * For any like request on a ship, the like state should toggle (create if not exists, 
     * remove if exists) and engagement counts should update accordingly.
     * 
     * **Validates: Requirements 8.1**
     */
    @Property(tries = 100)
    @Label("Feature: shipspace-mvp, Property 28: Like state toggling - Like creates like record")
    void likeCreatesLikeRecord(
            @ForAll @StringLength(min = 1, max = 50) @AlphaChars String userId,
            @ForAll @StringLength(min = 1, max = 50) @AlphaChars String shipId,
            @ForAll @IntRange(min = 0, max = 100) int initialLikeCount) {
        
        // Given: User and ship exist, no like exists yet
        User user = createMockUser(userId, "user_" + userId);
        Ship ship = createMockShip(shipId, "project_" + shipId, "user_" + shipId);
        ship.setLikeCount(initialLikeCount);
        
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(shipRepository.findById(shipId)).thenReturn(Optional.of(ship));
        when(likeRepository.findByUserIdAndShipId(userId, shipId)).thenReturn(Optional.empty());
        when(likeRepository.save(any(Like.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(shipCountRepository.incrementLikeCount(shipId, 1))
                .thenReturn(Ship.builder().id(shipId).likeCount(initialLikeCount + 1).build());

        // When: User likes the ship
        LikeToggleResponse response = engagementService.toggleLike(userId, shipId);

        // Then: Like should be created
        verify(likeRepository).save(argThat(like -> 
            like.getUserId().equals(userId) && 
            like.getShipId().equals(shipId) &&
            like.getCreatedAt() != null
        ));
        
        // And: Ship like count should be incremented atomically
        verify(shipCountRepository).incrementLikeCount(shipId, 1);
        
        // And: Response should indicate liked state
        assertThat(response.isLiked()).isTrue();
        assertThat(response.getLikeCount()).isEqualTo(initialLikeCount + 1);
        assertThat(response.getMessage()).contains("liked successfully");
    }

    /**
     * Property 28 (variant): Unlike removes like record
     * 
     * For any unlike request, the like should be removed and counts should be decremented.
     * 
     * **Validates: Requirements 8.1**
     */
    @Property(tries = 100)
    @Label("Feature: shipspace-mvp, Property 28: Like state toggling - Unlike removes like record")
    void unlikeRemovesLikeRecord(
            @ForAll @StringLength(min = 1, max = 50) @AlphaChars String userId,
            @ForAll @StringLength(min = 1, max = 50) @AlphaChars String shipId,
            @ForAll @IntRange(min = 1, max = 100) int initialLikeCount) {
        
        // Given: User and ship exist, like already exists
        User user = createMockUser(userId, "user_" + userId);
        Ship ship = createMockShip(shipId, "project_" + shipId, "user_" + shipId);
        ship.setLikeCount(initialLikeCount);
        
        Like existingLike = Like.builder()
                .id("like_" + userId + "_" + shipId)
                .userId(userId)
                .shipId(shipId)
                .createdAt(Instant.now())
                .build();
        
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(shipRepository.findById(shipId)).thenReturn(Optional.of(ship));
        when(likeRepository.findByUserIdAndShipId(userId, shipId)).thenReturn(Optional.of(existingLike));
        when(likeRepository.deleteByUserIdAndShipId(userId, shipId)).thenReturn(1L);
        when(shipCountRepository.incrementLikeCount(shipId, -1))
                .thenReturn(Ship.builder().id(shipId).likeCount(initialLikeCount - 1).build());

        // When: User unlikes the ship
        LikeToggleResponse response = engagementService.toggleLike(userId, shipId);

        // Then: Like should be deleted
        verify(likeRepository).deleteByUserIdAndShipId(userId, shipId);
        
        // And: Ship like count should be decremented atomically
        verify(shipCountRepository).incrementLikeCount(shipId, -1);
        
        // And: Response should indicate unliked state
        assertThat(response.isLiked()).isFalse();
        assertThat(response.getLikeCount()).isEqualTo(initialLikeCount - 1);
        assertThat(response.getMessage()).contains("unliked successfully");
    }

    /**
     * Property 28 (variant): Like count never goes negative
     * 
     * Even with race conditions or errors, like counts should never be negative.
     * 
     * **Validates: Requirements 8.1**
     */
    @Property(tries = 100)
    @Label("Feature: shipspace-mvp, Property 28: Like state toggling - Like count never negative")
    void likeCountNeverNegative(
            @ForAll @StringLength(min = 1, max = 50) @AlphaChars String userId,
            @ForAll @StringLength(min = 1, max = 50) @AlphaChars String shipId,
            @ForAll @IntRange(min = 0, max = 2) int initialLikeCount) {
        
        // Given: Ship with low like count and existing like
        User user = createMockUser(userId, "user_" + userId);
        Ship ship = createMockShip(shipId, "project_" + shipId, "user_" + shipId);
        ship.setLikeCount(initialLikeCount);
        
        Like existingLike = Like.builder()
                .id("like_" + userId + "_" + shipId)
                .userId(userId)
                .shipId(shipId)
                .createdAt(Instant.now())
                .build();
        
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(shipRepository.findById(shipId)).thenReturn(Optional.of(ship));
        when(likeRepository.findByUserIdAndShipId(userId, shipId)).thenReturn(Optional.of(existingLike));
        when(likeRepository.deleteByUserIdAndShipId(userId, shipId)).thenReturn(1L);
        when(shipCountRepository.incrementLikeCount(shipId, -1))
                .thenReturn(Ship.builder().id(shipId).likeCount(Math.max(0, initialLikeCount - 1)).build());

        // When: User unlikes the ship
        LikeToggleResponse response = engagementService.toggleLike(userId, shipId);

        // Then: Like count should never be negative
        verify(shipCountRepository).incrementLikeCount(shipId, -1);
        assertThat(response.getLikeCount()).isGreaterThanOrEqualTo(0);
    }

    /**
     * Property 28 (variant): Non-existent ship handling
     * 
     * Attempting to like a non-existent ship should throw ResourceNotFoundException.
     * 
     * **Validates: Requirements 8.1**
     */
    @Property(tries = 100)
    @Label("Feature: shipspace-mvp, Property 28: Like state toggling - Non-existent ship")
    void likeNonExistentShip(
            @ForAll @StringLength(min = 1, max = 50) @AlphaChars String userId,
            @ForAll @StringLength(min = 1, max = 50) @AlphaChars String shipId) {
        
        // Given: Ship does not exist
        when(shipRepository.existsById(shipId)).thenReturn(false);
        when(shipRepository.findById(shipId)).thenReturn(Optional.empty());

        // When/Then: Attempting to like should throw ResourceNotFoundException
        assertThatThrownBy(() -> engagementService.toggleLike(userId, shipId))
            .isInstanceOf(ResourceNotFoundException.class)
            .hasMessageContaining(shipId);
        
        // And: No like should be created
        verify(likeRepository, never()).save(any(Like.class));
    }

    /**
     * Property 29: Comment creation and validation
     * 
     * For any comment creation request, the comment should be validated, stored, 
     * and associated with the target ship.
     * 
     * **Validates: Requirements 8.2**
     */
    @Property(tries = 100)
    @Label("Feature: shipspace-mvp, Property 29: Comment creation and validation")
    void commentCreationAndValidation(
            @ForAll @StringLength(min = 1, max = 50) @AlphaChars String userId,
            @ForAll @StringLength(min = 1, max = 50) @AlphaChars String shipId,
            @ForAll @StringLength(min = 1, max = 500) String commentContent,
            @ForAll @IntRange(min = 0, max = 100) int initialCommentCount) {
        
        // Given: User and ship exist
        User user = createMockUser(userId, "user_" + userId);
        Ship ship = createMockShip(shipId, "project_" + shipId, "user_" + shipId);
        ship.setCommentCount(initialCommentCount);
        
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(shipRepository.findById(shipId)).thenReturn(Optional.of(ship));
        when(commentRepository.save(any(Comment.class))).thenAnswer(invocation -> {
            Comment comment = invocation.getArgument(0);
            comment.setId("comment_" + System.currentTimeMillis());
            return comment;
        });
        when(shipCountRepository.incrementCommentCount(shipId, 1))
                .thenReturn(Ship.builder().id(shipId).commentCount(initialCommentCount + 1).build());

        CommentCreateRequest request = CommentCreateRequest.builder()
                .content(commentContent)
                .build();

        // When: User creates a comment
        CommentResponse response = engagementService.addComment(userId, shipId, request);

        // Then: Comment should be saved with correct data
        verify(commentRepository).save(argThat(comment -> 
            comment.getUserId().equals(userId) && 
            comment.getShipId().equals(shipId) &&
            comment.getContent().equals(commentContent) &&
            comment.getCreatedAt() != null &&
            comment.getUpdatedAt() != null
        ));
        
        // And: Ship comment count should be incremented atomically
        verify(shipCountRepository).incrementCommentCount(shipId, 1);
        
        // And: Response should contain comment data and commenter profile
        assertThat(response).isNotNull();
        assertThat(response.getId()).isNotNull();
        assertThat(response.getShipId()).isEqualTo(shipId);
        assertThat(response.getContent()).isEqualTo(commentContent);
        assertThat(response.getCommenter()).isNotNull();
        assertThat(response.getCommenter().getId()).isEqualTo(userId);
        assertThat(response.getCommenter().getUsername()).isEqualTo(user.getUsername());
        assertThat(response.getCreatedAt()).isNotNull();
        assertThat(response.getUpdatedAt()).isNotNull();
    }

    /**
     * Property 29 (variant): Comment on non-existent ship
     * 
     * Attempting to comment on a non-existent ship should throw ResourceNotFoundException.
     * 
     * **Validates: Requirements 8.2**
     */
    @Property(tries = 100)
    @Label("Feature: shipspace-mvp, Property 29: Comment creation and validation - Non-existent ship")
    void commentOnNonExistentShip(
            @ForAll @StringLength(min = 1, max = 50) @AlphaChars String userId,
            @ForAll @StringLength(min = 1, max = 50) @AlphaChars String shipId,
            @ForAll @StringLength(min = 1, max = 500) String commentContent) {
        
        // Given: Ship does not exist
        when(shipRepository.existsById(shipId)).thenReturn(false);
        when(shipRepository.findById(shipId)).thenReturn(Optional.empty());

        CommentCreateRequest request = CommentCreateRequest.builder()
                .content(commentContent)
                .build();

        // When/Then: Attempting to comment should throw ResourceNotFoundException
        assertThatThrownBy(() -> engagementService.addComment(userId, shipId, request))
            .isInstanceOf(ResourceNotFoundException.class)
            .hasMessageContaining(shipId);
        
        // And: No comment should be created
        verify(commentRepository, never()).save(any(Comment.class));
    }

    /**
     * Property 29 (variant): Comment by non-existent user
     * 
     * Attempting to comment by a non-existent user should throw ResourceNotFoundException.
     * 
     * **Validates: Requirements 8.2**
     */
    @Property(tries = 100)
    @Label("Feature: shipspace-mvp, Property 29: Comment creation and validation - Non-existent user")
    void commentByNonExistentUser(
            @ForAll @StringLength(min = 1, max = 50) @AlphaChars String userId,
            @ForAll @StringLength(min = 1, max = 50) @AlphaChars String shipId,
            @ForAll @StringLength(min = 1, max = 500) String commentContent) {
        
        // Given: Ship exists but user does not
        Ship ship = createMockShip(shipId, "project_" + shipId, "user_" + shipId);
        when(shipRepository.findById(shipId)).thenReturn(Optional.of(ship));
        when(userRepository.existsById(userId)).thenReturn(false);
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        CommentCreateRequest request = CommentCreateRequest.builder()
                .content(commentContent)
                .build();

        // When/Then: Attempting to comment should throw ResourceNotFoundException
        assertThatThrownBy(() -> engagementService.addComment(userId, shipId, request))
            .isInstanceOf(ResourceNotFoundException.class)
            .hasMessageContaining(userId);
        
        // And: No comment should be created
        verify(commentRepository, never()).save(any(Comment.class));
    }

    /**
     * Property 30: Engagement data retrieval
     * 
     * For any ship, likes and comments should be retrievable with pagination, 
     * including commenter profile information and timestamps.
     * 
     * **Validates: Requirements 8.3, 8.5**
     */
    @Property(tries = 100)
    @Label("Feature: shipspace-mvp, Property 30: Engagement data retrieval - Comments pagination")
    void commentsPaginationRetrieval(
            @ForAll @StringLength(min = 1, max = 50) @AlphaChars String shipId,
            @ForAll @IntRange(min = 0, max = 3) int pageNumber,
            @ForAll @IntRange(min = 1, max = 20) int pageSize,
            @ForAll @IntRange(min = 0, max = 50) int totalComments) {
        
        // Given: Ship exists with comments
        Ship ship = createMockShip(shipId, "project_" + shipId, "user_" + shipId);
        when(shipRepository.findById(shipId)).thenReturn(Optional.of(ship));
        
        Pageable pageable = PageRequest.of(pageNumber, pageSize);
        List<Comment> commentList = createCommentList(totalComments, shipId);
        
        // Calculate expected page content
        int start = pageNumber * pageSize;
        int end = Math.min(start + pageSize, totalComments);
        List<Comment> pageContent = start < totalComments ? 
            commentList.subList(start, end) : new ArrayList<>();
        
        Page<Comment> commentPage = new PageImpl<>(pageContent, pageable, totalComments);
        when(commentRepository.findByShipId(shipId, pageable)).thenReturn(commentPage);
        
        List<User> commentUsers = pageContent.stream()
                .map(comment -> createMockUser(comment.getUserId(), "user_" + comment.getUserId()))
                .collect(java.util.stream.Collectors.toList());
        when(userRepository.findAllById(any())).thenReturn(commentUsers);

        // When: Requesting comments with pagination
        Page<CommentResponse> result = engagementService.getShipComments(shipId, pageable);

        // Then: Should return paginated results
        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(pageContent.size());
        assertThat(result.getTotalElements()).isEqualTo(totalComments);
        assertThat(result.getNumber()).isEqualTo(pageNumber);
        assertThat(result.getSize()).isEqualTo(pageSize);
        
        // And: Each result should contain comment data with commenter profile
        for (CommentResponse commentResponse : result.getContent()) {
            assertThat(commentResponse.getId()).isNotNull();
            assertThat(commentResponse.getShipId()).isEqualTo(shipId);
            assertThat(commentResponse.getContent()).isNotNull();
            assertThat(commentResponse.getCommenter()).isNotNull();
            assertThat(commentResponse.getCommenter().getId()).isNotNull();
            assertThat(commentResponse.getCommenter().getUsername()).isNotNull();
            assertThat(commentResponse.getCreatedAt()).isNotNull();
            assertThat(commentResponse.getUpdatedAt()).isNotNull();
        }
        
        verify(commentRepository).findByShipId(shipId, pageable);
    }

    /**
     * Property 30 (variant): Likes pagination retrieval
     * 
     * For any ship, likes should be retrievable with pagination and user profile information.
     * 
     * **Validates: Requirements 8.3, 8.5**
     */
    @Property(tries = 100)
    @Label("Feature: shipspace-mvp, Property 30: Engagement data retrieval - Likes pagination")
    void likesPaginationRetrieval(
            @ForAll @StringLength(min = 1, max = 50) @AlphaChars String shipId,
            @ForAll @IntRange(min = 0, max = 3) int pageNumber,
            @ForAll @IntRange(min = 1, max = 20) int pageSize,
            @ForAll @IntRange(min = 0, max = 50) int totalLikes) {
        
        // Given: Ship exists with likes
        Ship ship = createMockShip(shipId, "project_" + shipId, "user_" + shipId);
        when(shipRepository.findById(shipId)).thenReturn(Optional.of(ship));
        
        Pageable pageable = PageRequest.of(pageNumber, pageSize);
        List<Like> likeList = createLikeList(totalLikes, shipId);
        
        // Calculate expected page content
        int start = pageNumber * pageSize;
        int end = Math.min(start + pageSize, totalLikes);
        List<Like> pageContent = start < totalLikes ? 
            likeList.subList(start, end) : new ArrayList<>();
        
        Page<Like> likePage = new PageImpl<>(pageContent, pageable, totalLikes);
        when(likeRepository.findByShipId(shipId, pageable)).thenReturn(likePage);
        
        List<User> likeUsers = pageContent.stream()
                .map(like -> createMockUser(like.getUserId(), "user_" + like.getUserId()))
                .collect(java.util.stream.Collectors.toList());
        when(userRepository.findAllById(any())).thenReturn(likeUsers);

        // When: Requesting likes with pagination
        Page<LikeResponse> result = engagementService.getShipLikes(shipId, pageable);

        // Then: Should return paginated results
        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(pageContent.size());
        assertThat(result.getTotalElements()).isEqualTo(totalLikes);
        assertThat(result.getNumber()).isEqualTo(pageNumber);
        assertThat(result.getSize()).isEqualTo(pageSize);
        
        // And: Each result should contain like data with user profile
        for (LikeResponse likeResponse : result.getContent()) {
            assertThat(likeResponse.getId()).isNotNull();
            assertThat(likeResponse.getShipId()).isEqualTo(shipId);
            assertThat(likeResponse.getUser()).isNotNull();
            assertThat(likeResponse.getUser().getId()).isNotNull();
            assertThat(likeResponse.getUser().getUsername()).isNotNull();
            assertThat(likeResponse.getCreatedAt()).isNotNull();
        }
        
        verify(likeRepository).findByShipId(shipId, pageable);
    }

    /**
     * Property 30 (variant): Empty engagement lists
     * 
     * For any ship with no engagement, pagination should return empty results.
     * 
     * **Validates: Requirements 8.3, 8.5**
     */
    @Property(tries = 100)
    @Label("Feature: shipspace-mvp, Property 30: Engagement data retrieval - Empty lists")
    void emptyEngagementLists(
            @ForAll @StringLength(min = 1, max = 50) @AlphaChars String shipId,
            @ForAll @IntRange(min = 0, max = 3) int pageNumber,
            @ForAll @IntRange(min = 1, max = 20) int pageSize) {
        
        // Given: Ship exists with no engagement
        Ship ship = createMockShip(shipId, "project_" + shipId, "user_" + shipId);
        when(shipRepository.findById(shipId)).thenReturn(Optional.of(ship));
        
        Pageable pageable = PageRequest.of(pageNumber, pageSize);
        Page<Comment> emptyCommentPage = new PageImpl<>(new ArrayList<>(), pageable, 0);
        Page<Like> emptyLikePage = new PageImpl<>(new ArrayList<>(), pageable, 0);
        
        when(commentRepository.findByShipId(shipId, pageable)).thenReturn(emptyCommentPage);
        when(likeRepository.findByShipId(shipId, pageable)).thenReturn(emptyLikePage);

        // When: Requesting comments and likes
        Page<CommentResponse> commentResult = engagementService.getShipComments(shipId, pageable);
        Page<LikeResponse> likeResult = engagementService.getShipLikes(shipId, pageable);

        // Then: Should return empty pages
        assertThat(commentResult).isNotNull();
        assertThat(commentResult.getContent()).isEmpty();
        assertThat(commentResult.getTotalElements()).isEqualTo(0);
        
        assertThat(likeResult).isNotNull();
        assertThat(likeResult.getContent()).isEmpty();
        assertThat(likeResult.getTotalElements()).isEqualTo(0);
    }

    /**
     * Property 30 (variant): Non-existent ship engagement retrieval
     * 
     * Attempting to retrieve engagement for non-existent ships should throw ResourceNotFoundException.
     * 
     * **Validates: Requirements 8.3, 8.5**
     */
    @Property(tries = 100)
    @Label("Feature: shipspace-mvp, Property 30: Engagement data retrieval - Non-existent ship")
    void nonExistentShipEngagementRetrieval(
            @ForAll @StringLength(min = 1, max = 50) @AlphaChars String shipId,
            @ForAll @IntRange(min = 0, max = 3) int pageNumber,
            @ForAll @IntRange(min = 1, max = 20) int pageSize) {
        
        // Given: Ship does not exist
        when(shipRepository.existsById(shipId)).thenReturn(false);
        when(shipRepository.findById(shipId)).thenReturn(Optional.empty());
        
        Pageable pageable = PageRequest.of(pageNumber, pageSize);

        // When/Then: Requesting comments should throw ResourceNotFoundException
        assertThatThrownBy(() -> engagementService.getShipComments(shipId, pageable))
            .isInstanceOf(ResourceNotFoundException.class)
            .hasMessageContaining(shipId);
        
        // When/Then: Requesting likes should throw ResourceNotFoundException
        assertThatThrownBy(() -> engagementService.getShipLikes(shipId, pageable))
            .isInstanceOf(ResourceNotFoundException.class)
            .hasMessageContaining(shipId);
    }

    /**
     * Property 31: Duplicate like prevention
     * 
     * For any user and ship combination, multiple like requests should not create 
     * duplicate like records, and concurrent requests should be handled safely.
     * 
     * **Validates: Requirements 8.4**
     */
    @Property(tries = 100)
    @Label("Feature: shipspace-mvp, Property 31: Duplicate like prevention - Toggle behavior")
    void duplicateLikePrevention(
            @ForAll @StringLength(min = 1, max = 50) @AlphaChars String userId,
            @ForAll @StringLength(min = 1, max = 50) @AlphaChars String shipId,
            @ForAll @IntRange(min = 1, max = 100) int initialLikeCount) {
        
        // Given: User and ship exist, no like exists initially
        User user = createMockUser(userId, "user_" + userId);
        Ship ship = createMockShip(shipId, "project_" + shipId, "user_" + shipId);
        ship.setLikeCount(initialLikeCount);
        
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(shipRepository.findById(shipId)).thenReturn(Optional.of(ship));
        
        // First call: no like exists
        when(likeRepository.findByUserIdAndShipId(userId, shipId))
            .thenReturn(Optional.empty());
        when(likeRepository.save(any(Like.class))).thenAnswer(invocation -> {
            Like like = invocation.getArgument(0);
            like.setId("like_" + userId + "_" + shipId);
            return like;
        });
        when(shipCountRepository.incrementLikeCount(shipId, 1))
                .thenReturn(Ship.builder().id(shipId).likeCount(initialLikeCount + 1).build());
        when(shipCountRepository.incrementLikeCount(shipId, -1))
                .thenReturn(Ship.builder().id(shipId).likeCount(initialLikeCount).build());
        when(likeRepository.deleteByUserIdAndShipId(userId, shipId)).thenReturn(1L);

        // When: User likes the ship (first time)
        LikeToggleResponse firstResponse = engagementService.toggleLike(userId, shipId);

        // Then: Like should be created
        assertThat(firstResponse.isLiked()).isTrue();
        assertThat(firstResponse.getLikeCount()).isEqualTo(initialLikeCount + 1);
        verify(likeRepository, times(1)).save(any(Like.class));
        
        // Given: Now like exists
        Like existingLike = Like.builder()
                .id("like_" + userId + "_" + shipId)
                .userId(userId)
                .shipId(shipId)
                .createdAt(Instant.now())
                .build();
        when(likeRepository.findByUserIdAndShipId(userId, shipId))
            .thenReturn(Optional.of(existingLike));
        
        // Reset ship state for second call
        ship.setLikeCount(initialLikeCount + 1);

        // When: User likes the ship again (should unlike)
        LikeToggleResponse secondResponse = engagementService.toggleLike(userId, shipId);

        // Then: Like should be removed (toggle behavior)
        assertThat(secondResponse.isLiked()).isFalse();
        assertThat(secondResponse.getLikeCount()).isEqualTo(initialLikeCount);
        verify(likeRepository, times(1)).deleteByUserIdAndShipId(userId, shipId);
        
        // And: No additional like should be created
        verify(likeRepository, times(1)).save(any(Like.class)); // Still only 1 from first call
    }

    /**
     * Property 31 (variant): Race condition handling
     * 
     * When a duplicate key exception occurs (race condition), the system should 
     * handle it gracefully by treating it as an unlike operation.
     * 
     * **Validates: Requirements 8.4**
     */
    @Property(tries = 100)
    @Label("Feature: shipspace-mvp, Property 31: Duplicate like prevention - Race condition handling")
    void raceConditionHandling(
            @ForAll @StringLength(min = 1, max = 50) @AlphaChars String userId,
            @ForAll @StringLength(min = 1, max = 50) @AlphaChars String shipId,
            @ForAll @IntRange(min = 1, max = 100) int initialLikeCount) {
        
        // Given: User and ship exist, no like found initially (race condition scenario)
        User user = createMockUser(userId, "user_" + userId);
        Ship ship = createMockShip(shipId, "project_" + shipId, "user_" + shipId);
        ship.setLikeCount(initialLikeCount);
        
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(shipRepository.findById(shipId)).thenReturn(Optional.of(ship));
        when(likeRepository.findByUserIdAndShipId(userId, shipId)).thenReturn(Optional.empty());
        
        // Simulate race condition: DuplicateKeyException on save
        when(likeRepository.save(any(Like.class)))
            .thenThrow(new org.springframework.dao.DuplicateKeyException("Duplicate key"));
        when(likeRepository.deleteByUserIdAndShipId(userId, shipId)).thenReturn(1L);
        when(shipCountRepository.incrementLikeCount(shipId, -1))
                .thenReturn(Ship.builder().id(shipId).likeCount(Math.max(0, initialLikeCount - 1)).build());

        // When: User attempts to like (race condition occurs)
        LikeToggleResponse response = engagementService.toggleLike(userId, shipId);

        // Then: Should handle gracefully by treating as unlike
        assertThat(response.isLiked()).isFalse();
        
        // And: Should attempt to delete the duplicate like
        verify(likeRepository).deleteByUserIdAndShipId(userId, shipId);
        
        // And: Like count should be decremented atomically (or stay at 0)
        verify(shipCountRepository).incrementLikeCount(shipId, -1);
    }

    /**
     * Property 31 (variant): Unique constraint enforcement
     * 
     * The database should enforce unique constraint on userId + shipId combination.
     * 
     * **Validates: Requirements 8.4**
     */
    @Property(tries = 100)
    @Label("Feature: shipspace-mvp, Property 31: Duplicate like prevention - Unique constraint")
    void uniqueConstraintEnforcement(
            @ForAll @StringLength(min = 1, max = 50) @AlphaChars String userId,
            @ForAll @StringLength(min = 1, max = 50) @AlphaChars String shipId) {
        
        // Given: User and ship exist
        User user = createMockUser(userId, "user_" + userId);
        Ship ship = createMockShip(shipId, "project_" + shipId, "user_" + shipId);
        
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(shipRepository.findById(shipId)).thenReturn(Optional.of(ship));
        when(likeRepository.findByUserIdAndShipId(userId, shipId)).thenReturn(Optional.empty());
        when(likeRepository.save(any(Like.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(shipCountRepository.incrementLikeCount(shipId, 1))
                .thenReturn(Ship.builder().id(shipId).likeCount(1).build());

        // When: Creating a like
        engagementService.toggleLike(userId, shipId);

        // Then: Like should be saved with userId and shipId
        verify(likeRepository).save(argThat(like -> 
            like.getUserId().equals(userId) && 
            like.getShipId().equals(shipId)
        ));
        
        // Note: The actual unique constraint is enforced at the database level
        // via the @CompoundIndex annotation on the Like model
    }

    // Helper methods

    private User createMockUser(String userId, String username) {
        return User.builder()
                .id(userId)
                .githubId("github_" + userId)
                .username(username)
                .email(username + "@example.com")
                .avatarUrl("https://example.com/avatar/" + userId)
                .bio("Bio for " + username)
                .location("Location " + userId)
                .websiteUrl("https://example.com/" + username)
                .followerCount(0)
                .followingCount(0)
                .projectCount(0)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    private Ship createMockShip(String shipId, String projectId, String userId) {
        return Ship.builder()
                .id(shipId)
                .userId(userId)
                .projectId(projectId)
                .content("Ship content for " + shipId)
                .likeCount(0)
                .commentCount(0)
                .trendingScore(0.0)
                .editWindowExpires(Instant.now().plusSeconds(600))
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    private List<Comment> createCommentList(int count, String shipId) {
        List<Comment> comments = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Comment comment = Comment.builder()
                    .id("comment_" + i)
                    .userId("user_" + i)
                    .shipId(shipId)
                    .content("Comment content " + i)
                    .createdAt(Instant.now().minusSeconds(count - i))
                    .updatedAt(Instant.now().minusSeconds(count - i))
                    .build();
            comments.add(comment);
        }
        return comments;
    }

    private List<Like> createLikeList(int count, String shipId) {
        List<Like> likes = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Like like = Like.builder()
                    .id("like_" + i)
                    .userId("user_" + i)
                    .shipId(shipId)
                    .createdAt(Instant.now().minusSeconds(count - i))
                    .build();
            likes.add(like);
        }
        return likes;
    }
}
