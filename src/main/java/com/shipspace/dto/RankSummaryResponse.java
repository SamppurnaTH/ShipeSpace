package com.shipspace.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for user rank and level progression summary.
 * Contains the user's current ShipScore, level, rank tier, and next level score.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RankSummaryResponse {

    /**
     * The user's accumulated ShipScore points.
     */
    private int shipScore;

    /**
     * The user's current level (1-30).
     */
    private int level;

    /**
     * The user's current rank tier as a string (e.g., "DOCKED", "FLEET_ADMIRAL").
     */
    private String rank;

    /**
     * The ShipScore required to reach the next level.
     * Null if the user is at maximum level (30).
     */
    private Integer nextLevelScore;
}
