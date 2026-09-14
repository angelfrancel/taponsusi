package com.taponsusi.waitlist;

import com.taponsusi.inventory.InventoryItem;
import com.taponsusi.inventory.InventoryItemRepository;
import com.taponsusi.livesession.LiveSession;
import com.taponsusi.livesession.LiveSessionRepository;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class ConcurrentWaitlistIdempotencyTest {

    private static final int WORKER_COUNT = 5;
    private static final int ATTEMPT_COUNT = 20;

    @Autowired
    private WaitlistService waitlistService;

    @Autowired
    private SellerRepository sellerRepository;

    @Autowired
    private LiveSessionRepository liveSessionRepository;

    @Autowired
    private InventoryItemRepository inventoryItemRepository;

    @Autowired
    private WaitlistEntryRepository waitlistEntryRepository;

    @Autowired
    private TenantContext tenantContext;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    void concurrentRedeliveryCreatesExactlyOneEntry()
            throws Exception {

        TestFixture fixture = createFixture();

        String eventId =
                "concurrent-event-" + UUID.randomUUID();

        Instant sourceTime =
                Instant.parse("2026-09-14T03:00:00Z");

        ExecutorService executor =
                Executors.newFixedThreadPool(WORKER_COUNT);

        CountDownLatch workersReady =
                new CountDownLatch(WORKER_COUNT);

        CountDownLatch start =
                new CountDownLatch(1);

        AtomicInteger entriesCreated =
                new AtomicInteger();

        ConcurrentLinkedQueue<Throwable> unexpectedFailures =
                new ConcurrentLinkedQueue<>();

        List<Callable<Void>> tasks = new ArrayList<>();

        for (int attempt = 0;
             attempt < ATTEMPT_COUNT;
             attempt++) {

            tasks.add(() -> {
                workersReady.countDown();
                start.await();

                try {
                    Optional<WaitlistEntry> result =
                            waitlistService.enqueue(
                                    fixture.sellerId(),
                                    fixture.sessionId(),
                                    fixture.inventoryItemId(),
                                    "FACEBOOK",
                                    eventId,
                                    "buyer-123",
                                    1,
                                    sourceTime
                            );

                    if (result.isPresent()) {
                        entriesCreated.incrementAndGet();
                    }
                } catch (Throwable unexpected) {
                    unexpectedFailures.add(unexpected);
                }

                return null;
            });
        }


        try {
            for (Callable<Void> task : tasks) {
                executor.submit(task);
            }

            workersReady.await();
            start.countDown();

            executor.shutdown();

            while (!executor.isTerminated()) {
                Thread.onSpinWait();
            }

        } finally {
            start.countDown();
            executor.shutdownNow();
        }

        /*
         * The variable exists only to keep the executor section clear.
         * Submitted tasks report failures through unexpectedFailures.
         */

        assertThat(unexpectedFailures)
                .as(
                        "Concurrent duplicate delivery must not produce unexpected failures"
                )
                .isEmpty();

        assertThat(entriesCreated.get())
                .as(
                        "Exactly one worker should insert the waitlist entry"
                )
                .isEqualTo(1);

        List<WaitlistEntry> storedEntries =
                transactionTemplate.execute(status -> {
                    tenantContext.applyCurrentSeller(
                            entityManager,
                            fixture.sellerId()
                    );

                    return waitlistEntryRepository
                            .findAllBySellerIdAndInventoryItemIdOrderBySourceCreatedAtAscIdAsc(
                                    fixture.sellerId(),
                                    fixture.inventoryItemId()
                            );
                });

        assertThat(storedEntries).hasSize(1);
        assertThat(storedEntries.getFirst().getPlatformEventId())
                .isEqualTo(eventId);
        assertThat(storedEntries.getFirst().getBuyerPlatformId())
                .isEqualTo("buyer-123");
    }

    private TestFixture createFixture() {
        return transactionTemplate.execute(status -> {
            Seller seller = sellerRepository.save(
                    new Seller(
                            "concurrent-waitlist-"
                                    + UUID.randomUUID()
                                    + "@test.com",
                            "hash",
                            "Concurrent Waitlist Test Shop"
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
                                    "Concurrent Waitlist Session",
                                    "SIMULATION"
                            )
                    );

            InventoryItem item =
                    inventoryItemRepository.save(
                            new InventoryItem(
                                    seller.getId(),
                                    session.getId(),
                                    "SWERTE",
                                    "Red Dress",
                                    1,
                                    1
                            )
                    );

            return new TestFixture(
                    seller.getId(),
                    session.getId(),
                    item.getId()
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