# URL Shortener — Security Review

## Executive Summary

This document analyzes the current URL Shortener implementation against 15 security attack vectors. The review inspects actual source code without modifications. All findings are categorized by severity, affected component, attack scenario, current behavior, and mitigation.

**Overall Posture:** The application implements several security controls appropriately for an MVP (scheme whitelist, IP hashing, exception handling). However, there are opportunities for hardening, particularly around oversized inputs and enumeration attacks.

---

## 1. SSRF (Server-Side Request Forgery)

**Severity:** LOW (MVP acceptable)  
**Affected Component:** UrlValidator, UrlShortenerService  
**Status:** NOT VULNERABLE (mitigated by design)

### Attack Scenario
Attacker creates a short URL pointing to `http://localhost:8080/actuator/health` or internal IP addresses (e.g., `http://192.168.1.1/admin`), then accesses the shortener to trigger internal server requests.

### Current Behavior
- ✅ No server-side URL fetching in the implementation
- ✅ Shortener only stores and redirects; no HTTP client calls the original_url
- ✅ Client browser makes the redirect request, not the server
- The redirect is HTTP 302 to Location header; browser executes the redirect

### Vulnerability Status
**NOT VULNERABLE.** The application does not fetch URLs server-side. Risk is purely client-side (user browser visits URL), which is expected redirect behavior.

### Recommended Mitigation
None required for MVP. Document this design decision in architecture.

---

## 2. URL Scheme Attacks (javascript:, data:, file:)

**Severity:** MEDIUM  
**Affected Component:** UrlValidator  
**Status:** MITIGATED (whitelist implemented)

### Attack Scenario
Attacker creates a short URL with `javascript:alert('XSS')` or `data:text/html,<script>alert('XSS')</script>`, hoping to execute arbitrary JavaScript in the browser context.

### Current Behavior
- ✅ UrlValidator.isValidUrl() enforces HTTP/HTTPS scheme whitelist
- ✅ Validation occurs at URL creation time (POST /api/v1/urls)
- ✅ Rejects: javascript:, data:, file:, ftp:, etc.

### Vulnerability Status
**MITIGATED.** The scheme whitelist prevents javascript:, data:, file: attacks.

---

## 3. Open Redirect (Client Context)

**Severity:** MEDIUM  
**Affected Component:** UrlController.redirect()  
**Status:** ACCEPTED RISK (expected behavior)

### Attack Scenario
Attacker creates a short URL pointing to `http://attacker.com/phishing`, then sends the shortened URL in a phishing email. This is expected behavior for a URL shortener, not a vulnerability.

### Current Behavior
- ✅ No server-side validation prevents redirecting to external URLs
- ✅ This is expected behavior for a URL shortener
- ⚠️ No user warning or interstitial page before external redirect

### Vulnerability Status
**NOT A VULNERABILITY.** This is the intended design of a URL shortener. Risk is social (phishing), not technical.

---

## 4. XSS (Cross-Site Scripting)

**Severity:** LOW  
**Affected Component:** UrlController, Response DTOs  
**Status:** NOT VULNERABLE (stateless API)

### Attack Scenario
Attacker injects `<script>alert('XSS')</script>` into the original_url field, hoping it renders in responses.

### Current Behavior
- ✅ API is stateless JSON (no HTML response rendering)
- ✅ No HTML templates that render user input
- ✅ Spring Boot default: Content-Type: application/json

### Vulnerability Status
**NOT VULNERABLE.** No HTML rendering in responses; XSS vectors do not apply to JSON APIs.

---

## 5. SQL Injection

**Severity:** LOW  
**Affected Component:** Repository, Service  
**Status:** NOT VULNERABLE (parameterized queries via JPA)

### Attack Scenario
Attacker sends shortCode parameter with SQL injection: `GET /abc' OR '1'='1`

### Current Behavior
- ✅ All database queries use JPA/Hibernate ORM (parameterized queries)
- ✅ No raw SQL strings concatenating user input
- ✅ Spring Data JPA auto-generates safe queries

### Vulnerability Status
**NOT VULNERABLE.** JPA/Hibernate automatically parameterizes queries.

---

## 6. Input Validation Gaps

**Severity:** MEDIUM  
**Affected Component:** UrlValidator, UrlShortenerService  
**Status:** PARTIAL MITIGATION

### Attack Scenario: Oversized Input
Attacker sends a URL that is 10MB long, causing memory exhaustion or storage overflow.

### Current Behavior
- ✅ UrlValidator enforces max URL length: 2048 characters
- ✅ Bean Validation: @NotBlank on originalUrl field
- ⚠️ No application-level request size limit explicitly configured in application.yml
- ℹ️ Spring Boot default max request size is 1MB

