package com.taponsusi.messaging;

import com.taponsusi.security.TenantContext;
import com.taponsusi.waitlist.WaitlistEntry;
import com.taponsusi.waitlist.WaitlistStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OutboundMessageService {

    private final OutboundMessageRepository
            outboundMessageRepository;

    private final TenantContext tenantContext;

    @PersistenceContext
    private EntityManager entityManager;

    public OutboundMessageService(
            OutboundMessageRepository outboundMessageRepository,
            TenantContext tenantContext
    ) {
        this.outboundMessageRepository =
                outboundMessageRepository;
        this.tenantContext = tenantContext;
    }

    @Transactional
    public OutboundMessage enqueueWaitlistOffer(
            WaitlistEntry entry
    ) {
        validateOfferedEntry(entry);

        tenantContext.applyCurrentSeller(
                entityManager,
                entry.getSellerId()
        );

        String idempotencyKey =
                "WAITLIST-OFFER:" + entry.getId();

        String messageText = String.format(
                "The item is available again. "
                        + "We have temporarily held %d unit(s) "
                        + "for you until %s. "
                        + "Reply YES to accept or NO to decline.",
                entry.getOfferedQuantity(),
                entry.getOfferExpiresAt()
        );

        /*
         * ON CONFLICT prevents repeated execution from creating
         * multiple notification records for the same offer.
         */
        entityManager.createNativeQuery("""
                INSERT INTO outbound_messages (
                    seller_id,
                    message_type,
                    idempotency_key,
                    platform,
                    buyer_platform_id,
                    message_text,
                    status,
                    attempt_count,
                    created_at,
                    updated_at
                )
                VALUES (
                    :sellerId,
                    'WAITLIST_OFFER',
                    :idempotencyKey,
                    :platform,
                    :buyerPlatformId,
                    :messageText,
                    'PENDING',
                    0,
                    now(),
                    now()
                )
                ON CONFLICT (idempotency_key)
                DO NOTHING
                """)
                .setParameter(
                        "sellerId",
                        entry.getSellerId()
                )
                .setParameter(
                        "idempotencyKey",
                        idempotencyKey
                )
                .setParameter(
                        "platform",
                        entry.getPlatform()
                )
                .setParameter(
                        "buyerPlatformId",
                        entry.getBuyerPlatformId()
                )
                .setParameter(
                        "messageText",
                        messageText
                )
                .executeUpdate();

        return outboundMessageRepository
                .findBySellerIdAndIdempotencyKey(
                        entry.getSellerId(),
                        idempotencyKey
                )
                .orElseThrow(() ->
                        new IllegalStateException(
                                "Outbound message was not available "
                                        + "after enqueue: "
                                        + idempotencyKey
                        )
                );
    }

    private void validateOfferedEntry(
            WaitlistEntry entry
    ) {
        if (entry == null) {
            throw new IllegalArgumentException(
                    "Waitlist entry is required"
            );
        }

        if (entry.getId() == null) {
            throw new IllegalArgumentException(
                    "Waitlist entry must be persisted"
            );
        }

        if (entry.getStatus()
                != WaitlistStatus.OFFERED) {
            throw new IllegalArgumentException(
                    "Waitlist entry must have OFFERED status"
            );
        }

        if (entry.getOfferedQuantity() == null
                || entry.getOfferedQuantity() <= 0) {
            throw new IllegalArgumentException(
                    "Waitlist offer must have a held quantity"
            );
        }

        if (entry.getOfferExpiresAt() == null) {
            throw new IllegalArgumentException(
                    "Waitlist offer must have an expiration time"
            );
        }
    }
}