package com.vishwasena.urlshortener.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;

import java.time.OffsetDateTime;

@Entity
@Table(name = "click_event", indexes = {
        @Index(name = "idx_short_url_id", columnList = "short_url_id"),
        @Index(name = "idx_clicked_at", columnList = "clicked_at")
})
public class ClickEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "short_url_id", nullable = false, foreignKey = @ForeignKey(name = "fk_short_url_id"))
    private ShortUrl shortUrl;

    @NotNull
    @Column(name = "clicked_at", nullable = false, updatable = false)
    private OffsetDateTime clickedAt;

    @Column(name = "ip_hash", length = 64)
    private String ipHash;

    @Column(name = "user_agent", columnDefinition = "TEXT")
    private String userAgent;

    @Column(name = "referer", columnDefinition = "TEXT")
    private String referer;

    public ClickEvent() {
    }

    public ClickEvent(ShortUrl shortUrl, String ipHash, String userAgent, String referer) {
        this.shortUrl = shortUrl;
        this.ipHash = ipHash;
        this.userAgent = userAgent;
        this.referer = referer;
        this.clickedAt = OffsetDateTime.now();
    }

    @PrePersist
    protected void onCreate() {
        if (clickedAt == null) {
            clickedAt = OffsetDateTime.now();
        }
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public ShortUrl getShortUrl() {
        return shortUrl;
    }

    public void setShortUrl(ShortUrl shortUrl) {
        this.shortUrl = shortUrl;
    }

    public OffsetDateTime getClickedAt() {
        return clickedAt;
    }

    public void setClickedAt(OffsetDateTime clickedAt) {
        this.clickedAt = clickedAt;
    }

    public String getIpHash() {
        return ipHash;
    }

    public void setIpHash(String ipHash) {
        this.ipHash = ipHash;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }

    public String getReferer() {
        return referer;
    }

    public void setReferer(String referer) {
        this.referer = referer;
    }
}
