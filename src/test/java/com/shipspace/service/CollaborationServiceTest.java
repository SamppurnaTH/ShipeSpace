package com.shipspace.service;

import com.shipspace.dto.CollaborationRequestDto;
import com.shipspace.exception.ForbiddenException;
import com.shipspace.exception.ResourceNotFoundException;
import com.shipspace.model.CollaborationRequest;
import com.shipspace.model.Project;
import com.shipspace.model.ScoreEventType;
import com.shipspace.model.User;
import com.shipspace.repository.CollaborationRequestRepository;
import com.shipspace.repository.ProjectRepository;
import com.shipspace.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("CollaborationService Tests")
class CollaborationServiceTest {

    @Mock
    private CollaborationRequestRepository collaborationRequestRepository;

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ShipScoreService shipScoreService;

    @Mock
    private NotificationDispatchService notificationDispatchService;

    @InjectMocks
    private CollaborationService collaborationService;

    private User requester;
    private User owner;
    private Project project;
    private CollaborationRequest pendingRequest;
    private CollaborationRequestDto dto;

    @BeforeEach
    void setUp() {
        requester = User.builder()
                .id("requester-1")
                .username("requester")
                .avatarUrl("https://example.com/requester.png")
                .build();
        owner = User.builder()
                .id("owner-1")
                .username("owner")
                .avatarUrl("https://example.com/owner.png")
                .build();
        project = Project.builder()
                .id("project-1")
                .userId(owner.getId())
                .name("Builder OS")
                .description("A builder project")
                .build();
        pendingRequest = CollaborationRequest.builder()
                .id("collab-1")
                .requesterId(requester.getId())
                .ownerId(owner.getId())
                .projectId(project.getId())
                .message("I can help with testing.")
                .status(CollaborationRequest.Status.PENDING)
                .createdAt(Instant.parse("2026-08-12T00:00:00Z"))
                .build();
        dto = CollaborationRequestDto.builder()
                .projectId(project.getId())
                .message("I can help with testing.")
                .build();
    }

    @Test
    @DisplayName("sendRequest: creates pending request for another user's project")
    void sendRequest_ValidRequest_CreatesPendingRequest() {
        when(userRepository.existsById(requester.getId())).thenReturn(true);
        when(projectRepository.findById(project.getId())).thenReturn(Optional.of(project));
        when(userRepository.existsById(owner.getId())).thenReturn(true);
        when(collaborationRequestRepository.existsByRequesterIdAndProjectId(requester.getId(), project.getId()))
                .thenReturn(false);
        when(collaborationRequestRepository.save(any(CollaborationRequest.class))).thenReturn(pendingRequest);
        stubSummaries();

        CollaborationRequestDto.Response response = collaborationService.sendRequest(requester.getId(), dto);

        assertThat(response.getId()).isEqualTo("collab-1");
        assertThat(response.getStatus()).isEqualTo(CollaborationRequest.Status.PENDING);
        assertThat(response.getRequester().getUsername()).isEqualTo("requester");
        assertThat(response.getOwner().getUsername()).isEqualTo("owner");
        assertThat(response.getProject().getName()).isEqualTo("Builder OS");
        verify(notificationDispatchService).dispatch(
                eq(owner.getId()),
                eq("COLLABORATION_REQUESTED"),
                eq("requester requested to collaborate on Builder OS"),
                eq(requester.getId()),
                eq(requester.getAvatarUrl()),
                eq("collab-1"),
                eq("/collaborations"));
    }

    @Test
    @DisplayName("sendRequest: rejects self collaboration on own project")
    void sendRequest_SelfProject_Rejects() {
        Project ownProject = Project.builder().id("project-2").userId(requester.getId()).name("Own").build();
        when(userRepository.existsById(requester.getId())).thenReturn(true);
        when(projectRepository.findById(ownProject.getId())).thenReturn(Optional.of(ownProject));

        CollaborationRequestDto ownDto = CollaborationRequestDto.builder().projectId(ownProject.getId()).build();

        assertThatThrownBy(() -> collaborationService.sendRequest(requester.getId(), ownDto))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("own project");
        verify(collaborationRequestRepository, never()).save(any());
    }

