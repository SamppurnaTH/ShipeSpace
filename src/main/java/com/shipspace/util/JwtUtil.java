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

import jakarta.annotation.PostConstruct;
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
 * Phase 1 fix: Signing key is now pre-computed once at startup via @PostConstruct
 * instead of being re-derived on every token operation. This eliminates the
 * per-request byte[] allocation and Keys.hmacShaKeyFor() call.
 *
 * Phase 1 fix: Startup validation ensures the secret is at least 32 bytes
 * (256 bits) â€” the minimum for HMAC-SHA256. A short secret causes JJWT to
 * throw WeakKeyException at runtime on the first token generation, which is
 * worse than a fast-fail at startup.
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

    // Pre-computed once at startup â€” not re-derived per request
    private SecretKey signingKey;

    /**
     * Validate the secret length and pre-compute the signing key.
     * Fails fast at startup rather than at runtime on the first token operation.
     */
    @PostConstruct
    public void init() {
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            throw new IllegalStateException(
                    "JWT secret must be at least 32 bytes (256 bits) for HMAC-SHA256. " +
                    "Current length: " + keyBytes.length + " bytes. " +
                    "Set a stronger JWT_SECRET environment variable.");
        }
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
        log.info("JWT signing key initialized ({} bytes)", keyBytes.length);
    }

    /**
     * Generates a JWT access token for the given user ID.
     * Includes a unique jti claim for blacklist support.
     */
    public String generateToken(String userId, String username) {
        return generateToken(userId, username, java.util.Set.of("USER"));
    }

    public String generateToken(String userId, String username, java.util.Set<String> roles) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", userId);
        claims.put("username", username);
        claims.put("type", "access");
        claims.put("jti", java.util.UUID.randomUUID().toString());
        claims.put("roles", roles);
        return createToken(claims, userId, expiration);
    }

    /**
     * Generates a JWT refresh token for the given user ID.
     * Includes a unique jti claim for blacklist support.
     */
    public String generateRefreshToken(String userId) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", userId);
        claims.put("type", "refresh");
        claims.put("jti", java.util.UUID.randomUUID().toString());
        return createToken(claims, userId, refreshExpiration);
    }

    private String createToken(Map<String, Object> claims, String subject, Long expirationTime) {
        Instant now = Instant.now();
        Instant expiryDate = now.plusMillis(expirationTime);

        return Jwts.builder()
                .claims(claims)
                .subject(subject)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiryDate))
                .signWith(signingKey)
                .compact();
    }

    public String extractUserId(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public String extractUsername(String token) {
        return extractClaim(token, claims -> claims.get("username", String.class));
    }

    public String extractTokenType(String token) {
        return extractClaim(token, claims -> claims.get("type", String.class));
    }

    public String extractJti(String token) {
        return extractClaim(token, claims -> claims.get("jti", String.class));
    }

    @SuppressWarnings("unchecked")
    public java.util.Set<String> extractRoles(String token) {
        try {
            Object roles = extractClaim(token, claims -> claims.get("roles"));
            if (roles instanceof java.util.Collection<?> c) {
                return new java.util.HashSet<>((java.util.Collection<String>) c);
            }
        } catch (Exception ignored) {}
        return java.util.Set.of("USER");
    }

    public Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public Boolean isTokenExpired(String token) {
        try {
            return extractExpiration(token).before(new Date());
        } catch (ExpiredJwtException e) {
            return true;
        }
    }

    public Boolean validateToken(String token) {
        try {
            extractAllClaims(token);
            if (isTokenExpired(token)) {
                log.debug("Token validation failed: token is expired");
                return false;
            }
            return true;
        } catch (ExpiredJwtException e) {
            log.debug("Token validation failed: token expired");
            return false;
        } catch (MalformedJwtException e) {
            log.error("Token validation failed: malformed token");
            return false;
        } catch (SignatureException e) {
            log.error("Token validation failed: invalid signature");
            return false;
        } catch (UnsupportedJwtException e) {
            log.error("Token validation failed: unsupported token");
            return false;
        } catch (IllegalArgumentException e) {
            log.error("Token validation failed: illegal argument");
            return false;
        }
    }

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
}
