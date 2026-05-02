package com.shipspace.security;

import com.shipspace.util.JwtUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * JWT authentication filter.
 * Validates the token signature, expiry, type, and blacklist status
 * before setting the security context.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final TokenBlacklistService tokenBlacklistService;

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        try {
            String jwt = extractJwtFromRequest(request);

            if (jwt != null && jwtUtil.validateToken(jwt)) {
                // Reject refresh tokens used as access tokens
                String tokenType = jwtUtil.extractTokenType(jwt);
                if (!"access".equals(tokenType)) {
                    log.debug("Rejected non-access token type: {}", tokenType);
                    filterChain.doFilter(request, response);
                    return;
                }

                // Reject blacklisted tokens (revoked via logout or rotation)
                String jti = jwtUtil.extractJti(jwt);
                if (jti != null && tokenBlacklistService.isBlacklisted(jti)) {
                    log.debug("Rejected blacklisted token jti={}", jti);
                    filterChain.doFilter(request, response);
                    return;
                }

                String userId = jwtUtil.extractUserId(jwt);
                String username = jwtUtil.extractUsername(jwt);
                Set<String> roles = jwtUtil.extractRoles(jwt);

                var authorities = roles.stream()
                        .map(role -> (org.springframework.security.core.GrantedAuthority)
                                new org.springframework.security.core.authority.SimpleGrantedAuthority(
                                        role.startsWith("ROLE_") ? role : "ROLE_" + role))
                        .collect(Collectors.toList());

                UserDetails userDetails = User.builder()
                        .username(userId)
                        .password("")
                        .authorities(authorities)
                        .build();

                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(
                                userDetails, null, authorities);
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                SecurityContextHolder.getContext().setAuthentication(authentication);
                log.debug("Authenticated user: {} (ID: {})", username, userId);
            }

        } catch (Exception e) {
            log.error("Cannot set user authentication: {}", e.getMessage());
        }

        filterChain.doFilter(request, response);
    }

    private String extractJwtFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader(AUTHORIZATION_HEADER);
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith(BEARER_PREFIX)) {
            return bearerToken.substring(BEARER_PREFIX.length());
        }
        return null;
    }
}
