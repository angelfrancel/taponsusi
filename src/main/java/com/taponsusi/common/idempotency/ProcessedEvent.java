package com.taponsusi.common.idempotency;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "processed_events")
public class ProcessedEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String platform;

    @Column(name = "platform_event_id", nullable = false)
    private String platformEventId;

    @Column(name = "received_at", nullable = false, updatable = false)
    private Instant receivedAt;

    protected ProcessedEvent() {
    }

    public ProcessedEvent(String platform, String platformEventId) {
        this.platform = platform;
        this.platformEventId = platformEventId;
    }

    @PrePersist
    protected void onCreate() {
        this.receivedAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getPlatform() { return platform; }
    public String getPlatformEventId() { return platformEventId; }
    public Instant getReceivedAt() { return receivedAt; }
}