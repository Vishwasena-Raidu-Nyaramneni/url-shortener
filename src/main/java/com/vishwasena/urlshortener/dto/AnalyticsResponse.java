package com.vishwasena.urlshortener.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.OffsetDateTime;

public class AnalyticsResponse {
    @JsonProperty("short_url_id")
    private Long shortUrlId;

    @JsonProperty("short_code")
    private String shortCode;

    @JsonProperty("total_clicks")
    private Long totalClicks;

    @JsonProperty("unique_visitors")
    private Long uniqueVisitors;

    @JsonProperty("last_clicked_at")
    private OffsetDateTime lastClickedAt;

    public AnalyticsResponse() {
    }

    public AnalyticsResponse(Long shortUrlId, String shortCode, Long totalClicks, Long uniqueVisitors, OffsetDateTime lastClickedAt) {
        this.shortUrlId = shortUrlId;
        this.shortCode = shortCode;
        this.totalClicks = totalClicks;
        this.uniqueVisitors = uniqueVisitors;
        this.lastClickedAt = lastClickedAt;
    }

    public Long getShortUrlId() {
        return shortUrlId;
    }

    public void setShortUrlId(Long shortUrlId) {
        this.shortUrlId = shortUrlId;
    }

    public String getShortCode() {
        return shortCode;
    }

    public void setShortCode(String shortCode) {
        this.shortCode = shortCode;
    }

    public Long getTotalClicks() {
        return totalClicks;
    }

    public void setTotalClicks(Long totalClicks) {
        this.totalClicks = totalClicks;
    }

    public Long getUniqueVisitors() {
        return uniqueVisitors;
    }

    public void setUniqueVisitors(Long uniqueVisitors) {
        this.uniqueVisitors = uniqueVisitors;
    }

    public OffsetDateTime getLastClickedAt() {
        return lastClickedAt;
    }

    public void setLastClickedAt(OffsetDateTime lastClickedAt) {
        this.lastClickedAt = lastClickedAt;
    }
}
