package com.shipspace.config;

import com.shipspace.model.User;
import com.shipspace.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.Set;

/**
 * Allows banned users to keep reading and logging out, but blocks mutations.
 */
@Slf4j
@Component
@ConditionalOnBean(UserRepository.class)
@RequiredArgsConstructor
public class BannedUserWriteFilter extends OncePerRequestFilter {

    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS", "TRACE");

    private final UserRepository userRepository;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        if (SAFE_METHODS.contains(request.getMethod()) || isAuthEndpoint(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            filterChain.doFilter(request, response);
            return;
        }

        String userId = authentication.getName();
        boolean banned = userRepository.findById(userId)
                .map(User::isBanned)
                .orElse(false);

        if (!banned) {
            filterChain.doFilter(request, response);
            return;
        }

        log.warn("Blocked write request from banned user {}: {} {}",
                userId, request.getMethod(), request.getRequestURI());
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("""
                {"code":"BANNED_USER","message":"Banned users cannot perform write actions.","timestamp":"%s","path":"%s"}\
                """.formatted(Instant.now(), request.getRequestURI()));
    }

    private boolean isAuthEndpoint(HttpServletRequest request) {
        String path = request.getServletPath();
        return path != null && path.startsWith("/auth/");
    }
}
