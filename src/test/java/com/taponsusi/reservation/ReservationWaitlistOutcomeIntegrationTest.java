package com.taponsusi.reservation;

import com.taponsusi.inventory.InventoryItem;
import com.taponsusi.inventory.InventoryItemRepository;
import com.taponsusi.livesession.LiveSession;
import com.taponsusi.livesession.LiveSessionRepository;
import com.taponsusi.order.Order;
import com.taponsusi.order.OrderRepository;
import com.taponsusi.parser.PurchaseIntent;
import com.taponsusi.security.TenantContext;
import com.taponsusi.seller.Seller;
import com.taponsusi.seller.SellerRepository;
import com.taponsusi.waitlist.WaitlistEntry;
import com.taponsusi.waitlist.WaitlistEntryRepository;
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
class ReservationWaitlistOutcomeIntegrationTest {

    @Autowired
    private ReservationEngine reservationEngine;

    @Autowired
    private SellerRepository sellerRepository;

    @Autowired
    private LiveSessionRepository liveSessionRepository;

    @Autowired
    private InventoryItemRepository inventoryItemRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private WaitlistEntryRepository waitlistEntryRepository;

    @Autowired
    private TenantContext tenantContext;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    void outOfStockEventIsWaitlistedAndRedeliveryIsDuplicate() {
        TestFixture fixture = createFixture();

        String platform = "FACEBOOK";
        String platformEventId =
                "waitlist-comment-" + UUID.randomUUID();

        Instant sourceCreatedAt =
                Instant.parse("2026-09-16T02:30:00Z");

        PurchaseIntent intent = new PurchaseIntent(
                "buyer-123",
                fixture.sellerId(),
                fixture.sessionId(),
                "SWERTE",
                1,
                sourceCreatedAt
        );

        ReservationOutcome firstOutcome =
                reservationEngine.decide(
                        platform,
                        platformEventId,
                        intent,
                        fixture.inventoryItemId()
                );

        ReservationOutcome redeliveryOutcome =
                reservationEngine.decide(
                        platform,
                        platformEventId,
                        intent,
                        fixture.inventoryItemId()
                );

        assertThat(firstOutcome.type())
                .isEqualTo(
                        ReservationOutcomeType.WAITLISTED
                );

        assertThat(firstOutcome.optionalOrder())
                .isEmpty();

        assertThat(firstOutcome.optionalWaitlistEntry())
                .isPresent();

        WaitlistEntry returnedEntry =
                firstOutcome.optionalWaitlistEntry()
                        .orElseThrow();

        assertThat(returnedEntry.getBuyerPlatformId())
                .isEqualTo("buyer-123");

        assertThat(returnedEntry.getSourceCreatedAt())
                .isEqualTo(sourceCreatedAt);

        assertThat(redeliveryOutcome.type())
                .isEqualTo(
                        ReservationOutcomeType.DUPLICATE
                );

        assertThat(redeliveryOutcome.optionalOrder())
                .isEmpty();

        assertThat(redeliveryOutcome.optionalWaitlistEntry())
                .isEmpty();

        VerificationResult verification =
                transactionTemplate.execute(status -> {
                    tenantContext.applyCurrentSeller(
                            entityManager,
                            fixture.sellerId()
                    );

                    List<Order> orders =
                            orderRepository
                                    .findAllBySellerIdAndInventoryItemId(
                                            fixture.sellerId(),
                                            fixture.inventoryItemId()
                                    );

                    List<WaitlistEntry> waitlistEntries =
                            waitlistEntryRepository
                                    .findAllBySellerIdAndInventoryItemIdOrderBySourceCreatedAtAscIdAsc(
                                            fixture.sellerId(),
                                            fixture.inventoryItemId()
                                    );

                    Number processedEventCount =
                            (Number) entityManager
                                    .createNativeQuery("""
                                            SELECT COUNT(*)
                                            FROM processed_events
                                            WHERE platform = :platform
                                              AND platform_event_id = :eventId
                                            """)
                                    .setParameter(
                                            "platform",
                                            platform
                                    )
                                    .setParameter(
                                            "eventId",
                                            platformEventId
                                    )
                                    .getSingleResult();

                    return new VerificationResult(
                            orders,
                            waitlistEntries,
                            processedEventCount.longValue()
                    );
                });

        assertThat(verification).isNotNull();
        assertThat(verification.orders()).isEmpty();

        assertThat(verification.waitlistEntries())
                .hasSize(1);

        assertThat(
                verification.waitlistEntries()
                        .getFirst()
                        .getPlatformEventId()
        ).isEqualTo(platformEventId);

        assertThat(
                verification.waitlistEntries()
                        .getFirst()
                        .getSourceCreatedAt()
        ).isEqualTo(sourceCreatedAt);

        assertThat(verification.processedEventCount())
                .as(
                        "The original event is processed exactly once"
                )
                .isEqualTo(1L);
    }

    private TestFixture createFixture() {
        return transactionTemplate.execute(status -> {
            Seller seller = sellerRepository.save(
                    new Seller(
                            "waitlist-outcome-"
                                    + UUID.randomUUID()
                                    + "@test.com",
                            "hash",
                            "Waitlist Outcome Test Shop"
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
                                    "Waitlist Outcome Session",
                                    "SIMULATION"
                            )
                    );

            entityManager.createNativeQuery("""
                    UPDATE live_sessions
                    SET status = 'LIVE'
                    WHERE id = :sessionId
                    """)
                    .setParameter(
                            "sessionId",
                            session.getId()
                    )
                    .executeUpdate();

            InventoryItem item = new InventoryItem(
                    seller.getId(),
                    session.getId(),
                    "SWERTE",
                    "Red Dress",
                    1,
                    1
            );

            item.setAvailableStock(0);

            InventoryItem savedItem =
                    inventoryItemRepository.save(item);

            entityManager.flush();
            entityManager.clear();

            return new TestFixture(
                    seller.getId(),
                    session.getId(),
                    savedItem.getId()
            );
        });
    }

    private record TestFixture(
            Long sellerId,
            Long sessionId,
            Long inventoryItemId
    ) {
    }

    private record VerificationResult(
            List<Order> orders,
            List<WaitlistEntry> waitlistEntries,
            long processedEventCount
    ) {
    }
}