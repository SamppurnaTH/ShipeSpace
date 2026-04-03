package com.shipspace.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Global exception handler for the ShipSpace API.
 * Provides consistent error response format across all endpoints.
 * 
 * Implements Requirements 9.2 and 9.4:
 * - Consistent JSON response formats with standard HTTP status codes
 * - Structured error responses with descriptive messages
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Handles GitHub OAuth exceptions.
     * Returns 401 Unauthorized when GitHub authentication fails.
     */
    @ExceptionHandler(GitHubOAuthException.class)
    public ResponseEntity<ErrorResponse> handleGitHubOAuthException(
            GitHubOAuthException ex, WebRequest request) {
        log.error("GitHub OAuth error: {}", ex.getMessage());
        
        ErrorResponse error = ErrorResponse.builder()
                .code("GITHUB_OAUTH_ERROR")
                .message(ex.getMessage())
                .timestamp(Instant.now())
                .path(extractPath(request))
                .build();
        
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
    }

    /**
     * Handles invalid token exceptions.
     * Returns 401 Unauthorized when JWT token is invalid or expired.
     */
    @ExceptionHandler(InvalidTokenException.class)
    public ResponseEntity<ErrorResponse> handleInvalidTokenException(
            InvalidTokenException ex, WebRequest request) {
        log.error("Invalid token error: {}", ex.getMessage());
        
        ErrorResponse error = ErrorResponse.builder()
                .code("INVALID_TOKEN")
                .message(ex.getMessage())
                .timestamp(Instant.now())
                .path(extractPath(request))
                .build();
        
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
    }

    /**
     * Handles Spring Security authentication exceptions.
     * Returns 401 Unauthorized when authentication fails.
     */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuthenticationException(
            AuthenticationException ex, WebRequest request) {
        log.error("Authentication error: {}", ex.getMessage());
        
        ErrorResponse error = ErrorResponse.builder()
                .code("AUTHENTICATION_ERROR")
                .message("Authentication failed.")
                .timestamp(Instant.now())
                .path(extractPath(request))
                .build();
        
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
    }

    /**
     * Handles Spring Security access denied exceptions.
     * Returns 403 Forbidden when user lacks required permissions.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDeniedException(
            AccessDeniedException ex, WebRequest request) {
        log.error("Access denied: {}", ex.getMessage());
        
        ErrorResponse error = ErrorResponse.builder()
                .code("ACCESS_DENIED")
                .message("Access denied.")
                .timestamp(Instant.now())
                .path(extractPath(request))
                .build();
        
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error);
    }

    /**
     * Handles forbidden exceptions (authorization failures).
     * Returns 403 Forbidden when user attempts unauthorized operations.
     */
    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<ErrorResponse> handleForbiddenException(
            ForbiddenException ex, WebRequest request) {
        log.error("Forbidden: {}", ex.getMessage());
        
        ErrorResponse error = ErrorResponse.builder()
                .code("FORBIDDEN")
                .message(ex.getMessage())
                .timestamp(Instant.now())
                .path(extractPath(request))
                .build();
        
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error);
    }

    /**
     * Handles resource not found exceptions.
     * Returns 404 Not Found when requested resource doesn't exist.
     */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleResourceNotFoundException(
            ResourceNotFoundException ex, WebRequest request) {
        log.error("Resource not found: {}", ex.getMessage());
        
        ErrorResponse error = ErrorResponse.builder()
                .code("RESOURCE_NOT_FOUND")
                .message(ex.getMessage())
                .timestamp(Instant.now())
                .path(extractPath(request))
                .build();
        
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }

    /**
     * Handles validation errors from @Valid annotations.
     * Returns 400 Bad Request with field-specific error details.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(
            MethodArgumentNotValidException ex, WebRequest request) {
        log.error("Validation error: {}", ex.getMessage());
        
        Map<String, String> fieldErrors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach(error -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            fieldErrors.put(fieldName, errorMessage);
        });
        
        ErrorResponse error = ErrorResponse.builder()
                .code("VALIDATION_ERROR")
                .message("Validation failed")
                .timestamp(Instant.now())
                .path(extractPath(request))
                .details(fieldErrors)
                .build();
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    /**
     * Handles illegal argument exceptions.
     * Returns 400 Bad Request when invalid arguments are provided.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgumentException(
            IllegalArgumentException ex, WebRequest request) {
        log.error("Illegal argument: {}", ex.getMessage());
        
        ErrorResponse error = ErrorResponse.builder()
                .code("INVALID_ARGUMENT")
                .message(ex.getMessage())
                .timestamp(Instant.now())
                .path(extractPath(request))
                .build();
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    /**
     * Handles malformed JSON or unreadable request body.
     * Returns 400 Bad Request when request body cannot be parsed.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleHttpMessageNotReadableException(
            HttpMessageNotReadableException ex, WebRequest request) {
        log.error("Malformed request body: {}", ex.getMessage());
        
        ErrorResponse error = ErrorResponse.builder()
                .code("MALFORMED_REQUEST")
                .message("Request body is malformed or unreadable. Please check your JSON syntax.")
                .timestamp(Instant.now())
                .path(extractPath(request))
                .build();
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    /**
     * Handles missing required request parameters.
     * Returns 400 Bad Request when required parameters are missing.
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingServletRequestParameterException(
            MissingServletRequestParameterException ex, WebRequest request) {
        log.error("Missing request parameter: {}", ex.getMessage());
        
        ErrorResponse error = ErrorResponse.builder()
                .code("MISSING_PARAMETER")
                .message(String.format("Required parameter '%s' is missing", ex.getParameterName()))
                .timestamp(Instant.now())
                .path(extractPath(request))
                .build();
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    /**
     * Handles method argument type mismatch (e.g., passing string where number expected).
     * Returns 400 Bad Request when parameter types don't match.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentTypeMismatchException(
            MethodArgumentTypeMismatchException ex, WebRequest request) {
        log.error("Type mismatch for parameter: {}", ex.getMessage());
        
        String message = String.format("Parameter '%s' should be of type %s", 
                ex.getName(), 
                ex.getRequiredType() != null ? ex.getRequiredType().getSimpleName() : "unknown");
        
        ErrorResponse error = ErrorResponse.builder()
                .code("TYPE_MISMATCH")
                .message(message)
                .timestamp(Instant.now())
                .path(extractPath(request))
                .build();
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    /**
     * Handles Multibase integration exceptions.
     * Returns 502 Bad Gateway when external Multibase service fails.
     */
    @ExceptionHandler(MultibaseException.class)
    public ResponseEntity<ErrorResponse> handleMultibaseException(
            MultibaseException ex, WebRequest request) {
        log.error("Multibase error: {}", ex.getMessage());
        
        ErrorResponse error = ErrorResponse.builder()
                .code("MULTIBASE_ERROR")
                .message(ex.getMessage())
                .timestamp(Instant.now())
                .path(extractPath(request))
                .build();
        
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(error);
    }

    /**
     * Handles general runtime exceptions.
     * Internal message is logged but NOT exposed to the client.
     */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ErrorResponse> handleRuntimeException(
            RuntimeException ex, WebRequest request) {
        log.error("Runtime error: ", ex);
        
        ErrorResponse error = ErrorResponse.builder()
                .code("RUNTIME_ERROR")
                .message("An unexpected error occurred. Please try again later.")
                .timestamp(Instant.now())
                .path(extractPath(request))
                .build();
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }

    /**
     * Handles all other exceptions not caught by specific handlers.
     * Returns 500 Internal Server Error for unexpected errors.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGlobalException(
            Exception ex, WebRequest request) {
        log.error("Unexpected error: ", ex);
        
        ErrorResponse error = ErrorResponse.builder()
                .code("INTERNAL_SERVER_ERROR")
                .message("An unexpected error occurred. Please try again later.")
                .timestamp(Instant.now())
                .path(extractPath(request))
                .build();
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }

    /**
     * Extracts the request path from WebRequest.
     * 
     * @param request the web request
     * @return the cleaned request path
     */
    private String extractPath(WebRequest request) {
        return request.getDescription(false).replace("uri=", "");
    }
}
