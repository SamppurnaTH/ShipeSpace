package com.shipspace.exception;

/**
 * Exception thrown when GitHub OAuth authentication fails.
 * This can occur during token exchange or user data retrieval.
 */
public class GitHubOAuthException extends RuntimeException {

    public GitHubOAuthException(String message) {
        super(message);
    }

    public GitHubOAuthException(String message, Throwable cause) {
        super(message, cause);
    }
}
