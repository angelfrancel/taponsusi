package com.taponsusi.reservationproof.dto;

import com.taponsusi.reservationproof.ReservationProof;

import java.time.Instant;

public record ReservationProofResponse(
        Long id,
        Long orderId,
        Long sequenceNumber,
        String proofToken,
        String hash,
        Instant createdAt
) {
    public static ReservationProofResponse from(ReservationProof proof) {
        return new ReservationProofResponse(
                proof.getId(), proof.getOrderId(), proof.getSequenceNumber(),
                proof.getProofToken(), proof.getHash(), proof.getCreatedAt());
    }
}