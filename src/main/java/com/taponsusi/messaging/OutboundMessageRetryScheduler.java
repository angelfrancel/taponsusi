package com.taponsusi.messaging;

import com.taponsusi.seller.Seller;
import com.taponsusi.seller.SellerRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class OutboundMessageRetryScheduler {

    private static final Logger log =
            LoggerFactory.getLogger(
                    OutboundMessageRetryScheduler.class
            );

    private final SellerRepository sellerRepository;
    private final OutboundMessageRetryCandidateReader
            candidateReader;
    private final OutboundMessageDispatcher dispatcher;
    private final int maximumAttempts;

    public OutboundMessageRetryScheduler(
            SellerRepository sellerRepository,
            OutboundMessageRetryCandidateReader candidateReader,
            OutboundMessageDispatcher dispatcher,
            @Value("${taponsusi.messaging.maximum-attempts:5}")
            int maximumAttempts
    ) {
        this.sellerRepository = sellerRepository;
        this.candidateReader = candidateReader;
        this.dispatcher = dispatcher;
        this.maximumAttempts = maximumAttempts;
    }

    @Scheduled(
            fixedDelayString =
                    "${taponsusi.messaging.retry-delay-ms:300000}"
    )
    public void retryMessages() {
        List<Seller> sellers =
                sellerRepository.findAll();

        for (Seller seller : sellers) {
            retrySellerMessages(
                    seller.getId()
            );
        }
    }

    private void retrySellerMessages(
            Long sellerId
    ) {
        try {
            List<Long> messageIds =
                    candidateReader.findCandidates(
                            sellerId,
                            maximumAttempts
                    );

            for (Long messageId : messageIds) {
                try {
                    dispatcher.dispatch(
                            sellerId,
                            messageId
                    );
                } catch (RuntimeException exception) {
                    log.error(
                            "Outbound message retry failed "
                                    + "for seller {} and message {}",
                            sellerId,
                            messageId,
                            exception
                    );
                }
            }
        } catch (RuntimeException exception) {
            log.error(
                    "Could not load outbound messages "
                            + "for seller {}",
                    sellerId,
                    exception
            );
        }
    }
}