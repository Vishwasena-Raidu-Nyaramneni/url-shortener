package com.vishwasena.urlshortener.util;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;

public class UrlValidator {
    private static final int MAX_URL_LENGTH = 2048;

    private UrlValidator() {
        // Utility class
    }

    public static boolean isValidUrl(String urlString) {
        if (urlString == null || urlString.isBlank()) {
            return false;
        }

        if (urlString.length() > MAX_URL_LENGTH) {
            return false;
        }

        try {
            URL url = new URL(urlString);
            String scheme = url.getProtocol();

            // Only allow HTTP and HTTPS
            if (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https")) {
                return false;
            }

            // Check for embedded credentials (user:password@host)
            String userInfo = url.getUserInfo();
            if (userInfo != null && !userInfo.isEmpty()) {
                return false;
            }

            // Check for path traversal attempts
            String path = url.getPath();
            if (path != null && path.contains("..")) {
                return false;
            }

            // Parse as URI to ensure valid format
            new URI(urlString);
            return true;
        } catch (MalformedURLException | URISyntaxException e) {
            return false;
        }
    }

    public static String sanitizeUrl(String urlString) {
        if (urlString == null) {
            throw new IllegalArgumentException("URL cannot be null");
        }
        
        urlString = urlString.trim();
        
        if (!isValidUrl(urlString)) {
            throw new IllegalArgumentException("Invalid URL: " + urlString);
        }
        return urlString;
    }
}
