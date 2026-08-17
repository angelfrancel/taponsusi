package com.taponsusi.buyerreputation.dto;

import com.taponsusi.buyerreputation.ReputationFlag;
import jakarta.validation.constraints.NotNull;

public record UpdateFlagRequest(
        @NotNull(message = "Flag is required")
        ReputationFlag flaggedAs,
        String notes
) {
}