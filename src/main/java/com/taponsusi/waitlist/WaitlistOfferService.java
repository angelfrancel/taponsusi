package com.taponsusi.waitlist;

import com.taponsusi.common.exception.ResourceNotFoundException;
import com.taponsusi.inventory.InventoryItem;
import com.taponsusi.security.TenantContext;
import com.taponsusi.messaging.OutboundMessageService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

@Service
public class WaitlistOfferService {

    private final WaitlistEntryRepository waitlistEntryRepository;
    private final WaitlistStateMachine stateMachine;
    private final TenantContext tenantContext;
    private final long offerWindowMinutes;
    private final OutboundMessageService outboundMessageService;

    @PersistenceContext
    private EntityManager entityManager;

    public WaitlistOfferService(
            WaitlistEntryRepository waitlistEntryRepository,
            WaitlistStateMachine stateMachine,
            TenantContext tenantContext,
            @Value("${taponsusi.waitlist.offer-window-minutes:10}")
            long offerWindowMinutes, OutboundMessageService outboundMessageService
    ) {
        this.waitlistEntryRepository = waitlistEntryRepository;
        this.stateMachine = stateMachine;
        this.tenantContext = tenantContext;
        this.offerWindowMinutes = offerWindowMinutes;
        this.outboundMessageService = outboundMessageService;
    }

    @Transactional
    public Optional<WaitlistEntry> offerNext(
            Long sellerId,
            Long inventoryItemId
    ) {
        if (sellerId == null) {
            throw new IllegalArgumentException(
                    "Seller ID is required"
            );
        }

        if (inventoryItemId == null) {
            throw new IllegalArgumentException(
                    "Inventory item ID is required"
            );
        }

        tenantContext.applyCurrentSeller(
                entityManager,
                sellerId
        );

        /*
         * Reservation processing locks this same inventory row.
         * Therefore, stock reservations and waitlist offers cannot
         * modify the available quantity concurrently.
         */
        InventoryItem item = entityManager.find(
                InventoryItem.class,
                inventoryItemId,
                LockModeType.PESSIMISTIC_WRITE
        );

        if (item == null
                || !sellerId.equals(item.getSellerId())) {
            throw new ResourceNotFoundException(
                    "Inventory item not found: "
                            + inventoryItemId
            );
        }

        if (item.getAvailableStock() <= 0) {
            return Optional.empty();
        }

        /*
         * Only one buyer is asked to decide at a time for a given
         * inventory item.
         */
        boolean activeOfferExists =
                waitlistEntryRepository
                        .existsBySellerIdAndInventoryItemIdAndStatus(
                                sellerId,
                                inventoryItemId,
                                WaitlistStatus.OFFERED
                        );

        if (activeOfferExists) {
            return Optional.empty();
        }

        Optional<WaitlistEntry> nextEntry =
                waitlistEntryRepository
                        .findNextWaitingForUpdate(
                                sellerId,
                                inventoryItemId
                        );

        if (nextEntry.isEmpty()) {
            return Optional.empty();
        }

        WaitlistEntry entry = nextEntry.get();

        stateMachine.validateTransition(
                entry.getStatus(),
                WaitlistStatus.OFFERED
        );

        int offeredQuantity = Math.min(
                entry.getRequestedQuantity(),
                item.getAvailableStock()
        );

        Instant offeredAt = Instant.now();
        Instant offerExpiresAt = offeredAt.plus(
                offerWindowMinutes,
                ChronoUnit.MINUTES
        );

        /*
         * Deducting available stock creates the temporary hold.
         * Accepting the offer must not deduct it a second time.
         */
        item.setAvailableStock(
                item.getAvailableStock() - offeredQuantity
        );

        entry.markOffered(
                offeredAt,
                offerExpiresAt,
                offeredQuantity
        );

        outboundMessageService.enqueueWaitlistOffer(
                entry
        );

        return Optional.of(entry);
    }
}