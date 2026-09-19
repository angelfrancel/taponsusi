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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class WaitlistResolutionIntegrationTest {

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
    private WaitlistResolutionService waitlistResolutionService;

    @Autowired
    private TenantContext tenantContext;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    void decliningOfferReleasesStockToNextBuyer() {
        TestFixture fixture = createFixture();

        WaitlistEntry firstOffer =
                waitlistOfferService.offerNext(
                        fixture.sellerId(),
                        fixture.inventoryItemId()
                ).orElseThrow();

        assertThat(firstOffer.getBuyerPlatformId())
                .isEqualTo(fixture.firstBuyerId());

        WaitlistResolutionResult result =
                waitlistResolutionService.declineOffer(
                        fixture.sellerId(),
                        fixture.inventoryItemId(),
                        firstOffer.getId(),
                        fixture.firstBuyerId()
                );

        assertThat(result.resolvedEntry().getStatus())
                .isEqualTo(WaitlistStatus.DECLINED);

        assertThat(result.nextOffer()).isPresent();
        assertThat(
                result.nextOffer()
                        .orElseThrow()
                        .getBuyerPlatformId()
        ).isEqualTo(fixture.secondBuyerId());

        PersistedState state = loadState(fixture);

        /*
         * The first hold was released and immediately held for
         * the second buyer, so available stock remains zero.
         */
        assertThat(state.availableStock()).isZero();

        assertThat(state.entries())
                .extracting(WaitlistEntry::getBuyerPlatformId)
                .containsExactly(
                        fixture.firstBuyerId(),
                        fixture.secondBuyerId()
                );

        assertThat(state.entries().get(0).getStatus())
                .isEqualTo(WaitlistStatus.DECLINED);

        assertThat(state.entries().get(1).getStatus())
                .isEqualTo(WaitlistStatus.OFFERED);

        assertThat(state.entries().get(1).getOfferedQuantity())
                .isEqualTo(1);

        long activeOfferCount = state.entries()
                .stream()
                .filter(entry ->
                        entry.getStatus()
                                == WaitlistStatus.OFFERED
                )
                .count();

        assertThat(activeOfferCount).isEqualTo(1);
    }

    @Test
    void expiredOfferReleasesStockToNextBuyer() {
        TestFixture fixture = createFixture();

        WaitlistEntry firstOffer =
                waitlistOfferService.offerNext(
                        fixture.sellerId(),
                        fixture.inventoryItemId()
                ).orElseThrow();

        makeOfferExpired(
                fixture.sellerId(),
                firstOffer.getId()
        );

        WaitlistResolutionResult result =
                waitlistResolutionService.expireOffer(
                        fixture.sellerId(),
                        fixture.inventoryItemId(),
                        firstOffer.getId()
                );

        assertThat(result.resolvedEntry().getStatus())
                .isEqualTo(WaitlistStatus.EXPIRED);

        assertThat(result.nextOffer()).isPresent();
        assertThat(
                result.nextOffer()
                        .orElseThrow()
                        .getBuyerPlatformId()
        ).isEqualTo(fixture.secondBuyerId());

        PersistedState state = loadState(fixture);

        assertThat(state.availableStock()).isZero();

        assertThat(state.entries().get(0).getStatus())
                .isEqualTo(WaitlistStatus.EXPIRED);

        assertThat(state.entries().get(1).getStatus())
                .isEqualTo(WaitlistStatus.OFFERED);

        long activeOfferCount = state.entries()
                .stream()
                .filter(entry ->
                        entry.getStatus()
                                == WaitlistStatus.OFFERED
                )
                .count();

        assertThat(activeOfferCount).isEqualTo(1);
    }

    private void makeOfferExpired(
            Long sellerId,
            Long waitlistEntryId
    ) {
        transactionTemplate.executeWithoutResult(status -> {
            tenantContext.applyCurrentSeller(
                    entityManager,
                    sellerId
            );

            entityManager.createNativeQuery("""
                    UPDATE waitlist_entries
                    SET offer_expires_at =
                        now() - INTERVAL '1 second'
                    WHERE id = :waitlistEntryId
                    """)
                    .setParameter(
                            "waitlistEntryId",
                            waitlistEntryId
                    )
                    .executeUpdate();
        });
    }

    private TestFixture createFixture() {
        return transactionTemplate.execute(status -> {
            Seller seller = sellerRepository.save(
                    new Seller(
                            "waitlist-resolution-"
                                    + UUID.randomUUID()
                                    + "@test.com",
                            "hash",
                            "Waitlist Resolution Test Shop"
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
                                    "Waitlist Resolution Session",
                                    "SIMULATION"
                            )
                    );

            InventoryItem item =
                    inventoryItemRepository.save(
                            new InventoryItem(
                                    seller.getId(),
                                    session.getId(),
                                    "RESOLVE" + UUID.randomUUID(),
                                    "Waitlist Resolution Item",
                                    1,
                                    1
                            )
                    );

            String firstBuyerId =
                    "first-" + UUID.randomUUID();

            String secondBuyerId =
                    "second-" + UUID.randomUUID();

            waitlistEntryRepository.save(
                    new WaitlistEntry(
                            seller.getId(),
                            session.getId(),
                            item.getId(),
                            "SIMULATION",
                            "first-event-" + UUID.randomUUID(),
                            firstBuyerId,
                            1,
                            Instant.parse(
                                    "2026-09-19T01:00:00Z"
                            )
                    )
            );

            waitlistEntryRepository.save(
                    new WaitlistEntry(
                            seller.getId(),
                            session.getId(),
                            item.getId(),
                            "SIMULATION",
                            "second-event-" + UUID.randomUUID(),
                            secondBuyerId,
                            1,
                            Instant.parse(
                                    "2026-09-19T01:00:01Z"
                            )
                    )
            );

            return new TestFixture(
                    seller.getId(),
                    item.getId(),
                    firstBuyerId,
                    secondBuyerId
            );
        });
    }

    private PersistedState loadState(
            TestFixture fixture
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
    }

    private record TestFixture(
            Long sellerId,
            Long inventoryItemId,
            String firstBuyerId,
            String secondBuyerId
    ) {
    }

    private record PersistedState(
            Integer availableStock,
            List<WaitlistEntry> entries
    ) {
    }
}