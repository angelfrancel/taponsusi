package com.taponsusi.inventory;

import jakarta.persistence.*;
import java.time.Instant;


@Entity
@Table(name = "inventory_items")
public class InventoryItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "seller_id", nullable = false)
    private Long sellerId;

    @Column(name = "session_id", nullable = false)
    private Long sessionId;

    @Column(name = "reservation_code", nullable = false)
    private String reservationCode;

    @Column(nullable = false)
    private String name;

    @Column(name = "total_stock", nullable = false)
    private Integer totalStock;

    @Column(name = "available_stock", nullable = false)
    private Integer availableStock;

    @Column(name = "max_quantity_per_buyer", nullable = false)
    private Integer maxQuantityPerBuyer;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected InventoryItem() {
    }

    public InventoryItem(Long sellerId, Long sessionId, String reservationCode,
                         String name, Integer totalStock, Integer maxQuantityPerBuyer) {
        this.sellerId = sellerId;
        this.sessionId = sessionId;
        this.reservationCode = reservationCode;
        this.name = name;
        this.totalStock = totalStock;
        this.availableStock = totalStock;
        this.maxQuantityPerBuyer = maxQuantityPerBuyer;
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
    public String getReservationCode() { return reservationCode; }
    public String getName() { return name; }
    public Integer getTotalStock() { return totalStock; }
    public Integer getAvailableStock() { return availableStock; }
    public Integer getMaxQuantityPerBuyer() { return maxQuantityPerBuyer; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}