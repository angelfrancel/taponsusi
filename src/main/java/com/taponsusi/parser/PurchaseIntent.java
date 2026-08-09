package com.taponsusi.parser;


public record PurchaseIntent(
        String platformUserId,
        Long sellerId,
        Long sessionId,
        String reservationCode,
        int quantity
) {
}