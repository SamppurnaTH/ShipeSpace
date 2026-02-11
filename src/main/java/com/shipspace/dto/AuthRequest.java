package com.shipspace.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for authentication requests.
 * Used for GitHub OAuth code exchange.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthRequest {

    /**
     * GitHub OAuth authorization code.
     */
    @NotBlank(message = "OAuth code is required")
    private String code;
}
