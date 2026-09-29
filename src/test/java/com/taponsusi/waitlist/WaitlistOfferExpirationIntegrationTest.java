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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

@SpringBootTest
class WaitlistOfferExpirationIntegrationTest {

    @SpyBean
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
    private WaitlistOfferService offerService;

    @Autowired
    private WaitlistOfferExpirationCandidateReader candidateReader;

    @Autowired
    private WaitlistOfferExpirationScheduler scheduler;

    @Autowired
    private WaitlistResolutionService resolutionService;

    @Autowired
    private WaitlistPromotionService promotionService;

    @Autowired
    private TenantContext tenantContext;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @MockBean
    private Clock clock;

    @PersistenceContext
    private EntityManager entityManager;

    private Instant referenceNow;
    private final ThreadLocal<Instant> workerNow = new ThreadLocal<>();

    @BeforeEach
    void setUpClock() {
        referenceNow = Instant.now().truncatedTo(ChronoUnit.MICROS);
        when(clock.instant()).thenAnswer(invocation ->
                Optional.ofNullable(workerNow.get()).orElse(referenceNow)
        );
    }

    @Test
    void dueOfferExpiresAndRepeatedSchedulerRunDoesNotRestoreAgain() {
        Fixture fixture = createFixture(1);
        WaitlistEntry offered = offerFirst(fixture);
        setDeadline(fixture, offered.getId(), referenceNow.minusSeconds(60));
        scheduleOnly(fixture);

        scheduler.expireDueOffers();
        PersistedState afterFirstRun = loadState(fixture);
        assertThat(afterFirstRun.entries().getFirst().getStatus())
                .isEqualTo(WaitlistStatus.EXPIRED);
        assertThat(afterFirstRun.availableStock()).isEqualTo(1);
        assertThat(afterFirstRun.orders()).isEmpty();
        assertThat(candidateReader.findDueOffers(fixture.seller().getId()))
                .isEmpty();

        scheduler.expireDueOffers();
        PersistedState afterRetry = loadState(fixture);
        assertThat(afterRetry.entries().getFirst().getStatus())
                .isEqualTo(WaitlistStatus.EXPIRED);
        assertThat(afterRetry.availableStock()).isEqualTo(1);
        assertThat(afterRetry.orders()).isEmpty();
    }

    @Test
    void futureOfferIsNotSelectedOrExpired() {
        Fixture fixture = createFixture(1);
        WaitlistEntry offered = offerFirst(fixture);
        setDeadline(fixture, offered.getId(), referenceNow.plusSeconds(3600));
        scheduleOnly(fixture);

        assertThat(candidateReader.findDueOffers(fixture.seller().getId()))
                .isEmpty();
        scheduler.expireDueOffers();

        PersistedState state = loadState(fixture);
        assertThat(state.entries().getFirst().getStatus())
                .isEqualTo(WaitlistStatus.OFFERED);
        assertThat(state.availableStock()).isZero();
        assertThat(state.orders()).isEmpty();
    }

    @Test
    void offerIsDueAtItsExactDeadline() {
        Fixture fixture = createFixture(1);
        WaitlistEntry offered = offerFirst(fixture);
        setDeadline(fixture, offered.getId(), referenceNow);
        scheduleOnly(fixture);

        assertThat(candidateReader.findDueOffers(fixture.seller().getId()))
                .extracting(WaitlistOfferExpirationCandidateReader.Candidate::entryId)
                .containsExactly(offered.getId());
        scheduler.expireDueOffers();

        PersistedState state = loadState(fixture);
        assertThat(state.entries().getFirst().getStatus())
                .isEqualTo(WaitlistStatus.EXPIRED);
        assertThat(state.availableStock()).isEqualTo(1);
        assertThat(state.orders()).isEmpty();
    }

    @Test
    void expirationOffersHeldStockToEarliestWaitingBuyer() {
        Fixture fixture = createFixture(3);
        WaitlistEntry offered = offerFirst(fixture);
        setDeadline(fixture, offered.getId(), referenceNow.minusSeconds(60));
        scheduleOnly(fixture);

        scheduler.expireDueOffers();

        PersistedState state = loadState(fixture);
        assertThat(state.entries())
                .extracting(WaitlistEntry::getStatus)
                .containsExactly(
                        WaitlistStatus.EXPIRED,
                        WaitlistStatus.OFFERED,
                        WaitlistStatus.WAITING
                );
        assertThat(state.entries().get(1).getBuyerPlatformId())
                .isEqualTo(fixture.buyers().get(1));
        assertThat(state.entries().get(1).getOfferedQuantity()).isEqualTo(1);
        assertThat(state.availableStock()).isZero();
        assertThat(state.orders()).isEmpty();
    }

