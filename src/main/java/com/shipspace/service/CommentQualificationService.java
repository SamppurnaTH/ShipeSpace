package com.shipspace.service;

import com.shipspace.model.Comment;
import org.springframework.stereotype.Service;

/**
 * Service for determining if a comment qualifies for QUALITY_COMMENT scoring.
 * 
 * A comment qualifies if it meets both criteria:
 * - Content length >= 50 characters
 * - Upvotes >= 2
 */
@Service
public class CommentQualificationService {

    /**
     * Determines if a comment qualifies for QUALITY_COMMENT scoring.
     * 
     * @param comment the comment to evaluate
     * @return true if the comment meets both qualification criteria, false otherwise
     * @throws IllegalArgumentException if comment is null
     */
    public boolean isQualified(Comment comment) {
        if (comment == null) {
            throw new IllegalArgumentException("Comment cannot be null");
        }

        // Check content length >= 50 characters
        String content = comment.getContent();
        if (content == null || content.length() < 50) {
            return false;
        }

        // Check upvotes >= 2
        Integer upvotes = comment.getUpvotes();
        if (upvotes == null || upvotes < 2) {
            return false;
        }

        // Both criteria met
        return true;
    }
}
