package com.taponsusi.waitlist;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(
        name = "waitlist_entries",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uq_waitlist_platform_event",
                        columnNames = {
                                "platform",
                                "platform_event_id"
                        }
                )
        }
)
public class WaitlistEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "seller_id", nullable = false)
    private Long sellerId;

    @Column(name = "session_id", nullable = false)
    private Long sessionId;

    @Column(name = "inventory_item_id", nullable = false)
    private Long inventoryItemId;

    @Column(nullable = false, length = 30)
    private String platform;

    @Column(
            name = "platform_event_id",
            nullable = false
    )
    private String platformEventId;

    @Column(
            name = "buyer_platform_id",
            nullable = false
    )
    private String buyerPlatformId;

    @Column(
            name = "requested_quantity",
            nullable = false
    )
    private Integer requestedQuantity;

    @Column(
            name = "source_created_at",
            nullable = false
    )
    private Instant sourceCreatedAt;

    @Column(
            name = "received_at",
            nullable = false
    )
    private Instant receivedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private WaitlistStatus status;

    @Column(name = "offered_at")
    private Instant offeredAt;

    @Column(name = "offer_expires_at")
    private Instant offerExpiresAt;

    @Column(name = "offered_quantity")
    private Integer offeredQuantity;

    @Column(name = "promoted_order_id")
    private Long promotedOrderId;

    @Column(
            name = "created_at",
            nullable = false,
            updatable = false
    )
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected WaitlistEntry() {
    }

    public WaitlistEntry(
            Long sellerId,
            Long sessionId,
            Long inventoryItemId,
            String platform,
            String platformEventId,
            String buyerPlatformId,
            Integer requestedQuantity,
            Instant sourceCreatedAt
    ) {
        this.sellerId = sellerId;
        this.sessionId = sessionId;
        this.inventoryItemId = inventoryItemId;
        this.platform = platform;
        this.platformEventId = platformEventId;
        this.buyerPlatformId = buyerPlatformId;
        this.requestedQuantity = requestedQuantity;
        this.sourceCreatedAt = sourceCreatedAt;
        this.status = WaitlistStatus.WAITING;
    }

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();

        if (receivedAt == null) {
            receivedAt = now;
        }

        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Long getSellerId() {
        return sellerId;
    }

    public Long getSessionId() {
        return sessionId;
    }

    public Long getInventoryItemId() {
        return inventoryItemId;
    }

    public String getPlatform() {
        return platform;
    }

    public String getPlatformEventId() {
        return platformEventId;
    }

    public String getBuyerPlatformId() {
        return buyerPlatformId;
    }

    public Integer getRequestedQuantity() {
        return requestedQuantity;
    }

    public Instant getSourceCreatedAt() {
        return sourceCreatedAt;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }

    public WaitlistStatus getStatus() {
        return status;
    }

    public Instant getOfferedAt() {
        return offeredAt;
    }

    public Instant getOfferExpiresAt() {
        return offerExpiresAt;
    }

    public Integer getOfferedQuantity() {
        return offeredQuantity;
    }

    public Long getPromotedOrderId() {
        return promotedOrderId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    void markOffered(
            Instant offeredAt,
            Instant offerExpiresAt,
            Integer offeredQuantity
    ) {
        if (offeredAt == null) {
            throw new IllegalArgumentException(
                    "Offer time is required"
            );
        }

        if (offerExpiresAt == null
                || !offerExpiresAt.isAfter(offeredAt)) {
            throw new IllegalArgumentException(
                    "Offer expiration must be after the offer time"
            );
        }

        if (offeredQuantity == null
                || offeredQuantity <= 0
                || offeredQuantity > requestedQuantity) {
            throw new IllegalArgumentException(
                    "Offered quantity must be positive and cannot exceed requested quantity"
            );
        }

        this.status = WaitlistStatus.OFFERED;
        this.offeredAt = offeredAt;
        this.offerExpiresAt = offerExpiresAt;
        this.offeredQuantity = offeredQuantity;
    }

    void markPromoted(Long promotedOrderId) {
        this.status = WaitlistStatus.PROMOTED;
        this.promotedOrderId = promotedOrderId;
    }

    void markDeclined() {
        this.status = WaitlistStatus.DECLINED;
    }

    void markExpired() {
        this.status = WaitlistStatus.EXPIRED;
    }

    void markCancelled() {
        this.status = WaitlistStatus.CANCELLED;
    }
}