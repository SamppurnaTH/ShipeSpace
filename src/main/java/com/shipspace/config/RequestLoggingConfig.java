package com.shipspace.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Request/Response logging configuration for production monitoring.
 * 
 * <p>Logs all incoming API requests with:
 * <ul>
 *   <li>Request ID for tracing</li>
 *   <li>HTTP method and path</li>
 *   <li>Response status</li>
 *   <li>Response time</li>
 *   <li>User ID (if authenticated)</li>
 *   <li>Source IP address</li>
 * </ul>
 */
@Slf4j
@Configuration
public class RequestLoggingConfig {

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE + 2)
    public RequestLoggingFilter requestLoggingFilter() {
        return new RequestLoggingFilter();
    }

    /**
     * Request logging filter that captures request/response details.
     */
    @Slf4j
    public static class RequestLoggingFilter extends OncePerRequestFilter {

        private static final String REQUEST_ID_HEADER = "X-Request-ID";
        private static final String CORRELATION_ID_HEADER = "X-Correlation-ID";

        @Override
        protected void doFilterInternal(
                HttpServletRequest request,
                HttpServletResponse response,
                FilterChain filterChain
        ) throws ServletException, IOException {
            
            // Generate or extract request ID
            String requestId = request.getHeader(REQUEST_ID_HEADER);
            if (requestId == null || requestId.isEmpty()) {
                requestId = UUID.randomUUID().toString().substring(0, 8);
            }
            
            // Add request ID to response headers
            response.setHeader(REQUEST_ID_HEADER, requestId);
            response.setHeader(CORRELATION_ID_HEADER, requestId);
            
            // Store request ID in request attribute for downstream use
            request.setAttribute("requestId", requestId);
            
            // Start timing
            long startTime = System.currentTimeMillis();
            
            // Continue filter chain
            filterChain.doFilter(request, response);
            
            // Calculate response time
            long responseTime = System.currentTimeMillis() - startTime;
            
            // Get response status
            int status = response.getStatus();
            
            // Get user ID if available
            String userId = (String) request.getAttribute("userId");
            if (userId == null) {
                userId = "anonymous";
            }
            
            // Get client IP
            String clientIp = getClientIp(request);
            
            // Log request — structured format: METHOD PATH STATUS USER_ID RESPONSE_TIME CLIENT_IP
            String logMessage = String.format("[%s] %s %s -> %d (%dms) user=%s ip=%s",
                    requestId,
                    request.getMethod(),
                    request.getRequestURI(),
                    status,
                    responseTime,
                    userId,
                    clientIp
            );
            
            if (status >= 500) {
                log.error(logMessage);
            } else if (status >= 400) {
                log.warn(logMessage);
            } else if (responseTime > 1000) {
                log.warn(logMessage + " [SLOW REQUEST]");
            } else {
                log.info(logMessage);
            }
            
            // Log detailed info for errors
            if (status >= 500) {
                log.error("Request details - RequestID: {}, Query: {}, Body: {}",
                        requestId,
                        request.getQueryString(),
                        request.getParameterMap());
            }
        }

        /**
         * Extracts client IP address, handling proxy headers.
         */
        private String getClientIp(HttpServletRequest request) {
            String xForwardedFor = request.getHeader("X-Forwarded-For");
            if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
                return xForwardedFor.split(",")[0].trim();
            }
            
            String xRealIp = request.getHeader("X-Real-IP");
            if (xRealIp != null && !xRealIp.isEmpty()) {
                return xRealIp;
            }
            
            return request.getRemoteAddr();
        }
    }
}
