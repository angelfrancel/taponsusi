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
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class WaitlistOfferIntegrationTest {

    @Autowired
    private SellerRepository sellerRepository;

    @Autowired
    private LiveSessionRepository liveSessionRepository;

    @Autowired
    private InventoryItemRepository inventoryItemRepository;

    @Autowired
    private WaitlistEntryRepository waitlistEntryRepository;

    @Autowired
    private WaitlistOfferService waitlistOfferService;

    @Autowired
    private TenantContext tenantContext;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    void offersAvailableStockToEarliestWaitingBuyer() {
        TestFixture fixture = createFixture();

        Optional<WaitlistEntry> offered =
                waitlistOfferService.offerNext(
                        fixture.sellerId(),
                        fixture.inventoryItemId()
                );

        assertThat(offered).isPresent();
        assertThat(offered.get().getBuyerPlatformId())
                .isEqualTo("buyer-earliest");
        assertThat(offered.get().getStatus())
                .isEqualTo(WaitlistStatus.OFFERED);
        assertThat(offered.get().getOfferedQuantity())
                .isEqualTo(2);
        assertThat(offered.get().getOfferedAt())
                .isNotNull();
        assertThat(offered.get().getOfferExpiresAt())
                .isAfter(offered.get().getOfferedAt());

        PersistedState persistedState =
                transactionTemplate.execute(status -> {
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

                    List<WaitlistEntry> entries =
                            waitlistEntryRepository
                                    .findAllBySellerIdAndInventoryItemIdOrderBySourceCreatedAtAscIdAsc(
                                            fixture.sellerId(),
                                            fixture.inventoryItemId()
                                    );

                    return new PersistedState(
                            item.getAvailableStock(),
                            entries
                    );
                });

        assertThat(persistedState).isNotNull();
        assertThat(persistedState.availableStock())
                .isZero();

        assertThat(persistedState.entries())
                .extracting(WaitlistEntry::getBuyerPlatformId)
                .containsExactly(
                        "buyer-earliest",
                        "buyer-later"
                );

        assertThat(persistedState.entries().get(0).getStatus())
                .isEqualTo(WaitlistStatus.OFFERED);

        assertThat(
                persistedState.entries()
                        .get(0)
                        .getOfferedQuantity()
        ).isEqualTo(2);

        assertThat(persistedState.entries().get(1).getStatus())
                .isEqualTo(WaitlistStatus.WAITING);
    }

    @Test
    void secondCallDoesNotCreateAnotherActiveOffer() {
        TestFixture fixture = createFixture();

        Optional<WaitlistEntry> firstOffer =
                waitlistOfferService.offerNext(
                        fixture.sellerId(),
                        fixture.inventoryItemId()
                );

        Optional<WaitlistEntry> secondOffer =
                waitlistOfferService.offerNext(
                        fixture.sellerId(),
                        fixture.inventoryItemId()
                );

        assertThat(firstOffer).isPresent();
        assertThat(secondOffer).isEmpty();

        Long offeredCount =
                transactionTemplate.execute(status -> {
                    tenantContext.applyCurrentSeller(
                            entityManager,
                            fixture.sellerId()
                    );

                    return waitlistEntryRepository
                            .findAllBySellerIdAndInventoryItemIdOrderBySourceCreatedAtAscIdAsc(
                                    fixture.sellerId(),
                                    fixture.inventoryItemId()
                            )
                            .stream()
                            .filter(entry ->
                                    entry.getStatus()
                                            == WaitlistStatus.OFFERED
                            )
                            .count();
                });

        assertThat(offeredCount).isEqualTo(1L);
    }

    private TestFixture createFixture() {
        return transactionTemplate.execute(status -> {
            Seller seller = sellerRepository.save(
                    new Seller(
                            "waitlist-offer-"
                                    + UUID.randomUUID()
                                    + "@test.com",
                            "hash",
                            "Waitlist Offer Test Shop"
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
                                    "Waitlist Offer Session",
                                    "SIMULATION"
                            )
                    );

            InventoryItem item =
                    inventoryItemRepository.save(
                            new InventoryItem(
                                    seller.getId(),
                                    session.getId(),
                                    "OFFER" + UUID.randomUUID(),
                                    "Waitlist Offer Item",
                                    2,
                                    5
                            )
                    );

            /*
             * Save the later event first. The source timestamp,
             * rather than insertion order, must determine who is
             * offered the stock.
             */
            waitlistEntryRepository.save(
                    new WaitlistEntry(
                            seller.getId(),
                            session.getId(),
                            item.getId(),
                            "SIMULATION",
                            "later-" + UUID.randomUUID(),
                            "buyer-later",
                            1,
                            Instant.parse(
                                    "2026-09-18T10:00:02Z"
                            )
                    )
            );

            waitlistEntryRepository.save(
                    new WaitlistEntry(
                            seller.getId(),
                            session.getId(),
                            item.getId(),
                            "SIMULATION",
                            "earliest-" + UUID.randomUUID(),
                            "buyer-earliest",
                            3,
                            Instant.parse(
                                    "2026-09-18T10:00:01Z"
                            )
                    )
            );

            return new TestFixture(
                    seller.getId(),
                    item.getId()
            );
        });
    }

    private record TestFixture(
            Long sellerId,
            Long inventoryItemId
    ) {
    }

    private record PersistedState(
            Integer availableStock,
            List<WaitlistEntry> entries
    ) {
    }
}