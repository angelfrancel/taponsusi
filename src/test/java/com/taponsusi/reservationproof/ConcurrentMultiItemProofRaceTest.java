package com.taponsusi.reservationproof;

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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;


@SpringBootTest
class ConcurrentMultiItemProofRaceTest {

    private static final int RESERVATION_COUNT = 10;

    @Autowired
    private SellerRepository sellerRepository;

    @Autowired
    private LiveSessionRepository liveSessionRepository;

    @Autowired
    private InventoryItemRepository inventoryItemRepository;

    @Autowired
    private ReservationProofRepository reservationProofRepository;

    @Autowired
    private ReservationEngine reservationEngine;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    void concurrentReservationsForDifferentItemsProduceOneValidSellerWideChain()
            throws Exception {

        TransactionTemplate transactionTemplate =
                new TransactionTemplate(transactionManager);

        TestData testData = transactionTemplate.execute(status -> {
            String suffix = String.valueOf(System.nanoTime());

            Seller seller = sellerRepository.save(
                    new Seller(
                            "proof-race-" + suffix + "@test.com",
                            "hash",
                            "Proof Race Shop"
                    )
            );

            setCurrentSeller(seller.getId());

            LiveSession session = liveSessionRepository.save(
                    new LiveSession(
                            seller.getId(),
                            "Concurrent Proof Test",
                            "SIMULATION"
                    )
            );

            List<TestItem> items = new ArrayList<>();

            for (int index = 0; index < RESERVATION_COUNT; index++) {
                String code = "PROOF" + index + suffix;

                InventoryItem item = inventoryItemRepository.save(
                        new InventoryItem(
                                seller.getId(),
                                session.getId(),
                                code,
                                "Proof Item " + index,
                                1,
                                1
                        )
                );

                items.add(new TestItem(
                        item.getId(),
                        code,
                        "PROOF_BUYER_" + index
                ));
            }

            return new TestData(
                    seller.getId(),
                    session.getId(),
                    items
            );
        });

        assertThat(testData).isNotNull();

        ExecutorService executor =
                Executors.newFixedThreadPool(RESERVATION_COUNT);

        CountDownLatch workersReady =
                new CountDownLatch(RESERVATION_COUNT);

        CountDownLatch startSignal = new CountDownLatch(1);

        AtomicInteger successfulReservations = new AtomicInteger();

        ConcurrentLinkedQueue<Throwable> unexpectedFailures =
                new ConcurrentLinkedQueue<>();

        List<Future<?>> futures = new ArrayList<>();

        for (int index = 0; index < RESERVATION_COUNT; index++) {
            int claimIndex = index;
            TestItem item = testData.items().get(index);

            futures.add(executor.submit(() -> {
                workersReady.countDown();

                try {
                    startSignal.await();

                    PurchaseIntent intent = new PurchaseIntent(
                            item.buyerPlatformId(),
                            testData.sellerId(),
                            testData.sessionId(),
                            item.reservationCode(),
                            1
                    );

                    reservationEngine.reserve(
                            "SIMULATION",
                            "PROOF_RACE_EVENT_"
                                    + testData.sessionId()
                                    + "_"
                                    + claimIndex,
                            intent,
                            item.inventoryItemId()
                    );

                    successfulReservations.incrementAndGet();
                } catch (Throwable failure) {
                    unexpectedFailures.add(failure);
                }
            }));
        }

        assertThat(
                workersReady.await(10, TimeUnit.SECONDS)
        ).as("All proof workers should be ready").isTrue();

        startSignal.countDown();

        for (Future<?> future : futures) {
            future.get();
        }

        executor.shutdown();

        assertThat(
                executor.awaitTermination(10, TimeUnit.SECONDS)
        ).as("Proof workers should terminate").isTrue();

        List<ReservationProof> proofs =
                transactionTemplate.execute(status -> {
                    setCurrentSeller(testData.sellerId());

                    return reservationProofRepository.findAll()
                            .stream()
                            .sorted(
                                    Comparator.comparing(
                                            ReservationProof::getSequenceNumber
                                    )
                            )
                            .toList();
                });

        assertThat(unexpectedFailures)
                .as(
                        "Different inventory locks must not corrupt "
                                + "the seller-wide proof sequence"
                )
                .isEmpty();

        assertThat(successfulReservations.get())
                .isEqualTo(RESERVATION_COUNT);

        assertThat(proofs).isNotNull();
        assertThat(proofs).hasSize(RESERVATION_COUNT);

        List<Long> expectedSequences = LongStream
                .rangeClosed(1, RESERVATION_COUNT)
                .boxed()
                .toList();

        assertThat(proofs)
                .extracting(ReservationProof::getSequenceNumber)
                .containsExactlyElementsOf(expectedSequences);

        for (int index = 0; index < proofs.size(); index++) {
            ReservationProof proof = proofs.get(index);

            String expectedPreviousHash = index == 0
                    ? "GENESIS"
                    : proofs.get(index - 1).getHash();

            assertThat(proof.getPreviousHash())
                    .as(
                            "Proof sequence %s must link to its predecessor",
                            proof.getSequenceNumber()
                    )
                    .isEqualTo(expectedPreviousHash);

            assertThat(proof.getHash())
                    .as(
                            "Proof sequence %s must contain a valid hash",
                            proof.getSequenceNumber()
                    )
                    .isEqualTo(
                            sha256(
                                    proof.getProofData()
                                            + proof.getPreviousHash()
                            )
                    );
        }
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

    private String sha256(String input) {
        try {
            MessageDigest digest =
                    MessageDigest.getInstance("SHA-256");

            byte[] bytes = digest.digest(
                    input.getBytes(StandardCharsets.UTF_8)
            );

            StringBuilder result = new StringBuilder();

            for (byte value : bytes) {
                result.append(String.format("%02x", value));
            }

            return result.toString();
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "Unable to calculate test hash",
                    exception
            );
        }
    }

    private record TestData(
            Long sellerId,
            Long sessionId,
            List<TestItem> items
    ) {
    }

    private record TestItem(
            Long inventoryItemId,
            String reservationCode,
            String buyerPlatformId
    ) {
    }
}