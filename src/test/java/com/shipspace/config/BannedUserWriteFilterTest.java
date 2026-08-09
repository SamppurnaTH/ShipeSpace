package com.shipspace.config;

import com.shipspace.model.User;
import com.shipspace.repository.UserRepository;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BannedUserWriteFilterTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final BannedUserWriteFilter filter = new BannedUserWriteFilter(userRepository);
    private final FilterChain chain = mock(FilterChain.class);

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void bannedUser_canRead() throws Exception {
        authenticate("user-1");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/ships/ship-1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verify(userRepository, never()).findById("user-1");
    }

    @Test
    void bannedUser_cannotWrite() throws Exception {
        authenticate("user-1");
        when(userRepository.findById("user-1")).thenReturn(Optional.of(User.builder()
                .id("user-1")
                .banned(true)
                .build()));
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/ships");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("BANNED_USER");
        verify(chain, never()).doFilter(request, response);
    }

    private void authenticate(String userId) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userId, null, java.util.List.of()));
    }
}
