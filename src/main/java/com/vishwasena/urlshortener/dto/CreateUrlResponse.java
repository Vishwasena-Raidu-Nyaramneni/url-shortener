package com.vishwasena.urlshortener.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class CreateUrlResponse {
    @JsonProperty("id")
    private Long id;

    @JsonProperty("short_code")
    private String shortCode;

    @JsonProperty("original_url")
    private String originalUrl;

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
