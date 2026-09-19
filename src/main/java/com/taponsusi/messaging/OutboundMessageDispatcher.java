package com.taponsusi.messaging;

import com.taponsusi.security.TenantContext;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class OutboundMessageDispatcher {

    private final OutboundMessageRepository
            outboundMessageRepository;

    private final MessagingProvider messagingProvider;
    private final TenantContext tenantContext;

    @PersistenceContext
    private EntityManager entityManager;

    public OutboundMessageDispatcher(
            OutboundMessageRepository outboundMessageRepository,
            MessagingProvider messagingProvider,
            TenantContext tenantContext
    ) {
        this.outboundMessageRepository =
                outboundMessageRepository;
        this.messagingProvider = messagingProvider;
        this.tenantContext = tenantContext;
    }

    @Transactional
    public boolean dispatch(
            Long sellerId,
            Long messageId
    ) {
        tenantContext.applyCurrentSeller(
                entityManager,
                sellerId
        );

        OutboundMessage message =
                outboundMessageRepository
                        .findByIdForDelivery(
                                messageId,
                                sellerId
                        )
                        .orElse(null);

        if (message == null) {
            return false;
        }

        /*
         * The row lock and this status check prevent ordinary
         * concurrent dispatch attempts from sending twice.
         */
        if (message.getStatus()
                == OutboundMessageStatus.SENT) {
            return false;
        }

        try {
            messagingProvider.sendMessage(
                    message.getPlatform(),
                    message.getBuyerPlatformId(),
                    message.getMessageText()
            );

            message.markSent(Instant.now());
            return true;
        } catch (RuntimeException exception) {
            message.markFailed(
                    exception.getMessage()
            );

            /*
             * Do not rethrow. The transaction must commit the
             * FAILED status so the message can be retried later.
             */
            return false;
        }
    }
}