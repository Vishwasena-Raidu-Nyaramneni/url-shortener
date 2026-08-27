# URL Shortener — Requirements

## 1. Problem Statement

This service provides a URL shortening API for converting long URLs into compact, memorable short codes. Users can create shortened URLs that redirect to their original destinations, track usage through click analytics, and manage URL lifecycle through expiration and disabling.

## 2. Goals

- Provide a simple REST API for creating and managing shortened URLs
- Generate unique, collision-free short codes using Base62 encoding
- Track click analytics per shortened URL
- Support URL expiration and manual disabling
- Deliver fast, reliable redirects with analytics collection
- Demonstrate production-oriented design principles (validation, error handling, logging, transactions)

## 3. Functional Requirements

### 3.1 Create Short URL

**Requirement:** Users can create a shortened URL by providing an original URL and optional expiration time.

**Implemented Behavior:**
- Accept HTTP/HTTPS URLs only (max 2048 characters)
- Validate URL format using Java java.net.URL parsing
- Reject invalid schemes (data:, javascript:, file:, ftp:, etc.)
- Generate a unique 8-character Base62 short code using SecureRandom
- Handle short-code collisions by retrying up to 5 times (max 5 attempts)
- If all 5 retries fail, throw RuntimeException with message "Failed to generate unique short code after 5 attempts"
- Store shortened URL in database with status = "ACTIVE"
- Return created short URL with ID, short code, original URL, and full short URL (baseUrl + "/" + shortCode)

**Not Implemented / Future Enhancement:**
- Analytics on URL creation (e.g., which users create URLs)
- Custom short codes (user-specified)
- QR code generation

### 3.2 Redirect

**Requirement:** Users can access a shortened URL via short code and be redirected to the original URL.

**Implemented Behavior:**
- Accept HTTP GET requests to /{shortCode}
- Look up short code in database
- Return HTTP 302 (Found) redirect to original URL
- Record click event BEFORE returning redirect response
- Extract client IP from request headers (supports X-Forwarded-For, X-Real-IP, then request.getRemoteAddr())
- Extract User-Agent and Referer headers for click event
- For unknown short code, throw UrlNotFoundException, resulting in HTTP 404
- For expired URL, throw ExpiredUrlException, resulting in HTTP 410 (Gone)
- For disabled URL, throw DisabledUrlException, resulting in HTTP 410 (Gone)

**Design Decision:** Redirect returns HTTP 302 (temporary) rather than 301 (permanent), allowing the service to update URL mappings without browser cache issues.

**Design Decision:** Click recording happens in the same request as the redirect (synchronous), not asynchronously. This ensures accurate click counts but may impact redirect latency at scale.

### 3.3 URL Expiration

**Requirement:** URLs can optionally expire at a specified time.

**Implemented Behavior:**
- Accept optional expiresAt timestamp (ISO 8601 OffsetDateTime) in URL creation request
- Store expires_at column in short_url table (nullable)
- On redirect, check if expiresAt is before current time using OffsetDateTime.now()
- If expired, throw ExpiredUrlException with message "URL expired: {shortCode}"
- Return HTTP 410 (Gone) for expired URLs

**Not Implemented / Future Enhancement:**
- Background job to hard-delete or soft-delete expired URLs
- Validation to ensure expiresAt is in the future at creation time
- Pagination/filtering of analytics by date range

### 3.4 URL Disable

**Requirement:** Users can manually disable a shortened URL to prevent further redirects.

**Implemented Behavior:**
- Accept HTTP DELETE request to /api/v1/urls/{id}
- Look up URL by ID in database
- Set status = "DISABLED"
- Persist to database
- Return HTTP 204 (No Content)
- On subsequent redirect attempt to disabled URL, throw DisabledUrlException with message "URL disabled: {shortCode}"
- Return HTTP 410 (Gone) for disabled URLs

**Design Decision:** Use soft-delete (status = "DISABLED") rather than hard-delete to preserve audit trail and analytics history.

**Not Implemented / Future Enhancement:**
- Re-enable a disabled URL
- Audit trail of who disabled a URL and when
- Permission checks (anyone can disable any URL currently)

### 3.5 Click Analytics Collection

**Requirement:** The service records click events for analytics purposes.

**Implemented Behavior:**
- On each redirect request, create ClickEvent record with:
  - Foreign key reference to short_url_id
  - IP hash (SHA-256 one-way hash of client IP address)
  - User-Agent header value
  - Referer header value
  - Timestamp of click (OffsetDateTime, auto-set to current time)
