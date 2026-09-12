package com.taponsusi.livesession;

import java.util.List;

public record SessionPreflightResponse(
        Long sessionId,
        boolean ready,
        List<PreflightCheckResponse> checks
) {
}