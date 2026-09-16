package com.taponsusi.reservation;

import com.taponsusi.inventory.InventoryItem;
import com.taponsusi.inventory.InventoryItemRepository;
import com.taponsusi.livesession.LiveSession;
import com.taponsusi.livesession.LiveSessionRepository;
import com.taponsusi.order.Order;
import com.taponsusi.order.OrderRepository;
import com.taponsusi.parser.CommentEvent;
import com.taponsusi.parser.PurchaseIntent;
import com.taponsusi.parser.PurchaseIntentParser;
import com.taponsusi.platform.simulation.SimulationCommentGenerator;
import com.taponsusi.seller.Seller;
import com.taponsusi.seller.SellerRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;


@SpringBootTest
class ConcurrencyTest {

    @Autowired private SellerRepository sellerRepository;
    @Autowired private LiveSessionRepository liveSessionRepository;
    @Autowired private InventoryItemRepository inventoryItemRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private PurchaseIntentParser purchaseIntentParser;
    @Autowired private ReservationEngine reservationEngine;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    void fiveHundredConcurrentBuyersAgainstOneUnitOfStockYieldsExactlyOneOrder() throws InterruptedException {
        TransactionTemplate transactionTemplate =
                new TransactionTemplate(transactionManager);

        TestData testData = transactionTemplate.execute(status -> {
            Seller seller = sellerRepository.save(
                    new Seller(
                            "concurrency-test-"
                                    + System.currentTimeMillis()
                                    + "@test.com",
                            "hash",
                            "Concurrency Test Shop"
                    )
            );

            setCurrentSeller(seller.getId());

            LiveSession session = liveSessionRepository.save(
                    new LiveSession(
                            seller.getId(),
                            "Concurrency Test Session",
                            "SIMULATION"
                    )
            );

            entityManager.createNativeQuery("""
                UPDATE live_sessions
                SET status = 'LIVE'
                WHERE id = :sessionId
                """)
                    .setParameter("sessionId", session.getId())
                    .executeUpdate();

            entityManager.flush();
            entityManager.clear();

            InventoryItem item = inventoryItemRepository.save(
                    new InventoryItem(
                            seller.getId(),
                            session.getId(),
                            "TESTCODE",
                            "Test Item",
                            1,
                            1
                    )
            );

            return new TestData(
                    seller.getId(),
                    session.getId(),
                    item.getId()
            );
        });

        assertThat(testData).isNotNull();

        List<CommentEvent> events = SimulationCommentGenerator.generate(
                testData.sellerId(),
                testData.sessionId(),
                "TESTCODE",
                500
        );

        ExecutorService executor = Executors.newFixedThreadPool(10);
        AtomicInteger successfulReservations = new AtomicInteger();
        AtomicInteger outOfStockRejections = new AtomicInteger();
        AtomicInteger contentionRejections = new AtomicInteger();
        AtomicInteger duplicateResults = new AtomicInteger();
        ConcurrentLinkedQueue<Throwable> unexpectedFailures = new ConcurrentLinkedQueue<>();

        List<Callable<Void>> tasks = events.stream()
                .<Callable<Void>>map(event -> () -> {
                    try {
                        PurchaseIntent intent = purchaseIntentParser.parse(
                                event.sellerId(),
                                event.sessionId(),
                                event.platformUserId(),
                                event.rawMessage(),
                                event.sourceCreatedAt()
                        );
                        Optional<Order> result = reservationEngine.reserve(
                                event.platform(),
                                event.platformEventId(),
                                intent,
                                testData.inventoryItemId()
                        );

                        if (result.isPresent()) {
                            successfulReservations.incrementAndGet();
                        } else {
                            duplicateResults.incrementAndGet();
                        }
                    } catch (OutOfStockException e) {
                        outOfStockRejections.incrementAndGet();
                    } catch (ReservationContendedException e) {
                        contentionRejections.incrementAndGet();
                    } catch (Throwable unexpected) {
                        unexpectedFailures.add(unexpected);
                    }
                    return null;
                })
                .toList();

        List<Future<Void>> futures = executor.invokeAll(tasks);
        executor.shutdown();

        for (Future<Void> future : futures) {
            try {
                future.get();
            } catch (Exception unexpected) {
                unexpectedFailures.add(unexpected);
            }
        }

        VerificationResult verification = transactionTemplate.execute(status -> {
            setCurrentSeller(testData.sellerId());

            List<Order> orders =
                    orderRepository.findAllBySellerIdAndInventoryItemId(
                            testData.sellerId(),
                            testData.inventoryItemId()
                    );

            InventoryItem finalItem = inventoryItemRepository
                    .findById(testData.inventoryItemId())
                    .orElseThrow();

            Number processedCount = (Number) entityManager.createNativeQuery(
                            "SELECT COUNT(*) FROM processed_events "
                                    + "WHERE platform = 'SIMULATION' "
                                    + "AND platform_event_id LIKE :prefix")
                    .setParameter(
                            "prefix",
                            "SIM_EVENT_" + testData.sessionId() + "_%"
                    )
                    .getSingleResult();

            Number waitlistCount = (Number) entityManager
                    .createNativeQuery("""
                SELECT COUNT(*)
                FROM waitlist_entries
                WHERE seller_id = :sellerId
                  AND platform = 'SIMULATION'
                  AND platform_event_id LIKE :prefix
                  AND status = 'WAITING'
                """)
                    .setParameter(
                            "sellerId",
                            testData.sellerId()
                    )
                    .setParameter(
                            "prefix",
                            "SIM_EVENT_"
                                    + testData.sessionId()
                                    + "_%"
                    )
                    .getSingleResult();

            long proofCount = 0L;

            if (orders.size() == 1) {
                Number result = (Number) entityManager.createNativeQuery(
                                "SELECT COUNT(*) FROM reservation_proofs "
                                        + "WHERE seller_id = :sellerId "
                                        + "AND order_id = :orderId")
                        .setParameter("sellerId", testData.sellerId())
                        .setParameter("orderId", orders.get(0).getId())
                        .getSingleResult();

                proofCount = result.longValue();
            }

            return new VerificationResult(
                    orders,
                    finalItem.getAvailableStock(),
                    processedCount.longValue(),
                    waitlistCount.longValue(),
                    proofCount
            );
        });

        assertThat(verification).isNotNull();

        assertThat(unexpectedFailures)
                .as("No simulation task should fail with an unexpected exception")
                .isEmpty();

        assertThat(successfulReservations.get()).isEqualTo(1);
        assertThat(duplicateResults.get()).isZero();

        assertThat(
                successfulReservations.get()
                        + outOfStockRejections.get()
                        + contentionRejections.get()
                        + duplicateResults.get()
        ).isEqualTo(500);

        assertThat(verification.orders()).hasSize(1);
        assertThat(verification.orders().get(0).getQuantity()).isEqualTo(1);

        assertThat(verification.availableStock()).isZero();
        assertThat(verification.availableStock()).isGreaterThanOrEqualTo(0);

        long expectedProcessedEvents =
                successfulReservations.get()
                        + outOfStockRejections.get();

        assertThat(verification.processedEventCount())
                .as(
                        "Reservations and waitlist entries are committed as processed events"
                )
                .isEqualTo(expectedProcessedEvents);

        assertThat(verification.waitlistCount())
                .as(
                        "Every committed out-of-stock buyer enters the waitlist exactly once"
                )
                .isEqualTo(outOfStockRejections.get());

        assertThat(verification.proofCount()).isEqualTo(1L);
    }

    private record TestData(
            Long sellerId,
            Long sessionId,
            Long inventoryItemId
    ) {
    }

    private record VerificationResult(
            List<Order> orders,
            int availableStock,
            long processedEventCount,
            long waitlistCount,
            long proofCount
    ) {
    }

    private void setCurrentSeller(Long sellerId) {
        entityManager.createNativeQuery("SELECT set_config('app.current_seller_id', :sellerId, true)")
                .setParameter("sellerId", String.valueOf(sellerId))
                .getSingleResult();
    }
}
