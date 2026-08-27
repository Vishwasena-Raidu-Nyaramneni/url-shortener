package com.vishwasena.urlshortener.util;

import jakarta.servlet.http.HttpServletRequest;

public class ClientIpExtractor {

    private ClientIpExtractor() {
        // Utility class
    }

    public static String extractClientIp(HttpServletRequest request) {
        // Try X-Forwarded-For first (proxy/load balancer)
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            // X-Forwarded-For can contain multiple IPs, take the first one
            return xForwardedFor.split(",")[0].trim();
        }

        // Try X-Real-IP (nginx proxy)
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isBlank()) {
            return xRealIp.trim();
        }

        // Fallback to remote address
        return request.getRemoteAddr();
    }
}
