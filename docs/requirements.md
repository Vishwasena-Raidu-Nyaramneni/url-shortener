# URL Shortener — Requirements

## 1. Problem Statement

This service provides a URL shortening API for converting long URLs into compact, memorable short codes. Users can create shortened URLs that redirect to their original destinations, track usage through click analytics, and manage URL lifecycle through expiration and disabling.

## 2. Goals

- Provide a simple REST API for creating and managing shortened URLs
- Generate unique, collision-free short codes using Base62 encoding (8-character, ~218 trillion combinations)
- Track click analytics per shortened URL
- Support URL expiration and manual disabling
- Deliver fast, reliable redirects with analytics collection
- Demonstrate production-oriented design principles (validation, error handling, logging, transactions)

## 3. Key Assumptions & Design Decisions

### 3.1 Short-Code Generation

| Decision | Rationale |
|----------|-----------|
| **8-character Base62 code** | ~218T combinations; sufficient for MVP; balances memorability and uniqueness |
| **Alphabet: a-z, A-Z, 0-9** | URL-safe; no special character encoding needed |
| **SecureRandom generation** | Cryptographically unpredictable; prevents enumeration attacks |
| **Max 5 collision retries** | Collision probability ≈ zero with 218T space; 5 retries is practical fail-fast |

### 3.2 URL Expiration

- **Soft expiration:** Expiration is checked at access time, not enforced via background jobs
- **Future-only validation:** expiresAt must be a future timestamp at creation time
- **410 Gone for expired:** Distinct from 404 to allow clients to distinguish between deleted and expired

### 3.3 URL Disabling

- **Soft-delete approach:** status = "DISABLED" rather than hard-delete; preserves audit trail and analytics
- **410 Gone for disabled:** Same semantics as expired (both indicate URL is no longer accessible)
- **No re-enable in MVP:** Currently one-way; re-enabling deferred to future

### 3.4 Analytics

- **Synchronous recording:** Click events recorded in same request as redirect (not async); ensures accuracy at expense of potential latency
- **IP privacy:** One-way SHA-256 hash; no raw IP storage
- **Unique visitors:** Counted by distinct IP hash
- **Minimal tracking:** Only IP hash, User-Agent, Referer; no user identification

### 3.5 Redirect Behavior

- **HTTP 302 (Found):** Temporary redirect (not 301 permanent); allows mapping updates without browser cache issues
- **Immediate click recording:** Analytics recorded before redirect response returned

---

## 4. Functional Requirements

| Requirement | API | Implementation | Status |
|-------------|-----|-----------------|--------|
| Create short URL | POST /api/v1/urls | Accepts HTTP/HTTPS only; validates scheme whitelist; generates 8-char Base62 code; retries up to 5 times on collision; returns HTTP 201 with metadata | ✅ DONE |
| Redirect to original URL | GET /{shortCode} | Looks up short code; records click synchronously; returns HTTP 302 redirect; HTTP 404 if not found | ✅ DONE |
| Expiration support | Soft-checked on access | Accepts optional expiresAt (must be future); returns HTTP 410 if expired | ✅ DONE |
| Disable URL | DELETE /api/v1/urls/{id} | Sets status = "DISABLED"; soft-delete; returns HTTP 204 | ✅ DONE |
| Analytics collection | Implicit during redirect | Records click event: IP hash, User-Agent, Referer, timestamp; increments click_count | ✅ DONE |
| Analytics retrieval | GET /api/v1/urls/{id}/analytics | Returns total clicks, unique visitors (by IP hash), last clicked timestamp | ✅ DONE |
| Health check | GET /actuator/health | Spring Boot Actuator health probe | ✅ DONE |
| Error handling | All endpoints | Consistent JSON error responses with status, message, timestamp; HTTP 400 for invalid input; HTTP 410 for expired/disabled | ✅ DONE |

---

## 5. Non-Functional Requirements

