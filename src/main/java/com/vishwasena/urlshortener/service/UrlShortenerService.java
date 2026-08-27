package com.vishwasena.urlshortener.service;

import com.vishwasena.urlshortener.entity.ClickEvent;
import com.vishwasena.urlshortener.entity.ShortUrl;
import com.vishwasena.urlshortener.exception.DisabledUrlException;
import com.vishwasena.urlshortener.exception.ExpiredUrlException;
import com.vishwasena.urlshortener.exception.UrlNotFoundException;
import com.vishwasena.urlshortener.repository.ClickEventRepository;
import com.vishwasena.urlshortener.repository.ShortUrlRepository;
import com.vishwasena.urlshortener.util.IpHasher;
import com.vishwasena.urlshortener.util.ShortCodeGenerator;
import com.vishwasena.urlshortener.util.UrlValidator;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

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

    public void recordClick(String shortCode, String ipAddress, String userAgent, String referer) {
        ShortUrl shortUrl = getShortUrlByCode(shortCode);

        // Create click event
        String ipHash = IpHasher.hashIp(ipAddress);
        ClickEvent clickEvent = new ClickEvent(shortUrl, ipHash, userAgent, referer);
        clickEventRepository.save(clickEvent);

        // Increment click count
        shortUrl.incrementClickCount();
        shortUrlRepository.save(shortUrl);
    }

    @Transactional(readOnly = true)
    public AnalyticsData getAnalytics(Long shortUrlId) {
        ShortUrl shortUrl = shortUrlRepository.findById(shortUrlId)
                .orElseThrow(() -> new UrlNotFoundException("ID: " + shortUrlId));

        List<ClickEvent> clickEvents = clickEventRepository.findByShortUrlId(shortUrlId);
        long uniqueVisitors = clickEventRepository.countUniqueVisitors(shortUrlId);

        OffsetDateTime lastClickedAt = clickEvents.isEmpty() ? null : 
                clickEvents.stream()
                        .map(ClickEvent::getClickedAt)
                        .max(OffsetDateTime::compareTo)
                        .orElse(null);

        return new AnalyticsData(
                shortUrl.getId(),
                shortUrl.getShortCode(),
                shortUrl.getClickCount(),
                uniqueVisitors,
                lastClickedAt
        );
    }

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
