package com.shipspace.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Utility class for JWT token operations.
 * Handles token generation, validation, and extraction of claims.
 * 
 * This utility implements Requirements 1.3, 1.4, and 1.5:
 * - Generates JWT tokens with user information
 * - Validates JWT tokens and handles expiration
 * - Supports token refresh functionality
 */
@Slf4j
@Component
public class JwtUtil {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration}")
    private Long expiration;

    @Value("${jwt.refresh-expiration}")
    private Long refreshExpiration;

    /**
     * Generates a JWT access token for the given user ID.
     *
     * @param userId the user ID to include in the token
     * @param username the username to include in the token
     * @return the generated JWT token string
     */
    public String generateToken(String userId, String username) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", userId);
        claims.put("username", username);
        claims.put("type", "access");
        
        return createToken(claims, userId, expiration);
    }

    /**
     * Generates a JWT refresh token for the given user ID.
     *
     * @param userId the user ID to include in the token
     * @return the generated refresh token string
     */
    public String generateRefreshToken(String userId) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", userId);
        claims.put("type", "refresh");
        
        return createToken(claims, userId, refreshExpiration);
    }

    /**
     * Creates a JWT token with the specified claims and expiration.
     *
     * @param claims the claims to include in the token
     * @param subject the subject (user ID) of the token
     * @param expirationTime the expiration time in milliseconds
     * @return the generated JWT token string
     */
    private String createToken(Map<String, Object> claims, String subject, Long expirationTime) {
        Instant now = Instant.now();
        Instant expiryDate = now.plusMillis(expirationTime);

        return Jwts.builder()
                .claims(claims)
                .subject(subject)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiryDate))
                .signWith(getSigningKey())
                .compact();
    }

    /**
     * Extracts the user ID from the JWT token.
     *
     * @param token the JWT token
     * @return the user ID
     */
    public String extractUserId(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    /**
     * Extracts the username from the JWT token.
     *
     * @param token the JWT token
     * @return the username, or null if not present
     */
    public String extractUsername(String token) {
        return extractClaim(token, claims -> claims.get("username", String.class));
    }

    /**
     * Extracts the token type from the JWT token.
     *
     * @param token the JWT token
     * @return the token type (access or refresh)
     */
    public String extractTokenType(String token) {
        return extractClaim(token, claims -> claims.get("type", String.class));
    }

    /**
     * Extracts the expiration date from the JWT token.
     *
     * @param token the JWT token
     * @return the expiration date
     */
    public Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    /**
     * Extracts a specific claim from the JWT token.
     *
     * @param token the JWT token
     * @param claimsResolver function to extract the desired claim
     * @param <T> the type of the claim
     * @return the extracted claim value
     */
    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    /**
     * Extracts all claims from the JWT token.
     *
     * @param token the JWT token
     * @return the claims
     * @throws ExpiredJwtException if the token is expired
     * @throws MalformedJwtException if the token is malformed
     * @throws SignatureException if the signature is invalid
     */
    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * Checks if the JWT token is expired.
     *
     * @param token the JWT token
     * @return true if the token is expired, false otherwise
     */
    public Boolean isTokenExpired(String token) {
        try {
            return extractExpiration(token).before(new Date());
        } catch (ExpiredJwtException e) {
            return true;
        }
    }

    /**
     * Validates the JWT token.
     * Checks if the token is well-formed, not expired, and has a valid signature.
     *
     * @param token the JWT token to validate
     * @return true if the token is valid, false otherwise
     */
    public Boolean validateToken(String token) {
        try {
            // Parse the token - this will throw exceptions if invalid
            extractAllClaims(token);
            
            // Check if token is expired
            if (isTokenExpired(token)) {
                log.debug("Token validation failed: token is expired");
                return false;
            }
            
            log.debug("Token validation successful");
            return true;
            
        } catch (ExpiredJwtException e) {
            log.debug("Token validation failed: token expired", e);
            return false;
        } catch (MalformedJwtException e) {
            log.error("Token validation failed: malformed token", e);
            return false;
        } catch (SignatureException e) {
            log.error("Token validation failed: invalid signature", e);
            return false;
        } catch (UnsupportedJwtException e) {
            log.error("Token validation failed: unsupported token", e);
            return false;
        } catch (IllegalArgumentException e) {
            log.error("Token validation failed: illegal argument", e);
            return false;
        }
    }

    /**
     * Validates that the token is a refresh token.
     *
     * @param token the JWT token
     * @return true if the token is a valid refresh token, false otherwise
     */
    public Boolean validateRefreshToken(String token) {
        if (!validateToken(token)) {
            return false;
        }
        
        String tokenType = extractTokenType(token);
        if (!"refresh".equals(tokenType)) {
            log.debug("Token validation failed: not a refresh token");
            return false;
        }
        
        return true;
    }

    /**
     * Gets the signing key for JWT operations.
     *
     * @return the secret key
     */
    private SecretKey getSigningKey() {
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
