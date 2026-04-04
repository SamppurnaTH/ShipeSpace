package com.shipspace.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Adds security response headers to every request.
 * The duplicate CORS bean that was here previously has been removed —
 * CORS is handled exclusively by CorsConfig to avoid conflicting policies.
 */
@Configuration
public class SecurityHeadersConfig {

    @Bean
    public FilterRegistrationBean<OncePerRequestFilter> securityHeadersFilter() {
        FilterRegistrationBean<OncePerRequestFilter> registration = new FilterRegistrationBean<>();

        registration.setFilter(new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(HttpServletRequest request,
                                            HttpServletResponse response,
                                            FilterChain chain) throws ServletException, IOException {
                response.setHeader("X-Content-Type-Options", "nosniff");
                response.setHeader("X-Frame-Options", "DENY");
                response.setHeader("X-XSS-Protection", "1; mode=block");
                response.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");
                response.setHeader("Permissions-Policy",
                        "accelerometer=(), camera=(), geolocation=(), gyroscope=(), " +
                        "magnetometer=(), microphone=(), payment=(), usb=()");
                // Phase 4: CSP was missing. This is a pure JSON API — no HTML rendering.
                // Restricting to default-src 'none' prevents any browser from executing
                // content from this origin as a page (defense-in-depth for API endpoints).
                response.setHeader("Content-Security-Policy", "default-src 'none'");
                // HSTS: force HTTPS for 1 year, include subdomains
                response.setHeader("Strict-Transport-Security", "max-age=31536000; includeSubDomains");
                chain.doFilter(request, response);
            }
        });

        registration.addUrlPatterns("/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        registration.setName("securityHeadersFilter");
        return registration;
    }
}
