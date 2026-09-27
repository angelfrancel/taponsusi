package com.taponsusi.waitlist;

import com.taponsusi.common.exception.ResourceNotFoundException;
import com.taponsusi.order.Order;
import com.taponsusi.security.TenantContext;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class WaitlistOfferActionHandler {

    private final WaitlistOfferActionPayload actionPayload;
    private final WaitlistEntryRepository waitlistEntryRepository;
    private final WaitlistPromotionService promotionService;
    private final WaitlistResolutionService resolutionService;
    private final TenantContext tenantContext;

    @PersistenceContext
    private EntityManager entityManager;

    public WaitlistOfferActionHandler(
            WaitlistOfferActionPayload actionPayload,
            WaitlistEntryRepository waitlistEntryRepository,
            WaitlistPromotionService promotionService,
            WaitlistResolutionService resolutionService,
            TenantContext tenantContext
    ) {
        this.actionPayload = actionPayload;
        this.waitlistEntryRepository =
                waitlistEntryRepository;
        this.promotionService = promotionService;
        this.resolutionService = resolutionService;
        this.tenantContext = tenantContext;
    }

    @Transactional
    public Optional<WaitlistOfferActionResult> handle(
            Long sellerId,
            String buyerPlatformId,
            String payload
    ) {
        validateIdentity(
                sellerId,
                buyerPlatformId
        );

        Optional<WaitlistOfferActionCommand> command =
                actionPayload.parse(payload);

        /*
         * An unrelated or malformed platform event is ignored.
         * It is not treated as an application failure.
         */
        if (command.isEmpty()) {
            return Optional.empty();
        }

        tenantContext.applyCurrentSeller(
                entityManager,
                sellerId
        );

        WaitlistOfferActionCommand action =
                command.get();

        WaitlistEntry entry =
                waitlistEntryRepository
                        .findByIdAndSellerId(
                                action.waitlistEntryId(),
                                sellerId
                        )
                        .orElseThrow(() ->
                                new ResourceNotFoundException(
                                        "Waitlist entry not found: "
                                                + action.waitlistEntryId()
                                )
                        );

        /*
         * The entry ID identifies the offer, while the real
         * platform buyer ID proves who clicked the button.
         */
        if (!entry.getBuyerPlatformId()
                .equals(buyerPlatformId.trim())) {
            throw new WaitlistOfferNotAcceptableException(
                    "The responding buyer does not own this offer"
            );
        }

        return Optional.of(
                switch (action.action()) {
                    case ACCEPT ->
                            accept(
                                    sellerId,
                                    buyerPlatformId.trim(),
                                    entry
                            );

                    case DECLINE ->
                            decline(
                                    sellerId,
                                    buyerPlatformId.trim(),
                                    entry
                            );
                }
        );
    }

    private WaitlistOfferActionResult accept(
            Long sellerId,
            String buyerPlatformId,
            WaitlistEntry entry
    ) {
        Order order = promotionService.acceptOffer(
                sellerId,
                entry.getInventoryItemId(),
                entry.getId(),
                buyerPlatformId
        );

        return WaitlistOfferActionResult.accepted(
                entry.getId(),
                order.getId()
        );
    }

    private WaitlistOfferActionResult decline(
            Long sellerId,
            String buyerPlatformId,
            WaitlistEntry entry
    ) {
        WaitlistResolutionResult resolution =
                resolutionService.declineOffer(
                        sellerId,
                        entry.getInventoryItemId(),
                        entry.getId(),
                        buyerPlatformId
                );

        Long nextOfferedEntryId =
                resolution.nextOffer()
                        .map(WaitlistEntry::getId)
                        .orElse(null);

        return WaitlistOfferActionResult.declined(
                entry.getId(),
                nextOfferedEntryId
        );
    }

    private void validateIdentity(
            Long sellerId,
            String buyerPlatformId
    ) {
        if (sellerId == null) {
            throw new IllegalArgumentException(
                    "Seller ID is required"
            );
        }

        if (buyerPlatformId == null
                || buyerPlatformId.isBlank()) {
            throw new IllegalArgumentException(
                    "Buyer platform ID is required"
            );
        }
    }
}