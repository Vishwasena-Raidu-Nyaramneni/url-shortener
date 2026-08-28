package com.vishwasena.urlshortener.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.OffsetDateTime;

@Entity
@Table(name = "short_url", indexes = {
        @Index(name = "idx_short_code", columnList = "short_code", unique = true),
        @Index(name = "idx_status", columnList = "status"),
        @Index(name = "idx_expires_at", columnList = "expires_at")
})
@Schema(description = "A shortened URL entity containing the mapping between short code and original URL, along with metadata")
public class ShortUrl {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Schema(description = "Database ID of the short URL", example = "123")
    private Long id;

    @NotBlank
    @Column(name = "short_code", nullable = false, unique = true, length = 20)
    @Schema(description = "Unique 8-character Base62 identifier for the short URL", example = "abc123de")
    private String shortCode;

    @NotBlank
    @Column(name = "original_url", nullable = false, columnDefinition = "TEXT")
    @Schema(description = "The original URL that this short URL redirects to", example = "https://www.example.com/very/long/path")
    private String originalUrl;

    @NotNull
    @Column(name = "status", nullable = false, length = 20)
    @Schema(description = "Status of the short URL: ACTIVE or DISABLED", example = "ACTIVE")
    private String status; // ACTIVE, DISABLED

    @Column(name = "created_at", nullable = false, updatable = false)
    @Schema(description = "Timestamp when the short URL was created", example = "2024-08-28T10:15:30Z")
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    @Schema(description = "Timestamp when the short URL was last updated", example = "2024-08-28T10:15:30Z")
    private OffsetDateTime updatedAt;

    @Column(name = "expires_at")
    @Schema(description = "Timestamp when the short URL expires (optional)", example = "2026-12-31T23:59:59Z")
    private OffsetDateTime expiresAt;

    @Column(name = "click_count", nullable = false)
    @Schema(description = "Total number of times this short URL has been clicked", example = "42")
    private Long clickCount = 0L;

    public ShortUrl() {
    }

    public ShortUrl(String shortCode, String originalUrl, String status) {
        this.shortCode = shortCode;
        this.originalUrl = originalUrl;
        this.status = status;
        this.createdAt = OffsetDateTime.now();
        this.updatedAt = OffsetDateTime.now();
        this.clickCount = 0L;
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
        if (updatedAt == null) {
            updatedAt = OffsetDateTime.now();
        }
        if (clickCount == null) {
            clickCount = 0L;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getShortCode() {
        return shortCode;
    }

    public void setShortCode(String shortCode) {
        this.shortCode = shortCode;
    }

    public String getOriginalUrl() {
        return originalUrl;
    }

    public void setOriginalUrl(String originalUrl) {
        this.originalUrl = originalUrl;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public OffsetDateTime getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(OffsetDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }

    public Long getClickCount() {
        return clickCount;
    }

    public void setClickCount(Long clickCount) {
        this.clickCount = clickCount;
    }

    public void incrementClickCount() {
        this.clickCount++;
    }
}
