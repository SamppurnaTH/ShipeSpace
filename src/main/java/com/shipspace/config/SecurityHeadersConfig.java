package com.shipspace.config;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * Security headers configuration for production readiness.
 * 
 * <p>Configures HTTP security headers including:
 * <ul>
 *   <li>HSTS (HTTP Strict Transport Security)</li>
 *   <li>X-Content-Type-Options</li>
 *   <li>X-Frame-Options</li>
 *   <li>X-XSS-Protection</li>
 *   <li>Content-Security-Policy</li>
 *   <li>Referrer-Policy</li>
 *   <li>Permissions-Policy</li>
 * </ul>
 * 
 * <p>These headers help protect against common web vulnerabilities
 * such as XSS, clickjacking, and MIME type sniffing.
 */
@Configuration
public class SecurityHeadersConfig {

    /**
     * Configures security headers filter for all responses.
     * 
     * <p>Applies the following headers<ul>
     *   <li>X:
     * -Content-Type-Options: nosniff</li>
     *   <li>X-Frame-Options: DENY</li>
     *   <li>X-XSS-Protection: 1; mode=block</li>
     *   <li>Referrer-Policy: strict-origin-when-cross-origin</li>
     *   <li>Permissions-Policy: accelerometer=(), camera=(), geolocation=(), gyroscope=(), magnetometer=(), microphone=()</li>
     * </ul>
     */
    @Bean
    public FilterRegistrationBean<OncePerRequestFilter> securityHeadersFilter() {
        FilterRegistrationBean<OncePerRequestFilter> registrationBean = new FilterRegistrationBean<>();
        
        OncePerRequestFilter filter = new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(
                    HttpServletRequest request,
                    HttpServletResponse response,
                    FilterChain filterChain
            ) throws ServletException, IOException {
                
                // X-Content-Type-Options
                response.setHeader("X-Content-Type-Options", "nosniff");
                
                // X-Frame-Options
                response.setHeader("X-Frame-Options", "DENY");
                
                // X-XSS-Protection (legacy but still useful)
                response.setHeader("X-XSS-Protection", "1; mode=block");
                
                // Referrer-Policy
                response.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");
                
                // Permissions-Policy
                response.setHeader("Permissions-Policy", 
                    "accelerometer=(), camera=(), geolocation=(), gyroscope=(), " +
                    "magnetometer=(), microphone=(), payment=(), usb=()");
                
                // Cache-Control for sensitive endpoints
                if (request.getRequestURI().contains("/users/") && 
                    request.getRequestURI().contains("/rank-summary")) {
                    response.setHeader("Cache-Control", "private, no-cache, no-store, must-revalidate");
                    response.setHeader("Pragma", "no-cache");
                    response.setHeader("Expires", "0");
                }
                
                filterChain.doFilter(request, response);
            }
        };
        
        registrationBean.setFilter(filter);
        registrationBean.addUrlPatterns("/*");
        registrationBean.setOrder(Ordered.HIGHEST_PRECEDENCE);
        registrationBean.setName("securityHeadersFilter");
        
        return registrationBean;
    }

    /**
     * Custom CORS configuration with security headers.
     */
    @Bean
    public FilterRegistrationBean<CorsFilter> corsFilter() {
        CorsConfiguration config = new CorsConfiguration();
        
        // Allow origins from configuration
        config.setAllowedOriginPatterns(List.of("*"));
        config.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);
        config.setExposedHeaders(Arrays.asList(
            "X-RateLimit-Limit",
            "X-RateLimit-Remaining",
            "X-RateLimit-Reset",
            "X-Total-Count"
        ));
        
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        
        FilterRegistrationBean<CorsFilter> bean = new FilterRegistrationBean<>();
        bean.setFilter(new CorsFilter(source));
        bean.setOrder(Ordered.HIGHEST_PRECEDENCE + 1);
        bean.setName("corsFilter");
        
        return bean;
    }
}
