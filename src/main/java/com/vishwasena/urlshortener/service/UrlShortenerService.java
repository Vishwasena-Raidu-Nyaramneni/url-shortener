package com.vishwasena.urlshortener.service;

import com.vishwasena.urlshortener.entity.ClickEvent;
import com.vishwasena.urlshortener.entity.ShortUrl;
import com.vishwasena.urlshortener.exception.DisabledUrlException;
import com.vishwasena.urlshortener.exception.ExpiredUrlException;
import com.vishwasena.urlshortener.exception.UrlAlreadyExistsException;
import com.vishwasena.urlshortener.exception.UrlNotFoundException;
import com.vishwasena.urlshortener.repository.ClickEventRepository;
import com.vishwasena.urlshortener.repository.ShortUrlRepository;
import com.vishwasena.urlshortener.util.IpHasher;
import com.vishwasena.urlshortener.util.ShortCodeGenerator;
import com.vishwasena.urlshortener.util.UrlValidator;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

@Service
@Transactional
public class UrlShortenerService {
    private static final int MAX_COLLISION_RETRIES = 5;

    private final ShortUrlRepository shortUrlRepository;
    private final ClickEventRepository clickEventRepository;

    public UrlShortenerService(ShortUrlRepository shortUrlRepository, ClickEventRepository clickEventRepository) {
        this.shortUrlRepository = shortUrlRepository;
        this.clickEventRepository = clickEventRepository;
    }

    public ShortUrl createShortUrl(String originalUrl, OffsetDateTime expiresAt) {
        // Validate URL
        if (!UrlValidator.isValidUrl(originalUrl)) {
            throw new IllegalArgumentException("Invalid URL: " + originalUrl);
        }

        // Deduplication: check if same URL + same expiration already exists
        var existing = shortUrlRepository.findByOriginalUrlAndExpiresAt(originalUrl, expiresAt);
        if (existing.isPresent()) {
            throw new UrlAlreadyExistsException("URL with this configuration already exists. Short code: " + existing.get().getShortCode());
        }

        // Create short URL with collision retry
        ShortUrl shortUrl = null;
        for (int attempt = 0; attempt < MAX_COLLISION_RETRIES; attempt++) {
            String shortCode = ShortCodeGenerator.generate();
            shortUrl = new ShortUrl(shortCode, originalUrl, "ACTIVE");
            shortUrl.setExpiresAt(expiresAt);

            try {
                return shortUrlRepository.save(shortUrl);
            } catch (DataIntegrityViolationException e) {
                if (attempt == MAX_COLLISION_RETRIES - 1) {
                    throw new RuntimeException("Failed to generate unique short code after " + MAX_COLLISION_RETRIES + " attempts", e);
                }
                // Retry with next iteration
            }
        }

        throw new RuntimeException("Failed to create short URL");
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "short_urls", key = "#shortCode")
    public ShortUrl getShortUrlByCode(String shortCode) {
        ShortUrl shortUrl = shortUrlRepository.findByShortCode(shortCode)
                .orElseThrow(() -> new UrlNotFoundException(shortCode));

        // Check if URL is disabled
        if ("DISABLED".equals(shortUrl.getStatus())) {
            throw new DisabledUrlException(shortCode);
        }

        // Check if URL is expired
        if (shortUrl.getExpiresAt() != null && shortUrl.getExpiresAt().isBefore(OffsetDateTime.now())) {
            throw new ExpiredUrlException(shortCode);
        }

        return shortUrl;
    }

    @Transactional(readOnly = true)
    public ShortUrl getShortUrlById(Long id) {
        return shortUrlRepository.findById(id)
                .orElseThrow(() -> new UrlNotFoundException("ID: " + id));
    }

    @Transactional
    public void recordClick(String shortCode, String ipAddress, String userAgent, String referer) {
        ShortUrl shortUrl = getShortUrlByCode(shortCode);

        // Create click event
        String ipHash = IpHasher.hashIp(ipAddress);
        ClickEvent clickEvent = new ClickEvent(shortUrl, ipHash, userAgent, referer);
        clickEventRepository.save(clickEvent);

        // Increment click count (note: non-atomic, suitable for MVP; future optimization could use atomic SQL UPDATE)
        shortUrl.incrementClickCount();
        shortUrlRepository.save(shortUrl);
    }

    @Transactional(readOnly = true)
    public AnalyticsData getAnalytics(Long shortUrlId) {
        ShortUrl shortUrl = shortUrlRepository.findById(shortUrlId)
                .orElseThrow(() -> new UrlNotFoundException("ID: " + shortUrlId));

        long uniqueVisitors = clickEventRepository.countUniqueVisitors(shortUrlId);
        OffsetDateTime lastClickedAt = clickEventRepository.getLastClickedAt(shortUrlId);

        return new AnalyticsData(
                shortUrl.getId(),
                shortUrl.getShortCode(),
                shortUrl.getClickCount(),
                uniqueVisitors,
                lastClickedAt
        );
    }

    @CacheEvict(value = "short_urls", key = "#id.toString()")
    public void disableShortUrl(Long id) {
        ShortUrl shortUrl = shortUrlRepository.findById(id)
                .orElseThrow(() -> new UrlNotFoundException("ID: " + id));
        shortUrl.setStatus("DISABLED");
        shortUrlRepository.save(shortUrl);
    }

    public static class AnalyticsData {
        public final Long shortUrlId;
        public final String shortCode;
        public final Long totalClicks;
        public final Long uniqueVisitors;
        public final OffsetDateTime lastClickedAt;

        public AnalyticsData(Long shortUrlId, String shortCode, Long totalClicks, Long uniqueVisitors, OffsetDateTime lastClickedAt) {
            this.shortUrlId = shortUrlId;
            this.shortCode = shortCode;
            this.totalClicks = totalClicks;
            this.uniqueVisitors = uniqueVisitors;
            this.lastClickedAt = lastClickedAt;
        }
    }
}
