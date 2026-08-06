package com.taponsusi.security;

import org.springframework.security.core.context.SecurityContextHolder;


public final class AuthenticatedSeller {

    private AuthenticatedSeller() {
    }

    public static Long currentSellerId() {
        Object principal = SecurityContextHolder.getContext()
                .getAuthentication()
                .getPrincipal();

        if (principal instanceof Long sellerId) {
            return sellerId;
        }

        throw new IllegalStateException("No authenticated seller in the current request context");
    }
}