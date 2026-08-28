package com.vishwasena.urlshortener.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.OffsetDateTime;

@Schema(description = "Request payload to create a short URL")
public class CreateUrlRequest {
    @Schema(
        description = "Original URL to be shortened. Must be a valid HTTP or HTTPS URL between 1 and 2048 characters",
        example = "https://www.example.com/very/long/path/to/resource",
        minLength = 1,
        maxLength = 2048
    )
    @NotBlank(message = "Original URL is required")
    @Size(min = 1, max = 2048, message = "URL must be between 1 and 2048 characters")
    @JsonProperty("original_url")
    private String originalUrl;

    @Schema(
        description = "Expiration date and time in ISO-8601 format with timezone (e.g., RFC3339). Must be in the future. Optional field",
        example = "2026-12-31T23:59:59Z"
    )
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
