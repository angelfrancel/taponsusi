package com.taponsusi.waitlist;

import com.taponsusi.inventory.InventoryItem;
import com.taponsusi.inventory.InventoryItemRepository;
import com.taponsusi.livesession.LiveSession;
import com.taponsusi.livesession.LiveSessionRepository;
import com.taponsusi.order.Order;
import com.taponsusi.order.OrderRepository;
import com.taponsusi.security.TenantContext;
import com.taponsusi.seller.Seller;
import com.taponsusi.seller.SellerRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class WaitlistPromotionIntegrationTest {

    @Autowired
    private SellerRepository sellerRepository;

    @Autowired
    private LiveSessionRepository liveSessionRepository;

    @Autowired
    private InventoryItemRepository inventoryItemRepository;

    @Autowired
    private WaitlistEntryRepository waitlistEntryRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private WaitlistOfferService waitlistOfferService;

    @Autowired
    private WaitlistPromotionService waitlistPromotionService;

    @Autowired
    private TenantContext tenantContext;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    void convertsHeldOfferIntoOrderWithoutDeductingStockAgain() {
        TestFixture fixture = createFixture();

        WaitlistEntry offeredEntry =
                waitlistOfferService.offerNext(
                        fixture.sellerId(),
                        fixture.inventoryItemId()
                ).orElseThrow();

        assertThat(offeredEntry.getOfferedQuantity())
                .isEqualTo(1);

        Order promotedOrder =
                waitlistPromotionService.acceptOffer(
                        fixture.sellerId(),
                        fixture.inventoryItemId(),
                        offeredEntry.getId(),
                        fixture.buyerPlatformId()
                );

        assertThat(promotedOrder.getId()).isNotNull();
        assertThat(promotedOrder.getQuantity())
                .isEqualTo(1);
        assertThat(promotedOrder.getRequestedQuantity())
                .isEqualTo(3);
        assertThat(promotedOrder.getExpiresAt())
                .isNotNull();

        PersistedState state =
                loadPersistedState(
                        fixture,
                        offeredEntry.getId()
                );

        assertThat(state.availableStock())
                .isZero();

        assertThat(state.waitlistStatus())
                .isEqualTo(WaitlistStatus.PROMOTED);

        assertThat(state.promotedOrderId())
                .isEqualTo(promotedOrder.getId());

        assertThat(state.orders()).hasSize(1);

        Order persistedOrder = state.orders().getFirst();

        assertThat(persistedOrder.getId())
                .isEqualTo(promotedOrder.getId());
        assertThat(persistedOrder.getBuyerPlatformId())
                .isEqualTo(fixture.buyerPlatformId());
        assertThat(persistedOrder.getQuantity())
                .isEqualTo(1);

        assertThat(state.proofCount())
                .isEqualTo(1L);
    }

    @Test
    void sameOfferCannotBeAcceptedTwice() {
        TestFixture fixture = createFixture();

        WaitlistEntry offeredEntry =
                waitlistOfferService.offerNext(
                        fixture.sellerId(),
                        fixture.inventoryItemId()
                ).orElseThrow();

        waitlistPromotionService.acceptOffer(
                fixture.sellerId(),
                fixture.inventoryItemId(),
                offeredEntry.getId(),
                fixture.buyerPlatformId()
        );

        assertThatThrownBy(() ->
                waitlistPromotionService.acceptOffer(
                        fixture.sellerId(),
                        fixture.inventoryItemId(),
                        offeredEntry.getId(),
                        fixture.buyerPlatformId()
                )
        )
                .isInstanceOf(
                        WaitlistOfferNotAcceptableException.class
                )
                .hasMessageContaining(
                        "does not have an active offer"
                );

        PersistedState state =
                loadPersistedState(
                        fixture,
                        offeredEntry.getId()
                );

        assertThat(state.orders()).hasSize(1);
        assertThat(state.proofCount())
                .isEqualTo(1L);
        assertThat(state.availableStock())
                .isZero();
    }

    private TestFixture createFixture() {
        return transactionTemplate.execute(status -> {
            Seller seller = sellerRepository.save(
                    new Seller(
                            "waitlist-promotion-"
                                    + UUID.randomUUID()
                                    + "@test.com",
                            "hash",
                            "Waitlist Promotion Test Shop"
                    )
            );

            tenantContext.applyCurrentSeller(
                    entityManager,
                    seller.getId()
            );

            LiveSession session =
                    liveSessionRepository.save(
                            new LiveSession(
                                    seller.getId(),
                                    "Waitlist Promotion Session",
                                    "SIMULATION"
                            )
                    );

            InventoryItem item =
                    inventoryItemRepository.save(
                            new InventoryItem(
                                    seller.getId(),
                                    session.getId(),
                                    "PROMOTE" + UUID.randomUUID(),
                                    "Waitlist Promotion Item",
                                    1,
                                    5
                            )
                    );

            String buyerPlatformId =
                    "buyer-" + UUID.randomUUID();

            waitlistEntryRepository.save(
                    new WaitlistEntry(
                            seller.getId(),
                            session.getId(),
                            item.getId(),
                            "SIMULATION",
                            "event-" + UUID.randomUUID(),
                            buyerPlatformId,
                            3,
                            Instant.now().minusSeconds(60)
                    )
            );

            return new TestFixture(
                    seller.getId(),
                    item.getId(),
                    buyerPlatformId
            );
        });
    }

    private PersistedState loadPersistedState(
            TestFixture fixture,
            Long waitlistEntryId
    ) {
        return transactionTemplate.execute(status -> {
            tenantContext.applyCurrentSeller(
                    entityManager,
                    fixture.sellerId()
            );

            InventoryItem item =
                    inventoryItemRepository
                            .findByIdAndSellerId(
                                    fixture.inventoryItemId(),
                                    fixture.sellerId()
                            )
                            .orElseThrow();

            WaitlistEntry entry =
                    waitlistEntryRepository
                            .findByIdAndSellerId(
                                    waitlistEntryId,
                                    fixture.sellerId()
                            )
                            .orElseThrow();

            List<Order> orders =
                    orderRepository
                            .findAllBySellerIdAndInventoryItemId(
                                    fixture.sellerId(),
                                    fixture.inventoryItemId()
                            );

            Number proofCount = (Number) entityManager
                    .createNativeQuery("""
                            SELECT COUNT(*)
                            FROM reservation_proofs
                            WHERE seller_id = :sellerId
                              AND order_id = :orderId
                            """)
                    .setParameter(
                            "sellerId",
                            fixture.sellerId()
                    )
                    .setParameter(
                            "orderId",
                            entry.getPromotedOrderId()
                    )
                    .getSingleResult();

            return new PersistedState(
                    item.getAvailableStock(),
                    entry.getStatus(),
                    entry.getPromotedOrderId(),
                    orders,
                    proofCount.longValue()
            );
        });
    }

    private record TestFixture(
            Long sellerId,
            Long inventoryItemId,
            String buyerPlatformId
    ) {
    }

    private record PersistedState(
            Integer availableStock,
            WaitlistStatus waitlistStatus,
            Long promotedOrderId,
            List<Order> orders,
            Long proofCount
    ) {
    }
}