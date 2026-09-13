package com.taponsusi.livesession;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface LiveSessionRepository
        extends JpaRepository<LiveSession, Long> {

    List<LiveSession> findAllBySellerId(Long sellerId);

    Optional<LiveSession> findByIdAndSellerId(
            Long id,
            Long sellerId
    );

    Optional<LiveSession> findByFacebookPageId(
            String facebookPageId
    );

    /*
     * Multiple reservations may hold this shared lock concurrently.
     * A status transition requiring an exclusive lock must wait until
     * current reservations finish.
     */
    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("""
            SELECT session
            FROM LiveSession session
            WHERE session.id = :sessionId
              AND session.sellerId = :sellerId
            """)
    Optional<LiveSession> findForReservation(
            @Param("sessionId") Long sessionId,
            @Param("sellerId") Long sellerId
    );

    /*
     * Status transitions are serialized against reservations and
     * other transitions for the same session.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT session
            FROM LiveSession session
            WHERE session.id = :sessionId
              AND session.sellerId = :sellerId
            """)
    Optional<LiveSession> findForStatusTransition(
            @Param("sessionId") Long sessionId,
            @Param("sellerId") Long sellerId
    );
}