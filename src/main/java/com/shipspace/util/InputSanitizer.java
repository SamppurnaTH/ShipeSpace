package com.shipspace.util;

import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * Strips HTML tags and dangerous characters from user-generated text fields.
 * Applied on write to bio, content, description, and message fields.
 *
 * Strategy: strip all HTML tags and HTML entities, then normalize whitespace.
 * This is a defence-in-depth measure — the frontend must also escape on render.
 *
 * Phase 1 fix: Added HTML entity decoding before tag stripping to prevent
 * bypass via encoded payloads like &lt;script&gt;. Without this, an attacker
 * could submit "&lt;script&gt;alert(1)&lt;/script&gt;" which survives tag
 * stripping and gets decoded by the browser into executable script.
 */
@Component
public class InputSanitizer {

    // Matches any HTML tag including self-closing and attributes
    private static final Pattern HTML_TAG_PATTERN = Pattern.compile("<[^>]*>", Pattern.DOTALL);
    // Matches HTML entities like &lt; &gt; &amp; &#x27; &#xNN; etc.
    private static final Pattern HTML_ENTITY_PATTERN = Pattern.compile("&(?:#x?[0-9a-fA-F]{1,6}|[a-zA-Z]{2,10});");
    // Null bytes and control characters (except tab, newline, carriage return)
    private static final Pattern CONTROL_CHAR_PATTERN = Pattern.compile("[\0\u0001-\u0008\u000B\u000C\u000E-\u001F\u007F]");
    // Unicode direction override characters — used in bidi attacks
    private static final Pattern BIDI_OVERRIDE_PATTERN = Pattern.compile("[\u202A-\u202E\u2066-\u2069\u200F\u200E]");

    /**
     * Sanitize a free-text field.
     * Order matters:
     *   1. Strip control chars and bidi overrides
     *   2. Decode HTML entities (prevents &lt;script&gt; bypass)
     *   3. Strip any HTML tags that were hidden behind entities
     *   4. Strip remaining HTML entities
     *   5. Normalize whitespace
     *
     * Returns null if input is null.
     */
    public String sanitize(String input) {
        if (input == null) return null;
        String result = CONTROL_CHAR_PATTERN.matcher(input).replaceAll("");
        result = BIDI_OVERRIDE_PATTERN.matcher(result).replaceAll("");
        // Decode common HTML entities before stripping tags
        result = decodeHtmlEntities(result);
        // Strip tags (catches anything that was entity-encoded)
        result = HTML_TAG_PATTERN.matcher(result).replaceAll("");
        // Strip any remaining raw entities
        result = HTML_ENTITY_PATTERN.matcher(result).replaceAll("");
        // Normalize multiple whitespace sequences to single space
        result = result.replaceAll("\\s{2,}", " ").trim();
        return result;
    }

    /**
     * Sanitize and enforce a maximum length.
     */
    public String sanitize(String input, int maxLength) {
        String sanitized = sanitize(input);
        if (sanitized == null) return null;
        return sanitized.length() > maxLength ? sanitized.substring(0, maxLength) : sanitized;
    }

    /**
     * Decode the most dangerous HTML entities that could hide script tags.
     * We only decode angle brackets and quotes — enough to expose hidden tags
     * without fully rendering HTML (which would require a full HTML parser).
     */
    private String decodeHtmlEntities(String input) {
        return input
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&#60;", "<")
                .replace("&#62;", ">")
                .replace("&#x3C;", "<")
                .replace("&#x3E;", ">")
                .replace("&#X3C;", "<")
                .replace("&#X3E;", ">")
                .replace("&amp;", "&");  // must be last to avoid double-decoding
    }
}
