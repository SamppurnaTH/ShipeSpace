package com.shipspace.dto;

import com.shipspace.model.Ship;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * DTO for user activity responses.
 * Contains recent ships and engagement activity for a user.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserActivityResponse {

    /**
     * List of recent ships created by the user.
     */
    private List<Ship> recentShips;

    /**
     * Total number of ships created by the user.
     */
    private long totalShips;

    /**
     * Total engagement count (likes + comments) across all user's ships.
     */
    private long totalEngagement;
}
