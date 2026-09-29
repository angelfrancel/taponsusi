package com.taponsusi.platform.simulation.dto;

import jakarta.validation.constraints.NotBlank;

public record SimulationWaitlistActionRequest(
        @NotBlank(message = "Buyer platform ID is required")
        String buyerPlatformId,
        @NotBlank(message = "Action payload is required")
        String payload
) {
}
