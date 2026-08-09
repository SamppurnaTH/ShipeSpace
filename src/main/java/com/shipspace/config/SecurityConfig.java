package com.shipspace.config;

import com.shipspace.security.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Security configuration for the ShipSpace API.
 * Swagger/OpenAPI endpoints are only permitted when SWAGGER_ENABLED=true.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final ObjectProvider<BannedUserWriteFilter> bannedUserWriteFilter;

    @Value("${SWAGGER_ENABLED:false}")
    private boolean swaggerEnabled;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> {
                // Always-public endpoints
                // Note: paths are relative to the servlet context (/api is the context-path,
                // so Spring Security sees paths WITHOUT the /api prefix)
                auth.requestMatchers(
                        "/auth/github",
                        "/auth/state",
                        "/auth/refresh",
                        "/health",
                        "/health/**",
                        "/actuator/health",
                        "/actuator/health/**",
                        "/feeds/trending",
                        "/feeds/trending/**",
                        "/search",
                        "/users/*/ships",
                        "/demo/status",
                        "/demo/reset",
                        "/demo/data"
                ).permitAll();

                auth.requestMatchers(HttpMethod.GET,
                        "/users/*",
                        "/projects/*",
                        "/projects/*/ships",
                        "/ships/*",
                        "/ships/*/comments",
                        "/ships/*/likes",
                        "/v1/users/*/rank-summary"
                ).permitAll();

                // Swagger only when explicitly enabled (dev/staging)
                if (swaggerEnabled) {
                    auth.requestMatchers(
                            "/api-docs/**",
                            "/swagger-ui/**",
                            "/swagger-ui.html"
                    ).permitAll();
                }

                auth.requestMatchers("/admin/**").hasRole("ADMIN");
                auth.anyRequest().authenticated();
            })
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        bannedUserWriteFilter.ifAvailable(filter ->
                http.addFilterAfter(filter, JwtAuthenticationFilter.class));

        return http.build();
    }
}
