package com.taponsusi.reservationproof;

import com.taponsusi.common.exception.ResourceNotFoundException;
import com.taponsusi.order.Order;
import com.taponsusi.order.OrderRepository;
import com.taponsusi.reservationproof.dto.ProofVerificationResponse;
import com.taponsusi.reservationproof.dto.ReservationProofResponse;
import com.taponsusi.security.TenantContext;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;


@Service
public class ReservationProofService {

    private static final String GENESIS_HASH = "GENESIS";

    private final ReservationProofRepository reservationProofRepository;
    private final OrderRepository orderRepository;
    private final TenantContext tenantContext;

    @PersistenceContext
    private EntityManager entityManager;

    public ReservationProofService(ReservationProofRepository reservationProofRepository,
                                   OrderRepository orderRepository,
                                   TenantContext tenantContext) {
        this.reservationProofRepository = reservationProofRepository;
        this.orderRepository = orderRepository;
        this.tenantContext = tenantContext;
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
        String proofToken = computeProofToken(order.getBuyerPlatformId(), order.getInventoryItemId(), hash);

        ReservationProof proof = new ReservationProof(
                order.getSellerId(), order.getId(), nextSequence, proofData, previousHash, hash, proofToken);

        return reservationProofRepository.save(proof);
    }

    @Transactional(readOnly = true)
    public ReservationProofResponse getProofForOrder(Long sellerId, Long orderId) {
        tenantContext.applyCurrentSeller(entityManager, sellerId);

        ReservationProof proof = reservationProofRepository.findBySellerIdAndOrderId(sellerId, orderId)
                .orElseThrow(() -> new ResourceNotFoundException("No reservation proof for order: " + orderId));

        return ReservationProofResponse.from(proof);
    }

    /**
     * Section 6.1 verification: a proof_token only resolves to a valid
     * reservation if it also matches the buyerPlatformId on file for
     * that order. A cloned display name/photo can supply neither the
     * real platform_user_id AND the correct token together.
     */
    @Transactional(readOnly = true)
    public ProofVerificationResponse verifyToken(Long sellerId, String buyerPlatformId, String token) {
        tenantContext.applyCurrentSeller(entityManager, sellerId);

        String normalizedToken = normalizeToken(token);

        return reservationProofRepository.findBySellerIdAndProofToken(sellerId, normalizedToken)
                .flatMap(proof -> orderRepository.findById(proof.getOrderId())
                        .filter(order -> order.getBuyerPlatformId().equals(buyerPlatformId))
                        .map(order -> ProofVerificationResponse.valid(order, proof.getSequenceNumber())))
                .orElseGet(ProofVerificationResponse::invalid);
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

    private String computeProofToken(String buyerPlatformId, Long inventoryItemId, String hash) {
        String raw = buyerPlatformId + "|" + inventoryItemId + "|" + hash;
        String hex = sha256(raw).substring(0, 12).toUpperCase();
        return hex.substring(0, 4) + "-" + hex.substring(4, 8) + "-" + hex.substring(8, 12);
    }

    private String normalizeToken(String token) {
        return token == null ? "" : token.trim().toUpperCase();
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