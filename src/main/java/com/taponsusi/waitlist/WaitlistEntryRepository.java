package com.taponsusi.waitlist;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface WaitlistEntryRepository
        extends JpaRepository<WaitlistEntry, Long> {

    boolean existsByPlatformAndPlatformEventId(
            String platform,
            String platformEventId
    );

    List<WaitlistEntry>
    findAllBySellerIdAndInventoryItemIdOrderBySourceCreatedAtAscIdAsc(
            Long sellerId,
            Long inventoryItemId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT entry
            FROM WaitlistEntry entry
            WHERE entry.sellerId = :sellerId
              AND entry.inventoryItemId = :inventoryItemId
              AND entry.status = com.taponsusi.waitlist.WaitlistStatus.WAITING
            ORDER BY entry.sourceCreatedAt ASC, entry.id ASC
            """)
    List<WaitlistEntry> findWaitingForPromotion(
            @Param("sellerId") Long sellerId,
            @Param("inventoryItemId") Long inventoryItemId
    );

    Optional<WaitlistEntry> findByIdAndSellerId(
            Long id,
            Long sellerId
    );
}