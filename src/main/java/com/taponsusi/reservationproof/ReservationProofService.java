package com.taponsusi.reservationproof;

import com.taponsusi.order.Order;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;


@Service
public class ReservationProofService {

    private static final String GENESIS_HASH = "GENESIS";

    private final ReservationProofRepository reservationProofRepository;

    public ReservationProofService(ReservationProofRepository reservationProofRepository) {
        this.reservationProofRepository = reservationProofRepository;
    }

    @Transactional
    public ReservationProof generateProof(Order order) {
        String previousHash = reservationProofRepository
                .findTopBySellerIdOrderBySequenceNumberDesc(order.getSellerId())
                .map(ReservationProof::getHash)
                .orElse(GENESIS_HASH);

        long nextSequence = reservationProofRepository
                .findTopBySellerIdOrderBySequenceNumberDesc(order.getSellerId())
                .map(p -> p.getSequenceNumber() + 1)
                .orElse(1L);

        String proofData = buildProofData(order, nextSequence);
        String hash = sha256(proofData + previousHash);

        ReservationProof proof = new ReservationProof(
                order.getSellerId(), order.getId(), nextSequence, proofData, previousHash, hash);

        return reservationProofRepository.save(proof);
    }

    private String buildProofData(Order order, long sequenceNumber) {
        return String.join("|",
                "orderId=" + order.getId(),
                "buyer=" + order.getBuyerPlatformId(),
                "inventoryItemId=" + order.getInventoryItemId(),
                "sequence=" + sequenceNumber,
                "timestamp=" + order.getCreatedAt()
        );
    }

    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hashBytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}