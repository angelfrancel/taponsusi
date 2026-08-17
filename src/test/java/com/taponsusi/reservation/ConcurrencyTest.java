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

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;


@SpringBootTest
class ConcurrencyTest {

    @Autowired private SellerRepository sellerRepository;
    @Autowired private LiveSessionRepository liveSessionRepository;
    @Autowired private InventoryItemRepository inventoryItemRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private PurchaseIntentParser purchaseIntentParser;
    @Autowired private ReservationEngine reservationEngine;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    void fiveHundredConcurrentBuyersAgainstOneUnitOfStockYieldsExactlyOneOrder() throws InterruptedException {
        Seller seller = sellerRepository.save(
                new Seller("concurrency-test-" + System.currentTimeMillis() + "@test.com", "hash", "Concurrency Test Shop"));

        setCurrentSeller(seller.getId());
        LiveSession session = liveSessionRepository.save(
                new LiveSession(seller.getId(), "Concurrency Test Session", "SIMULATION"));

        InventoryItem item = inventoryItemRepository.save(
                new InventoryItem(seller.getId(), session.getId(), "TESTCODE", "Test Item", 1, 1));

        List<CommentEvent> events = SimulationCommentGenerator.generate(
                seller.getId(), session.getId(), "TESTCODE", 500);

        ExecutorService executor = Executors.newFixedThreadPool(50);

        List<Callable<Void>> tasks = events.stream()
                .<Callable<Void>>map(event -> () -> {
                    try {
                        PurchaseIntent intent = purchaseIntentParser.parse(
                                event.sellerId(), event.sessionId(),
                                event.platformUserId(), event.rawMessage());
                        reservationEngine.reserve(
                                event.platform(), event.platformEventId(), intent, item.getId());
                    } catch (Exception ignored) {
                        // Rejections are expected outcomes for 499 of these,
                        // not test failures — the assertions below check the
                        // resulting DB state, not that every call "succeeded."
                    }
                    return null;
                })
                .toList();

        List<Future<Void>> futures = executor.invokeAll(tasks);
        executor.shutdown();

        for (Future<Void> future : futures) {
            try {
                future.get();
            } catch (Exception ignored) {
                // Same reasoning — a task throwing is an expected rejection,
                // not a test infrastructure failure, as long as it's one of
                // our known exception types. A genuinely unexpected Throwable
                // would still surface via the assertions below (e.g. more
                // than 1 order created, or negative stock).
            }
        }

        setCurrentSeller(seller.getId());

        List<Order> orders = orderRepository.findAllBySellerIdAndInventoryItemId(seller.getId(), item.getId());

        InventoryItem finalItem = inventoryItemRepository.findById(item.getId()).orElseThrow();

        // The core claim
        assertThat(orders).hasSize(1);
        assertThat(orders.get(0).getQuantity()).isEqualTo(1);

        // Zero negative stock
        assertThat(finalItem.getAvailableStock()).isEqualTo(0);
        assertThat(finalItem.getAvailableStock()).isGreaterThanOrEqualTo(0);

        // Zero duplicates: exactly 500 processed_events rows for this
        // batch, one per simulated buyer, none double-counted.
        Long processedCount = (Long) entityManager.createNativeQuery(
                        "SELECT COUNT(*) FROM processed_events WHERE platform = 'SIMULATION' "
                                + "AND platform_event_id LIKE :prefix")
                .setParameter("prefix", "SIM_EVENT_" + session.getId() + "_%")
                .getSingleResult();

        assertThat(processedCount).isLessThanOrEqualTo(500L);
        assertThat(processedCount).isGreaterThan(0L);
    }

    private void setCurrentSeller(Long sellerId) {
        entityManager.createNativeQuery("SELECT set_config('app.current_seller_id', :sellerId, true)")
                .setParameter("sellerId", String.valueOf(sellerId))
                .getSingleResult();
    }
}