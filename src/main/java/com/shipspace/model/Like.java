package com.shipspace.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Like document model representing a user's like on a ship.
 * Tracks engagement and prevents duplicate likes.
 */
@Document(collection = "likes")
@CompoundIndex(name = "user_ship_idx", def = "{'userId': 1, 'shipId': 1}", unique = true)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Like {

    @Id
    private String id;

    /**
     * ID of the user who liked the ship.
     */
    @NotBlank(message = "User ID is required")
    @Indexed
    private String userId;

    /**
     * ID of the ship that was liked.
     */
    @NotBlank(message = "Ship ID is required")
    @Indexed
    private String shipId;

    /**
     * Timestamp when the like was created.
     */
    @Builder.Default
    private Instant createdAt = Instant.now();
}
