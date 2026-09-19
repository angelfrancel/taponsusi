package com.taponsusi.waitlist;

import com.taponsusi.audit.AuditService;
import com.taponsusi.common.exception.ResourceNotFoundException;
import com.taponsusi.inventory.InventoryItem;
import com.taponsusi.order.Order;
import com.taponsusi.order.OrderRepository;
import com.taponsusi.reservationproof.ReservationProofService;
import com.taponsusi.security.TenantContext;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
public class WaitlistPromotionService {

    private final WaitlistEntryRepository waitlistEntryRepository;
    private final OrderRepository orderRepository;
    private final WaitlistStateMachine stateMachine;
    private final TenantContext tenantContext;
    private final ReservationProofService reservationProofService;
    private final AuditService auditService;
    private final long reservationWindowMinutes;

    @PersistenceContext
    private EntityManager entityManager;

    public WaitlistPromotionService(
            WaitlistEntryRepository waitlistEntryRepository,
            OrderRepository orderRepository,
            WaitlistStateMachine stateMachine,
            TenantContext tenantContext,
            ReservationProofService reservationProofService,
            AuditService auditService,
            @Value("${taponsusi.reservation.window-minutes}")
            long reservationWindowMinutes
    ) {
        this.waitlistEntryRepository =
                waitlistEntryRepository;
        this.orderRepository = orderRepository;
        this.stateMachine = stateMachine;
        this.tenantContext = tenantContext;
        this.reservationProofService =
                reservationProofService;
        this.auditService = auditService;
        this.reservationWindowMinutes =
                reservationWindowMinutes;
    }

    @Transactional
    public Order acceptOffer(
            Long sellerId,
            Long inventoryItemId,
            Long waitlistEntryId,
            String buyerPlatformId
    ) {
        validateRequiredValues(
                sellerId,
                inventoryItemId,
                waitlistEntryId,
                buyerPlatformId
        );

        tenantContext.applyCurrentSeller(
                entityManager,
                sellerId
        );

        /*
         * Every operation that changes or converts held stock
         * locks inventory first. This maintains one consistent
         * lock order and avoids deadlocks.
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

        if (!entry.getBuyerPlatformId()
                .equals(buyerPlatformId.trim())) {
            throw new WaitlistOfferNotAcceptableException(
                    "The responding buyer does not own this offer"
            );
        }

        if (entry.getStatus()
                != WaitlistStatus.OFFERED) {
            throw new WaitlistOfferNotAcceptableException(
                    "Waitlist entry does not have an active offer"
            );
        }

        Instant acceptedAt = Instant.now();

        if (entry.getOfferExpiresAt() == null
                || !entry.getOfferExpiresAt()
                .isAfter(acceptedAt)) {
            throw new WaitlistOfferNotAcceptableException(
                    "Waitlist offer has expired"
            );
        }

        if (entry.getOfferedQuantity() == null
                || entry.getOfferedQuantity() <= 0) {
            throw new IllegalStateException(
                    "Active waitlist offer has no held quantity"
            );
        }

        stateMachine.validateTransition(
                entry.getStatus(),
                WaitlistStatus.PROMOTED
        );

        /*
         * Stock was already deducted when the offer was created.
         * Do not deduct inventory again here.
         */
        Order order = new Order(
                sellerId,
                entry.getSessionId(),
                inventoryItemId,
                entry.getBuyerPlatformId(),
                entry.getRequestedQuantity(),
                entry.getOfferedQuantity()
        );

        order.setExpiresAt(
                acceptedAt.plus(
                        reservationWindowMinutes,
                        ChronoUnit.MINUTES
                )
        );

        Order savedOrder = orderRepository.save(order);

        reservationProofService.generateProof(
                savedOrder
        );

        entry.markPromoted(
                savedOrder.getId()
        );

        auditService.record(
                sellerId,
                "WAITLIST_PROMOTED",
                "WaitlistEntry",
                entry.getId(),
                "Buyer "
                        + entry.getBuyerPlatformId()
                        + " accepted an offer for "
                        + entry.getOfferedQuantity()
                        + " unit(s) of inventory item "
                        + inventoryItemId
                        + "; order "
                        + savedOrder.getId()
                        + " was created"
        );

        return savedOrder;
    }

    private void validateRequiredValues(
            Long sellerId,
            Long inventoryItemId,
            Long waitlistEntryId,
            String buyerPlatformId
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

        if (buyerPlatformId == null
                || buyerPlatformId.isBlank()) {
            throw new IllegalArgumentException(
                    "Buyer platform ID is required"
            );
        }
    }
}