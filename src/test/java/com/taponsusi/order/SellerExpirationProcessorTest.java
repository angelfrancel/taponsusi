package com.taponsusi.order;

import com.taponsusi.inventory.InventoryItem;
import com.taponsusi.inventory.InventoryItemRepository;
import com.taponsusi.livesession.LiveSession;
import com.taponsusi.livesession.LiveSessionRepository;
import com.taponsusi.seller.Seller;
import com.taponsusi.seller.SellerRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;


@SpringBootTest
class SellerExpirationProcessorTest {

    @Autowired
    private SellerRepository sellerRepository;

    @Autowired
    private LiveSessionRepository liveSessionRepository;

    @Autowired
    private InventoryItemRepository inventoryItemRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private SellerExpirationProcessor expirationProcessor;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    void processingOneSellerDoesNotExpireAnotherSellersOrder() {
        TransactionTemplate transactionTemplate =
                new TransactionTemplate(transactionManager);

        TestData testData = transactionTemplate.execute(status -> {
            String suffix = String.valueOf(System.nanoTime());

            Seller sellerA = sellerRepository.save(
                    new Seller(
                            "expiration-a-" + suffix + "@test.com",
                            "hash",
                            "Expiration Shop A"
                    )
            );

            Seller sellerB = sellerRepository.save(
                    new Seller(
                            "expiration-b-" + suffix + "@test.com",
                            "hash",
                            "Expiration Shop B"
                    )
            );

            setCurrentSeller(sellerA.getId());

            LiveSession sessionA = liveSessionRepository.save(
                    new LiveSession(
                            sellerA.getId(),
                            "Seller A Session",
                            "SIMULATION"
                    )
            );

            InventoryItem itemA = inventoryItemRepository.save(
                    new InventoryItem(
                            sellerA.getId(),
                            sessionA.getId(),
                            "EXPIRE-A-" + suffix,
                            "Seller A Item",
                            1,
                            1
                    )
            );

            Order orderA = new Order(
                    sellerA.getId(),
                    sessionA.getId(),
                    itemA.getId(),
                    "BUYER-A",
                    1,
                    1
            );
            orderA.setExpiresAt(Instant.now().minusSeconds(60));
            orderA = orderRepository.save(orderA);

            setCurrentSeller(sellerB.getId());

            LiveSession sessionB = liveSessionRepository.save(
                    new LiveSession(
                            sellerB.getId(),
                            "Seller B Session",
                            "SIMULATION"
                    )
            );

            InventoryItem itemB = inventoryItemRepository.save(
                    new InventoryItem(
                            sellerB.getId(),
                            sessionB.getId(),
                            "EXPIRE-B-" + suffix,
                            "Seller B Item",
                            1,
                            1
                    )
            );

            Order orderB = new Order(
                    sellerB.getId(),
                    sessionB.getId(),
                    itemB.getId(),
                    "BUYER-B",
                    1,
                    1
            );
            orderB.setExpiresAt(Instant.now().minusSeconds(60));
            orderB = orderRepository.save(orderB);

            return new TestData(
                    sellerA.getId(),
                    orderA.getId(),
                    sellerB.getId(),
                    orderB.getId()
            );
        });

        assertThat(testData).isNotNull();

        expirationProcessor.processSeller(testData.sellerAId());

        OrderStatus sellerAStatus = loadOrderStatus(
                testData.sellerAId(),
                testData.orderAId()
        );

        OrderStatus sellerBStatus = loadOrderStatus(
                testData.sellerBId(),
                testData.orderBId()
        );

        assertThat(sellerAStatus).isEqualTo(OrderStatus.OVERDUE);
        assertThat(sellerBStatus).isEqualTo(OrderStatus.PENDING);
    }

    private OrderStatus loadOrderStatus(Long sellerId, Long orderId) {
        TransactionTemplate transactionTemplate =
                new TransactionTemplate(transactionManager);

        return transactionTemplate.execute(status -> {
            setCurrentSeller(sellerId);

            return orderRepository.findById(orderId)
                    .orElseThrow()
                    .getStatus();
        });
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
            Long sellerAId,
            Long orderAId,
            Long sellerBId,
            Long orderBId
    ) {
    }
}