package com.taponsusi.waitlist;

import com.taponsusi.audit.AuditService;
import com.taponsusi.common.exception.ResourceNotFoundException;
import com.taponsusi.inventory.InventoryItem;
import com.taponsusi.security.TenantContext;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

@Service
public class WaitlistResolutionService {

    private final WaitlistEntryRepository waitlistEntryRepository;
    private final WaitlistStateMachine stateMachine;
    private final WaitlistOfferService waitlistOfferService;
    private final TenantContext tenantContext;
    private final AuditService auditService;

    @PersistenceContext
    private EntityManager entityManager;

    public WaitlistResolutionService(
            WaitlistEntryRepository waitlistEntryRepository,
            WaitlistStateMachine stateMachine,
            WaitlistOfferService waitlistOfferService,
            TenantContext tenantContext,
            AuditService auditService
    ) {
        this.waitlistEntryRepository =
                waitlistEntryRepository;
        this.stateMachine = stateMachine;
        this.waitlistOfferService =
                waitlistOfferService;
        this.tenantContext = tenantContext;
        this.auditService = auditService;
    }

    @Transactional
    public WaitlistResolutionResult declineOffer(
            Long sellerId,
            Long inventoryItemId,
            Long waitlistEntryId,
            String buyerPlatformId
    ) {
        validateIdentifiers(
                sellerId,
                inventoryItemId,
                waitlistEntryId
        );

        if (buyerPlatformId == null
                || buyerPlatformId.isBlank()) {
            throw new IllegalArgumentException(
                    "Buyer platform ID is required"
            );
        }

        ResolutionContext context = lockOffer(
                sellerId,
                inventoryItemId,
                waitlistEntryId
        );

        WaitlistEntry entry = context.entry();

        if (!entry.getBuyerPlatformId()
                .equals(buyerPlatformId.trim())) {
            throw new WaitlistOfferNotAcceptableException(
                    "The responding buyer does not own this offer"
            );
        }

        stateMachine.validateTransition(
                entry.getStatus(),
                WaitlistStatus.DECLINED
        );

        releaseHeldStock(
                context.item(),
                entry
        );

        entry.markDeclined();

        auditService.record(
                sellerId,
                "WAITLIST_OFFER_DECLINED",
                "WaitlistEntry",
                entry.getId(),
                "Buyer "
                        + entry.getBuyerPlatformId()
                        + " declined an offer for "
                        + entry.getOfferedQuantity()
                        + " unit(s) of inventory item "
                        + inventoryItemId
        );

        Optional<WaitlistEntry> nextOffer =
                waitlistOfferService.offerNext(
                        sellerId,
                        inventoryItemId
                );

        return new WaitlistResolutionResult(
                entry,
                nextOffer
        );
    }

    @Transactional
    public WaitlistResolutionResult expireOffer(
            Long sellerId,
            Long inventoryItemId,
            Long waitlistEntryId
    ) {
        validateIdentifiers(
                sellerId,
                inventoryItemId,
                waitlistEntryId
        );

        ResolutionContext context = lockOffer(
                sellerId,
                inventoryItemId,
                waitlistEntryId
        );

        WaitlistEntry entry = context.entry();
        Instant now = Instant.now();

        if (entry.getOfferExpiresAt() == null
                || entry.getOfferExpiresAt().isAfter(now)) {
            throw new WaitlistOfferNotAcceptableException(
                    "Waitlist offer has not expired"
            );
        }

        stateMachine.validateTransition(
                entry.getStatus(),
                WaitlistStatus.EXPIRED
        );

        releaseHeldStock(
                context.item(),
                entry
        );

        entry.markExpired();

        auditService.record(
                sellerId,
                "WAITLIST_OFFER_EXPIRED",
                "WaitlistEntry",
                entry.getId(),
                "Offer expired for buyer "
                        + entry.getBuyerPlatformId()
                        + "; released "
                        + entry.getOfferedQuantity()
                        + " unit(s) of inventory item "
                        + inventoryItemId
        );

        Optional<WaitlistEntry> nextOffer =
                waitlistOfferService.offerNext(
                        sellerId,
                        inventoryItemId
                );

        return new WaitlistResolutionResult(
                entry,
                nextOffer
        );
    }

    private ResolutionContext lockOffer(
            Long sellerId,
            Long inventoryItemId,
            Long waitlistEntryId
    ) {
        tenantContext.applyCurrentSeller(
                entityManager,
                sellerId
        );

        /*
         * Maintain the same lock order used by offering and
         * promotion: inventory first, then waitlist entry.
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

        WaitlistEntry entry = waitlistEntryRepository
                .findByIdForPromotion(
                        waitlistEntryId,
                        sellerId,
                        inventoryItemId
                )
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Waitlist entry not found: "
                                        + waitlistEntryId
                        )
                );

        if (entry.getStatus()
                != WaitlistStatus.OFFERED) {
            throw new WaitlistOfferNotAcceptableException(
                    "Waitlist entry does not have an active offer"
            );
        }

        return new ResolutionContext(
                item,
                entry
        );
    }

    private void releaseHeldStock(
            InventoryItem item,
            WaitlistEntry entry
    ) {
        Integer heldQuantity =
                entry.getOfferedQuantity();

        if (heldQuantity == null
                || heldQuantity <= 0) {
            throw new IllegalStateException(
                    "Active waitlist offer has no held quantity"
            );
        }

        int restoredStock =
                item.getAvailableStock()
                        + heldQuantity;

        if (restoredStock > item.getTotalStock()) {
            throw new IllegalStateException(
                    "Releasing the waitlist hold would exceed total stock"
            );
        }

        item.setAvailableStock(restoredStock);
    }

    private void validateIdentifiers(
            Long sellerId,
            Long inventoryItemId,
            Long waitlistEntryId
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

        if (waitlistEntryId == null) {
            throw new IllegalArgumentException(
                    "Waitlist entry ID is required"
            );
        }
    }

    private record ResolutionContext(
            InventoryItem item,
            WaitlistEntry entry
    ) {
    }
}