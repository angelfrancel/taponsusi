package com.taponsusi.order;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "orders")
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "seller_id", nullable = false)
    private Long sellerId;

    @Column(name = "session_id", nullable = false)
    private Long sessionId;

    @Column(name = "inventory_item_id", nullable = false)
    private Long inventoryItemId;

    @Column(name = "buyer_platform_id", nullable = false)
    private String buyerPlatformId;

    @Column(name = "requested_quantity", nullable = false)
    private Integer requestedQuantity;

    @Column(nullable = false)
    private Integer quantity; // fulfilled amount

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Order() {
    }

    public Order(Long sellerId, Long sessionId, Long inventoryItemId, String buyerPlatformId,
                 Integer requestedQuantity, Integer quantity) {
        this.sellerId = sellerId;
        this.sessionId = sessionId;
        this.inventoryItemId = inventoryItemId;
        this.buyerPlatformId = buyerPlatformId;
        this.requestedQuantity = requestedQuantity;
        this.quantity = quantity;
        this.status = OrderStatus.PENDING;
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public Long getSellerId() { return sellerId; }
    public Long getSessionId() { return sessionId; }
    public Long getInventoryItemId() { return inventoryItemId; }
    public String getBuyerPlatformId() { return buyerPlatformId; }
    public Integer getRequestedQuantity() { return requestedQuantity; }
    public Integer getQuantity() { return quantity; }
    public OrderStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public void setStatus(OrderStatus status) { this.status = status; }
}