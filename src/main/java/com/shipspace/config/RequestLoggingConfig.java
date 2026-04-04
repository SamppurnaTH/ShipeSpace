package com.shipspace.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
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

            // Phase 8: put requestId into MDC so it appears in every log line
            // emitted during this request — critical for log correlation in ELK/CloudWatch.
            MDC.put("requestId", requestId);

            // Start timing
            long startTime = System.currentTimeMillis();

            try {
                // Continue filter chain
                filterChain.doFilter(request, response);

                // Calculate response time and log INSIDE the try so MDC is still set
                long responseTime = System.currentTimeMillis() - startTime;
                int status = response.getStatus();
                String userId = (String) request.getAttribute("userId");
                if (userId == null) userId = "anonymous";
                String clientIp = getClientIp(request);

                String logMessage = String.format("[%s] %s %s -> %d (%dms) user=%s ip=%s",
                        requestId, request.getMethod(), request.getRequestURI(),
                        status, responseTime, userId, clientIp);

                if (status >= 500) {
                    log.error(logMessage);
                } else if (status >= 400) {
                    log.warn(logMessage);
                } else if (responseTime > 1000) {
                    log.warn("{} [SLOW REQUEST]", logMessage);
                } else {
                    log.info(logMessage);
                }
            } finally {
                // Always clear MDC — thread pool reuse means stale values leak across requests
                MDC.remove("requestId");
            }
        }

        /**
         * Extracts client IP address.
         * Phase 4 fix: only trusts X-Forwarded-For when the direct connection
         * comes from a private/loopback address (trusted reverse proxy).
         * Blindly reading X-Forwarded-For from any source allows IP spoofing.
         */
        private String getClientIp(HttpServletRequest request) {
            String remoteAddr = request.getRemoteAddr();
            if (isTrustedProxy(remoteAddr)) {
                String xff = request.getHeader("X-Forwarded-For");
                if (xff != null && !xff.isEmpty()) {
                    return xff.split(",")[0].trim();
                }
                String xRealIp = request.getHeader("X-Real-IP");
                if (xRealIp != null && !xRealIp.isEmpty()) {
                    return xRealIp;
                }
            }
            return remoteAddr;
        }

        private boolean isTrustedProxy(String addr) {
            if (addr == null) return false;
            return addr.equals("127.0.0.1") || addr.equals("::1")
                    || addr.startsWith("10.")
                    || addr.startsWith("172.16.") || addr.startsWith("172.17.")
                    || addr.startsWith("172.18.") || addr.startsWith("172.19.")
                    || addr.startsWith("172.2")
                    || addr.startsWith("172.30.") || addr.startsWith("172.31.")
                    || addr.startsWith("192.168.");
        }
    }
}
