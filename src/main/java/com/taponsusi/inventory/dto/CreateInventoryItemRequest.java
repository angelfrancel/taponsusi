package com.taponsusi.inventory.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateInventoryItemRequest(

        @NotBlank(message = "Reservation code is required")
        String reservationCode,

        @NotBlank(message = "Name is required")
        String name,

        @NotNull(message = "Total stock is required")
        @Min(value = 1, message = "Total stock must be at least 1")
        Integer totalStock,

        Integer maxQuantityPerBuyer
) {
}