package com.vishwasena.urlshortener.repository;

import com.vishwasena.urlshortener.entity.ClickEvent;
import com.vishwasena.urlshortener.entity.ShortUrl;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("test")
class ShortUrlRepositoryTest {

    @Autowired
    private ShortUrlRepository shortUrlRepository;

    @Autowired
    private ClickEventRepository clickEventRepository;

    @Test
    void testSaveAndFindByShortCode() {
        ShortUrl shortUrl = new ShortUrl("test123", "https://example.com", "ACTIVE");
        ShortUrl saved = shortUrlRepository.save(shortUrl);

        assertNotNull(saved.getId());
        Optional<ShortUrl> found = shortUrlRepository.findByShortCode("test123");
        assertTrue(found.isPresent());
        assertEquals("https://example.com", found.get().getOriginalUrl());
    }

    @Test
    void testShortCodeUniqueness() {
        ShortUrl url1 = new ShortUrl("unique1", "https://example.com", "ACTIVE");
        shortUrlRepository.save(url1);

        ShortUrl url2 = new ShortUrl("unique1", "https://different.com", "ACTIVE");
        assertThrows(Exception.class, () -> {
            shortUrlRepository.saveAndFlush(url2);
        });
    }

    @Test
    void testFindByShortCodeNotFound() {
        Optional<ShortUrl> found = shortUrlRepository.findByShortCode("nonexistent");
        assertFalse(found.isPresent());
    }

    @Test
    void testClickEventCountUniqueVisitors() {
        ShortUrl shortUrl = new ShortUrl("analytics1", "https://example.com", "ACTIVE");
        ShortUrl saved = shortUrlRepository.save(shortUrl);

        // Record clicks from same IP twice (should count as 1 unique visitor)
        String ipHash = "hash123";
        clickEventRepository.save(new ClickEvent(saved, ipHash, "Mozilla", null));
        clickEventRepository.save(new ClickEvent(saved, ipHash, "Chrome", null));

        // Record click from different IP
        clickEventRepository.save(new ClickEvent(saved, "hash456", "Safari", null));

        long uniqueCount = clickEventRepository.countUniqueVisitors(saved.getId());
        assertEquals(2, uniqueCount);
    }

    @Test
    void testClickEventPersistence() {
        ShortUrl shortUrl = new ShortUrl("clicks1", "https://example.com", "ACTIVE");
        ShortUrl saved = shortUrlRepository.save(shortUrl);

        ClickEvent event = new ClickEvent(saved, "hash789", "Mozilla/5.0", "https://referrer.com");
        ClickEvent clickSaved = clickEventRepository.save(event);

        assertNotNull(clickSaved.getId());
        assertEquals("hash789", clickSaved.getIpHash());
        assertEquals("Mozilla/5.0", clickSaved.getUserAgent());
    }
}
