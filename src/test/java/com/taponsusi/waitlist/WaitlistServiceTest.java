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
class WaitlistServiceTest {

    @Autowired
    private WaitlistService waitlistService;

    @Autowired
    private SellerRepository sellerRepository;

    @Autowired
    private LiveSessionRepository liveSessionRepository;

    @Autowired
    private InventoryItemRepository inventoryItemRepository;

    @Autowired
    private TenantContext tenantContext;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    void repeatedPlatformEventCreatesOnlyOneEntry() {
        TestFixture fixture = createFixture();
        String eventId = "event-" + UUID.randomUUID();
        Instant sourceTime = Instant.now();

        Optional<WaitlistEntry> first =
                waitlistService.enqueue(
                        fixture.sellerId(),
                        fixture.sessionId(),
                        fixture.inventoryItemId(),
                        "facebook",
                        eventId,
                        "buyer-123",
                        1,
                        sourceTime
                );

        Optional<WaitlistEntry> duplicate =
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

        assertThat(first).isPresent();
        assertThat(duplicate).isEmpty();

        Long storedCount =
                transactionTemplate.execute(status -> {
                    tenantContext.applyCurrentSeller(
                            entityManager,
                            fixture.sellerId()
                    );

                    return entityManager.createQuery(
                                    """
                                    SELECT COUNT(entry)
                                    FROM WaitlistEntry entry
                                    WHERE entry.sellerId = :sellerId
                                      AND entry.inventoryItemId = :itemId
                                      AND entry.platformEventId = :eventId
                                    """,
                                    Long.class
                            )
                            .setParameter(
                                    "sellerId",
                                    fixture.sellerId()
                            )
                            .setParameter(
                                    "itemId",
                                    fixture.inventoryItemId()
                            )
                            .setParameter("eventId", eventId)
                            .getSingleResult();
                });

        assertThat(storedCount).isEqualTo(1L);
    }

    private TestFixture createFixture() {
        return transactionTemplate.execute(status -> {
            Seller seller = sellerRepository.save(
                    new Seller(
                            "waitlist-service-"
                                    + UUID.randomUUID()
                                    + "@test.com",
                            "hash",
                            "Waitlist Service Test Shop"
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
                                    "Waitlist Service Session",
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