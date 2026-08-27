package com.vishwasena.urlshortener.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vishwasena.urlshortener.dto.CreateUrlRequest;
import com.vishwasena.urlshortener.entity.ClickEvent;
import com.vishwasena.urlshortener.entity.ShortUrl;
import com.vishwasena.urlshortener.repository.ClickEventRepository;
import com.vishwasena.urlshortener.repository.ShortUrlRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class UrlControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ShortUrlRepository shortUrlRepository;

    @Autowired
    private ClickEventRepository clickEventRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private ShortUrl testUrl;

    @BeforeEach
    void setUp() {
        clickEventRepository.deleteAll();
        shortUrlRepository.deleteAll();

        testUrl = new ShortUrl("test123", "https://example.com", "ACTIVE");
        testUrl = shortUrlRepository.save(testUrl);
    }

    @Test
    void testCreateShortUrlSuccess() throws Exception {
        CreateUrlRequest request = new CreateUrlRequest("https://github.com", null);

        mockMvc.perform(post("/api/v1/urls")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.short_code").isNotEmpty())
                .andExpect(jsonPath("$.original_url").value("https://github.com"))
                .andExpect(jsonPath("$.short_url").value(containsString("http://localhost:8080/")));
    }

    @Test
    void testCreateShortUrlInvalid() throws Exception {
        CreateUrlRequest request = new CreateUrlRequest("javascript:alert('xss')", null);

        mockMvc.perform(post("/api/v1/urls")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void testCreateShortUrlMissingUrl() throws Exception {
        CreateUrlRequest request = new CreateUrlRequest(null, null);

        mockMvc.perform(post("/api/v1/urls")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testRedirectSuccess() throws Exception {
        mockMvc.perform(get("/test123")
                .header("User-Agent", "Mozilla/5.0")
                .header("Referer", "https://referrer.com")
                .header("X-Forwarded-For", "192.168.1.1"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("https://example.com"));

        // Verify click was recorded
        int clickCount = clickEventRepository.findByShortUrlId(testUrl.getId()).size();
        assertTrue(clickCount > 0);

        // Verify click count was incremented
        ShortUrl updated = shortUrlRepository.findById(testUrl.getId()).get();
        assertEquals(1L, updated.getClickCount().longValue());
    }

    @Test
    void testRedirectExpiredUrl() throws Exception {
        ShortUrl expired = new ShortUrl("expired1", "https://example.com", "ACTIVE");
        expired.setExpiresAt(OffsetDateTime.now().minusHours(1));
        shortUrlRepository.save(expired);

        mockMvc.perform(get("/expired1"))
                .andExpect(status().isGone());
    }

    @Test
    void testRedirectDisabledUrl() throws Exception {
        ShortUrl disabled = new ShortUrl("disabled1", "https://example.com", "DISABLED");
        shortUrlRepository.save(disabled);

        mockMvc.perform(get("/disabled1"))
                .andExpect(status().isGone());
    }

    @Test
    void testRedirectNotFound() throws Exception {
        mockMvc.perform(get("/nonexistent"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void testGetShortUrlById() throws Exception {
        mockMvc.perform(get("/api/v1/urls/" + testUrl.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.short_code").value("test123"))
                .andExpect(jsonPath("$.original_url").value("https://example.com"));
    }

    @Test
    void testGetShortUrlByIdNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/urls/99999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void testDisableShortUrl() throws Exception {
        mockMvc.perform(delete("/api/v1/urls/" + testUrl.getId()))
                .andExpect(status().isNoContent());

        ShortUrl updated = shortUrlRepository.findById(testUrl.getId()).get();
        assertEquals("DISABLED", updated.getStatus());
    }

    @Test
    void testDisableNonexistentUrl() throws Exception {
        mockMvc.perform(delete("/api/v1/urls/99999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void testGetAnalytics() throws Exception {
        // Record some clicks
        ClickEvent event1 = new ClickEvent(testUrl, "hash1", "Mozilla", null);
        ClickEvent event2 = new ClickEvent(testUrl, "hash1", "Chrome", null);
        ClickEvent event3 = new ClickEvent(testUrl, "hash2", "Safari", null);
        clickEventRepository.save(event1);
        clickEventRepository.save(event2);
        clickEventRepository.save(event3);

        // Refresh click count
        testUrl.setClickCount(3L);
        shortUrlRepository.save(testUrl);

        mockMvc.perform(get("/api/v1/urls/" + testUrl.getId() + "/analytics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.short_code").value("test123"))
                .andExpect(jsonPath("$.total_clicks").value(3))
                .andExpect(jsonPath("$.unique_visitors").value(2))
                .andExpect(jsonPath("$.last_clicked_at").isNotEmpty());
    }

    @Test
    void testGetAnalyticsNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/urls/99999/analytics"))
                .andExpect(status().isNotFound());
    }

    @Test
    void testMultipleRedirectsIncrementClickCount() throws Exception {
        mockMvc.perform(get("/test123")
                .header("X-Forwarded-For", "192.168.1.1"))
                .andExpect(status().isFound());

        mockMvc.perform(get("/test123")
                .header("X-Forwarded-For", "192.168.1.2"))
                .andExpect(status().isFound());

        mockMvc.perform(get("/test123")
                .header("X-Forwarded-For", "192.168.1.1"))
                .andExpect(status().isFound());

        ShortUrl updated = shortUrlRepository.findById(testUrl.getId()).get();
        assertEquals(3L, updated.getClickCount().longValue());
    }
}
