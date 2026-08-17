package com.taponsusi.reservationproof;

import jakarta.persistence.*;
import java.time.Instant;


@Entity
@Table(name = "reservation_proofs")
public class ReservationProof {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "seller_id", nullable = false)
    private Long sellerId;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "sequence_number", nullable = false)
    private Long sequenceNumber;

    @Column(name = "proof_data", nullable = false, columnDefinition = "TEXT")
    private String proofData;

    @Column(name = "previous_hash", nullable = false)
    private String previousHash;

    @Column(nullable = false)
    private String hash;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ReservationProof() {
    }

    public ReservationProof(Long sellerId, Long orderId, Long sequenceNumber,
                            String proofData, String previousHash, String hash) {
        this.sellerId = sellerId;
        this.orderId = orderId;
        this.sequenceNumber = sequenceNumber;
        this.proofData = proofData;
        this.previousHash = previousHash;
        this.hash = hash;
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public Long getSellerId() { return sellerId; }
    public Long getOrderId() { return orderId; }
    public Long getSequenceNumber() { return sequenceNumber; }
    public String getProofData() { return proofData; }
    public String getPreviousHash() { return previousHash; }
    public String getHash() { return hash; }
    public Instant getCreatedAt() { return createdAt; }
}