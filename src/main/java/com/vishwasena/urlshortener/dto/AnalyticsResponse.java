package com.vishwasena.urlshortener.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;

@Schema(description = "Analytics data for a short URL")
public class AnalyticsResponse {
    @Schema(description = "Database ID of the short URL", example = "123")
    @JsonProperty("short_url_id")
    private Long shortUrlId;

    @Schema(description = "Unique short code", example = "abc123de")
    @JsonProperty("short_code")
    private String shortCode;

    @Schema(description = "Total number of clicks on this short URL", example = "42")
    @JsonProperty("total_clicks")
    private Long totalClicks;

    @Schema(description = "Number of unique visitors (based on IP hash)", example = "15")
    @JsonProperty("unique_visitors")
    private Long uniqueVisitors;

    @Schema(description = "Timestamp of the last click in ISO-8601 format", example = "2024-08-28T14:30:45Z")
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
