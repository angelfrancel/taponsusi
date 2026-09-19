package com.taponsusi.messaging;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OutboundMessageRepository
        extends JpaRepository<OutboundMessage, Long> {

    boolean existsByIdempotencyKey(
            String idempotencyKey
    );

    Optional<OutboundMessage>
    findBySellerIdAndIdempotencyKey(
            Long sellerId,
            String idempotencyKey
    );

    List<OutboundMessage>
    findAllBySellerIdAndStatusOrderByCreatedAtAscIdAsc(
            Long sellerId,
            OutboundMessageStatus status
    );
}