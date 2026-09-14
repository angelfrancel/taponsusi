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
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class WaitlistRowLevelSecurityTest {

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
    void sellerCannotReadAnotherSellersWaitlistEntry() {
        TestFixture sellerA = createFixture("seller-a");
        TestFixture sellerB = createFixture("seller-b");

        Optional<WaitlistEntry> ownEntry =
                transactionTemplate.execute(status -> {
                    tenantContext.applyCurrentSeller(
                            entityManager,
                            sellerA.sellerId()
                    );

                    return waitlistEntryRepository
                            .findByIdAndSellerId(
                                    sellerA.waitlistEntryId(),
                                    sellerA.sellerId()
                            );
                });

        Optional<WaitlistEntry> otherSellersEntry =
                transactionTemplate.execute(status -> {
                    tenantContext.applyCurrentSeller(
                            entityManager,
                            sellerA.sellerId()
                    );

                    /*
                     * Even with Seller B's exact entry ID and seller ID,
                     * PostgreSQL RLS must hide the row from Seller A.
                     */
                    return waitlistEntryRepository
                            .findByIdAndSellerId(
                                    sellerB.waitlistEntryId(),
                                    sellerB.sellerId()
                            );
                });

        assertThat(ownEntry).isPresent();
        assertThat(otherSellersEntry).isEmpty();
    }

    private TestFixture createFixture(String label) {
        return transactionTemplate.execute(status -> {
            Seller seller = sellerRepository.save(
                    new Seller(
                            label
                                    + "-"
                                    + UUID.randomUUID()
                                    + "@test.com",
                            "hash",
                            label + " Waitlist Shop"
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
                                    label + " Session",
                                    "SIMULATION"
                            )
                    );

            InventoryItem item =
                    inventoryItemRepository.save(
                            new InventoryItem(
                                    seller.getId(),
                                    session.getId(),
                                    "CODE" + seller.getId(),
                                    label + " Item",
                                    1,
                                    1
                            )
                    );

            WaitlistEntry entry =
                    waitlistEntryRepository.save(
                            new WaitlistEntry(
                                    seller.getId(),
                                    session.getId(),
                                    item.getId(),
                                    "SIMULATION",
                                    "event-" + UUID.randomUUID(),
                                    label + "-buyer",
                                    1,
                                    Instant.now()
                            )
                    );

            return new TestFixture(
                    seller.getId(),
                    entry.getId()
            );
        });
    }

    private record TestFixture(
            Long sellerId,
            Long waitlistEntryId
    ) {
    }
}