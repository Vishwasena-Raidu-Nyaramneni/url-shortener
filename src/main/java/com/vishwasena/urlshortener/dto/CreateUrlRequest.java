package com.vishwasena.urlshortener.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Size;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.OffsetDateTime;

public class CreateUrlRequest {
    @NotBlank(message = "Original URL is required")
    @Size(min = 1, max = 2048, message = "URL must be between 1 and 2048 characters")
    @JsonProperty("original_url")
    private String originalUrl;

    @Future(message = "Expiration date must be in the future (UTC). Example: 2026-12-31T23:59:59Z")
    @JsonProperty("expires_at")
    private OffsetDateTime expiresAt;

    public CreateUrlRequest() {
    }

    public CreateUrlRequest(String originalUrl, OffsetDateTime expiresAt) {
        this.originalUrl = originalUrl;
        this.expiresAt = expiresAt;
    }

    public String getOriginalUrl() {
        return originalUrl;
    }

    public void setOriginalUrl(String originalUrl) {
        this.originalUrl = originalUrl;
    }

    public OffsetDateTime getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(OffsetDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }
}