    @Test
    void candidateSelectionAndProcessingStayInsideSellerTenant() {
        Fixture firstSeller = createFixture(1);
        Fixture secondSeller = createFixture(1);
        WaitlistEntry firstOffer = offerFirst(firstSeller);
        WaitlistEntry secondOffer = offerFirst(secondSeller);
        setDeadline(firstSeller, firstOffer.getId(), referenceNow.minusSeconds(60));
        setDeadline(secondSeller, secondOffer.getId(), referenceNow.minusSeconds(60));

        assertThat(candidateReader.findDueOffers(firstSeller.seller().getId()))
                .extracting(WaitlistOfferExpirationCandidateReader.Candidate::entryId)
                .containsExactly(firstOffer.getId());

        List<WaitlistEntry> hidden = transactionTemplate.execute(status -> {
            tenantContext.applyCurrentSeller(
                    entityManager, firstSeller.seller().getId()
            );
            return waitlistEntryRepository
                    .findTop50BySellerIdAndStatusAndOfferExpiresAtLessThanEqualOrderByOfferExpiresAtAscIdAsc(
                            secondSeller.seller().getId(),
                            WaitlistStatus.OFFERED,
                            referenceNow
                    );
        });
        assertThat(hidden).isEmpty();

        scheduleOnly(firstSeller);
        scheduler.expireDueOffers();

        assertThat(loadState(firstSeller).entries().getFirst().getStatus())
                .isEqualTo(WaitlistStatus.EXPIRED);
        assertThat(loadState(secondSeller).entries().getFirst().getStatus())
                .isEqualTo(WaitlistStatus.OFFERED);
    }

    @Test
    void overlappingExpirationAttemptsReleaseOneHold() throws Exception {
        Fixture fixture = createFixture(1);
        WaitlistEntry offered = offerFirst(fixture);
        setDeadline(fixture, offered.getId(), referenceNow.minusSeconds(60));

        List<Boolean> outcomes = race(
                () -> tryExpire(fixture, offered.getId()),
                () -> tryExpire(fixture, offered.getId())
        );

        assertThat(outcomes).containsExactlyInAnyOrder(true, false);
        PersistedState state = loadState(fixture);
        assertThat(state.entries().getFirst().getStatus())
                .isEqualTo(WaitlistStatus.EXPIRED);
        assertThat(state.availableStock()).isEqualTo(1);
        assertThat(state.orders()).isEmpty();
    }

    @Test
    void acceptRacingExpirationHasExactlyOneValidOutcome() throws Exception {
        Fixture fixture = createFixture(1);
        WaitlistEntry offered = offerFirst(fixture);
        Instant deadline = referenceNow.plusSeconds(3600);
        setDeadline(fixture, offered.getId(), deadline);

        List<Boolean> outcomes = race(
                () -> {
                    workerNow.set(deadline.minusSeconds(1));
                    try {
                        promotionService.acceptOffer(
                                fixture.seller().getId(),
                                fixture.itemId(),
                                offered.getId(),
                                fixture.buyers().getFirst()
                        );
                        return true;
                    } catch (WaitlistOfferNotAcceptableException stale) {
                        return false;
                    } finally {
                        workerNow.remove();
                    }
                },
                () -> {
                    workerNow.set(deadline);
                    try {
                        return tryExpire(fixture, offered.getId());
                    } finally {
                        workerNow.remove();
                    }
                }
        );

        assertThat(outcomes).containsExactlyInAnyOrder(true, false);
        PersistedState state = loadState(fixture);
        WaitlistEntry resolved = state.entries().getFirst();
        if (resolved.getStatus() == WaitlistStatus.PROMOTED) {
            assertThat(state.orders()).hasSize(1);
            assertThat(resolved.getPromotedOrderId())
                    .isEqualTo(state.orders().getFirst().getId());
            assertThat(state.availableStock()).isZero();
        } else {
            assertThat(resolved.getStatus()).isEqualTo(WaitlistStatus.EXPIRED);
            assertThat(resolved.getPromotedOrderId()).isNull();
            assertThat(state.orders()).isEmpty();
            assertThat(state.availableStock()).isEqualTo(1);
        }
    }

