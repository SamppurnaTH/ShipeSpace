package com.shipspace.exception;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for GlobalExceptionHandler.
 * Validates consistent error response formatting across all exception types.
 * 
 * Tests Requirements 9.2 and 9.4:
 * - Consistent JSON response formats with standard HTTP status codes
 * - Structured error responses with descriptive messages
 */
@DisplayName("GlobalExceptionHandler Tests")
class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler exceptionHandler;
    private WebRequest webRequest;

    @BeforeEach
    void setUp() {
        exceptionHandler = new GlobalExceptionHandler();
        webRequest = mock(WebRequest.class);
        when(webRequest.getDescription(false)).thenReturn("uri=/api/test");
    }

    @Test
    @DisplayName("Should handle GitHubOAuthException with 401 status")
    void handleGitHubOAuthException_ReturnsUnauthorized() {
        // Arrange
        GitHubOAuthException exception = new GitHubOAuthException("GitHub authentication failed");

        // Act
        ResponseEntity<ErrorResponse> response = exceptionHandler.handleGitHubOAuthException(exception, webRequest);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo("GITHUB_OAUTH_ERROR");
        assertThat(response.getBody().getMessage()).isEqualTo("GitHub authentication failed");
        assertThat(response.getBody().getPath()).isEqualTo("/api/test");
        assertThat(response.getBody().getTimestamp()).isNotNull();
        assertThat(response.getBody().getRequestId()).isNotNull();
    }

    @Test
    @DisplayName("Should handle InvalidTokenException with 401 status")
    void handleInvalidTokenException_ReturnsUnauthorized() {
        // Arrange
        InvalidTokenException exception = new InvalidTokenException("Token has expired");

        // Act
        ResponseEntity<ErrorResponse> response = exceptionHandler.handleInvalidTokenException(exception, webRequest);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo("INVALID_TOKEN");
        assertThat(response.getBody().getMessage()).isEqualTo("Token has expired");
        assertThat(response.getBody().getPath()).isEqualTo("/api/test");
    }

    @Test
    @DisplayName("Should handle AuthenticationException with 401 status")
    void handleAuthenticationException_ReturnsUnauthorized() {
        // Arrange
        AuthenticationException exception = mock(AuthenticationException.class);
        when(exception.getMessage()).thenReturn("Bad credentials");

        // Act
        ResponseEntity<ErrorResponse> response = exceptionHandler.handleAuthenticationException(exception, webRequest);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo("AUTHENTICATION_ERROR");
        assertThat(response.getBody().getMessage()).contains("Authentication failed");
        assertThat(response.getBody().getMessage()).contains("Bad credentials");
    }

    @Test
    @DisplayName("Should handle AccessDeniedException with 403 status")
    void handleAccessDeniedException_ReturnsForbidden() {
        // Arrange
        AccessDeniedException exception = new AccessDeniedException("Access is denied");

        // Act
        ResponseEntity<ErrorResponse> response = exceptionHandler.handleAccessDeniedException(exception, webRequest);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo("ACCESS_DENIED");
        assertThat(response.getBody().getMessage()).contains("Access denied");
    }

    @Test
    @DisplayName("Should handle ForbiddenException with 403 status")
    void handleForbiddenException_ReturnsForbidden() {
        // Arrange
        ForbiddenException exception = new ForbiddenException("update", "project");

        // Act
        ResponseEntity<ErrorResponse> response = exceptionHandler.handleForbiddenException(exception, webRequest);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo("FORBIDDEN");
        assertThat(response.getBody().getMessage()).contains("permission");
    }

    @Test
    @DisplayName("Should handle ResourceNotFoundException with 404 status")
    void handleResourceNotFoundException_ReturnsNotFound() {
        // Arrange
        ResourceNotFoundException exception = new ResourceNotFoundException("Project", "123");

        // Act
        ResponseEntity<ErrorResponse> response = exceptionHandler.handleResourceNotFoundException(exception, webRequest);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo("RESOURCE_NOT_FOUND");
        assertThat(response.getBody().getMessage()).contains("not found");
        assertThat(response.getBody().getMessage()).contains("123");
    }

    @Test
    @DisplayName("Should handle MethodArgumentNotValidException with 400 status and field errors")
    void handleValidationException_ReturnsBadRequestWithFieldErrors() {
        // Arrange
        BindingResult bindingResult = mock(BindingResult.class);
        FieldError fieldError1 = new FieldError("user", "email", "must not be blank");
        FieldError fieldError2 = new FieldError("user", "username", "size must be between 3 and 50");
        when(bindingResult.getAllErrors()).thenReturn(List.of(fieldError1, fieldError2));
        
        // Create a real MethodParameter using reflection
        java.lang.reflect.Method method;
        try {
            method = GlobalExceptionHandlerTest.class.getDeclaredMethod("dummyMethod", String.class);
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
        org.springframework.core.MethodParameter methodParameter = 
                new org.springframework.core.MethodParameter(method, 0);
        
        MethodArgumentNotValidException exception = new MethodArgumentNotValidException(methodParameter, bindingResult);

        // Act
        ResponseEntity<ErrorResponse> response = exceptionHandler.handleValidationException(exception, webRequest);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo("VALIDATION_ERROR");
        assertThat(response.getBody().getMessage()).isEqualTo("Validation failed");
        assertThat(response.getBody().getDetails()).isNotNull();
        assertThat(response.getBody().getDetails()).hasSize(2);
        assertThat(response.getBody().getDetails().get("email")).isEqualTo("must not be blank");
        assertThat(response.getBody().getDetails().get("username")).isEqualTo("size must be between 3 and 50");
    }
    
    // Dummy method for creating MethodParameter in tests
    @SuppressWarnings("unused")
    private void dummyMethod(String param) {
    }

    @Test
    @DisplayName("Should handle IllegalArgumentException with 400 status")
    void handleIllegalArgumentException_ReturnsBadRequest() {
        // Arrange
        IllegalArgumentException exception = new IllegalArgumentException("Invalid time window format");

        // Act
        ResponseEntity<ErrorResponse> response = exceptionHandler.handleIllegalArgumentException(exception, webRequest);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo("INVALID_ARGUMENT");
        assertThat(response.getBody().getMessage()).isEqualTo("Invalid time window format");
    }

    @Test
    @DisplayName("Should handle HttpMessageNotReadableException with 400 status")
    void handleHttpMessageNotReadableException_ReturnsBadRequest() {
        // Arrange
        HttpMessageNotReadableException exception = mock(HttpMessageNotReadableException.class);
        when(exception.getMessage()).thenReturn("JSON parse error");

        // Act
        ResponseEntity<ErrorResponse> response = exceptionHandler.handleHttpMessageNotReadableException(exception, webRequest);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo("MALFORMED_REQUEST");
        assertThat(response.getBody().getMessage()).contains("malformed");
    }

    @Test
    @DisplayName("Should handle MissingServletRequestParameterException with 400 status")
    void handleMissingServletRequestParameterException_ReturnsBadRequest() {
        // Arrange
        MissingServletRequestParameterException exception = 
                new MissingServletRequestParameterException("page", "int");

        // Act
        ResponseEntity<ErrorResponse> response = 
                exceptionHandler.handleMissingServletRequestParameterException(exception, webRequest);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo("MISSING_PARAMETER");
        assertThat(response.getBody().getMessage()).contains("page");
        assertThat(response.getBody().getMessage()).contains("missing");
    }

    @Test
    @DisplayName("Should handle MethodArgumentTypeMismatchException with 400 status")
    void handleMethodArgumentTypeMismatchException_ReturnsBadRequest() {
        // Arrange
        MethodArgumentTypeMismatchException exception = mock(MethodArgumentTypeMismatchException.class);
        when(exception.getName()).thenReturn("page");
        when(exception.getRequiredType()).thenReturn((Class) Integer.class);
        when(exception.getMessage()).thenReturn("Failed to convert value");

        // Act
        ResponseEntity<ErrorResponse> response = 
                exceptionHandler.handleMethodArgumentTypeMismatchException(exception, webRequest);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo("TYPE_MISMATCH");
        assertThat(response.getBody().getMessage()).contains("page");
        assertThat(response.getBody().getMessage()).contains("Integer");
    }

    @Test
    @DisplayName("Should handle MultibaseException with 502 status")
    void handleMultibaseException_ReturnsBadGateway() {
        // Arrange
        MultibaseException exception = new MultibaseException("Failed to upload to Multibase");

        // Act
        ResponseEntity<ErrorResponse> response = exceptionHandler.handleMultibaseException(exception, webRequest);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo("MULTIBASE_ERROR");
        assertThat(response.getBody().getMessage()).contains("Multibase");
    }

    @Test
    @DisplayName("Should handle RuntimeException with 500 status")
    void handleRuntimeException_ReturnsInternalServerError() {
        // Arrange
        RuntimeException exception = new RuntimeException("Unexpected runtime error");

        // Act
        ResponseEntity<ErrorResponse> response = exceptionHandler.handleRuntimeException(exception, webRequest);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo("RUNTIME_ERROR");
        assertThat(response.getBody().getMessage()).contains("unexpected error");
    }

    @Test
    @DisplayName("Should handle generic Exception with 500 status")
    void handleGlobalException_ReturnsInternalServerError() {
        // Arrange
        Exception exception = new Exception("Generic error");

        // Act
        ResponseEntity<ErrorResponse> response = exceptionHandler.handleGlobalException(exception, webRequest);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo("INTERNAL_SERVER_ERROR");
        assertThat(response.getBody().getMessage()).contains("unexpected error");
    }

    @Test
    @DisplayName("Should include requestId in all error responses")
    void allErrorResponses_IncludeRequestId() {
        // Arrange
        ResourceNotFoundException exception = new ResourceNotFoundException("Test", "123");

        // Act
        ResponseEntity<ErrorResponse> response = exceptionHandler.handleResourceNotFoundException(exception, webRequest);

        // Assert
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getRequestId()).isNotNull();
        assertThat(response.getBody().getRequestId()).isNotEmpty();
    }

    @Test
    @DisplayName("Should include timestamp in all error responses")
    void allErrorResponses_IncludeTimestamp() {
        // Arrange
        ResourceNotFoundException exception = new ResourceNotFoundException("Test", "123");

        // Act
        ResponseEntity<ErrorResponse> response = exceptionHandler.handleResourceNotFoundException(exception, webRequest);

        // Assert
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getTimestamp()).isNotNull();
    }

    @Test
    @DisplayName("Should include path in all error responses")
    void allErrorResponses_IncludePath() {
        // Arrange
        ResourceNotFoundException exception = new ResourceNotFoundException("Test", "123");

        // Act
        ResponseEntity<ErrorResponse> response = exceptionHandler.handleResourceNotFoundException(exception, webRequest);

        // Assert
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getPath()).isEqualTo("/api/test");
    }
}
