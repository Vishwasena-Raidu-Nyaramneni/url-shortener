package com.vishwasena.urlshortener.service;

import com.vishwasena.urlshortener.entity.ShortUrl;
import com.vishwasena.urlshortener.exception.DisabledUrlException;
import com.vishwasena.urlshortener.exception.ExpiredUrlException;
import com.vishwasena.urlshortener.exception.UrlNotFoundException;
import com.vishwasena.urlshortener.repository.ClickEventRepository;
import com.vishwasena.urlshortener.repository.ShortUrlRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.OffsetDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UrlShortenerServiceTest {

    @Mock
    private ShortUrlRepository shortUrlRepository;

    @Mock
    private ClickEventRepository clickEventRepository;

    @InjectMocks
    private UrlShortenerService service;

    @Test
    void testCreateShortUrlValid() {
        when(shortUrlRepository.save(any(ShortUrl.class))).thenAnswer(invocation -> {
            ShortUrl url = invocation.getArgument(0);
            url.setId(1L);
            return url;
        });

        ShortUrl result = service.createShortUrl("https://example.com", null);

        assertNotNull(result);
        assertNotNull(result.getShortCode());
        assertEquals(8, result.getShortCode().length());
        assertEquals("https://example.com", result.getOriginalUrl());
        assertEquals("ACTIVE", result.getStatus());
    }

    @Test
    void testCreateShortUrlInvalid() {
        assertThrows(IllegalArgumentException.class, () -> {
            service.createShortUrl("javascript:alert('xss')", null);
        });
    }

    @Test
    void testGetShortUrlByCodeNotFound() {
        when(shortUrlRepository.findByShortCode("nonexistent")).thenReturn(Optional.empty());

        assertThrows(UrlNotFoundException.class, () -> {
            service.getShortUrlByCode("nonexistent");
        });
    }

    @Test
    void testGetShortUrlByCodeExpired() {
        ShortUrl expired = new ShortUrl("expired1", "https://example.com", "ACTIVE");
        expired.setExpiresAt(OffsetDateTime.now().minusHours(1));

        when(shortUrlRepository.findByShortCode("expired1")).thenReturn(Optional.of(expired));

        assertThrows(ExpiredUrlException.class, () -> {
            service.getShortUrlByCode("expired1");
        });
    }

    @Test
    void testGetShortUrlByCodeDisabled() {
        ShortUrl disabled = new ShortUrl("disabled1", "https://example.com", "DISABLED");

        when(shortUrlRepository.findByShortCode("disabled1")).thenReturn(Optional.of(disabled));

        assertThrows(DisabledUrlException.class, () -> {
            service.getShortUrlByCode("disabled1");
        });
    }

    @Test
    void testGetShortUrlByCodeValid() {
        ShortUrl active = new ShortUrl("active1", "https://example.com", "ACTIVE");
        when(shortUrlRepository.findByShortCode("active1")).thenReturn(Optional.of(active));

        ShortUrl result = service.getShortUrlByCode("active1");
        assertEquals("active1", result.getShortCode());
    }

    @Test
    void testDisableShortUrl() {
        ShortUrl url = new ShortUrl("disable1", "https://example.com", "ACTIVE");
        url.setId(1L);

        when(shortUrlRepository.findById(1L)).thenReturn(Optional.of(url));
        when(shortUrlRepository.save(any(ShortUrl.class))).thenReturn(url);

        service.disableShortUrl(1L);
        assertEquals("DISABLED", url.getStatus());
    }

    @Test
    void testCreateShortUrlCollisionRetrySuccess() {
        when(shortUrlRepository.save(any(ShortUrl.class)))
                .thenThrow(new DataIntegrityViolationException("Duplicate key", new RuntimeException()))
                .thenThrow(new DataIntegrityViolationException("Duplicate key", new RuntimeException()))
                .thenAnswer(invocation -> {
                    ShortUrl url = invocation.getArgument(0);
                    url.setId(1L);
                    return url;
                });

        ShortUrl result = service.createShortUrl("https://example.com", null);

        assertNotNull(result);
        assertEquals("https://example.com", result.getOriginalUrl());
        assertEquals("ACTIVE", result.getStatus());
    }

    @Test
    void testCreateShortUrlCollisionFailureAllRetries() {
        when(shortUrlRepository.save(any(ShortUrl.class)))
                .thenThrow(new DataIntegrityViolationException("Duplicate key", new RuntimeException()));

        assertThrows(RuntimeException.class, () -> {
            service.createShortUrl("https://example.com", null);
        });
    }
}