| Requirement | Implementation |
|-------------|-----------------|
| **URL Validation** | Accept only HTTP/HTTPS; reject data:, javascript:, file:, ftp:; max 2048 chars |
| **Short-Code Generation** | SecureRandom Base62 (a-z, A-Z, 0-9); 8 characters; collision-free via unique constraint + retries |
| **IP Privacy** | One-way SHA-256 hash; no raw IP storage |
| **Database** | PostgreSQL 15+ with Flyway migrations; Foreign keys for referential integrity |
| **Transactions** | Atomic create operations; transactional click recording |
| **Error Semantics** | 404 = not found; 410 = expired/disabled; 400 = invalid input; 409 = conflict; 500 = server error (generic, no details) |
| **Logging** | INFO level for operations; DEBUG level for details; no sensitive data in logs |

---

## 6. Acceptance Criteria

- ✅ All 10 core functional requirements implemented
- ✅ All 90 unit and integration tests pass
- ✅ Database enforces short-code uniqueness (unique constraint)
- ✅ Collision handling via retries (max 5 attempts)
- ✅ Expiration checked at access time; soft-delete for disabling
- ✅ Analytics recorded synchronously with redirect
- ✅ IP privacy enforced (hashed, not raw storage)
- ✅ Error responses consistent and secure (no exception details)
- ✅ Docker deployment working (docker-compose up)
- ✅ Swagger UI available; API fully documented
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

---

## 5. API Endpoints

| Endpoint | Method | Purpose | Status |
|----------|--------|---------|--------|
| /api/v1/urls | POST | Create short URL | ✅ DONE |
| /{shortCode} | GET | Redirect and record click | ✅ DONE |
| /api/v1/urls/{id} | GET | Retrieve URL metadata | ✅ DONE |
| /api/v1/urls/{id} | DELETE | Disable URL (soft-delete) | ✅ DONE |
| /api/v1/urls/{id}/analytics | GET | Retrieve click analytics | ✅ DONE |
| /actuator/health | GET | Health check | ✅ DONE |

See [Architecture](architecture.md) for request/response schemas and [Swagger](http://localhost:8080/swagger-ui.html) for interactive API documentation.

---

## 6. Database Schema

**short_url:**
- id (BIGINT PK, auto-increment)
- short_code (VARCHAR UNIQUE NOT NULL) — Base62, 8 chars
- original_url (TEXT NOT NULL)
- status (VARCHAR NOT NULL) — ACTIVE or DISABLED
- created_at, updated_at, expires_at (TIMESTAMP WITH TZ)
- click_count (BIGINT, default 0)

**click_event:**
- id (BIGINT PK)
- short_url_id (BIGINT FK)
- clicked_at (TIMESTAMP WITH TZ)
- ip_hash (VARCHAR 64) — SHA-256 one-way
- user_agent (TEXT)
- referer (TEXT)

See [Flyway migrations](../src/main/resources/db/migration/) for schema details.

---

## 7. Key Security Features

| Feature | Implementation | Status |
|---------|-----------------|--------|
| URL Scheme Validation | Only HTTP/HTTPS; rejects data:, javascript:, file:, ftp: | ✅ DONE |
| IP Privacy | SHA-256 one-way hashing; no raw IP storage | ✅ DONE |
| Input Validation | Size limits (max 2048 chars), format validation, Bean Validation | ✅ DONE |
| SQL Injection Prevention | Parameterized queries via Spring Data JPA | ✅ DONE |
| Error Handling | Generic error messages; no exception details to clients | ✅ DONE |

See [docs/security.md](security.md) for detailed threat analysis and controls.

---

## 8. Status: Complete

All 10 core requirements implemented and tested:
- ✅ 90/90 unit and integration tests pass
- ✅ Maven build succeeds
- ✅ Docker deployment verified
- ✅ Database persistence verified
- ✅ Swagger/OpenAPI documentation complete
- ✅ All endpoints tested with valid/invalid/edge case inputs
