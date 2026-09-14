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
class WaitlistOrderingTest {

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
    void ordersEntriesBySourceTimeThenDatabaseId() {
        TestFixture fixture = createFixture();

        Instant earliest =
                Instant.parse("2026-09-14T01:00:00Z");

        Instant tied =
                Instant.parse("2026-09-14T01:00:01Z");

        Instant latest =
                Instant.parse("2026-09-14T01:00:02Z");

        transactionTemplate.executeWithoutResult(status -> {
            tenantContext.applyCurrentSeller(
                    entityManager,
                    fixture.sellerId()
            );

            /*
             * Save the latest event first to prove that insertion
             * order does not override the source timestamp.
             */
            waitlistEntryRepository.save(new WaitlistEntry(
                    fixture.sellerId(),
                    fixture.sessionId(),
                    fixture.inventoryItemId(),
                    "FACEBOOK",
                    "event-latest-" + UUID.randomUUID(),
                    "buyer-latest",
                    1,
                    latest
            ));

            /*
             * These two have the same source timestamp. Their IDs
             * provide the stable tie-break.
             */
            waitlistEntryRepository.save(new WaitlistEntry(
                    fixture.sellerId(),
                    fixture.sessionId(),
                    fixture.inventoryItemId(),
                    "FACEBOOK",
                    "event-tied-first-" + UUID.randomUUID(),
                    "buyer-tied-first",
                    1,
                    tied
            ));

            waitlistEntryRepository.save(new WaitlistEntry(
                    fixture.sellerId(),
                    fixture.sessionId(),
                    fixture.inventoryItemId(),
                    "FACEBOOK",
                    "event-tied-second-" + UUID.randomUUID(),
                    "buyer-tied-second",
                    1,
                    tied
            ));

            /*
             * Save the earliest event last.
             */
            waitlistEntryRepository.save(new WaitlistEntry(
                    fixture.sellerId(),
                    fixture.sessionId(),
                    fixture.inventoryItemId(),
                    "FACEBOOK",
                    "event-earliest-" + UUID.randomUUID(),
                    "buyer-earliest",
                    1,
                    earliest
            ));
        });

        List<String> orderedBuyers =
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
                            .map(WaitlistEntry::getBuyerPlatformId)
                            .toList();
                });

        assertThat(orderedBuyers).containsExactly(
                "buyer-earliest",
                "buyer-tied-first",
                "buyer-tied-second",
                "buyer-latest"
        );
    }

    private TestFixture createFixture() {
        return transactionTemplate.execute(status -> {
            Seller seller = sellerRepository.save(
                    new Seller(
                            "waitlist-ordering-"
                                    + UUID.randomUUID()
                                    + "@test.com",
                            "hash",
                            "Waitlist Ordering Test Shop"
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
                                    "Waitlist Ordering Session",
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