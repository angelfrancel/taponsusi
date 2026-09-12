package com.taponsusi.livesession;

import java.util.stream.Collectors;

public class LiveSessionNotReadyException extends RuntimeException {

    public LiveSessionNotReadyException(SessionPreflightResponse preflight) {
        super(buildMessage(preflight));
    }

    private static String buildMessage(SessionPreflightResponse preflight) {
        String failures = preflight.checks().stream()
                .filter(check -> !check.passed())
                .map(PreflightCheckResponse::message)
                .collect(Collectors.joining("; "));

        return "Live session is not ready: " + failures;
    }
}