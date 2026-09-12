package com.taponsusi.livesession;

import jakarta.persistence.*;
import java.time.Instant;


@Entity
@Table(name = "live_sessions")
public class LiveSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "facebook_page_id")
    private String facebookPageId;

    @Column(name = "seller_id", nullable = false)
    private Long sellerId;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String platform; // e.g. SIMULATION, FACEBOOK

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LiveSessionStatus status;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected LiveSession() {
    }

    public LiveSession(Long sellerId, String title, String platform) {
        this.sellerId = sellerId;
        this.title = title;
        this.platform = platform;
        this.status = LiveSessionStatus.DRAFT;;
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
    public String getFacebookPageId() { return facebookPageId; }
    public String getTitle() { return title; }
    public String getPlatform() { return platform; }
    public LiveSessionStatus getStatus() { return status; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getEndedAt() { return endedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    void setStatus(LiveSessionStatus status) { this.status = status; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }
    public void setEndedAt(Instant endedAt) { this.endedAt = endedAt; }
    public void setFacebookPageId(String facebookPageId) { this.facebookPageId = facebookPageId; }
}