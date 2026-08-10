package com.taponsusi.order;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrderRepository extends JpaRepository<Order, Long> {

    List<Order> findAllBySellerIdAndInventoryItemIdAndBuyerPlatformId(
            Long sellerId, Long inventoryItemId, String buyerPlatformId);
}