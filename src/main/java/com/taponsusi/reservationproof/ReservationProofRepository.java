package com.taponsusi.reservationproof;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ReservationProofRepository extends JpaRepository<ReservationProof, Long> {

    Optional<ReservationProof> findTopBySellerIdOrderBySequenceNumberDesc(Long sellerId);

    Optional<ReservationProof> findBySellerIdAndOrderId(Long sellerId, Long orderId);

    Optional<ReservationProof> findBySellerIdAndProofToken(Long sellerId, String proofToken);
}