- Increment click_count field in short_url table
- Store both in a single database transaction
- IP hashing prevents storage of raw IP addresses for privacy

**Design Decision:** IP addresses are hashed using SHA-256, making the hash deterministic (same IP always produces same hash) but irreversible. This enables unique visitor counting without storing raw IPs.

**Not Implemented / Future Enhancement:**
- Asynchronous click event recording (currently synchronous)
- Click event cleanup/retention policy
- Per-visitor tracking (cookies or user accounts)
- Geolocation from IP hash
- Bot/crawler detection

### 3.6 Analytics Retrieval

**Requirement:** Users can retrieve analytics for a shortened URL.

**Implemented Behavior:**
- Accept HTTP GET request to /api/v1/urls/{id}/analytics
- Look up URL by ID
- Return JSON response with:
  - short_url_id: Long ID
  - short_code: String short code
  - 	otal_clicks: Long (from short_url.click_count field)
  - unique_visitors: Long (COUNT DISTINCT of ip_hash from click_event table)
  - last_clicked_at: OffsetDateTime (MAX of clicked_at from click_event records, or null if no clicks)
- Return HTTP 200 (OK)

**Known Issue (Efficiency):** last_clicked_at calculation loads ALL ClickEvent objects into memory and streams to find max, rather than using SQL aggregate. This is inefficient for URLs with high click volumes. Acceptable for MVP but should be optimized with native query in production.

**Not Implemented / Future Enhancement:**
- Pagination of click events
- Filtering by date range
- Hourly/daily aggregation
- Click source attribution (referer analytics)
- User agent analytics

### 3.7 Health Monitoring

**Requirement:** The service exposes health endpoint for operational monitoring.

**Implemented Behavior:**
- Spring Boot Actuator enabled with /actuator/health endpoint
- Returns HTTP 200 (OK) with basic health status
- Health details shown only when authorized (configured in application.yml)
- Includes database connectivity status

**Not Implemented / Future Enhancement:**
- Custom health indicators (cache status, external service status)
- Detailed metrics (request counts, latency histograms)
- Prometheus metrics

## 4. API Requirements

### 4.1 POST /api/v1/urls

**Purpose:** Create a shortened URL

**Request:**
`json
{
  "original_url": "https://example.com/very/long/path",
  "expires_at": "2025-12-31T23:59:59+00:00"
}
`
- original_url: Required, must be valid HTTP/HTTPS URL, max 2048 characters
- expires_at: Optional, ISO 8601 OffsetDateTime

**Response (HTTP 201 Created):**
`json
{
  "id": 1,
  "short_code": "aBcD1234",
  "original_url": "https://example.com/very/long/path",
  "short_url": "http://localhost:8080/aBcD1234"
}
`

**Error Responses:**
- HTTP 400 (Bad Request): Invalid URL, unsupported scheme, URL too long, validation error
- HTTP 500 (Internal Server Error): Failed to generate unique short code after 5 attempts, database error

### 4.2 GET /{shortCode}

**Purpose:** Redirect to original URL and record click event

**Request:** HTTP GET, no body

**Response (HTTP 302 Found):**
- Location header: original URL
- Body: Empty (standard redirect)

**Error Responses:**
- HTTP 404 (Not Found): Unknown short code
- HTTP 410 (Gone): URL is expired or disabled
- HTTP 500 (Internal Server Error): Unexpected error

### 4.3 GET /api/v1/urls/{id}

**Purpose:** Retrieve metadata for a shortened URL

**Request:** HTTP GET, no body

**Response (HTTP 200 OK):**
`json
{
  "id": 1,
  "short_code": "aBcD1234",
  "original_url": "https://example.com/very/long/path",
  "status": "ACTIVE",
  "created_at": "2025-01-15T10:30:00+00:00",
  "updated_at": "2025-01-15T10:30:00+00:00",
  "expires_at": "2025-12-31T23:59:59+00:00",
  "click_count": 42
}
`

**Error Responses:**
- HTTP 404 (Not Found): Short URL with given ID does not exist
- HTTP 500 (Internal Server Error): Unexpected error

**Design Note:** This endpoint does NOT check expiration or disabled status; it returns metadata regardless. Use analytics endpoint to see if URL is actively used.

### 4.4 DELETE /api/v1/urls/{id}

**Purpose:** Disable a shortened URL

