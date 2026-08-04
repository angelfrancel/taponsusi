package com.taponsusi.seller;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "sellers")
public class Seller {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "business_name", nullable = false)
    private String businessName;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Seller() {
        // required by JPA — never called directly in our own code
    }

    public Seller(String email, String passwordHash, String businessName) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.businessName = businessName;
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
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public String getBusinessName() { return businessName; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setBusinessName(String businessName) { this.businessName = businessName; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
}