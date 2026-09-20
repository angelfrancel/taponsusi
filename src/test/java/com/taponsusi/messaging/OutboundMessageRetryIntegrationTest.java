package com.taponsusi.messaging;

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
class OutboundMessageRetryIntegrationTest {

    @Autowired
    private SellerRepository sellerRepository;

    @Autowired
    private OutboundMessageRepository
            outboundMessageRepository;

    @Autowired
    private OutboundMessageRetryCandidateReader
            candidateReader;

    @Autowired
    private TenantContext tenantContext;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    void returnsOnlyRetryableMessagesBelowAttemptLimit() {
        Long sellerId = createSeller("retry-selection");

        Long pendingId = createMessage(
                sellerId,
                "pending",
                MessageState.PENDING
        );

        Long failedId = createMessage(
                sellerId,
                "failed",
                MessageState.FAILED_ONCE
        );

        Long sentId = createMessage(
                sellerId,
                "sent",
                MessageState.SENT
        );

        Long exhaustedId = createMessage(
                sellerId,
                "exhausted",
                MessageState.FAILED_FIVE_TIMES
        );

        List<Long> candidates =
                candidateReader.findCandidates(
                        sellerId,
                        5
                );

        assertThat(candidates)
                .containsExactly(
                        pendingId,
                        failedId
                );

        assertThat(candidates)
                .doesNotContain(
                        sentId,
                        exhaustedId
                );
    }

    @Test
    void sellerCannotReadAnotherSellersRetryCandidates() {
        Long sellerA = createSeller("retry-seller-a");
        Long sellerB = createSeller("retry-seller-b");

        createMessage(
                sellerB,
                "seller-b-message",
                MessageState.PENDING
        );

        List<OutboundMessage> hiddenMessages =
                transactionTemplate.execute(status -> {
                    tenantContext.applyCurrentSeller(
                            entityManager,
                            sellerA
                    );

                    /*
                     * The query explicitly asks for Seller B's
                     * records while PostgreSQL is operating under
                     * Seller A's tenant context.
                     */
                    return outboundMessageRepository
                            .findTop50BySellerIdAndStatusInAndAttemptCountLessThanOrderByCreatedAtAscIdAsc(
                                    sellerB,
                                    List.of(
                                            OutboundMessageStatus.PENDING,
                                            OutboundMessageStatus.FAILED
                                    ),
                                    5
                            );
                });

        assertThat(hiddenMessages).isEmpty();
    }

    private Long createSeller(String label) {
        return transactionTemplate.execute(status -> {
            Seller seller = sellerRepository.save(
                    new Seller(
                            label
                                    + "-"
                                    + UUID.randomUUID()
                                    + "@test.com",
                            "hash",
                            label + " Shop"
                    )
            );

            return seller.getId();
        });
    }

    private Long createMessage(
            Long sellerId,
            String label,
            MessageState state
    ) {
        return transactionTemplate.execute(status -> {
            tenantContext.applyCurrentSeller(
                    entityManager,
                    sellerId
            );

            OutboundMessage message =
                    new OutboundMessage(
                            sellerId,
                            OutboundMessageType.WAITLIST_OFFER,
                            label + "-" + UUID.randomUUID(),
                            "SIMULATION",
                            "buyer-" + UUID.randomUUID(),
                            "Test outbound message"
                    );

            switch (state) {
                case PENDING -> {
                    // Keep the initial PENDING state.
                }

                case FAILED_ONCE ->
                        message.markFailed(
                                "Temporary failure"
                        );

                case SENT ->
                        message.markSent(
                                Instant.now()
                        );

                case FAILED_FIVE_TIMES -> {
                    for (int attempt = 0;
                         attempt < 5;
                         attempt++) {
                        message.markFailed(
                                "Failure " + attempt
                        );
                    }
                }
            }

            OutboundMessage saved =
                    outboundMessageRepository.save(
                            message
                    );

            return saved.getId();
        });
    }

    private enum MessageState {
        PENDING,
        FAILED_ONCE,
        SENT,
        FAILED_FIVE_TIMES
    }
}