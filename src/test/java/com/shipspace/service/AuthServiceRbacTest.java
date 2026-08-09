package com.shipspace.service;

import com.shipspace.dto.AuthResponse;
import com.shipspace.model.User;
import com.shipspace.repository.UserRepository;
import com.shipspace.security.TokenBlacklistService;
import com.shipspace.util.JwtUtil;
import io.micrometer.core.instrument.Counter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuthServiceRbacTest {

    private JwtUtil jwtUtil;
    private UserRepository userRepository;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(jwtUtil, "secret",
                "rbac-test-secret-key-minimum-32-chars-required!!");
        ReflectionTestUtils.setField(jwtUtil, "expiration", 900000L);
        ReflectionTestUtils.setField(jwtUtil, "refreshExpiration", 604800000L);
        jwtUtil.init();

        userRepository = mock(UserRepository.class);
        authService = new AuthService(
                jwtUtil,
                userRepository,
                mock(GitHubOAuthService.class),
                mock(TokenBlacklistService.class),
                mock(Counter.class),
                mock(Counter.class),
                mock(Counter.class));
        ReflectionTestUtils.setField(authService, "jwtExpiration", 900000L);
    }

    @Test
    void refreshToken_usesPersistedRolesInNewAccessToken() {
        User admin = User.builder()
                .id("admin-user")
                .username("admin")
                .roles(Set.of("USER", "ADMIN"))
                .build();
        when(userRepository.findById("admin-user")).thenReturn(Optional.of(admin));

        String refreshToken = jwtUtil.generateRefreshToken("admin-user");
        AuthResponse response = authService.refreshToken(refreshToken);

        assertThat(jwtUtil.extractRoles(response.getAccessToken()))
                .containsExactlyInAnyOrder("USER", "ADMIN");
    }

    @Test
    void getCurrentUserResponse_exposesRolesAndBannedState() {
        User admin = User.builder()
                .id("admin-user")
                .username("admin")
                .roles(Set.of("USER", "ADMIN"))
                .banned(true)
                .build();
        when(userRepository.findById("admin-user")).thenReturn(Optional.of(admin));

        var response = authService.getCurrentUserResponse("admin-user");

        assertThat(response.getRoles()).containsExactlyInAnyOrder("USER", "ADMIN");
        assertThat(response.isBanned()).isTrue();
    }
}
