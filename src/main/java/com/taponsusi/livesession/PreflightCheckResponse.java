package com.taponsusi.livesession;

public record PreflightCheckResponse(
        String code,
        boolean passed,
        String message
) {
}