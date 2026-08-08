package com.taponsusi.inventory.dto;

import com.taponsusi.inventory.InventoryItem;

public record InventoryItemResponse(
        Long id,
        Long sessionId,
        String reservationCode,
        String name,
        Integer totalStock,
        Integer availableStock,
        Integer maxQuantityPerBuyer
) {
    public static InventoryItemResponse from(InventoryItem item) {
        return new InventoryItemResponse(
                item.getId(),
                item.getSessionId(),
                item.getReservationCode(),
                item.getName(),
                item.getTotalStock(),
                item.getAvailableStock(),
                item.getMaxQuantityPerBuyer()
        );
    }
}