package com.taponsusi.buyerreputation.dto;

import com.taponsusi.buyerreputation.BuyerReputation;
import com.taponsusi.buyerreputation.ReputationFlag;

public record BuyerReputationResponse(
        Long id,
        String buyerPlatformId,
        Integer totalOrders,
        Integer ordersPaidOnTime,
        Integer ordersOverdue,
        Integer ordersCancelledBySeller,
        ReputationFlag flaggedAs,
        String notes
) {
    public static BuyerReputationResponse from(BuyerReputation r) {
        return new BuyerReputationResponse(
                r.getId(), r.getBuyerPlatformId(), r.getTotalOrders(),
                r.getOrdersPaidOnTime(), r.getOrdersOverdue(), r.getOrdersCancelledBySeller(),
                r.getFlaggedAs(), r.getNotes()
        );
    }
}