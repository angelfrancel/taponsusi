package com.taponsusi.order.dto;

import com.taponsusi.order.Order;
import com.taponsusi.order.OrderStatus;

import java.time.Instant;

public record OrderResponse(
        Long id,
        Long sessionId,
        Long inventoryItemId,
        String buyerPlatformId,
        Integer requestedQuantity,
        Integer quantity,
        OrderStatus status,
        Instant createdAt
) {
    public static OrderResponse from(Order order) {
        return new OrderResponse(
                order.getId(),
                order.getSessionId(),
                order.getInventoryItemId(),
                order.getBuyerPlatformId(),
                order.getRequestedQuantity(),
                order.getQuantity(),
                order.getStatus(),
                order.getCreatedAt()
        );
    }
}