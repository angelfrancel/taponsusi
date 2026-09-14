package com.taponsusi.waitlist;

import com.taponsusi.common.exception.ResourceNotFoundException;
import com.taponsusi.inventory.InventoryItem;
import com.taponsusi.inventory.InventoryItemRepository;
import com.taponsusi.security.TenantContext;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
public class WaitlistService {

    private final InventoryItemRepository inventoryItemRepository;
    private final WaitlistEntryRepository waitlistEntryRepository;
    private final TenantContext tenantContext;

    @PersistenceContext
    private EntityManager entityManager;

    public WaitlistService(
            InventoryItemRepository inventoryItemRepository,
            WaitlistEntryRepository waitlistEntryRepository,
            TenantContext tenantContext
    ) {
        this.inventoryItemRepository = inventoryItemRepository;
        this.waitlistEntryRepository = waitlistEntryRepository;
        this.tenantContext = tenantContext;
    }

    @Transactional
    public Optional<WaitlistEntry> enqueue(
            Long sellerId,
            Long sessionId,
            Long inventoryItemId,
            String platform,
            String platformEventId,
            String buyerPlatformId,
            Integer requestedQuantity,
            Instant sourceCreatedAt
    ) {
        tenantContext.applyCurrentSeller(
                entityManager,
                sellerId
        );

        validateRequiredValues(
                platform,
                platformEventId,
                buyerPlatformId,
                requestedQuantity,
                sourceCreatedAt
        );

        InventoryItem item = inventoryItemRepository
                .findByIdAndSellerId(
                        inventoryItemId,
                        sellerId
                )
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Inventory item not found: "
                                        + inventoryItemId
                        )
                );

        if (!item.getSessionId().equals(sessionId)) {
            throw new IllegalArgumentException(
                    "Inventory item does not belong to session "
                            + sessionId
            );
        }

        /*
         * The unique database constraint and ON CONFLICT make this
         * idempotent even when two workers process the same event
         * concurrently.
         */
        @SuppressWarnings("unchecked")
        List<Number> insertedIds = entityManager
                .createNativeQuery("""
                        INSERT INTO waitlist_entries (
                            seller_id,
                            session_id,
                            inventory_item_id,
                            platform,
                            platform_event_id,
                            buyer_platform_id,
                            requested_quantity,
                            source_created_at,
                            status,
                            received_at,
                            created_at,
                            updated_at
                        )
                        VALUES (
                            :sellerId,
                            :sessionId,
                            :inventoryItemId,
                            :platform,
                            :platformEventId,
                            :buyerPlatformId,
                            :requestedQuantity,
                            :sourceCreatedAt,
                            'WAITING',
                            now(),
                            now(),
                            now()
                        )
                        ON CONFLICT (
                            platform,
                            platform_event_id
                        )
                        DO NOTHING
                        RETURNING id
                        """)
                .setParameter("sellerId", sellerId)
                .setParameter("sessionId", sessionId)
                .setParameter(
                        "inventoryItemId",
                        inventoryItemId
                )
                .setParameter(
                        "platform",
                        normalizePlatform(platform)
                )
                .setParameter(
                        "platformEventId",
                        platformEventId.trim()
                )
                .setParameter(
                        "buyerPlatformId",
                        buyerPlatformId.trim()
                )
                .setParameter(
                        "requestedQuantity",
                        requestedQuantity
                )
                .setParameter(
                        "sourceCreatedAt",
                        sourceCreatedAt
                )
                .getResultList();

        if (insertedIds.isEmpty()) {
            return Optional.empty();
        }

        Long waitlistEntryId =
                insertedIds.getFirst().longValue();

        return waitlistEntryRepository.findByIdAndSellerId(
                waitlistEntryId,
                sellerId
        );
    }

    private void validateRequiredValues(
            String platform,
            String platformEventId,
            String buyerPlatformId,
            Integer requestedQuantity,
            Instant sourceCreatedAt
    ) {
        if (!hasText(platform)) {
            throw new IllegalArgumentException(
                    "Platform is required"
            );
        }

        if (!hasText(platformEventId)) {
            throw new IllegalArgumentException(
                    "Platform event ID is required"
            );
        }

        if (!hasText(buyerPlatformId)) {
            throw new IllegalArgumentException(
                    "Buyer platform ID is required"
            );
        }

        if (requestedQuantity == null
                || requestedQuantity <= 0) {
            throw new IllegalArgumentException(
                    "Requested quantity must be positive"
            );
        }

        if (sourceCreatedAt == null) {
            throw new IllegalArgumentException(
                    "Source creation time is required"
            );
        }
    }

    private String normalizePlatform(String platform) {
        return platform.trim().toUpperCase();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}