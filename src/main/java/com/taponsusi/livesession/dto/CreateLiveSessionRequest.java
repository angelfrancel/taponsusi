package com.taponsusi.livesession.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateLiveSessionRequest(

        @NotBlank(message = "Title is required")
        String title,

        @NotBlank(message = "Platform is required")
        String platform
) {
}