    @Test
    @DisplayName("sendRequest: rejects missing project")
    void sendRequest_MissingProject_Rejects() {
        when(userRepository.existsById(requester.getId())).thenReturn(true);
        when(projectRepository.findById(project.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> collaborationService.sendRequest(requester.getId(), dto))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Project not found");
    }

    @Test
    @DisplayName("sendRequest: duplicate request returns existing persisted state")
    void sendRequest_Duplicate_ReturnsExistingState() {
        CollaborationRequest accepted = pendingRequest;
        accepted.setStatus(CollaborationRequest.Status.ACCEPTED);
        accepted.setResolvedAt(Instant.parse("2026-08-12T00:10:00Z"));

        when(userRepository.existsById(requester.getId())).thenReturn(true);
        when(projectRepository.findById(project.getId())).thenReturn(Optional.of(project));
        when(userRepository.existsById(owner.getId())).thenReturn(true);
        when(collaborationRequestRepository.existsByRequesterIdAndProjectId(requester.getId(), project.getId()))
                .thenReturn(true);
        when(collaborationRequestRepository.findByRequesterIdAndProjectId(requester.getId(), project.getId()))
                .thenReturn(Optional.of(accepted));
        stubSummaries();

        CollaborationRequestDto.Response response = collaborationService.sendRequest(requester.getId(), dto);

        assertThat(response.getId()).isEqualTo("collab-1");
        assertThat(response.getStatus()).isEqualTo(CollaborationRequest.Status.ACCEPTED);
        assertThat(response.getResolvedAt()).isNotNull();
        verify(collaborationRequestRepository, never()).save(any());
        verify(notificationDispatchService, never()).dispatch(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("sendRequest: duplicate key race returns existing request")
    void sendRequest_DuplicateKeyRace_ReturnsExistingRequest() {
        when(userRepository.existsById(requester.getId())).thenReturn(true);
        when(projectRepository.findById(project.getId())).thenReturn(Optional.of(project));
        when(userRepository.existsById(owner.getId())).thenReturn(true);
        when(collaborationRequestRepository.existsByRequesterIdAndProjectId(requester.getId(), project.getId()))
                .thenReturn(false);
        when(collaborationRequestRepository.save(any(CollaborationRequest.class)))
                .thenThrow(new DuplicateKeyException("duplicate"));
        when(collaborationRequestRepository.findByRequesterIdAndProjectId(requester.getId(), project.getId()))
                .thenReturn(Optional.of(pendingRequest));
        stubSummaries();

        CollaborationRequestDto.Response response = collaborationService.sendRequest(requester.getId(), dto);

        assertThat(response.getId()).isEqualTo("collab-1");
        assertThat(response.getStatus()).isEqualTo(CollaborationRequest.Status.PENDING);
        verify(notificationDispatchService, never()).dispatch(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("getIncomingRequests: returns all statuses for authenticated owner")
    void getIncomingRequests_ReturnsAllOwnerStatuses() {
        CollaborationRequest accepted = CollaborationRequest.builder()
                .id("collab-2")
                .requesterId("requester-2")
                .ownerId(owner.getId())
                .projectId("project-2")
                .status(CollaborationRequest.Status.ACCEPTED)
                .build();
        when(collaborationRequestRepository.findByOwnerId(eq(owner.getId()), any()))
                .thenReturn(new PageImpl<>(List.of(pendingRequest, accepted), PageRequest.of(0, 20), 2));

        Page<CollaborationRequestDto.Response> response =
                collaborationService.getIncomingRequests(owner.getId(), PageRequest.of(0, 20));

        assertThat(response.getContent()).extracting(CollaborationRequestDto.Response::getStatus)
                .containsExactly(CollaborationRequest.Status.PENDING, CollaborationRequest.Status.ACCEPTED);
        verify(collaborationRequestRepository).findByOwnerId(eq(owner.getId()), any());
    }

    @Test
    @DisplayName("getSentRequests: returns only authenticated requester's requests")
    void getSentRequests_ReturnsRequesterRequests() {
        when(collaborationRequestRepository.findByRequesterId(eq(requester.getId()), any()))
                .thenReturn(new PageImpl<>(List.of(pendingRequest), PageRequest.of(0, 20), 1));

        Page<CollaborationRequestDto.Response> response =
                collaborationService.getSentRequests(requester.getId(), PageRequest.of(0, 20));

        assertThat(response.getContent()).hasSize(1);
        assertThat(response.getContent().get(0).getRequesterId()).isEqualTo(requester.getId());
        verify(collaborationRequestRepository).findByRequesterId(eq(requester.getId()), any());
    }

    @Test
    @DisplayName("acceptRequest: owner accepts pending request and scores requester once")
    void acceptRequest_OwnerAccepts_ScoresRequester() {
        when(collaborationRequestRepository.findById(pendingRequest.getId())).thenReturn(Optional.of(pendingRequest));
        when(collaborationRequestRepository.save(pendingRequest)).thenReturn(pendingRequest);
        when(projectRepository.findById(project.getId())).thenReturn(Optional.of(project));
        when(userRepository.findById(owner.getId())).thenReturn(Optional.of(owner));
        when(shipScoreService.applyScoreEvent(requester.getId(), ScoreEventType.COLLAB_ACCEPTED, pendingRequest.getId()))
                .thenReturn(true);

        CollaborationRequestDto.Response response =
                collaborationService.acceptRequest(owner.getId(), pendingRequest.getId());

        assertThat(response.getStatus()).isEqualTo(CollaborationRequest.Status.ACCEPTED);
        assertThat(pendingRequest.getResolvedAt()).isNotNull();
        verify(shipScoreService).applyScoreEvent(requester.getId(), ScoreEventType.COLLAB_ACCEPTED, pendingRequest.getId());
        verify(notificationDispatchService).dispatch(
                eq(requester.getId()),
                eq("COLLABORATION_ACCEPTED"),
                eq("Your collaboration request for Builder OS was accepted."),
                eq(owner.getId()),
                eq(owner.getAvatarUrl()),
                eq(pendingRequest.getId()),
                eq("/collaborations"));
    }

    @Test
    @DisplayName("acceptRequest: requester cannot accept own sent request")
    void acceptRequest_RequesterCannotAccept() {
        when(collaborationRequestRepository.findById(pendingRequest.getId())).thenReturn(Optional.of(pendingRequest));

        assertThatThrownBy(() -> collaborationService.acceptRequest(requester.getId(), pendingRequest.getId()))
                .isInstanceOf(ForbiddenException.class);
        verify(shipScoreService, never()).applyScoreEvent(any(), any(), any());
    }

    @Test
    @DisplayName("acceptRequest: repeated accept is safely rejected without duplicate score")
    void acceptRequest_RepeatedAccept_NoDuplicateScore() {
        pendingRequest.setStatus(CollaborationRequest.Status.ACCEPTED);
        when(collaborationRequestRepository.findById(pendingRequest.getId())).thenReturn(Optional.of(pendingRequest));

        assertThatThrownBy(() -> collaborationService.acceptRequest(owner.getId(), pendingRequest.getId()))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("no longer pending");
        verify(shipScoreService, never()).applyScoreEvent(any(), any(), any());
        verify(notificationDispatchService, never()).dispatch(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("declineRequest: owner declines pending request")
    void declineRequest_OwnerDeclines() {
        when(collaborationRequestRepository.findById(pendingRequest.getId())).thenReturn(Optional.of(pendingRequest));
        when(collaborationRequestRepository.save(pendingRequest)).thenReturn(pendingRequest);
        when(projectRepository.findById(project.getId())).thenReturn(Optional.of(project));
        when(userRepository.findById(owner.getId())).thenReturn(Optional.of(owner));

        CollaborationRequestDto.Response response =
                collaborationService.declineRequest(owner.getId(), pendingRequest.getId());

        assertThat(response.getStatus()).isEqualTo(CollaborationRequest.Status.DECLINED);
        assertThat(pendingRequest.getResolvedAt()).isNotNull();
        verify(shipScoreService, never()).applyScoreEvent(any(), any(), any());
        verify(notificationDispatchService).dispatch(
                eq(requester.getId()),
                eq("COLLABORATION_DECLINED"),
                eq("Your collaboration request for Builder OS was declined."),
                eq(owner.getId()),
                eq(owner.getAvatarUrl()),
                eq(pendingRequest.getId()),
                eq("/collaborations"));
    }

    @Test
    @DisplayName("declineRequest: third party cannot decline")
    void declineRequest_ThirdPartyCannotDecline() {
        when(collaborationRequestRepository.findById(pendingRequest.getId())).thenReturn(Optional.of(pendingRequest));

        assertThatThrownBy(() -> collaborationService.declineRequest("user-c", pendingRequest.getId()))
                .isInstanceOf(ForbiddenException.class);
        verify(collaborationRequestRepository, never()).save(any());
    }

    @Test
    @DisplayName("toResponse: missing referenced project or users does not throw")
    void toResponse_MissingReferences_DoesNotThrow() {
        when(collaborationRequestRepository.findByRequesterId(eq(requester.getId()), any()))
                .thenReturn(new PageImpl<>(List.of(pendingRequest), PageRequest.of(0, 20), 1));
        when(userRepository.findById(requester.getId())).thenReturn(Optional.empty());
        when(userRepository.findById(owner.getId())).thenReturn(Optional.empty());
        when(projectRepository.findById(project.getId())).thenReturn(Optional.empty());

        Page<CollaborationRequestDto.Response> response =
                collaborationService.getSentRequests(requester.getId(), PageRequest.of(0, 20));

        assertThat(response.getContent().get(0).getRequester()).isNull();
        assertThat(response.getContent().get(0).getOwner()).isNull();
        assertThat(response.getContent().get(0).getProject()).isNull();
    }

    @Test
    @DisplayName("acceptRequest: captures accepted status before score side effect")
    void acceptRequest_StatusPersistsBeforeScoreSideEffect() {
        when(collaborationRequestRepository.findById(pendingRequest.getId())).thenReturn(Optional.of(pendingRequest));
        when(collaborationRequestRepository.save(any(CollaborationRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(projectRepository.findById(project.getId())).thenReturn(Optional.of(project));
        when(userRepository.findById(owner.getId())).thenReturn(Optional.of(owner));
        when(shipScoreService.applyScoreEvent(requester.getId(), ScoreEventType.COLLAB_ACCEPTED, pendingRequest.getId()))
                .thenReturn(false);

        collaborationService.acceptRequest(owner.getId(), pendingRequest.getId());

        ArgumentCaptor<CollaborationRequest> captor = ArgumentCaptor.forClass(CollaborationRequest.class);
        verify(collaborationRequestRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(CollaborationRequest.Status.ACCEPTED);
        verify(shipScoreService, times(1))
                .applyScoreEvent(requester.getId(), ScoreEventType.COLLAB_ACCEPTED, pendingRequest.getId());
    }

    private void stubSummaries() {
        when(userRepository.findById(requester.getId())).thenReturn(Optional.of(requester));
        when(userRepository.findById(owner.getId())).thenReturn(Optional.of(owner));
        when(projectRepository.findById(project.getId())).thenReturn(Optional.of(project));
    }
}
