package com.taponsusi.waitlist;

import com.taponsusi.audit.AuditService;
import com.taponsusi.inventory.InventoryItem;
import com.taponsusi.order.Order;
import com.taponsusi.order.OrderRepository;
import com.taponsusi.reservationproof.ReservationProofService;
import com.taponsusi.security.TenantContext;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WaitlistPromotionServiceTest {

    private static final Long SELLER_ID = 101L;
    private static final Long SESSION_ID = 201L;
    private static final Long INVENTORY_ITEM_ID = 301L;
    private static final Long WAITLIST_ENTRY_ID = 401L;
    private static final Long ORDER_ID = 501L;
    private static final String BUYER_ID = "BUYER-001";

    private WaitlistEntryRepository waitlistEntryRepository;
    private OrderRepository orderRepository;
    private TenantContext tenantContext;
    private ReservationProofService reservationProofService;
    private AuditService auditService;
    private EntityManager entityManager;
    private WaitlistPromotionService service;

    @BeforeEach
    void setUp() {
        waitlistEntryRepository =
                mock(WaitlistEntryRepository.class);
        orderRepository = mock(OrderRepository.class);
        tenantContext = mock(TenantContext.class);
        reservationProofService =
                mock(ReservationProofService.class);
        auditService = mock(AuditService.class);
        entityManager = mock(EntityManager.class);

        service = new WaitlistPromotionService(
                waitlistEntryRepository,
                orderRepository,
                new WaitlistStateMachine(),
                tenantContext,
                reservationProofService,
                auditService,
                60
        );

        ReflectionTestUtils.setField(
                service,
                "entityManager",
                entityManager
        );
    }

    @Test
    void acceptsActiveOfferWithoutDeductingStockAgain() {
        InventoryItem item = createItemWithHeldStock();
        WaitlistEntry entry = createOfferedEntry();

        when(entityManager.find(
                InventoryItem.class,
                INVENTORY_ITEM_ID,
                LockModeType.PESSIMISTIC_WRITE
        )).thenReturn(item);

        when(waitlistEntryRepository
                .findByIdForPromotion(
                        WAITLIST_ENTRY_ID,
                        SELLER_ID,
                        INVENTORY_ITEM_ID
                ))
                .thenReturn(Optional.of(entry));

        when(orderRepository.save(any(Order.class)))
                .thenAnswer(invocation -> {
                    Order order = invocation.getArgument(0);

                    ReflectionTestUtils.setField(
                            order,
                            "id",
                            ORDER_ID
                    );

                    return order;
                });

        Order result = service.acceptOffer(
                SELLER_ID,
                INVENTORY_ITEM_ID,
                WAITLIST_ENTRY_ID,
                BUYER_ID
        );

        assertThat(result.getId())
                .isEqualTo(ORDER_ID);
        assertThat(result.getSellerId())
                .isEqualTo(SELLER_ID);
        assertThat(result.getBuyerPlatformId())
                .isEqualTo(BUYER_ID);
        assertThat(result.getRequestedQuantity())
                .isEqualTo(3);
        assertThat(result.getQuantity())
                .isEqualTo(1);
        assertThat(result.getExpiresAt())
                .isAfter(Instant.now());

        assertThat(entry.getStatus())
                .isEqualTo(WaitlistStatus.PROMOTED);
        assertThat(entry.getPromotedOrderId())
                .isEqualTo(ORDER_ID);

        /*
         * Stock remains zero because the one available unit was
         * already held when the offer was created.
         */
        assertThat(item.getAvailableStock())
                .isZero();

        verify(reservationProofService)
                .generateProof(result);

        verify(auditService).record(
                SELLER_ID,
                "WAITLIST_PROMOTED",
                "WaitlistEntry",
                WAITLIST_ENTRY_ID,
                "Buyer BUYER-001 accepted an offer for "
                        + "1 unit(s) of inventory item "
                        + INVENTORY_ITEM_ID
                        + "; order "
                        + ORDER_ID
                        + " was created"
        );
    }

    @Test
    void rejectsResponseFromDifferentBuyer() {
        InventoryItem item = createItemWithHeldStock();
        WaitlistEntry entry = createOfferedEntry();

        when(entityManager.find(
                InventoryItem.class,
                INVENTORY_ITEM_ID,
                LockModeType.PESSIMISTIC_WRITE
        )).thenReturn(item);

        when(waitlistEntryRepository
                .findByIdForPromotion(
                        WAITLIST_ENTRY_ID,
                        SELLER_ID,
                        INVENTORY_ITEM_ID
                ))
                .thenReturn(Optional.of(entry));

        assertThatThrownBy(() ->
                service.acceptOffer(
                        SELLER_ID,
                        INVENTORY_ITEM_ID,
                        WAITLIST_ENTRY_ID,
                        "IMPERSONATOR"
                )
        )
                .isInstanceOf(
                        WaitlistOfferNotAcceptableException.class
                )
                .hasMessageContaining(
                        "does not own this offer"
                );

        verify(orderRepository, never())
                .save(any(Order.class));

        verify(reservationProofService, never())
                .generateProof(any(Order.class));
    }

    @Test
    void rejectsExpiredOffer() {
        InventoryItem item = createItemWithHeldStock();
        WaitlistEntry entry = createOfferedEntry();

        ReflectionTestUtils.setField(
                entry,
                "offerExpiresAt",
                Instant.now().minusSeconds(1)
        );

        when(entityManager.find(
                InventoryItem.class,
                INVENTORY_ITEM_ID,
                LockModeType.PESSIMISTIC_WRITE
        )).thenReturn(item);

        when(waitlistEntryRepository
                .findByIdForPromotion(
                        WAITLIST_ENTRY_ID,
                        SELLER_ID,
                        INVENTORY_ITEM_ID
                ))
                .thenReturn(Optional.of(entry));

        assertThatThrownBy(() ->
                service.acceptOffer(
                        SELLER_ID,
                        INVENTORY_ITEM_ID,
                        WAITLIST_ENTRY_ID,
                        BUYER_ID
                )
        )
                .isInstanceOf(
                        WaitlistOfferNotAcceptableException.class
                )
                .hasMessageContaining("expired");

        verify(orderRepository, never())
                .save(any(Order.class));
    }

    private InventoryItem createItemWithHeldStock() {
        InventoryItem item = new InventoryItem(
                SELLER_ID,
                SESSION_ID,
                "ITEM-CODE",
                "Test Item",
                1,
                5
        );

        item.setAvailableStock(0);
        return item;
    }

    private WaitlistEntry createOfferedEntry() {
        WaitlistEntry entry = new WaitlistEntry(
                SELLER_ID,
                SESSION_ID,
                INVENTORY_ITEM_ID,
                "SIMULATION",
                "EVENT-001",
                BUYER_ID,
                3,
                Instant.now().minusSeconds(120)
        );

        ReflectionTestUtils.setField(
                entry,
                "id",
                WAITLIST_ENTRY_ID
        );

        entry.markOffered(
                Instant.now().minusSeconds(60),
                Instant.now().plusSeconds(600),
                1
        );

        return entry;
    }
}