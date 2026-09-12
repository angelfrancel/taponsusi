package com.taponsusi.livesession.dto;

import com.taponsusi.livesession.LiveSessionStatus;
import jakarta.validation.constraints.NotNull;

public record TransitionLiveSessionRequest(

        @NotNull(message = "Status is required")
        LiveSessionStatus status
) {
}