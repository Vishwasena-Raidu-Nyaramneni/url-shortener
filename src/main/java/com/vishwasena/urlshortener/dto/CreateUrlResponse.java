package com.vishwasena.urlshortener.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Response payload containing details of the created or existing short URL")
public class CreateUrlResponse {
    @Schema(description = "Database ID of the short URL", example = "123")
    @JsonProperty("id")
    private Long id;

    @Schema(description = "Unique short code (8-character Base62 identifier)", example = "abc123de")
    @JsonProperty("short_code")
    private String shortCode;

    @Schema(description = "The original URL that was shortened", example = "https://www.example.com/very/long/path")
    @JsonProperty("original_url")
    private String originalUrl;

    @Schema(description = "Complete short URL ready to use for redirects", example = "http://localhost:8080/abc123de")
    @JsonProperty("short_url")
    private String shortUrl;

    public CreateUrlResponse() {
    }

    public CreateUrlResponse(Long id, String shortCode, String originalUrl, String shortUrl) {
        this.id = id;
        this.shortCode = shortCode;
        this.originalUrl = originalUrl;
        this.shortUrl = shortUrl;
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

    public String getShortUrl() {
        return shortUrl;
    }

    public void setShortUrl(String shortUrl) {
        this.shortUrl = shortUrl;
    }
}
