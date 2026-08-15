package com.taponsusi.audit;

import jakarta.persistence.*;
import java.time.Instant;


@Entity
@Table(name = "audit_events")
public class AuditEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "seller_id", nullable = false)
    private Long sellerId;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(name = "entity_type", nullable = false)
    private String entityType;

    @Column(name = "entity_id", nullable = false)
    private Long entityId;

    @Column(columnDefinition = "TEXT")
    private String detail;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected AuditEvent() {
    }

    public AuditEvent(Long sellerId, String eventType, String entityType, Long entityId, String detail) {
        this.sellerId = sellerId;
        this.eventType = eventType;
        this.entityType = entityType;
        this.entityId = entityId;
        this.detail = detail;
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public Long getSellerId() { return sellerId; }
    public String getEventType() { return eventType; }
    public String getEntityType() { return entityType; }
    public Long getEntityId() { return entityId; }
    public String getDetail() { return detail; }
    public Instant getCreatedAt() { return createdAt; }
}