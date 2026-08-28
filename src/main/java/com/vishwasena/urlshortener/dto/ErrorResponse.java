package com.vishwasena.urlshortener.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Error response payload returned when an API request fails")
public class ErrorResponse {
    @Schema(description = "HTTP status code", example = "404")
    @JsonProperty("status")
    private int status;

    @Schema(description = "Human-readable error message describing what went wrong", example = "Short URL not found: abc123de")
    @JsonProperty("message")
    private String message;

    @Schema(description = "Unix timestamp in milliseconds when the error occurred", example = "1724841330000")
    @JsonProperty("timestamp")
    private long timestamp;

    public ErrorResponse() {
    }

    public ErrorResponse(int status, String message) {
        this.status = status;
        this.message = message;
        this.timestamp = System.currentTimeMillis();
    }

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
}
