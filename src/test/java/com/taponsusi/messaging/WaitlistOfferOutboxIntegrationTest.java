package com.taponsusi.messaging;

import com.taponsusi.inventory.InventoryItem;
import com.taponsusi.inventory.InventoryItemRepository;
import com.taponsusi.livesession.LiveSession;
import com.taponsusi.livesession.LiveSessionRepository;
import com.taponsusi.security.TenantContext;
import com.taponsusi.seller.Seller;
import com.taponsusi.seller.SellerRepository;
import com.taponsusi.waitlist.WaitlistEntry;
import com.taponsusi.waitlist.WaitlistEntryRepository;
import com.taponsusi.waitlist.WaitlistOfferService;
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
class WaitlistOfferOutboxIntegrationTest {

    @Autowired
    private SellerRepository sellerRepository;

    @Autowired
    private LiveSessionRepository liveSessionRepository;

    @Autowired
    private InventoryItemRepository inventoryItemRepository;

    @Autowired
    private WaitlistEntryRepository waitlistEntryRepository;

    @Autowired
    private OutboundMessageRepository outboundMessageRepository;

    @Autowired
    private OutboundMessageService outboundMessageService;

    @Autowired
    private WaitlistOfferService waitlistOfferService;

    @Autowired
    private TenantContext tenantContext;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    void offerQueuesOnePendingBuyerMessage() {
        TestFixture fixture = createFixture();

        WaitlistEntry offeredEntry =
                waitlistOfferService.offerNext(
                        fixture.sellerId(),
                        fixture.inventoryItemId()
                ).orElseThrow();

        List<OutboundMessage> messages =
                loadPendingMessages(
                        fixture.sellerId()
                );

        assertThat(messages).hasSize(1);

        OutboundMessage message = messages.getFirst();

        assertThat(message.getMessageType())
                .isEqualTo(
                        OutboundMessageType.WAITLIST_OFFER
                );

        assertThat(message.getIdempotencyKey())
                .isEqualTo(
                        "WAITLIST-OFFER:"
                                + offeredEntry.getId()
                );

        assertThat(message.getPlatform())
                .isEqualTo("SIMULATION");

        assertThat(message.getBuyerPlatformId())
                .isEqualTo(fixture.buyerPlatformId());

        assertThat(message.getStatus())
                .isEqualTo(
                        OutboundMessageStatus.PENDING
                );

        assertThat(message.getAttemptCount())
                .isZero();

        assertThat(message.getMessageText())
                .contains(
                        "temporarily held 1 unit(s)"
                )
                .contains(
                        offeredEntry
                                .getOfferExpiresAt()
                                .toString()
                )
                .contains("Reply YES")
                .contains("NO to decline");
    }

    @Test
    void sameOfferCannotQueueDuplicateMessage() {
        TestFixture fixture = createFixture();

        WaitlistEntry offeredEntry =
                waitlistOfferService.offerNext(
                        fixture.sellerId(),
                        fixture.inventoryItemId()
                ).orElseThrow();

        OutboundMessage original =
                outboundMessageService
                        .enqueueWaitlistOffer(
                                offeredEntry
                        );

        OutboundMessage repeated =
                outboundMessageService
                        .enqueueWaitlistOffer(
                                offeredEntry
                        );

        assertThat(repeated.getId())
                .isEqualTo(original.getId());

        List<OutboundMessage> messages =
                loadPendingMessages(
                        fixture.sellerId()
                );

        assertThat(messages).hasSize(1);
    }

    @Test
    void anotherSellerCannotReadOfferMessage() {
        TestFixture fixture = createFixture();

        WaitlistEntry offeredEntry =
                waitlistOfferService.offerNext(
                        fixture.sellerId(),
                        fixture.inventoryItemId()
                ).orElseThrow();

        Long otherSellerId = createOtherSeller();

        Optional<OutboundMessage> hiddenMessage =
                transactionTemplate.execute(status -> {
                    tenantContext.applyCurrentSeller(
                            entityManager,
                            otherSellerId
                    );

                    return outboundMessageRepository
                            .findBySellerIdAndIdempotencyKey(
                                    fixture.sellerId(),
                                    "WAITLIST-OFFER:"
                                            + offeredEntry.getId()
                            );
                });

        assertThat(hiddenMessage).isEmpty();
    }

    private List<OutboundMessage> loadPendingMessages(
            Long sellerId
    ) {
        return transactionTemplate.execute(status -> {
            tenantContext.applyCurrentSeller(
                    entityManager,
                    sellerId
            );

            return outboundMessageRepository
                    .findAllBySellerIdAndStatusOrderByCreatedAtAscIdAsc(
                            sellerId,
                            OutboundMessageStatus.PENDING
                    );
        });
    }

    private TestFixture createFixture() {
        return transactionTemplate.execute(status -> {
            Seller seller = sellerRepository.save(
                    new Seller(
                            "outbox-offer-"
                                    + UUID.randomUUID()
                                    + "@test.com",
                            "hash",
                            "Outbox Offer Test Shop"
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
                                    "Outbox Offer Session",
                                    "SIMULATION"
                            )
                    );

            InventoryItem item =
                    inventoryItemRepository.save(
                            new InventoryItem(
                                    seller.getId(),
                                    session.getId(),
                                    "OUTBOX" + UUID.randomUUID(),
                                    "Outbox Offer Item",
                                    1,
                                    1
                            )
                    );

            String buyerPlatformId =
                    "buyer-" + UUID.randomUUID();

            waitlistEntryRepository.save(
                    new WaitlistEntry(
                            seller.getId(),
                            session.getId(),
                            item.getId(),
                            "SIMULATION",
                            "event-" + UUID.randomUUID(),
                            buyerPlatformId,
                            1,
                            Instant.now().minusSeconds(60)
                    )
            );

            return new TestFixture(
                    seller.getId(),
                    item.getId(),
                    buyerPlatformId
            );
        });
    }

    private Long createOtherSeller() {
        return transactionTemplate.execute(status -> {
            Seller seller = sellerRepository.save(
                    new Seller(
                            "other-outbox-"
                                    + UUID.randomUUID()
                                    + "@test.com",
                            "hash",
                            "Other Outbox Shop"
                    )
            );

            return seller.getId();
        });
    }

    private record TestFixture(
            Long sellerId,
            Long inventoryItemId,
            String buyerPlatformId
    ) {
    }
}