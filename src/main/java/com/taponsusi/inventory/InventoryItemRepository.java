package com.taponsusi.inventory;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface InventoryItemRepository extends JpaRepository<InventoryItem, Long> {

    List<InventoryItem> findAllBySellerIdAndSessionId(Long sellerId, Long sessionId);

    Optional<InventoryItem> findByIdAndSellerId(Long id, Long sellerId);

    boolean existsBySellerIdAndSessionIdAndReservationCode(
            Long sellerId, Long sessionId, String reservationCode);

    Optional<InventoryItem> findBySellerIdAndSessionIdAndReservationCode(
            Long sellerId, Long sessionId, String reservationCode);

}