**Request:** HTTP DELETE, no body

**Response (HTTP 204 No Content):** Empty body

**Error Responses:**
- HTTP 404 (Not Found): Short URL with given ID does not exist
- HTTP 500 (Internal Server Error): Unexpected error

### 4.5 GET /api/v1/urls/{id}/analytics

**Purpose:** Retrieve analytics for a shortened URL

**Request:** HTTP GET, no body

**Response (HTTP 200 OK):**
`json
{
  "short_url_id": 1,
  "short_code": "aBcD1234",
  "total_clicks": 42,
  "unique_visitors": 15,
  "last_clicked_at": "2025-01-20T14:22:30+00:00"
}
`

**Error Responses:**
- HTTP 404 (Not Found): Short URL with given ID does not exist
- HTTP 500 (Internal Server Error): Unexpected error

## 5. Validation Requirements

### URL Validation

- **Scheme:** Only HTTP and HTTPS allowed
- **Schemes Rejected:** javascript:, data:, file:, ftp:, gopher:, telnet:, or any other non-HTTP scheme
- **Format:** Must be parseable by java.net.URL
- **Length:** Maximum 2048 characters
- **Null/Blank:** Rejected (cannot be null or empty after trim)

### Short Code Format

- **Character Set:** Base62 (a-z, A-Z, 0-9)
- **Length:** 8 characters
- **Uniqueness:** Database constraint enforces UNIQUE on short_code column
- **Generation:** Uses SecureRandom (not sequential)

### Request Validation

- **POST /api/v1/urls:** original_url is required (Bean Validation @NotBlank)
- **expires_at:** Optional, must be valid ISO 8601 OffsetDateTime if provided
- **Path Parameters:** id and shortCode must be syntactically valid

## 6. Error Handling Requirements

### HTTP Status Codes

| Scenario | HTTP Status | Error Type | Message |
|----------|-------------|-----------|---------|
| Unknown short code | 404 NOT FOUND | UrlNotFoundException | "Short code not found: {shortCode}" |
| URL expired | 410 GONE | ExpiredUrlException | "URL expired: {shortCode}" |
| URL disabled | 410 GONE | DisabledUrlException | "URL disabled: {shortCode}" |
| Invalid URL format | 400 BAD REQUEST | IllegalArgumentException | "Invalid URL: {url}" |
| Validation error | 400 BAD REQUEST | MethodArgumentNotValidException | "{field}: {message}, ..." |
| Unknown ID | 404 NOT FOUND | UrlNotFoundException | "Short code not found: ID: {id}" |
| Collision failure | 500 INTERNAL ERROR | RuntimeException | "Failed to generate unique short code after 5 attempts" |
| Unexpected error | 500 INTERNAL ERROR | Generic Exception | "Internal server error" (no stack trace) |

### Error Response Format

All error responses return JSON:
`json
{
  "code": 400,
  "message": "Invalid URL: javascript:alert('xss')"
}
`

### Logging

- **Exception Logging:** Unexpected exceptions (500) are logged at ERROR level with full stack trace
- **Debug Logging:** Application-specific operations logged at DEBUG level (com.vishwasena.urlshortener package)
- **Sensitive Data:** Raw IP addresses not logged; only IP hashes stored

## 7. Data Requirements

### short_url Table

| Column | Type | Constraint | Purpose |
|--------|------|-----------|---------|
| id | BIGINT | PRIMARY KEY, auto-increment | Unique identifier |
| short_code | VARCHAR(20) | NOT NULL, UNIQUE | Short code for redirect (typically 8 chars) |
| original_url | TEXT | NOT NULL | Full destination URL |
| status | VARCHAR(20) | NOT NULL | ACTIVE or DISABLED |
| created_at | TIMESTAMP WITH TIME ZONE | NOT NULL, updatable=false | Creation time (auto-set) |
| updated_at | TIMESTAMP WITH TIME ZONE | NOT NULL | Last update time (auto-updated) |
| expires_at | TIMESTAMP WITH TIME ZONE | NULLABLE | Expiration time (optional) |
| click_count | BIGINT | NOT NULL, default=0 | Total clicks |

**Indexes:**
- UNIQUE idx_short_code on short_code (for fast redirect lookup)
- idx_status on status (for filtering active/disabled URLs)
- idx_expires_at on expires_at (for finding expired URLs)

### click_event Table

