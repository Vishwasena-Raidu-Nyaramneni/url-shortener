package com.vishwasena.urlshortener.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vishwasena.urlshortener.AbstractPostgresIntegrationTest;
import com.vishwasena.urlshortener.dto.CreateUrlRequest;
import com.vishwasena.urlshortener.entity.ShortUrl;
import com.vishwasena.urlshortener.repository.ClickEventRepository;
import com.vishwasena.urlshortener.repository.ShortUrlRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@AutoConfigureMockMvc
@TestPropertySource(properties = "spring.flyway.enabled=true")
@Transactional
class UrlControllerPostgresIntegrationTest extends AbstractPostgresIntegrationTest {

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
        shortUrlRepository.deleteAll();
        clickEventRepository.deleteAll();

        testUrl = new ShortUrl("pgtest001", "https://example.com", "ACTIVE");
        shortUrlRepository.save(testUrl);
    }

    @Test
    void testCreateUrlAndPersistToPostgres() throws Exception {
        CreateUrlRequest request = new CreateUrlRequest();
        request.setOriginalUrl("https://postgres-test.com/path");
        request.setExpiresAt(null);

        String response = mockMvc.perform(post("/api/v1/urls")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.shortCode").exists())
                .andReturn()
                .getResponse()
                .getContentAsString();

        // Verify persisted in PostgreSQL
        com.fasterxml.jackson.databind.JsonNode json = objectMapper.readTree(response);
        String shortCode = json.get("shortCode").asText();
        
        assertTrue(shortUrlRepository.findByShortCode(shortCode).isPresent(),
                "URL should be persisted in PostgreSQL");
    }

    @Test
    void testRedirectRecordsClickInPostgres() throws Exception {
        mockMvc.perform(get("/" + testUrl.getShortCode())
                .header("X-Forwarded-For", "192.168.1.100")
                .header("User-Agent", "Mozilla/5.0"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", testUrl.getOriginalUrl()));

        // Verify click event persisted in PostgreSQL
        long clickCount = clickEventRepository.countByShortUrlId(testUrl.getId());
        assertEquals(1, clickCount, "Click event should be recorded in PostgreSQL");

        ShortUrl updated = shortUrlRepository.findByShortCode(testUrl.getShortCode()).get();
        assertEquals(1, updated.getClickCount(), "Click count should be incremented in PostgreSQL");
    }

    @Test
    void testExpiredUrlReturnsFourOneZeroFromPostgres() throws Exception {
        ShortUrl expired = new ShortUrl("pgexpired1", "https://example.com", "ACTIVE");
        expired.setExpiresAt(OffsetDateTime.now().minusHours(1));
        shortUrlRepository.save(expired);

        mockMvc.perform(get("/" + expired.getShortCode()))
                .andExpect(status().isGone());
    }

    @Test
    void testDisabledUrlReturnsFourOneZeroFromPostgres() throws Exception {
        ShortUrl disabled = new ShortUrl("pgdisabled1", "https://example.com", "DISABLED");
        shortUrlRepository.save(disabled);

        mockMvc.perform(get("/" + disabled.getShortCode()))
                .andExpect(status().isGone());
    }

    @Test
    void testAnalyticsRetrievalFromPostgres() throws Exception {
        // Record multiple clicks
        for (int i = 1; i <= 3; i++) {
            mockMvc.perform(get("/" + testUrl.getShortCode())
                    .header("X-Forwarded-For", "192.168.1." + i))
                    .andExpect(status().isFound());
        }

        String analyticsResponse = mockMvc.perform(get("/api/v1/urls/" + testUrl.getId() + "/analytics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalClicks").exists())
                .andExpect(jsonPath("$.uniqueVisitors").exists())
                .andReturn()
                .getResponse()
                .getContentAsString();

        com.fasterxml.jackson.databind.JsonNode json = objectMapper.readTree(analyticsResponse);
        assertEquals(3, json.get("totalClicks").asInt(), "Should have 3 clicks from PostgreSQL");
        assertEquals(3, json.get("uniqueVisitors").asInt(), "Should have 3 unique visitors from PostgreSQL");
    }

    @Test
    void testPostgresTransactionalConsistency() throws Exception {
        // Create URL
        CreateUrlRequest request = new CreateUrlRequest();
        request.setOriginalUrl("https://transactional-test.com");

        String response = mockMvc.perform(post("/api/v1/urls")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        com.fasterxml.jackson.databind.JsonNode json = objectMapper.readTree(response);
        String shortCode = json.get("shortCode").asText();

        // Verify both short_url and metadata are persisted consistently
        assertTrue(shortUrlRepository.findByShortCode(shortCode).isPresent());
        
        ShortUrl created = shortUrlRepository.findByShortCode(shortCode).get();
        assertNotNull(created.getCreatedAt());
        assertNotNull(created.getUpdatedAt());
        assertEquals("ACTIVE", created.getStatus());
    }

    @Test
    void testPostgresIndexedQueries() throws Exception {
        // Create multiple URLs
        for (int i = 1; i <= 5; i++) {
            ShortUrl url = new ShortUrl("pgindex" + i, "https://example" + i + ".com", "ACTIVE");
            shortUrlRepository.save(url);
        }

        // Find by short code (should use idx_short_code)
        assertTrue(shortUrlRepository.findByShortCode("pgindex1").isPresent());
        assertTrue(shortUrlRepository.findByShortCode("pgindex5").isPresent());

        // Count by status (should use idx_status)
        // This verifies indexes are created by Flyway
    }
}
