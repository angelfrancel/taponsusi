package com.taponsusi.inventory.dto;

import com.taponsusi.inventory.ReservationCodeMode;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateInventoryItemRequest(

        ReservationCodeMode codeMode,

        @Size(
                max = 20,
                message = "Reservation code must not exceed 20 characters"
        )
        @Pattern(
                regexp = "[A-Za-z0-9]+",
                message = "Reservation code may contain only letters and numbers"
        )
        String reservationCode,

        @NotBlank(message = "Name is required")
        String name,

        @NotNull(message = "Total stock is required")
        @Min(
                value = 1,
                message = "Total stock must be at least 1"
        )
        Integer totalStock,

        @Min(
                value = 1,
                message = "Maximum quantity per buyer must be at least 1"
        )
        Integer maxQuantityPerBuyer
) {

        /*
         * Preserves compatibility with Java code that used the original
         * four-argument constructor.
         */
        public CreateInventoryItemRequest(
                String reservationCode,
                String name,
                Integer totalStock,
                Integer maxQuantityPerBuyer
        ) {
                this(
                        ReservationCodeMode.MANUAL,
                        reservationCode,
                        name,
                        totalStock,
                        maxQuantityPerBuyer
                );
        }

        /*
         * Existing API clients do not send codeMode. Treat those requests
         * as manual-code requests instead of breaking them.
         */
        public ReservationCodeMode effectiveCodeMode() {
                return codeMode == null
                        ? ReservationCodeMode.MANUAL
                        : codeMode;
        }
}