    private boolean tryExpire(Fixture fixture, Long entryId) {
        try {
            resolutionService.expireOffer(
                    fixture.seller().getId(), fixture.itemId(), entryId
            );
            return true;
        } catch (WaitlistOfferNotAcceptableException stale) {
            return false;
        }
    }

    private List<Boolean> race(
            java.util.concurrent.Callable<Boolean> first,
            java.util.concurrent.Callable<Boolean> second
    ) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<Boolean> firstResult = executor.submit(() -> {
                ready.countDown();
                start.await();
                return first.call();
            });
            Future<Boolean> secondResult = executor.submit(() -> {
                ready.countDown();
                start.await();
                return second.call();
            });
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            return List.of(
                    firstResult.get(20, TimeUnit.SECONDS),
                    secondResult.get(20, TimeUnit.SECONDS)
            );
        } finally {
            executor.shutdownNow();
        }
    }

    private Fixture createFixture(int buyerCount) {
        return transactionTemplate.execute(status -> {
            Seller seller = sellerRepository.save(new Seller(
                    "offer-expiration-" + UUID.randomUUID() + "@test.com",
                    "hash",
                    "Offer Expiration Shop"
            ));
            tenantContext.applyCurrentSeller(entityManager, seller.getId());

            LiveSession session = liveSessionRepository.save(new LiveSession(
                    seller.getId(), "Offer Expiration Session", "SIMULATION"
            ));
            InventoryItem item = inventoryItemRepository.save(new InventoryItem(
                    seller.getId(), session.getId(),
                    "EXPIRE" + UUID.randomUUID(), "Offer Expiration Item", 1, 1
            ));

            List<String> buyers = new ArrayList<>();
            for (int index = 0; index < buyerCount; index++) {
                String buyerId = "buyer-" + UUID.randomUUID();
                buyers.add(buyerId);
                waitlistEntryRepository.save(new WaitlistEntry(
                        seller.getId(), session.getId(), item.getId(),
                        "SIMULATION", "event-" + UUID.randomUUID(),
                        buyerId, 1, referenceNow.minusSeconds(100 - index)
                ));
            }
            return new Fixture(seller, item.getId(), buyers);
        });
    }

    private WaitlistEntry offerFirst(Fixture fixture) {
        return offerService.offerNext(
                fixture.seller().getId(), fixture.itemId()
        ).orElseThrow();
    }

    private void setDeadline(Fixture fixture, Long entryId, Instant deadline) {
        transactionTemplate.executeWithoutResult(status -> {
            tenantContext.applyCurrentSeller(
                    entityManager, fixture.seller().getId()
            );
            entityManager.createNativeQuery("""
                    UPDATE waitlist_entries
                    SET offer_expires_at = :deadline
                    WHERE id = :entryId
                    """)
                    .setParameter("deadline", Timestamp.from(deadline))
                    .setParameter("entryId", entryId)
                    .executeUpdate();
        });
    }

    private void scheduleOnly(Fixture fixture) {
        doReturn(List.of(fixture.seller()))
                .when(sellerRepository).findAll();
    }

    private PersistedState loadState(Fixture fixture) {
        return transactionTemplate.execute(status -> {
            tenantContext.applyCurrentSeller(
                    entityManager, fixture.seller().getId()
            );
            InventoryItem item = inventoryItemRepository
                    .findByIdAndSellerId(fixture.itemId(), fixture.seller().getId())
                    .orElseThrow();
            List<WaitlistEntry> entries = waitlistEntryRepository
                    .findAllBySellerIdAndInventoryItemIdOrderBySourceCreatedAtAscIdAsc(
                            fixture.seller().getId(), fixture.itemId()
                    );
            List<Order> orders = orderRepository
                    .findAllBySellerIdAndInventoryItemId(
                            fixture.seller().getId(), fixture.itemId()
                    );
            return new PersistedState(item.getAvailableStock(), entries, orders);
        });
    }

    private record Fixture(Seller seller, Long itemId, List<String> buyers) {
    }

    private record PersistedState(
            int availableStock,
            List<WaitlistEntry> entries,
            List<Order> orders
    ) {
    }
}
