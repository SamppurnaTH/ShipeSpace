package com.shipspace.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for feed items.
 * Combines ship data with associated project and user information.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FeedItemResponse {

    /**
     * Ship information.
     */
    private ShipResponse ship;

    /**
     * Project information associated with the ship.
     */
    private ProjectResponse project;

    /**
     * User profile information of the ship creator.
     */
    private UserProfileResponse user;

    /**
     * Whether the requesting user has liked this ship.
     * Null when the feed is served to an unauthenticated user.
     */
    private Boolean liked;
}
