package com.vishwasena.urlshortener.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.OffsetDateTime;

public class CreateUrlRequest {
    @NotBlank(message = "Original URL is required")
    @JsonProperty("original_url")
    private String originalUrl;

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