### Vulnerability Status
**LOW RISK for MVP.** URL validation prevents 2048+ char URLs; Spring default 1MB request limit provides defense-in-depth.

---

## 7. IP Address Privacy

**Severity:** LOW  
**Affected Component:** ClientIpExtractor, IpHasher, ClickEvent  
**Status:** MITIGATED (hashing implemented)

### Attack Scenario
Application stores raw IP addresses in click_event table, allowing tracking and privacy violations.

### Current Behavior
- ✅ ClickEvent stores only ipHash (SHA-256), not raw IP
- ✅ IpHasher.hash() produces deterministic SHA-256 hash
- ✅ Database schema confirms: ipHash VARCHAR(64), no ip_address column
- ⚠️ SHA-256 hashing is deterministic but not salted (precomputable)

### Vulnerability Status
**MITIGATED.** Raw IPs are never persisted; only hashes.

---

## 8. Sensitive Data in Logs

**Severity:** MEDIUM  
**Affected Component:** GlobalExceptionHandler, application.yml  
**Status:** PARTIALLY MITIGATED

### Attack Scenario
Application logs contain sensitive data (original URLs, stack traces) exposed in log files or monitoring systems.

### Current Behavior
- ✅ GlobalExceptionHandler does NOT expose details to client (generic "An error occurred")
- ✅ HTTP 500 response returns generic message (no stack trace)
- ⚠️ Full exception stack trace logged to application logs
- ⚠️ application.yml enables SQL binding logging at TRACE level (logs SQL parameters)

### Vulnerability Status
**MEDIUM RISK.** Application correctly hides errors from clients, but logs are verbose.

---

## 9. Exception Information Leakage to Client

**Severity:** LOW  
**Affected Component:** GlobalExceptionHandler  
**Status:** MITIGATED (generic messages)

### Attack Scenario
Attacker analyzes error responses to map application internals.

### Current Behavior
- ✅ All exceptions caught by GlobalExceptionHandler
- ✅ Client always receives generic error message
- ✅ No stack traces, exception class names, or SQL errors exposed
- ✅ HTTP status codes follow standard semantics

### Vulnerability Status
**NOT VULNERABLE.** Exception details properly hidden.

---

## 10. Predictable Short Codes (Enumeration & Brute Force)

**Severity:** MEDIUM  
**Affected Component:** ShortCodeGenerator  
**Status:** MITIGATED (cryptographic randomness)

### Attack Scenario
If short codes were sequential, attacker could enumerate all URLs. If predictable, attacker could guess valid codes.

### Current Behavior
- ✅ ShortCodeGenerator uses SecureRandom (cryptographically secure)
- ✅ Generates 8-character Base62 codes: 62^8 ≈ 218 trillion combinations
- ✅ No sequential ID logic

### Vulnerability Status
**NOT VULNERABLE.** Codes are cryptographically random and sufficiently long.

---

## 11. Short-Code Collision Handling & Denial of Service

**Severity:** LOW (operational concern)  
**Affected Component:** UrlShortenerService.create()  
**Status:** MITIGATED (retry logic)

### Attack Scenario
Attacker intentionally triggers collisions by creating millions of URLs, hoping to exhaust retries and cause service degradation.

### Current Behavior
- ✅ Collision detection: Database UNIQUE constraint on short_code
- ✅ DataIntegrityViolationException caught and retry logic triggered
- ✅ Max 5 retries before failing with HTTP 500
- ✅ Test coverage: UrlShortenerServiceTest includes collision retry tests

### Vulnerability Status
**LOW RISK.** Collision probability is negligible (~1 in trillions). Retry logic is reasonable.

---

## 12. URL Enumeration by ID Prediction

**Severity:** MEDIUM  
**Affected Component:** UrlController.getAnalytics()  
**Status:** PARTIALLY MITIGATED

### Attack Scenario
Attacker enumerates analytics by guessing URL IDs: `GET /api/v1/urls/1/analytics`, `GET /api/v1/urls/2/analytics`

### Current Behavior
- ✅ Database IDs are BIGSERIAL (64-bit, unlikely to guess by brute force)
- ⚠️ No authentication or authorization on analytics endpoint
- ⚠️ No rate limiting on analytics endpoint

### Vulnerability Status
**MEDIUM RISK.** If ID is leaked or discoverable, analytics are exposed. However, BIGSERIAL makes brute-force enumeration impractical.

---

## 13. Oversized Inputs (DoS)

**Severity:** MEDIUM  
**Affected Component:** UrlValidator, HTTP Request Handler  
**Status:** PARTIALLY MITIGATED

### Attack Scenario
Attacker sends extremely large inputs to exhaust server memory or bandwidth.

