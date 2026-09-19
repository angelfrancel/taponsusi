package com.taponsusi.messaging;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    @Query(
            value = """
                SELECT *
                FROM outbound_messages
                WHERE id = :messageId
                  AND seller_id = :sellerId
                FOR UPDATE
                """,
            nativeQuery = true
    )
    Optional<OutboundMessage> findByIdForDelivery(
            @Param("messageId") Long messageId,
            @Param("sellerId") Long sellerId
    );
}