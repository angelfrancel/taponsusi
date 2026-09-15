package com.taponsusi.parser;

import java.time.Instant;

public record PurchaseIntent(
        String platformUserId,
        Long sellerId,
        Long sessionId,
        String reservationCode,
        int quantity,
        Instant sourceCreatedAt
) {

    /*
     * Preserves compatibility with existing tests and internal code
     * that creates an intent without a platform event timestamp.
     */
    public PurchaseIntent(
            String platformUserId,
            Long sellerId,
            Long sessionId,
            String reservationCode,
            int quantity
    ) {
        this(
                platformUserId,
                sellerId,
                sessionId,
                reservationCode,
                quantity,
                Instant.now()
        );
    }
}