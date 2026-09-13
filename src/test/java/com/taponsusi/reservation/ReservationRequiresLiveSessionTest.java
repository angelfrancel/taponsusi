package com.taponsusi.reservation;

import com.taponsusi.inventory.InventoryItem;
import com.taponsusi.inventory.InventoryItemRepository;
import com.taponsusi.livesession.LiveSession;
import com.taponsusi.livesession.LiveSessionRepository;
import com.taponsusi.livesession.LiveSessionStatus;
import com.taponsusi.parser.PurchaseIntent;
import com.taponsusi.parser.PurchaseIntentParser;
import com.taponsusi.security.TenantContext;
import com.taponsusi.seller.Seller;
import com.taponsusi.seller.SellerRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class ReservationRequiresLiveSessionTest {

    private static final String BUYER_ID =
            "draft-session-buyer";

    @Autowired
    private SellerRepository sellerRepository;

    @Autowired
    private LiveSessionRepository liveSessionRepository;

    @Autowired
    private InventoryItemRepository inventoryItemRepository;

    @Autowired
    private PurchaseIntentParser purchaseIntentParser;

    @Autowired
    private ReservationEngine reservationEngine;

    @Autowired
    private TenantContext tenantContext;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @PersistenceContext
    private EntityManager entityManager;

    @ParameterizedTest
    @EnumSource(
            value = LiveSessionStatus.class,
            names = {
                    "DRAFT",
                    "READY",
                    "PAUSED",
                    "ENDED"
            }
    )
    void nonLiveSessionCannotAcceptReservation(
            LiveSessionStatus sessionStatus
    ) {
        TestFixture fixture =
                createSessionWithInventory(sessionStatus);

        PurchaseIntent intent = purchaseIntentParser.parse(
                fixture.sellerId(),
                fixture.sessionId(),
                BUYER_ID,
                "MINE SWERTE"
        );

        assertThatThrownBy(() ->
                reservationEngine.reserve(
                        "SIMULATION",
                        "non-live-event-"
                                + sessionStatus
                                + "-"
                                + UUID.randomUUID(),
                        intent,
                        fixture.inventoryItemId()
                )
        )
                .isInstanceOf(SessionNotLiveException.class)
                .hasMessageContaining(sessionStatus.name());

        Integer remainingStock =
                transactionTemplate.execute(status -> {
                    tenantContext.applyCurrentSeller(
                            entityManager,
                            fixture.sellerId()
                    );

                    return inventoryItemRepository
                            .findById(fixture.inventoryItemId())
                            .orElseThrow()
                            .getAvailableStock();
                });

        assertThat(remainingStock).isEqualTo(2);
    }

    private TestFixture createSessionWithInventory( LiveSessionStatus sessionStatus) {
        return transactionTemplate.execute(status -> {
            Seller seller = sellerRepository.save(
                    new Seller(
                            "draft-session-"
                                    + UUID.randomUUID()
                                    + "@test.com",
                            "hash",
                            "Draft Session Test Shop"
                    )
            );

            tenantContext.applyCurrentSeller(
                    entityManager,
                    seller.getId()
            );

            LiveSession session = new LiveSession(
                    seller.getId(),
                    "Draft Session",
                    "SIMULATION"
            );

            LiveSession savedSession =
                    liveSessionRepository.save(session);

            entityManager.createNativeQuery("""
                    UPDATE live_sessions
                    SET status = :sessionStatus
                    WHERE id = :sessionId
                    """)
                    .setParameter(
                            "sessionStatus",
                            sessionStatus.name()
                    )
                    .setParameter(
                            "sessionId",
                            savedSession.getId()
                    )
                    .executeUpdate();

            entityManager.flush();
            entityManager.clear();

            InventoryItem item = new InventoryItem(
                    seller.getId(),
                    savedSession.getId(),
                    "SWERTE",
                    "Red Dress",
                    2,
                    1
            );

            InventoryItem savedItem =
                    inventoryItemRepository.save(item);

            return new TestFixture(
                    seller.getId(),
                    savedSession.getId(),
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
}