| Column | Type | Constraint | Purpose |
|--------|------|-----------|---------|
| id | BIGINT | PRIMARY KEY, auto-increment | Unique identifier |
| short_url_id | BIGINT | NOT NULL, FOREIGN KEY | Reference to short_url |
| clicked_at | TIMESTAMP WITH TIME ZONE | NOT NULL, updatable=false | Click timestamp (auto-set) |
| ip_hash | VARCHAR(64) | NULLABLE | SHA-256 hash of client IP |
| user_agent | TEXT | NULLABLE | HTTP User-Agent header |
| referer | TEXT | NULLABLE | HTTP Referer header |

**Indexes:**
- idx_short_url_id on short_url_id (for analytics queries)
- idx_clicked_at on clicked_at (for finding recent clicks)
- FOREIGN KEY fk_short_url_id to short_url(id)

## 8. Security Requirements

### URL Validation (Prevention of Malicious Redirects)

- **Scheme Whitelist:** Only HTTP and HTTPS allowed
- **Rejection Logic:** Reject javascript:, data:, file:, ftp:, and all other schemes
- **Implementation:** Uses java.net.URL to parse and validate scheme

**Security Assumption:** This prevents common client-side redirect vulnerabilities (XSS via malicious shortened URLs).

### IP Privacy

- **No Raw IP Storage:** Raw client IP addresses are never stored in database
- **Hashing:** Client IP is SHA-256 hashed before storage (one-way hash)
- **Deterministic Hashing:** Same IP always produces same hash (allows unique visitor counting)
- **Hash Format:** 64-character hex string

**Known Risk (MEDIUM):** IP extraction relies on X-Forwarded-For and X-Real-IP headers without validation. In proxy scenarios (load balancers, CDNs), these headers can be spoofed. Acceptable for MVP but should add IP validation/trust proxy configuration in production.

### Database Credentials

- **Storage:** Hardcoded in application.yml in repository
- **Known Risk (HIGH):** Credentials visible in source control. For production, use environment variables or external secrets management.

### No Server-Side URL Fetching

- **Implemented:** Service does NOT fetch or validate destination URLs (no HTTP requests to original URLs)
- **Rationale:** Prevents SSRF attacks, reduces latency, avoids dependency on external services

### Authentication & Authorization

- **Current Status:** Not implemented
- **Access Model:** All endpoints are publicly accessible (anyone can create, retrieve, delete any URL)
- **Design Decision:** Acceptable for interview prototype; production would require API key or OAuth

### No Rate Limiting

- **Current Status:** Not implemented
- **Known Risk (MEDIUM):** Service is vulnerable to abuse (unlimited URL creation, redirect spam)
- **Future Enhancement:** Add rate limiting by IP or API key

## 9. Non-Functional Requirements

### Reliability

- **Requirement:** Service must handle database failures gracefully
- **Implementation:** Global exception handler catches all exceptions; 500 responses returned without exposing internal details
- **Transactions:** All URL creation and click recording use @Transactional (Spring-managed transactions)
- **Known Issue:** Click event recording happens in same transaction as redirect; if recording fails, entire redirect fails

### Maintainability

- **Requirement:** Clean separation of concerns
- **Implementation:** Modular architecture with controller → service → repository layers
- **Code Quality:** Minimal comments (only where logic is non-obvious); clear method and variable names
- **Logging:** Debug-level logs for significant operations (URL creation, click recording)

### Performance

- **Requirement:** Redirects must be fast (common use case)
- **Implementation:** Direct database lookup by short_code (indexed), minimal processing
- **Known Issue:** Analytics calculation loads all ClickEvent objects into memory; should use SQL aggregate
- **Design Decision:** Synchronous click recording (may impact latency at scale; acceptable for MVP)

### Scalability

- **Requirement:** Support growth without major refactoring
- **Implementation:** Short code generation uses SecureRandom, database-enforced uniqueness; no sequential IDs that could become bottleneck
- **Database Indexes:** Properly indexed for common queries
- **Thread Safety:** Spring Bean scope (singleton); service methods are thread-safe (no mutable shared state)
- **Known Limitation:** Synchronous click recording will become bottleneck at very high request volumes

### Observability

- **Logging:** DEBUG logging for URL creation, click recording, analytics retrieval
- **Health Endpoint:** /actuator/health provides basic service health
- **Not Implemented:** Structured logging (JSON), distributed tracing, custom metrics

### Testability

