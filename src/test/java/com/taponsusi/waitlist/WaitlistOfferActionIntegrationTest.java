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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class WaitlistOfferActionIntegrationTest {

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
    private WaitlistOfferActionHandler actionHandler;

    @Autowired
    private WaitlistOfferActionPayload actionPayload;

    @Autowired
    private TenantContext tenantContext;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    void acceptButtonCreatesExactlyOneOrder() {
        TestFixture fixture = createFixture();

        WaitlistEntry offeredEntry =
                waitlistOfferService.offerNext(
                        fixture.sellerId(),
                        fixture.inventoryItemId()
                ).orElseThrow();

        String payload =
                actionPayload.createAcceptPayload(
                        offeredEntry.getId()
                );

        WaitlistOfferActionResult result =
                actionHandler.handle(
                        fixture.sellerId(),
                        fixture.buyerPlatformId(),
                        payload
                ).orElseThrow();

        assertThat(result.action())
                .isEqualTo(
                        WaitlistOfferActionType.ACCEPT
                );

        assertThat(result.waitlistEntryId())
                .isEqualTo(offeredEntry.getId());

        assertThat(result.createdOrderId())
                .isNotNull();

        PersistedState state =
                loadState(
                        fixture,
                        offeredEntry.getId()
                );

        assertThat(state.waitlistStatus())
                .isEqualTo(WaitlistStatus.PROMOTED);

        assertThat(state.promotedOrderId())
                .isEqualTo(result.createdOrderId());

        assertThat(state.availableStock()).isZero();

        assertThat(state.orders()).hasSize(1);

        Order order = state.orders().getFirst();

        assertThat(order.getId())
                .isEqualTo(result.createdOrderId());

        assertThat(order.getBuyerPlatformId())
                .isEqualTo(
                        fixture.buyerPlatformId()
                );

        assertThat(order.getQuantity()).isEqualTo(1);
    }

    @Test
    void declineButtonReleasesHeldStockWithoutCreatingOrder() {
        TestFixture fixture = createFixture();
        WaitlistEntry offeredEntry = waitlistOfferService.offerNext(
                fixture.sellerId(), fixture.inventoryItemId()
        ).orElseThrow();

        WaitlistOfferActionResult result = actionHandler.handle(
                fixture.sellerId(),
                fixture.buyerPlatformId(),
                actionPayload.createDeclinePayload(offeredEntry.getId())
        ).orElseThrow();

        assertThat(result.action()).isEqualTo(WaitlistOfferActionType.DECLINE);
        assertThat(result.waitlistEntryId()).isEqualTo(offeredEntry.getId());
        assertThat(result.createdOrderId()).isNull();

        PersistedState state = loadState(fixture, offeredEntry.getId());
        assertThat(state.waitlistStatus()).isEqualTo(WaitlistStatus.DECLINED);
        assertThat(state.availableStock()).isEqualTo(1);
        assertThat(state.promotedOrderId()).isNull();
        assertThat(state.orders()).isEmpty();
    }

    @Test
    void differentBuyerCannotAcceptOfferedStock() {
        assertWrongBuyerCannotAct(true);
    }

    @Test
    void differentBuyerCannotDeclineOfferedStock() {
        assertWrongBuyerCannotAct(false);
    }

    @Test
    void repeatedAcceptKeepsOneOrderAndOneStockDeduction() {
        TestFixture fixture = createFixture();
        WaitlistEntry offeredEntry = waitlistOfferService.offerNext(
                fixture.sellerId(), fixture.inventoryItemId()
        ).orElseThrow();
        String payload = actionPayload.createAcceptPayload(offeredEntry.getId());

        WaitlistOfferActionResult firstResult = actionHandler.handle(
                fixture.sellerId(), fixture.buyerPlatformId(), payload
        ).orElseThrow();

        assertThatThrownBy(() -> actionHandler.handle(
                fixture.sellerId(), fixture.buyerPlatformId(), payload
        )).isInstanceOf(WaitlistOfferNotAcceptableException.class);

        PersistedState state = loadState(fixture, offeredEntry.getId());
        assertThat(state.waitlistStatus()).isEqualTo(WaitlistStatus.PROMOTED);
        assertThat(state.availableStock()).isZero();
        assertThat(state.orders()).hasSize(1);
        assertThat(state.promotedOrderId()).isEqualTo(firstResult.createdOrderId());
        assertThat(state.orders().getFirst().getId())
                .isEqualTo(state.promotedOrderId());
    }

    @Test
    void repeatedDeclineReleasesHeldStockOnlyOnce() {
        TestFixture fixture = createFixture();
        WaitlistEntry offeredEntry = waitlistOfferService.offerNext(
                fixture.sellerId(), fixture.inventoryItemId()
        ).orElseThrow();
        String payload = actionPayload.createDeclinePayload(offeredEntry.getId());

        actionHandler.handle(
                fixture.sellerId(), fixture.buyerPlatformId(), payload
        ).orElseThrow();

        assertThatThrownBy(() -> actionHandler.handle(
                fixture.sellerId(), fixture.buyerPlatformId(), payload
        )).isInstanceOf(WaitlistOfferNotAcceptableException.class);

        PersistedState state = loadState(fixture, offeredEntry.getId());
        assertThat(state.waitlistStatus()).isEqualTo(WaitlistStatus.DECLINED);
        assertThat(state.availableStock()).isEqualTo(1);
        assertThat(state.promotedOrderId()).isNull();
        assertThat(state.orders()).isEmpty();
    }

    private void assertWrongBuyerCannotAct(boolean accept) {
        TestFixture fixture = createFixture();
        WaitlistEntry offeredEntry = waitlistOfferService.offerNext(
                fixture.sellerId(), fixture.inventoryItemId()
        ).orElseThrow();
        String payload = accept
                ? actionPayload.createAcceptPayload(offeredEntry.getId())
                : actionPayload.createDeclinePayload(offeredEntry.getId());

        assertThatThrownBy(() -> actionHandler.handle(
                fixture.sellerId(), "another-buyer-" + UUID.randomUUID(), payload
        )).isInstanceOf(WaitlistOfferNotAcceptableException.class);

        PersistedState state = loadState(fixture, offeredEntry.getId());
        assertThat(state.waitlistStatus()).isEqualTo(WaitlistStatus.OFFERED);
        assertThat(state.availableStock()).isZero();
        assertThat(state.promotedOrderId()).isNull();
        assertThat(state.orders()).isEmpty();
    }

    private TestFixture createFixture() {
        return transactionTemplate.execute(status -> {
            Seller seller = sellerRepository.save(
                    new Seller(
                            "action-integration-"
                                    + UUID.randomUUID()
                                    + "@test.com",
                            "hash",
                            "Action Integration Shop"
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
                                    "Action Integration Session",
                                    "SIMULATION"
                            )
                    );

            InventoryItem item =
                    inventoryItemRepository.save(
                            new InventoryItem(
                                    seller.getId(),
                                    session.getId(),
                                    "ACTION" + UUID.randomUUID(),
                                    "Action Test Item",
                                    1,
                                    1
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
                            1,
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

    private PersistedState loadState(
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

            return new PersistedState(
                    item.getAvailableStock(),
                    entry.getStatus(),
                    entry.getPromotedOrderId(),
                    orders
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
            List<Order> orders
    ) {
    }
}
