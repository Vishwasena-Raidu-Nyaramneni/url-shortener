package com.vishwasena.urlshortener.controller;

import com.vishwasena.urlshortener.dto.AnalyticsResponse;
import com.vishwasena.urlshortener.dto.CreateUrlRequest;
import com.vishwasena.urlshortener.dto.CreateUrlResponse;
import com.vishwasena.urlshortener.entity.ShortUrl;
import com.vishwasena.urlshortener.service.UrlShortenerService;
import com.vishwasena.urlshortener.util.ClientIpExtractor;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.view.RedirectView;

@RestController
@RequestMapping
public class UrlController {
    private final UrlShortenerService urlShortenerService;
    private final String baseUrl;

    public UrlController(UrlShortenerService urlShortenerService, @Value("${app.base-url:http://localhost:8080}") String baseUrl) {
        this.urlShortenerService = urlShortenerService;
        this.baseUrl = baseUrl;
    }

    @PostMapping("/api/v1/urls")
    public ResponseEntity<CreateUrlResponse> createShortUrl(@Valid @RequestBody CreateUrlRequest request) {
        ShortUrl shortUrl = urlShortenerService.createShortUrl(request.getOriginalUrl(), request.getExpiresAt());

        String shortUrlFull = baseUrl + "/" + shortUrl.getShortCode();
        CreateUrlResponse response = new CreateUrlResponse(
                shortUrl.getId(),
                shortUrl.getShortCode(),
                shortUrl.getOriginalUrl(),
                shortUrlFull
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{shortCode}")
    public RedirectView redirect(@PathVariable String shortCode, HttpServletRequest request) {
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
    public ResponseEntity<ShortUrl> getShortUrl(@PathVariable Long id) {
        ShortUrl shortUrl = urlShortenerService.getShortUrlById(id);
        return ResponseEntity.ok(shortUrl);
    }

    @DeleteMapping("/api/v1/urls/{id}")
    public ResponseEntity<Void> disableShortUrl(@PathVariable Long id) {
        urlShortenerService.disableShortUrl(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/v1/urls/{id}/analytics")
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
