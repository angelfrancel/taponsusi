package com.taponsusi.buyerreputation;

import com.taponsusi.inventory.InventoryItem;
import com.taponsusi.inventory.InventoryItemRepository;
import com.taponsusi.livesession.LiveSession;
import com.taponsusi.livesession.LiveSessionRepository;
import com.taponsusi.order.Order;
import com.taponsusi.parser.PurchaseIntent;
import com.taponsusi.reservation.ReservationEngine;
import com.taponsusi.seller.Seller;
import com.taponsusi.seller.SellerRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;


@SpringBootTest
class ReputationDoesNotBlockReservationTest {

    @Autowired
    private SellerRepository sellerRepository;

    @Autowired
    private LiveSessionRepository liveSessionRepository;

    @Autowired
    private InventoryItemRepository inventoryItemRepository;

    @Autowired
    private BuyerReputationService buyerReputationService;

    @Autowired
    private ReservationEngine reservationEngine;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    void reputationLabelDoesNotPreventValidReservation() {
        TransactionTemplate transactionTemplate =
                new TransactionTemplate(transactionManager);

        TestData testData = transactionTemplate.execute(status -> {
            String suffix = UUID.randomUUID().toString();
            String buyerPlatformId = "LABELED_BUYER_" + suffix;

            Seller seller = sellerRepository.save(
                    new Seller(
                            "reputation-label-" + suffix + "@test.com",
                            "hash",
                            "Reputation Label Shop"
                    )
            );

            setCurrentSeller(seller.getId());

            LiveSession session = liveSessionRepository.save(
                    new LiveSession(
                            seller.getId(),
                            "Reputation Label Session",
                            "SIMULATION"
                    )
            );

            String reservationCode =
                    "LABEL" + suffix.replace("-", "").toUpperCase();

            InventoryItem item = inventoryItemRepository.save(
                    new InventoryItem(
                            seller.getId(),
                            session.getId(),
                            reservationCode,
                            "Labeled Buyer Item",
                            1,
                            1
                    )
            );

            buyerReputationService.setFlag(
                    seller.getId(),
                    buyerPlatformId,
                    ReputationFlag.BLOCKED,
                    "Seller-visible reputation label only"
            );

            return new TestData(
                    seller.getId(),
                    session.getId(),
                    item.getId(),
                    reservationCode,
                    buyerPlatformId
            );
        });

        assertThat(testData).isNotNull();

        PurchaseIntent intent = new PurchaseIntent(
                testData.buyerPlatformId(),
                testData.sellerId(),
                testData.sessionId(),
                testData.reservationCode(),
                1
        );

        Optional<Order> result = reservationEngine.reserve(
                "SIMULATION",
                "REPUTATION_LABEL_EVENT_" + UUID.randomUUID(),
                intent,
                testData.inventoryItemId()
        );

        assertThat(result)
                .as(
                        "Reputation labels must never automatically "
                                + "reject a valid reservation"
                )
                .isPresent();

        assertThat(result.orElseThrow().getBuyerPlatformId())
                .isEqualTo(testData.buyerPlatformId());

        Integer remainingStock = transactionTemplate.execute(status -> {
            setCurrentSeller(testData.sellerId());

            return inventoryItemRepository
                    .findById(testData.inventoryItemId())
                    .orElseThrow()
                    .getAvailableStock();
        });

        assertThat(remainingStock).isZero();
    }

    private void setCurrentSeller(Long sellerId) {
        entityManager.createNativeQuery(
                        "SELECT set_config("
                                + "'app.current_seller_id', "
                                + ":sellerId, "
                                + "true)"
                )
                .setParameter("sellerId", String.valueOf(sellerId))
                .getSingleResult();
    }

    private record TestData(
            Long sellerId,
            Long sessionId,
            Long inventoryItemId,
            String reservationCode,
            String buyerPlatformId
    ) {
    }
}