package com.taponsusi.messaging;

import com.taponsusi.security.TenantContext;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class OutboundMessageRetryCandidateReader {

    private static final List<OutboundMessageStatus>
            RETRYABLE_STATUSES = List.of(
            OutboundMessageStatus.PENDING,
            OutboundMessageStatus.FAILED
    );

    private final OutboundMessageRepository
            outboundMessageRepository;

    private final TenantContext tenantContext;

    @PersistenceContext
    private EntityManager entityManager;

    public OutboundMessageRetryCandidateReader(
            OutboundMessageRepository outboundMessageRepository,
            TenantContext tenantContext
    ) {
        this.outboundMessageRepository =
                outboundMessageRepository;
        this.tenantContext = tenantContext;
    }

    @Transactional(readOnly = true)
    public List<Long> findCandidates(
            Long sellerId,
            int maximumAttempts
    ) {
        tenantContext.applyCurrentSeller(
                entityManager,
                sellerId
        );

        return outboundMessageRepository
                .findTop50BySellerIdAndStatusInAndAttemptCountLessThanOrderByCreatedAtAscIdAsc(
                        sellerId,
                        RETRYABLE_STATUSES,
                        maximumAttempts
                )
                .stream()
                .map(OutboundMessage::getId)
                .toList();
    }
}