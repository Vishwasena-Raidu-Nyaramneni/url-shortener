package com.vishwasena.urlshortener.controller;

import com.vishwasena.urlshortener.dto.AnalyticsResponse;
import com.vishwasena.urlshortener.dto.CreateUrlRequest;
import com.vishwasena.urlshortener.dto.CreateUrlResponse;
import com.vishwasena.urlshortener.entity.ShortUrl;
import com.vishwasena.urlshortener.exception.UrlAlreadyExistsException;
import com.vishwasena.urlshortener.exception.UrlNotFoundException;
import com.vishwasena.urlshortener.service.UrlShortenerService;
import com.vishwasena.urlshortener.util.ClientIpExtractor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.view.RedirectView;

@RestController
@RequestMapping
@Tag(name = "URL Management", description = "Create, redirect, and manage short URLs with analytics")
public class UrlController {
    private final UrlShortenerService urlShortenerService;
    private final String baseUrl;

    public UrlController(UrlShortenerService urlShortenerService, @Value("${app.base-url:http://localhost:8080}") String baseUrl) {
        this.urlShortenerService = urlShortenerService;
        this.baseUrl = baseUrl;
    }

    @PostMapping("/api/v1/urls")
    @Operation(
        summary = "Create a short URL",
        description = "Creates a new short URL for the provided original URL, or returns the existing short URL if the URL has already been shortened",
        tags = {"URL Management"}
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "Short URL created successfully"),
        @ApiResponse(responseCode = "200", description = "Short URL already exists for this URL"),
        @ApiResponse(responseCode = "400", description = "Invalid input - URL validation failed"),
        @ApiResponse(responseCode = "409", description = "URL conflict")
    })
    public ResponseEntity<CreateUrlResponse> createShortUrl(@Valid @RequestBody CreateUrlRequest request) {
        try {
            ShortUrl shortUrl = urlShortenerService.createShortUrl(request.getOriginalUrl(), request.getExpiresAt());

            String shortUrlFull = baseUrl + "/" + shortUrl.getShortCode();
            CreateUrlResponse response = new CreateUrlResponse(
                    shortUrl.getId(),
                    shortUrl.getShortCode(),
                    shortUrl.getOriginalUrl(),
                    shortUrlFull
            );

            // New URL created - return 201 CREATED
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (UrlAlreadyExistsException ex) {
            // URL already exists - return 200 OK with existing URL details
            // Extract short code from exception message
            String message = ex.getMessage();
            String shortCode = message.substring(message.lastIndexOf(": ") + 2);
            
            ShortUrl existingUrl = urlShortenerService.getShortUrlByCode(shortCode);
            String shortUrlFull = baseUrl + "/" + shortCode;
            CreateUrlResponse response = new CreateUrlResponse(
                    existingUrl.getId(),
                    shortCode,
                    existingUrl.getOriginalUrl(),
                    shortUrlFull
            );

            // Existing URL returned - return 200 OK
            return ResponseEntity.status(HttpStatus.OK).body(response);
        }
    }

    @GetMapping("/{shortCode}")
    @Operation(
        summary = "Redirect to original URL",
        description = "Redirects to the original URL for the provided short code and records click analytics",
        tags = {"URL Management"}
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "302", description = "Redirect to original URL"),
        @ApiResponse(responseCode = "404", description = "Short code not found"),
        @ApiResponse(responseCode = "410", description = "Short URL has expired or is disabled")
    })
    public RedirectView redirect(@PathVariable String shortCode, HttpServletRequest request) {
        // Validate short code is not empty
        if (shortCode == null || shortCode.trim().isEmpty()) {
            throw new UrlNotFoundException("Short code is required");
        }

        // Record click before redirecting
        String clientIp = ClientIpExtractor.extractClientIp(request);
        String userAgent = request.getHeader("User-Agent");
        String referer = request.getHeader("Referer");

        urlShortenerService.recordClick(shortCode, clientIp, userAgent, referer);

        // Fetch the URL and redirect
        ShortUrl shortUrl = urlShortenerService.getShortUrlByCode(shortCode);
        RedirectView redirectView = new RedirectView();
        redirectView.setUrl(shortUrl.getOriginalUrl());
        redirectView.setStatusCode(HttpStatus.FOUND);
        return redirectView;
    }

    @GetMapping("/api/v1/urls/{id}")
    @Operation(
        summary = "Get short URL details",
        description = "Retrieves detailed information about a short URL by its ID",
        tags = {"URL Management"}
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Short URL details retrieved successfully"),
        @ApiResponse(responseCode = "404", description = "Short URL not found")
    })
    public ResponseEntity<ShortUrl> getShortUrl(@PathVariable Long id) {
        ShortUrl shortUrl = urlShortenerService.getShortUrlById(id);
        return ResponseEntity.ok(shortUrl);
    }

    @DeleteMapping("/api/v1/urls/{id}")
    @Operation(
        summary = "Disable short URL",
        description = "Disables a short URL, preventing further redirects",
        tags = {"URL Management"}
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "204", description = "Short URL disabled successfully"),
        @ApiResponse(responseCode = "404", description = "Short URL not found")
    })
    public ResponseEntity<Void> disableShortUrl(@PathVariable Long id) {
        urlShortenerService.disableShortUrl(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/v1/urls/{id}/analytics")
    @Operation(
        summary = "Get analytics data",
        description = "Retrieves analytics data for a short URL including total clicks, unique visitors, and last click timestamp",
        tags = {"URL Management"}
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Analytics data retrieved successfully"),
        @ApiResponse(responseCode = "404", description = "Short URL not found"),
        @ApiResponse(responseCode = "410", description = "Short URL has expired or is disabled")
    })
    public ResponseEntity<AnalyticsResponse> getAnalytics(@PathVariable Long id) {
        UrlShortenerService.AnalyticsData data = urlShortenerService.getAnalytics(id);
        AnalyticsResponse response = new AnalyticsResponse(
                data.shortUrlId,
                data.shortCode,
                data.totalClicks,
                data.uniqueVisitors,
                data.lastClickedAt
        );
        return ResponseEntity.ok(response);
    }
}

