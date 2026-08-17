package com.taponsusi.buyerreputation;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "buyer_reputation")
public class BuyerReputation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "seller_id", nullable = false)
    private Long sellerId;

    @Column(name = "buyer_platform_id", nullable = false)
    private String buyerPlatformId;

    @Column(name = "total_orders", nullable = false)
    private Integer totalOrders = 0;

    @Column(name = "orders_paid_on_time", nullable = false)
    private Integer ordersPaidOnTime = 0;

    @Column(name = "orders_overdue", nullable = false)
    private Integer ordersOverdue = 0;

    @Column(name = "orders_cancelled_by_seller", nullable = false)
    private Integer ordersCancelledBySeller = 0;

    @Enumerated(EnumType.STRING)
    @Column(name = "flagged_as", nullable = false)
    private ReputationFlag flaggedAs = ReputationFlag.NONE;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "last_updated", nullable = false)
    private Instant lastUpdated;

    protected BuyerReputation() {
    }

    public BuyerReputation(Long sellerId, String buyerPlatformId) {
        this.sellerId = sellerId;
        this.buyerPlatformId = buyerPlatformId;
    }

    @PrePersist
    @PreUpdate
    protected void onSave() {
        this.lastUpdated = Instant.now();
    }

    public Long getId() { return id; }
    public Long getSellerId() { return sellerId; }
    public String getBuyerPlatformId() { return buyerPlatformId; }
    public Integer getTotalOrders() { return totalOrders; }
    public Integer getOrdersPaidOnTime() { return ordersPaidOnTime; }
    public Integer getOrdersOverdue() { return ordersOverdue; }
    public Integer getOrdersCancelledBySeller() { return ordersCancelledBySeller; }
    public ReputationFlag getFlaggedAs() { return flaggedAs; }
    public String getNotes() { return notes; }

    public void incrementTotalOrders() { this.totalOrders++; }
    public void incrementPaidOnTime() { this.ordersPaidOnTime++; }
    public void incrementOverdue() { this.ordersOverdue++; }
    public void incrementCancelledBySeller() { this.ordersCancelledBySeller++; }
    public void setFlaggedAs(ReputationFlag flaggedAs) { this.flaggedAs = flaggedAs; }
    public void setNotes(String notes) { this.notes = notes; }
}