- **Implementation:** All business logic testable in isolation (unit tests possible)
- **Database Testing:** Integration tests use H2 in-memory database (Testcontainers unavailable on Windows)
- **Test Coverage:** 30 test cases covering URL creation, redirect, expiration, disabling, analytics, error handling

## 10. Assumptions

1. **Collision Rarity:** Base62^8 provides ~218 trillion combinations; collision is extremely rare (max 5 retries is sufficient)

2. **IP Header Trustworthiness:** Service assumes X-Forwarded-For and X-Real-IP headers are trustworthy. In production, configure Spring Security or firewall to validate proxy headers.

3. **Clock Synchronization:** Expiration checks use OffsetDateTime.now() on server. Assumes server clock is synchronized (within a few seconds).

4. **Single-Server Deployment:** Design assumes single-server or load-balanced behind reverse proxy. No distributed cache or clock skew handling.

5. **Database Availability:** Service assumes PostgreSQL is always available. No retry logic for database connection failures (acceptable for MVP).

6. **URL Immutability:** Original URL is not changeable after creation (no UPDATE endpoint). Allows caching assumptions.

7. **Analytics Eventual Consistency:** Not required; all analytics generated from synchronous database queries.

8. **No Request Signing:** All endpoints are publicly accessible; no HMAC or signature validation.

## 11. Ambiguities and Engineering Decisions

### 1. Collision Retry Attempts

**Ambiguity:** Assignment specifies "retry when collision occurs" but doesn't specify max attempts.

**Decision:** Implemented max 5 retries. Rationale: Base62^8 provides ~218 trillion combinations; collision probability is vanishingly small. 5 retries provides safety margin while keeping code simple.

**Impact:** On collision failure after 5 retries, user receives HTTP 500 (not retried by client). Acceptable because collisions are extremely rare.

### 2. Redirect HTTP Status Code

**Ambiguity:** Assignment specifies "HTTP 302 for MVP" but doesn't explain rationale.

**Decision:** Implemented HTTP 302 (Found, temporary redirect) as specified.

**Rationale:** Allows service to update URL mappings later without browser cache invalidation. HTTP 301 (Permanent) would cause browsers to cache and bypass service entirely.

### 3. IP Extraction in Proxy Scenarios

**Ambiguity:** Assignment warns "client IP extraction can be spoofed" but doesn't specify handling.

**Decision:** Extract from X-Forwarded-For and X-Real-IP headers in order, falling back to equest.getRemoteAddr(). No validation of proxy configuration.

**Risk:** Headers can be spoofed if service is not properly configured behind trusted proxy. Acceptable for MVP (interview environment assumed secure).

### 4. Unique Visitor Definition

**Ambiguity:** Assignment specifies "unique visitors based on IP hash" but doesn't define "unique."

**Decision:** Unique visitor = distinct IP hash (one hash = one visitor, regardless of time or user agent).

**Impact:** Cannot distinguish same visitor visiting multiple times (by design; IP hash is deterministic and irreversible).

### 5. Click Recording Failure Handling

**Ambiguity:** What if click event recording fails?

**Decision:** Let exception bubble up; fail the redirect rather than silently lose analytics.

**Rationale:** Consistency is more important than availability; better to fail loudly than silently lose data. Acceptable for MVP.

**Production Decision:** Could implement async click recording to separate redirect latency from analytics reliability.

### 6. Expired URL Cleanup

**Ambiguity:** Assignment doesn't mention background job for expired URLs.

**Decision:** No cleanup job implemented. Expired URLs remain in database indefinitely (soft-expired only at access time).

**Rationale:** Acceptable for MVP (interview prototype); reduces complexity. Production would need cleanup job and retention policy.

### 7. Rate Limiting

**Ambiguity:** Assignment mentions "unknown redirect endpoint vulnerable" but doesn't specify solution.

**Decision:** Not implemented for MVP. No request throttling or IP-based rate limiting.

**Risk:** Service vulnerable to abuse (unlimited URL creation, redirect spam). Flagged as HIGH priority future task.

### 8. Authentication & Authorization

**Ambiguity:** Assignment specifies "no authentication unless requested"; DELETE endpoint is public.

**Decision:** All endpoints are publicly accessible. Anyone can create, retrieve, or delete any URL.

**Rationale:** Acceptable for interview prototype; production would require API key or OAuth2. Current design focuses on core URL shortening functionality.

## 12. Out of Scope

The following features are intentionally excluded from this one-day prototype:

