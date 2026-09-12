package com.taponsusi.livesession;

import com.taponsusi.inventory.InventoryItem;
import com.taponsusi.inventory.InventoryItemRepository;
import com.taponsusi.order.Order;
import com.taponsusi.parser.PurchaseIntent;
import com.taponsusi.parser.PurchaseIntentParser;
import com.taponsusi.reservation.ReservationEngine;
import com.taponsusi.security.TenantContext;
import com.taponsusi.seller.Seller;
import com.taponsusi.seller.SellerRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class RealisticBuyerCommentReservationTest {

    private static final String BUYER_ID = "facebook-buyer-123";

    @Autowired
    private SellerRepository sellerRepository;

    @Autowired
    private LiveSessionRepository liveSessionRepository;

    @Autowired
    private InventoryItemRepository inventoryItemRepository;

    @Autowired
    private PurchaseIntentParser purchaseIntentParser;

    @Autowired
    private ReservationEngine reservationEngine;

    @Autowired
    private TenantContext tenantContext;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    void realisticMineCommentReservesCorrectInventoryItem() {
        TestFixture fixture = createLiveSessionWithInventory();

        PurchaseIntent intent = purchaseIntentParser.parse(
                fixture.sellerId(),
                fixture.sessionId(),
                BUYER_ID,
                "MINE SWERTE X2"
        );

        Optional<Order> result = reservationEngine.reserve(
                "FACEBOOK",
                "comment-" + UUID.randomUUID(),
                intent,
                fixture.inventoryItemId()
        );

        assertThat(result).isPresent();

        Order order = result.orElseThrow();

        assertThat(order.getSellerId())
                .isEqualTo(fixture.sellerId());

        assertThat(order.getSessionId())
                .isEqualTo(fixture.sessionId());

        assertThat(order.getInventoryItemId())
                .isEqualTo(fixture.inventoryItemId());

        assertThat(order.getBuyerPlatformId())
                .isEqualTo(BUYER_ID);

        assertThat(order.getRequestedQuantity())
                .isEqualTo(2);

        assertThat(order.getQuantity())
                .isEqualTo(2);

        Integer remainingStock =
                transactionTemplate.execute(status -> {
                    tenantContext.applyCurrentSeller(
                            entityManager,
                            fixture.sellerId()
                    );

                    return inventoryItemRepository
                            .findById(fixture.inventoryItemId())
                            .orElseThrow()
                            .getAvailableStock();
                });

        assertThat(remainingStock).isEqualTo(1);
    }

    private TestFixture createLiveSessionWithInventory() {
        return transactionTemplate.execute(status -> {
            Seller seller = sellerRepository.save(
                    new Seller(
                            "realistic-comment-"
                                    + UUID.randomUUID()
                                    + "@test.com",
                            "hash",
                            "Realistic Comment Test Shop"
                    )
            );

            tenantContext.applyCurrentSeller(
                    entityManager,
                    seller.getId()
            );

            LiveSession session = new LiveSession(
                    seller.getId(),
                    "Realistic Comment Test Session",
                    "SIMULATION"
            );

            session.setStatus(LiveSessionStatus.LIVE);

            LiveSession savedSession =
                    liveSessionRepository.save(session);

            InventoryItem item = new InventoryItem(
                    seller.getId(),
                    savedSession.getId(),
                    "SWERTE",
                    "Red Dress",
                    3,
                    3
            );

            InventoryItem savedItem =
                    inventoryItemRepository.save(item);

            return new TestFixture(
                    seller.getId(),
                    savedSession.getId(),
                    savedItem.getId()
            );
        });
    }

    private record TestFixture(
            Long sellerId,
            Long sessionId,
            Long inventoryItemId
    ) {
    }
}