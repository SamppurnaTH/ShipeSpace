package com.shipspace.service;

import com.shipspace.dto.CurrentUserResponse;
import com.shipspace.dto.ProjectResponse;
import com.shipspace.dto.ShipResponse;
import com.shipspace.dto.UserActivityResponse;
import com.shipspace.dto.UserProfileResponse;
import com.shipspace.dto.UserUpdateRequest;
import com.shipspace.exception.ResourceNotFoundException;
import com.shipspace.model.Project;
import com.shipspace.model.Ship;
import com.shipspace.model.User;
import com.shipspace.repository.ProjectRepository;
import com.shipspace.repository.ShipRepository;
import com.shipspace.repository.UserRepository;
import com.shipspace.util.DtoMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for UserService.
 * Tests user profile retrieval, updates, and activity aggregation.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("UserService Tests")
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private ShipRepository shipRepository;

    @Mock
    private DtoMapper dtoMapper;

    @InjectMocks
    private UserService userService;

    private User testUser;
    private String testUserId;

    @BeforeEach
    void setUp() {
        testUserId = "user123";
        testUser = User.builder()
                .id(testUserId)
                .githubId("github123")
                .username("testuser")
                .email("test@example.com")
                .avatarUrl("https://example.com/avatar.jpg")
                .bio("Test bio")
                .location("Test City")
                .websiteUrl("https://example.com")
                .followerCount(10)
                .followingCount(5)
                .projectCount(3)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    @Test
    @DisplayName("Should retrieve user by ID successfully")
    void getUserById_Success() {
        // Arrange
        when(userRepository.findById(testUserId)).thenReturn(Optional.of(testUser));

        // Act
        User result = userService.getUserById(testUserId);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(testUserId);
        assertThat(result.getUsername()).isEqualTo("testuser");
        verify(userRepository).findById(testUserId);
    }

    @Test
    @DisplayName("Should throw ResourceNotFoundException when user not found")
    void getUserById_NotFound() {
        // Arrange
        when(userRepository.findById(testUserId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> userService.getUserById(testUserId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("User not found");
        
        verify(userRepository).findById(testUserId);
    }

    @Test
    @DisplayName("Should retrieve public profile successfully")
    void getPublicProfile_Success() {
        // Arrange
        when(userRepository.findById(testUserId)).thenReturn(Optional.of(testUser));

        // Act
        UserProfileResponse result = userService.getPublicProfile(testUserId);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(testUserId);
        assertThat(result.getUsername()).isEqualTo("testuser");
        assertThat(result.getBio()).isEqualTo("Test bio");
        assertThat(result.getLocation()).isEqualTo("Test City");
        assertThat(result.getFollowerCount()).isEqualTo(10);
        assertThat(result.getFollowingCount()).isEqualTo(5);
        assertThat(result.getProjectCount()).isEqualTo(3);
        verify(userRepository).findById(testUserId);
    }

    @Test
    @DisplayName("Should update user profile with all fields")
    void updateUserProfile_AllFields() {
        // Arrange
        UserUpdateRequest updateRequest = UserUpdateRequest.builder()
                .email("newemail@example.com")
                .bio("New bio")
                .location("New City")
                .websiteUrl("https://newsite.com")
                .avatarUrl("https://example.com/newavatar.jpg")
                .build();

        when(userRepository.findById(testUserId)).thenReturn(Optional.of(testUser));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        CurrentUserResponse result = userService.updateUserProfile(testUserId, updateRequest);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getEmail()).isEqualTo("newemail@example.com");
        assertThat(result.getBio()).isEqualTo("New bio");
        assertThat(result.getLocation()).isEqualTo("New City");
        assertThat(result.getWebsiteUrl()).isEqualTo("https://newsite.com");
        assertThat(result.getAvatarUrl()).isEqualTo("https://example.com/newavatar.jpg");
        verify(userRepository).findById(testUserId);
        verify(userRepository).save(any(User.class));
    }

    @Test
    @DisplayName("Should update user profile with partial fields")
    void updateUserProfile_PartialFields() {
        // Arrange
        UserUpdateRequest updateRequest = UserUpdateRequest.builder()
                .bio("Updated bio only")
                .build();

        when(userRepository.findById(testUserId)).thenReturn(Optional.of(testUser));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        CurrentUserResponse result = userService.updateUserProfile(testUserId, updateRequest);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getBio()).isEqualTo("Updated bio only");
        assertThat(result.getEmail()).isEqualTo("test@example.com"); // Unchanged
        assertThat(result.getLocation()).isEqualTo("Test City"); // Unchanged
        verify(userRepository).findById(testUserId);
        verify(userRepository).save(any(User.class));
    }

    @Test
    @DisplayName("Should retrieve user projects successfully")
    void getUserProjects_Success() {
        // Arrange
        List<Project> projects = Arrays.asList(
                Project.builder().id("proj1").userId(testUserId).name("Project 1").build(),
                Project.builder().id("proj2").userId(testUserId).name("Project 2").build()
        );

        when(userRepository.findById(testUserId)).thenReturn(Optional.of(testUser));
        when(projectRepository.findByUserId(testUserId)).thenReturn(projects);
        when(dtoMapper.toProjectResponse(any(Project.class))).thenAnswer(inv -> {
            Project p = inv.getArgument(0);
            return ProjectResponse.builder().id(p.getId()).userId(p.getUserId()).name(p.getName()).build();
        });

        // Act
        List<ProjectResponse> result = userService.getUserProjects(testUserId);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result).hasSize(2);
        assertThat(result.get(0).getName()).isEqualTo("Project 1");
        assertThat(result.get(1).getName()).isEqualTo("Project 2");
        verify(userRepository).findById(testUserId);
        verify(projectRepository).findByUserId(testUserId);
    }

    @Test
    @DisplayName("Should retrieve user activity successfully")
    void getUserActivity_Success() {
        // Arrange
        List<Ship> recentShips = Arrays.asList(
                Ship.builder()
                        .id("ship1")
                        .userId(testUserId)
                        .content("Ship 1")
                        .likeCount(5)
                        .commentCount(3)
                        .build(),
                Ship.builder()
                        .id("ship2")
                        .userId(testUserId)
                        .content("Ship 2")
                        .likeCount(10)
                        .commentCount(2)
                        .build()
        );
        Page<Ship> shipPage = new PageImpl<>(recentShips);

        when(userRepository.findById(testUserId)).thenReturn(Optional.of(testUser));
        when(shipRepository.findByUserId(eq(testUserId), any(Pageable.class))).thenReturn(shipPage);
        when(dtoMapper.toShipResponse(any(Ship.class))).thenAnswer(inv -> {
            Ship s = inv.getArgument(0);
            return ShipResponse.builder().id(s.getId()).userId(s.getUserId()).content(s.getContent())
                    .likeCount(s.getLikeCount()).commentCount(s.getCommentCount()).build();
        });

        // Act
        UserActivityResponse result = userService.getUserActivity(testUserId);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getRecentShips()).hasSize(2);
        assertThat(result.getTotalShips()).isEqualTo(2);
        assertThat(result.getTotalEngagement()).isEqualTo(20); // 5+3+10+2
        verify(userRepository).findById(testUserId);
        verify(shipRepository, times(2)).findByUserId(eq(testUserId), any(Pageable.class));
    }

    @Test
    @DisplayName("Should retrieve user activity with custom limit")
    void getUserActivity_CustomLimit() {
        // Arrange
        List<Ship> recentShips = Arrays.asList(
                Ship.builder().id("ship1").userId(testUserId).likeCount(1).commentCount(1).build()
        );
        Page<Ship> shipPage = new PageImpl<>(recentShips);

        when(userRepository.findById(testUserId)).thenReturn(Optional.of(testUser));
        when(shipRepository.findByUserId(eq(testUserId), any(Pageable.class))).thenReturn(shipPage);

        // Act
        UserActivityResponse result = userService.getUserActivity(testUserId, 5);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getRecentShips()).hasSize(1);
        verify(userRepository).findById(testUserId);
        verify(shipRepository, times(2)).findByUserId(eq(testUserId), any(Pageable.class));
    }

    @Test
    @DisplayName("Should update project count successfully")
    void updateProjectCount_Success() {
        // Arrange
        when(userRepository.findById(testUserId)).thenReturn(Optional.of(testUser));
        when(projectRepository.countByUserId(testUserId)).thenReturn(5L);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        userService.updateProjectCount(testUserId);

        // Assert
        verify(userRepository).findById(testUserId);
        verify(projectRepository).countByUserId(testUserId);
        verify(userRepository).save(argThat(user -> 
                user.getProjectCount() == 5
        ));
    }

    @Test
    @DisplayName("Should handle empty projects list")
    void getUserProjects_EmptyList() {
        // Arrange
        when(userRepository.findById(testUserId)).thenReturn(Optional.of(testUser));
        when(projectRepository.findByUserId(testUserId)).thenReturn(Arrays.asList());

        // Act
        List<ProjectResponse> result = userService.getUserProjects(testUserId);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result).isEmpty();
        verify(userRepository).findById(testUserId);
        verify(projectRepository).findByUserId(testUserId);
    }

    @Test
    @DisplayName("Should handle empty activity")
    void getUserActivity_EmptyActivity() {
        // Arrange
        Page<Ship> emptyPage = new PageImpl<>(Arrays.asList());

        when(userRepository.findById(testUserId)).thenReturn(Optional.of(testUser));
        when(shipRepository.findByUserId(eq(testUserId), any(Pageable.class))).thenReturn(emptyPage);

        // Act
        UserActivityResponse result = userService.getUserActivity(testUserId);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getRecentShips()).isEmpty();
        assertThat(result.getTotalShips()).isEqualTo(0);
        assertThat(result.getTotalEngagement()).isEqualTo(0);
        verify(userRepository).findById(testUserId);
    }
}
