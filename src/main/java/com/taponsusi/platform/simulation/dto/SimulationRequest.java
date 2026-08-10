package com.taponsusi.platform.simulation.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record SimulationRequest(

        @NotNull(message = "Session ID is required")
        Long sessionId,

        @NotBlank(message = "Inventory code is required")
        String inventoryCode,

        @NotNull(message = "simulatedBuyers is required")
        @Min(value = 1, message = "simulatedBuyers must be at least 1")
        Integer simulatedBuyers
) {
}