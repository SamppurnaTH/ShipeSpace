package com.shipspace.controller;

import com.shipspace.model.User;
import com.shipspace.repository.ReportRepository;
import com.shipspace.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminControllerTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final ReportRepository reportRepository = mock(ReportRepository.class);
    private final AdminController controller = new AdminController(userRepository, reportRepository);

    @Test
    void banUser_blocksSelfBan() {
        UserDetails admin = org.springframework.security.core.userdetails.User
                .withUsername("admin-1")
                .password("")
                .roles("ADMIN")
                .build();

        assertThatThrownBy(() -> controller.banUser("admin-1", Map.of("banned", "true"), admin))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("cannot ban or unban");

        verify(userRepository, never()).save(org.mockito.ArgumentMatchers.any(User.class));
    }

    @Test
    void banUser_updatesTargetUser() {
        UserDetails admin = org.springframework.security.core.userdetails.User
                .withUsername("admin-1")
                .password("")
                .roles("ADMIN")
                .build();
        User target = User.builder().id("user-1").username("builder").banned(false).build();
        when(userRepository.findById("user-1")).thenReturn(Optional.of(target));

        var response = controller.banUser("user-1", Map.of("banned", "true"), admin);

        assertThat(response.getBody()).containsEntry("userId", "user-1").containsEntry("banned", true);
        assertThat(target.isBanned()).isTrue();
        verify(userRepository).save(target);
    }

    @Test
    void getUser_returnsAdminVisibleFields() {
        User target = User.builder()
                .id("user-1")
                .githubId("12345")
                .username("builder")
                .roles(java.util.Set.of("USER", "ADMIN"))
                .banned(true)
                .build();
        when(userRepository.findById("user-1")).thenReturn(Optional.of(target));

        var response = controller.getUser("user-1");

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getGithubId()).isEqualTo("12345");
        assertThat(response.getBody().getRoles()).containsExactlyInAnyOrder("USER", "ADMIN");
        assertThat(response.getBody().isBanned()).isTrue();
    }
}
