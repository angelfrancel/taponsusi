package com.taponsusi.reservationproof.dto;

import com.taponsusi.order.Order;

public record ProofVerificationResponse(
        boolean valid,
        Long orderId,
        String buyerPlatformId,
        Integer quantity,
        Long sequenceNumber
) {
    public static ProofVerificationResponse invalid() {
        return new ProofVerificationResponse(false, null, null, null, null);
    }

    public static ProofVerificationResponse valid(Order order, Long sequenceNumber) {
        return new ProofVerificationResponse(
                true, order.getId(), order.getBuyerPlatformId(), order.getQuantity(), sequenceNumber);
    }
}