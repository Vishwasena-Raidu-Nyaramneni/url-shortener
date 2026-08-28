package com.vishwasena.urlshortener.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class UrlValidatorTest {

    @Test
    void testValidHttpUrl() {
        assertTrue(UrlValidator.isValidUrl("http://example.com"));
    }

    @Test
    void testValidHttpUrlWithPath() {
        assertTrue(UrlValidator.isValidUrl("http://example.com/path/to/resource"));
    }

    @Test
    void testValidHttpsUrl() {
        assertTrue(UrlValidator.isValidUrl("https://example.com/path"));
    }

    @Test
    void testValidUrlWithQuery() {
        assertTrue(UrlValidator.isValidUrl("https://example.com/path?query=value"));
    }

    @Test
    void testValidUrlWithFragment() {
        assertTrue(UrlValidator.isValidUrl("https://example.com/path#section"));
    }

    @Test
    void testNullUrl() {
        assertFalse(UrlValidator.isValidUrl(null));
    }

    @Test
    void testBlankUrl() {
        assertFalse(UrlValidator.isValidUrl("   "));
    }

    @Test
    void testJavascriptUrl() {
        assertFalse(UrlValidator.isValidUrl("javascript:alert('xss')"));
    }

    @Test
    void testDataUrl() {
        assertFalse(UrlValidator.isValidUrl("data:text/html,<h1>test</h1>"));
    }

    @Test
    void testFileUrl() {
        assertFalse(UrlValidator.isValidUrl("file:///etc/passwd"));
    }

    @Test
    void testFtpUrl() {
        assertFalse(UrlValidator.isValidUrl("ftp://ftp.example.com"));
    }

    @Test
    void testUrlTooLong() {
        StringBuilder sb = new StringBuilder("https://example.com/");
        for (int i = 0; i < 3000; i++) {
            sb.append("a");
        }
        assertFalse(UrlValidator.isValidUrl(sb.toString()));
    }

    @Test
    void testMalformedUrl() {
        assertFalse(UrlValidator.isValidUrl("not a url"));
    }

    @Test
    void testSanitizeValidUrl() {
        String url = "  https://example.com  ";
        String sanitized = UrlValidator.sanitizeUrl(url);
        assertEquals("https://example.com", sanitized);
    }

    @Test
    void testSanitizeInvalidUrl() {
        assertThrows(IllegalArgumentException.class, () -> {
            UrlValidator.sanitizeUrl("javascript:alert('xss')");
        });
    }
}
