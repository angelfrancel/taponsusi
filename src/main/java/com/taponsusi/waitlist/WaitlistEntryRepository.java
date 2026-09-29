package com.taponsusi.waitlist;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface WaitlistEntryRepository
        extends JpaRepository<WaitlistEntry, Long> {

    boolean existsByPlatformAndPlatformEventId(
            String platform,
            String platformEventId
    );

    boolean existsBySellerIdAndInventoryItemIdAndStatus(
            Long sellerId,
            Long inventoryItemId,
            WaitlistStatus status
    );

    List<WaitlistEntry>
    findAllBySellerIdAndInventoryItemIdOrderBySourceCreatedAtAscIdAsc(
            Long sellerId,
            Long inventoryItemId
    );

    @Query(
            value = """
                    SELECT *
                    FROM waitlist_entries
                    WHERE seller_id = :sellerId
                      AND inventory_item_id = :inventoryItemId
                      AND status = 'WAITING'
                    ORDER BY source_created_at ASC, id ASC
                    FOR UPDATE SKIP LOCKED
                    LIMIT 1
                    """,
            nativeQuery = true
    )
    Optional<WaitlistEntry> findNextWaitingForUpdate(
            @Param("sellerId") Long sellerId,
            @Param("inventoryItemId") Long inventoryItemId
    );

    @Query(
            value = """
                SELECT *
                FROM waitlist_entries
                WHERE id = :waitlistEntryId
                  AND seller_id = :sellerId
                  AND inventory_item_id = :inventoryItemId
                FOR UPDATE
                """,
            nativeQuery = true
    )
    Optional<WaitlistEntry> findByIdForPromotion(
            @Param("waitlistEntryId") Long waitlistEntryId,
            @Param("sellerId") Long sellerId,
            @Param("inventoryItemId") Long inventoryItemId
    );

    Optional<WaitlistEntry> findByIdAndSellerId(
            Long id,
            Long sellerId
    );

    List<WaitlistEntry>
    findTop50BySellerIdAndStatusAndOfferExpiresAtLessThanEqualOrderByOfferExpiresAtAscIdAsc(
            Long sellerId,
            WaitlistStatus status,
            Instant cutoff
    );
}
