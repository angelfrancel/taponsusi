package com.taponsusi.messaging;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(
        name = "outbound_messages",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uq_outbound_message_idempotency",
                        columnNames = "idempotency_key"
                )
        }
)
public class OutboundMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "seller_id", nullable = false)
    private Long sellerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "message_type", nullable = false, length = 50)
    private OutboundMessageType messageType;

    @Column(
            name = "idempotency_key",
            nullable = false,
            length = 255
    )
    private String idempotencyKey;

    @Column(nullable = false, length = 30)
    private String platform;

    @Column(
            name = "buyer_platform_id",
            nullable = false
    )
    private String buyerPlatformId;

    @Column(
            name = "message_text",
            nullable = false,
            columnDefinition = "TEXT"
    )
    private String messageText;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private OutboundMessageStatus status;

    @Column(name = "attempt_count", nullable = false)
    private Integer attemptCount;

    @Column(name = "last_error", length = 1000)
    private String lastError;

    @Column(
            name = "created_at",
            nullable = false,
            updatable = false
    )
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "sent_at")
    private Instant sentAt;

    protected OutboundMessage() {
    }

    public OutboundMessage(
            Long sellerId,
            OutboundMessageType messageType,
            String idempotencyKey,
            String platform,
            String buyerPlatformId,
            String messageText
    ) {
        this.sellerId = sellerId;
        this.messageType = messageType;
        this.idempotencyKey = idempotencyKey;
        this.platform = platform;
        this.buyerPlatformId = buyerPlatformId;
        this.messageText = messageText;
        this.status = OutboundMessageStatus.PENDING;
        this.attemptCount = 0;
    }

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();

        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    public void markSent(Instant sentAt) {
        if (sentAt == null) {
            throw new IllegalArgumentException(
                    "Sent time is required"
            );
        }

        this.status = OutboundMessageStatus.SENT;
        this.sentAt = sentAt;
        this.lastError = null;
        this.attemptCount++;
    }

    public void markFailed(String errorMessage) {
        this.status = OutboundMessageStatus.FAILED;
        this.sentAt = null;
        this.lastError = truncateError(errorMessage);
        this.attemptCount++;
    }

    private String truncateError(String errorMessage) {
        if (errorMessage == null
                || errorMessage.isBlank()) {
            return "Unknown messaging failure";
        }

        String trimmed = errorMessage.trim();

        if (trimmed.length() <= 1000) {
            return trimmed;
        }

        return trimmed.substring(0, 1000);
    }

    public Long getId() {
        return id;
    }

    public Long getSellerId() {
        return sellerId;
    }

    public OutboundMessageType getMessageType() {
        return messageType;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getPlatform() {
        return platform;
    }

    public String getBuyerPlatformId() {
        return buyerPlatformId;
    }

    public String getMessageText() {
        return messageText;
    }

    public OutboundMessageStatus getStatus() {
        return status;
    }

    public Integer getAttemptCount() {
        return attemptCount;
    }

    public String getLastError() {
        return lastError;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getSentAt() {
        return sentAt;
    }
}