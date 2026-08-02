package com.taponsusi.common.dto;

import java.time.Instant;

public record ApiResponse<T>(
        boolean success,
        T data,
        String errorCode,
        String message,
        Instant timestamp
) {

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, data, null, null, Instant.now());
    }

    public static <T> ApiResponse<T> error(String errorCode, String message) {
        return new ApiResponse<>(false, null, errorCode, message, Instant.now());
    }
}