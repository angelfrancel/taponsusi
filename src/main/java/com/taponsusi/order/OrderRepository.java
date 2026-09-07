package com.taponsusi.order;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrderRepository extends JpaRepository<Order, Long> {

    List<Order> findAllBySellerIdAndInventoryItemIdAndBuyerPlatformId(
            Long sellerId, Long inventoryItemId, String buyerPlatformId);

    List<Order> findAllBySellerIdAndSessionIdAndBuyerPlatformId(Long sellerId, Long sessionId, String buyerPlatformId);

    java.util.List<Order> findAllBySellerIdAndStatusAndExpiresAtBefore(
            Long sellerId, OrderStatus status, java.time.Instant threshold);

    java.util.List<Order> findAllBySellerIdAndStatusAndOverdueSinceBefore(
            Long sellerId, OrderStatus status, java.time.Instant threshold);

    java.util.List<Order> findAllBySellerIdAndInventoryItemId(Long sellerId, Long inventoryItemId);
}