### Current Behavior
- ✅ URL length validated: max 2048 characters
- ✅ Spring Boot default max request size: 1MB
- ⚠️ ClickEvent.userAgent is VARCHAR(2000) (no explicit max in entity validation)
- ⚠️ No rate limiting implemented
- ⚠️ No connection limits configured

### Vulnerability Status
**MEDIUM RISK.** Application is vulnerable to large request sizes and connection exhaustion.

---

## 14. Redirect Endpoint Abuse (Click Flooding / DoS)

**Severity:** MEDIUM  
**Affected Component:** UrlController.redirect(), UrlShortenerService.recordClick()  
**Status:** NOT MITIGATED

### Attack Scenario
Attacker sends thousands of requests to `GET /{shortCode}`, causing:
- Database spam: click_count incremented excessively
- click_event table fills with bogus records
- Server CPU exhausted
- Analytics become meaningless

### Current Behavior
- ⚠️ No rate limiting on GET /{shortCode} endpoint
- ⚠️ Each request increments click_count and inserts ClickEvent row
- ⚠️ No captcha, authentication, or per-IP limits
- ⚠️ Database can accept unlimited ClickEvent inserts

### Vulnerability Status
**MEDIUM RISK.** Application is vulnerable to click flooding and DoS attacks.

---

## 15. Timezone & Timestamp Ambiguities

**Severity:** LOW  
**Affected Component:** OffsetDateTime, UrlShortenerService.getShortUrlByCode()  
**Status:** MITIGATED (OffsetDateTime)

### Attack Scenario
Attacker crafts requests around expiration times to exploit timezone ambiguities.

### Current Behavior
- ✅ Timestamps stored as OffsetDateTime (java.time API)
- ✅ Database type: TIMESTAMP WITH TIME ZONE (PostgreSQL)
- ✅ Offset is preserved in the database (not ambiguous LocalDateTime)
- ✅ Expiration check: expiresAt.isBefore(OffsetDateTime.now())

### Vulnerability Status
**NOT VULNERABLE.** OffsetDateTime and TIMESTAMP WITH TIME ZONE eliminate ambiguity.

---

## Summary Table

| # | Attack Vector | Severity | Status | MVP Decision |
|---|---|---|---|---|
| 1 | SSRF | LOW | NOT VULNERABLE | Document design |
| 2 | URL Schemes | MEDIUM | MITIGATED | Whitelist sufficient |
| 3 | Open Redirect | MEDIUM | ACCEPTED RISK | Expected behavior |
| 4 | XSS | LOW | NOT VULNERABLE | JSON API only |
| 5 | SQL Injection | LOW | NOT VULNERABLE | Continue using JPA |
| 6 | Input Validation | MEDIUM | PARTIAL | URL length enforced |
| 7 | IP Privacy | LOW | MITIGATED | Hashing implemented |
| 8 | Sensitive Logs | MEDIUM | PARTIAL | Hide from clients |
| 9 | Exception Leakage | LOW | MITIGATED | Generic messages |
| 10 | Predictable Codes | MEDIUM | NOT VULNERABLE | SecureRandom sufficient |
| 11 | Collision DoS | LOW | MITIGATED | Retry logic adequate |
| 12 | ID Enumeration | MEDIUM | PARTIAL | BIGSERIAL impractical |
| 13 | Oversized Inputs | MEDIUM | PARTIAL | Spring defaults help |
| 14 | Redirect Abuse | MEDIUM | NOT MITIGATED | No rate limiting |
| 15 | Timezone | LOW | NOT VULNERABLE | OffsetDateTime correct |

---

## Risk Prioritization for Engineer Review

### Immediate (MVP - No Breaking Changes Recommended)
1. **#8 Sensitive Logs** (MEDIUM) — Document warning about log file security.
2. **#13 Oversized Inputs** (MEDIUM) — Add explicit configuration.
3. **#14 Redirect Abuse** (MEDIUM) — Document as known limitation.

### Deferred to Future
- #3 Open Redirect (confirmation page, opt-in)
- #12 ID Enumeration (authentication/authorization)
- #7 IP Privacy (salting for hashes)

### No Action Required
- #1, #2, #4, #5, #9, #10, #15 (already properly mitigated)

---

## Conclusion

The URL Shortener demonstrates solid security fundamentals for an MVP:
- ✅ Proper scheme validation
- ✅ No SSRF vulnerability
- ✅ Cryptographically secure short codes
- ✅ IP privacy (hashing)
- ✅ Exception handling
- ✅ Parameterized queries

Main areas for future hardening:
- Rate limiting (redirect abuse)
- Authentication/authorization (analytics)
- Explicit input size configuration
- Log security documentation

**No critical vulnerabilities prevent this application from functioning as a secure MVP.**