- **Authentication & Authorization:** No API keys, OAuth, or user accounts
- **Rate Limiting:** No request throttling or abuse prevention
- **Asynchronous Processing:** Click recording is synchronous (single-threaded per request)
- **Caching:** No Redis or in-memory cache
- **Analytics Pagination:** All click events loaded into memory for analytics
- **Date Range Filtering:** Analytics cover all-time data only
- **Custom Short Codes:** Users cannot specify their own short codes
- **QR Code Generation:** No QR code API
- **URL Validation/Preview:** Service does not fetch destination URLs
- **Geolocation:** No IP-to-location mapping
- **Bot Detection:** No detection of crawlers or automated traffic
- **Frontend:** No web UI (API-only)
- **Kubernetes/Microservices:** Single monolith deployment only
- **Kafka/Event Streaming:** No pub-sub or async event processing
- **Secrets Management:** No HashiCorp Vault or AWS Secrets Manager
- **Structured Logging:** No JSON logging or distributed tracing
- **CORS Configuration:** No cross-origin request handling
- **GraphQL:** REST API only
- **WebSockets:** No real-time updates
- **URL Expiration Cleanup Job:** Expired URLs not automatically deleted
- **Re-enable Disabled URLs:** Disable is permanent (no un-disable)

## 13. Acceptance Criteria

### Core Functionality

- ✅ User can create a short URL for any HTTP/HTTPS URL (max 2048 chars)
- ✅ Created short URL uses 8-character Base62 short code (a-z, A-Z, 0-9)
- ✅ Short code is unique and collision-free (database constraint enforced)
- ✅ User receives response with short URL (baseUrl + "/" + shortCode)
- ✅ User can access short URL and be redirected to original URL (HTTP 302)
- ✅ Redirect is recorded as click event with IP hash, User-Agent, Referer
- ✅ User can disable a short URL (DELETE /api/v1/urls/{id} returns 204)
- ✅ Disabled URL returns HTTP 410 (Gone) on redirect attempt
- ✅ User can set optional expiration time on URL creation
- ✅ Expired URL returns HTTP 410 (Gone) on redirect attempt
- ✅ User can retrieve analytics for any short URL (total clicks, unique visitors, last clicked)
- ✅ Analytics shows unique visitors as distinct IP hashes (not multiple clicks from same IP)
- ✅ Unknown short code returns HTTP 404 (Not Found)
- ✅ Invalid URL format returns HTTP 400 (Bad Request)
- ✅ Unsupported URL schemes (javascript:, data:, file:) rejected with HTTP 400
- ✅ URL too long (>2048 chars) returns HTTP 400
- ✅ Health endpoint available at /actuator/health
- ✅ All errors return JSON ErrorResponse with code and message
- ✅ No raw IP addresses stored in database (only SHA-256 hashes)
- ✅ Database persistence verified (URLs survive service restart)
- ✅ All 30 integration and unit tests pass

### Data Integrity

- ✅ short_code column has UNIQUE constraint (database-enforced)
- ✅ click_count accurately reflects number of redirect attempts
- ✅ IP hash is deterministic (same IP always produces same hash)
- ✅ Timestamps use OffsetDateTime (timezone-aware)
- ✅ created_at and updated_at auto-managed by @PrePersist/@PreUpdate

### Error Handling

- ✅ All domain exceptions caught by GlobalExceptionHandler
- ✅ UrlNotFoundException (404) vs ExpiredUrlException (410) vs DisabledUrlException (410)
- ✅ Validation errors (400) include field names and messages
- ✅ Unexpected errors (500) logged without exposing stack traces to client
- ✅ Collision retry loop retries up to 5 times on DataIntegrityViolationException

### Code Quality

- ✅ Clear separation: controller → service → repository
- ✅ Entities use JPA annotations and lifecycle hooks
- ✅ Business logic in service layer (not controller or repository)
- ✅ Exception handling centralized in GlobalExceptionHandler
- ✅ Validation in UrlValidator utility class
- ✅ URL sanitization and validation implemented
- ✅ Logging at DEBUG level for key operations
- ✅ Maven build succeeds with no warnings
- ✅ All classes properly documented (javadoc where needed)

---

## Summary

This document describes the URL Shortener service as implemented for the interview assignment. The service provides core functionality for URL shortening, analytics, and lifecycle management. The MVP is intentionally scoped to demonstration-quality code suitable for an interview, with clear documentation of what is implemented, what is not, and what decisions were made during development.

**Last Updated:** January 2025
