# URL Shortener — Architecture

## 1. Architecture Overview

The URL Shortener is a **modular monolith** using a layered architecture appropriate for an interview prototype. The design prioritizes simplicity, maintainability, and clear separation of concerns over premature architectural complexity (microservices, distributed caching, message queues).

**Why this architecture?**

- **Simplicity:** Easier to develop, test, and deploy in a single Spring Boot application
- **No distributed system complexity:** Avoids CAP theorem concerns, clock skew, eventual consistency issues
- **Clear dependencies:** Explicit flow from controller → service → repository → database
- **Interview-appropriate scope:** Demonstrates production-quality code without over-engineering
- **No premature optimization:** Monolith scales adequately for prototype traffic; can be refactored to microservices if needed

The service is deployed as a single JAR file running on Spring Boot, backed by PostgreSQL. All components run in the same JVM process.

## 2. Architecture Diagram

`mermaid
graph LR
    Client["HTTP Client<br/>(Browser, API Client)"]
    
    subgraph Spring["Spring Boot Application"]
        DispatcherServlet["Dispatcher Servlet<br/>(Spring MVC)"]
        Controller["UrlController<br/>(REST Endpoints)"]
        Validation["Request Validation<br/>(@Valid, Bean Validation)"]
        Service["UrlShortenerService<br/>(Business Logic)"]
        Util["Utility Classes<br/>(IpHasher, ShortCodeGenerator,<br/>UrlValidator, ClientIpExtractor)"]
        Repository["Repository Layer<br/>(JPA Repositories)"]
        Entity["Entity Classes<br/>(JPA Entities)"]
    end
    
    Flyway["Flyway<br/>(Database Migrations)"]
    Database["PostgreSQL<br/>(Persistent Storage)"]
    
    Actuator["Spring Actuator<br/>(/actuator/health)"]
    
    Client -->|HTTP| DispatcherServlet
    DispatcherServlet -->|Routes| Controller
    Controller -->|Validates| Validation
    Validation -->|Valid Request| Service
    Service -->|Uses| Util
    Service -->|Reads/Writes| Repository
    Repository -->|ORM Mapping| Entity
    Entity -->|SQL| Database
    
    Flyway -->|Migrations| Database
    Client -->|GET /actuator/health| Actuator
    
    style Spring fill:#e8f4f8
    style Database fill:#ffe8e8
    style Client fill:#e8ffe8

## 3. Technology Stack

| Component | Technology | Version | Purpose |
|-----------|-----------|---------|---------|
| **Language** | Java | 21 (LTS) | Primary development language |
| **Framework** | Spring Boot | 3.3.0 | Application framework & web server |
| **Web** | Spring Web MVC | 3.3.0 | REST API & HTTP handling |
| **ORM** | Spring Data JPA | 3.3.0 | Database abstraction layer |
| **Validation** | Bean Validation (Jakarta) | 3.0 | Request & entity validation |
| **Database** | PostgreSQL | Latest (via driver) | Persistent data storage |
| **Driver** | org.postgresql | Latest | PostgreSQL JDBC driver |
| **Migrations** | Flyway | Latest | Database schema versioning |
| **Monitoring** | Spring Actuator | 3.3.0 | Health & operational endpoints |
| **API Docs** | Springdoc OpenAPI | 2.3.0 | Swagger/OpenAPI documentation |
| **Testing** | JUnit 5 | Latest | Unit testing framework |
| **Mocking** | Mockito | Latest | Mock object framework |
| **Integration Tests** | Testcontainers | 1.19.3 | PostgreSQL containers (future) |
| **Build** | Maven | 3.x | Build & dependency management |

**Build Configuration:**
- Java source/target: 21
- Project encoding: UTF-8
- Maven Compiler source/target: 21

## 4. Package Structure

`
com.vishwasena.urlshortener
├── controller/
│   └── UrlController.java         # REST endpoints & HTTP handling
├── service/
│   └── UrlShortenerService.java   # Business logic & orchestration
├── repository/
│   ├── ShortUrlRepository.java    # Data access for ShortUrl
│   └── ClickEventRepository.java  # Data access for ClickEvent
├── entity/
│   ├── ShortUrl.java              # JPA entity for short_url table
│   └── ClickEvent.java            # JPA entity for click_event table
├── dto/
│   ├── CreateUrlRequest.java      # Request DTO
│   ├── CreateUrlResponse.java     # Response DTO
│   ├── AnalyticsResponse.java     # Analytics DTO
│   └── ErrorResponse.java         # Error DTO
├── exception/
│   ├── GlobalExceptionHandler.java  # Centralized exception handling
│   ├── UrlNotFoundException.java     # Custom exception
│   ├── ExpiredUrlException.java     # Custom exception
│   └── DisabledUrlException.java    # Custom exception
├── util/
│   ├── ShortCodeGenerator.java    # Base62 short code generation
│   ├── UrlValidator.java          # URL scheme/format validation
│   ├── IpHasher.java              # SHA-256 IP hashing
│   └── ClientIpExtractor.java     # Extract client IP from headers
├── config/
│   └── (Empty; Spring Boot auto-configuration used)
└── UrlShortenerApplication.java   # Spring Boot entry point
`

**Package Responsibilities:**

- **controller:** HTTP request/response handling, parameter extraction, status codes
- **service:** Business logic, validation orchestration, transaction management
- **repository:** Database queries using Spring Data JPA conventions
- **entity:** JPA entity definitions and lifecycle hooks
- **dto:** Request/response data transfer objects (decouples API from entities)
- **exception:** Domain exceptions and centralized error handling
- **util:** Stateless utility functions (IP extraction, hashing, validation)

## 5. Create URL Request Flow

`
1. Client sends:
   POST /api/v1/urls
   {
     "original_url": "https://example.com/very/long/path",
     "expires_at": "2025-12-31T23:59:59Z"
   }

2. DispatcherServlet routes to UrlController.createShortUrl()

3. Spring validates request:
   - @Valid annotation on CreateUrlRequest
   - Bean Validation checks @NotBlank on original_url
   - Returns HTTP 400 if validation fails

4. UrlController calls UrlShortenerService.createShortUrl()

5. UrlShortenerService.createShortUrl():
   a. Call UrlValidator.isValidUrl(originalUrl)
      - Check scheme is HTTP/HTTPS only
      - Check length ≤ 2048 characters
      - Parse URL using java.net.URL
      - Return false if invalid → throw IllegalArgumentException
   
   b. Retry loop (max 5 attempts):
      i.   Generate new short code: ShortCodeGenerator.generate()
           - SecureRandom selects 8 random Base62 characters
      ii.  Create ShortUrl entity with:
           - short_code (generated)
           - original_url (validated)
           - status = "ACTIVE"
           - expires_at (optional)
      iii. Call ShortUrlRepository.save(shortUrl)
      iv.  On success: return entity
      v.   On DataIntegrityViolationException (collision):
           - If attempt < 4: retry next iteration
           - If attempt >= 4: throw RuntimeException

6. ShortUrlRepository.save() performs:
   - @PrePersist lifecycle hook sets created_at & updated_at to now()
   - Inserts row into short_url table
   - Database enforces UNIQUE constraint on short_code
   - Returns entity with auto-generated id

7. UrlController constructs CreateUrlResponse:
   - id, short_code, original_url, short_url (baseUrl + "/" + shortCode)

8. Spring converts response to JSON:
   - Jackson serializes using SNAKE_CASE property naming

9. Return HTTP 201 (Created) with response JSON
`

## 6. Redirect Request Flow

`
1. Client sends:
   GET /aBcD1234
   Headers: User-Agent, Referer, X-Forwarded-For (optional)

2. DispatcherServlet routes to UrlController.redirect(@PathVariable shortCode)

3. UrlController extracts request data:
   a. ClientIpExtractor.extractClientIp(request):
      - Try header "X-Forwarded-For" (proxy/LB) → split by comma, take first
      - Try header "X-Real-IP" (nginx) → use if present
      - Fallback to request.getRemoteAddr()
   
   b. Extract headers:
      - User-Agent: request.getHeader("User-Agent")
      - Referer: request.getHeader("Referer")

4. UrlController calls UrlShortenerService.recordClick()
   (Click recording happens BEFORE redirect)

5. UrlShortenerService.recordClick(shortCode, ipAddress, userAgent, referer):
   a. Call getShortUrlByCode(shortCode) to fetch & validate
   
   b. getShortUrlByCode():
      i.   Query ShortUrlRepository.findByShortCode(shortCode)
      ii.  If not found: throw UrlNotFoundException → HTTP 404
      iii. Check if status == "DISABLED":
           - If yes: throw DisabledUrlException → HTTP 410
      iv.  Check if expires_at < now():
           - If yes: throw ExpiredUrlException → HTTP 410
      v.   Return validated ShortUrl entity
   
   c. Hash the client IP: IpHasher.hashIp(ipAddress)
      - SHA-256 hash using MessageDigest
      - Return 64-char hex string
   
   d. Create ClickEvent with:
      - shortUrl (reference)
      - ipHash (hashed IP)
      - userAgent
      - referer
      - @PrePersist sets clicked_at = now()
   
   e. ClickEventRepository.save(clickEvent)
      - Insert into click_event table
   
   f. Increment click counter:
      - shortUrl.incrementClickCount() (adds 1)
      - ShortUrlRepository.save(shortUrl)
      - @PreUpdate sets updated_at = now()

6. UrlController constructs RedirectView:
   - Set URL to shortUrl.getOriginalUrl()
   - Set status code to HTTP 302 (Found/temporary redirect)
   - Return RedirectView

7. Spring returns HTTP 302 with Location header:
   Location: https://example.com/very/long/path

8. Client browser follows redirect to original URL

Note: All steps 3-6 happen in a single HTTP request/response cycle.
If any step fails, the entire request fails; click not recorded unless redirect succeeds.

## 7. Analytics Flow

`
1. Client sends:
   GET /api/v1/urls/{id}/analytics

2. UrlController.getAnalytics(id) called

3. UrlShortenerService.getAnalytics(Long shortUrlId):
   
   a. Query ShortUrlRepository.findById(shortUrlId)
      - If not found: throw UrlNotFoundException → HTTP 404
      - Load ShortUrl entity
   
   b. Query ClickEventRepository.findByShortUrlId(shortUrlId)
      - Fetch ALL ClickEvent records for this short URL
      - Loaded into memory (potential inefficiency at scale)
   
   c. Calculate unique visitors:
      - Call ClickEventRepository.countUniqueVisitors(shortUrlId)
      - Uses @Query with COUNT(DISTINCT ipHash)
      - Single SQL query returns long count
   
   d. Calculate last clicked timestamp:
      - Stream clickEvents
      - Map to clicked_at field
      - Find max using OffsetDateTime::compareTo
      - Null if no events
   
   e. Return AnalyticsData record:
      - shortUrlId
      - shortCode
      - totalClicks (from shortUrl.clickCount field)
      - uniqueVisitors (from query in step c)
      - lastClickedAt (from step d)

4. UrlController constructs AnalyticsResponse DTO

5. Spring converts to JSON (SNAKE_CASE naming)

6. Return HTTP 200 with response

Note: This design loads all ClickEvent objects into memory
to calculate lastClickedAt. For high-volume URLs (thousands of
clicks), consider using native SQL query with MAX aggregate.
`

## 8. Database Architecture

### Schema Design

**short_url table** - Stores shortened URL mappings

| Column | Type | Constraint | Purpose |
|--------|------|-----------|---------|
| id | BIGSERIAL | PRIMARY KEY, auto-increment | Unique identifier |
| short_code | VARCHAR(20) | NOT NULL, UNIQUE | Redirect lookup key |
| original_url | TEXT | NOT NULL | Destination URL |
| status | VARCHAR(20) | NOT NULL, DEFAULT 'ACTIVE' | ACTIVE or DISABLED |
| created_at | TIMESTAMP WITH TIME ZONE | NOT NULL | Creation timestamp (read-only) |
| updated_at | TIMESTAMP WITH TIME ZONE | NOT NULL | Last update timestamp (auto-updated) |
| expires_at | TIMESTAMP WITH TIME ZONE | NULLABLE | Expiration time (optional) |
| click_count | BIGINT | NOT NULL, DEFAULT 0 | Total clicks (incremented on redirect) |

**Indexes on short_url:**
- idx_short_code (UNIQUE): Fast lookup by short code → used in redirect flow
- idx_status: Filter active/disabled URLs
- idx_expires_at: Find expired URLs (for cleanup job if added)

**click_event table** - Records click activity

| Column | Type | Constraint | Purpose |
|--------|------|-----------|---------|
| id | BIGSERIAL | PRIMARY KEY, auto-increment | Unique click event ID |
| short_url_id | BIGINT | NOT NULL, FOREIGN KEY | Reference to short_url.id (ON DELETE CASCADE) |
| clicked_at | TIMESTAMP WITH TIME ZONE | NOT NULL | Click timestamp (read-only) |
| ip_hash | VARCHAR(64) | NULLABLE | SHA-256 hash of client IP (64 hex chars) |
| user_agent | TEXT | NULLABLE | HTTP User-Agent header |
| referer | TEXT | NULLABLE | HTTP Referer header |

**Indexes on click_event:**
- idx_short_url_id: Fast query for all clicks on a URL → used in analytics
- idx_clicked_at: Find recent clicks (for time-based queries)
- Foreign Key constraint: ON DELETE CASCADE (deleting URL also deletes click events)

### Relationships

`
    ShortUrl (1)
       ↓
       └─── (1 to many) ─→ ClickEvent (many)
            
    - One short URL has multiple click events
    - Deleting a short URL cascades to delete all click events
    - No orphaned click events possible
`

### Migration Strategy

- **Tool:** Flyway (v1.19.3)
- **Migrations:** src/main/resources/db/migration/
- **Current Migration:** V1__Initial_schema.sql (both tables created)
- **Auto-execution:** Enabled in application.yml (flyway.enabled: true)
- **Timing:** Runs on Spring Boot startup, before business logic

### Data Lifecycle

1. **Short URL Creation:** Inserted by service, status = ACTIVE
2. **Click Recording:** New ClickEvent inserted, click_count incremented
3. **Expiration:** Not deleted; checked at redirect time (soft expiration)
4. **Disabling:** status changed to DISABLED; URL remains in database
5. **Deletion:** Click events deleted via foreign key cascade

## 9. Transaction Boundaries

**Transaction Manager:** Spring's PlatformTransactionManager (JPA-backed)

**@Transactional Methods in UrlShortenerService:**

| Method | Scope | Read-Only | Purpose |
|--------|-------|-----------|---------|
| createShortUrl() | REQUIRED (default) | No | Create short URL + persist |
| getShortUrlByCode() | REQUIRED | Yes (readOnly=true) | Query by short code |
| getShortUrlById() | REQUIRED | Yes (readOnly=true) | Query by ID |
| ecordClick() | REQUIRED | No | Insert click event + update click_count |
| getAnalytics() | REQUIRED | Yes (readOnly=true) | Query analytics |
| disableShortUrl() | REQUIRED | No | Update status field |

**Transaction Details:**

1. **createShortUrl():**
   - Opens transaction
   - Short-code generation (no DB calls)
   - Inserts ShortUrl row
   - If collision: catches DataIntegrityViolationException, retries
   - Commits on success or throws on final collision

2. **recordClick():**
   - Opens transaction
   - Calls getShortUrlByCode() (read-only query within same transaction)
   - Checks expiration/disabled status
   - Inserts ClickEvent row
   - Updates click_count on ShortUrl
   - Commits all changes atomically
   - If any step fails: rollback (no partial click recorded)

3. **getAnalytics():**
   - Opens read-only transaction
   - Queries ShortUrl by ID
   - Queries all ClickEvent records
   - Executes COUNT(DISTINCT ipHash) query
   - Commits read-only transaction (releases resources)

**Transaction Isolation:** Default PostgreSQL isolation level (usually READ_COMMITTED)

**Why Transactions Matter:**
- Ensures click_count and ClickEvent stay in sync (atomic increment)
- Prevents partial writes if failure occurs mid-redirect
- Database consistency guaranteed even under concurrent requests

## 10. Error Handling

### Exception Hierarchy

`
Exception
  ├── Runtime Exceptions (Checked Exceptions)
  │   ├── UrlNotFoundException (custom)
  │   ├── ExpiredUrlException (custom)
  │   ├── DisabledUrlException (custom)
  │   ├── IllegalArgumentException (validation failures)
  │   └── RuntimeException (collision failures)
  │
  └── Spring Framework Exceptions
      ├── MethodArgumentNotValidException (Bean Validation failures)
      └── DataIntegrityViolationException (unique constraint violations)
`

### GlobalExceptionHandler

Located in com.vishwasena.urlshortener.exception.GlobalExceptionHandler

**@RestControllerAdvice** catches exceptions across all @RestController classes

| Exception | HTTP Status | Error Code | Logged |
|-----------|-------------|-----------|--------|
| UrlNotFoundException | 404 NOT FOUND | 404 | No |
| ExpiredUrlException | 410 GONE | 410 | No |
| DisabledUrlException | 410 GONE | 410 | No |
| IllegalArgumentException | 400 BAD REQUEST | 400 | No |
| MethodArgumentNotValidException | 400 BAD REQUEST | 400 | No (fields in message) |
| Exception (generic catch-all) | 500 INTERNAL ERROR | 500 | Yes (ERROR level) |

### Error Response Format

All errors return JSON:
`json
{
  "code": 400,
  "message": "Invalid URL: javascript:alert('xss')"
}
`

### Logging

- **Level:** DEBUG for application-specific operations, INFO/ERROR for framework
- **Config:** src/main/resources/application.yml
- **Package Levels:**
  - com.vishwasena.urlshortener: DEBUG (all business operations)
  - org.springframework.web: INFO (Spring MVC)
  - org.hibernate.SQL: DEBUG (generated SQL)

**No sensitive data in logs:**
- Raw IP addresses never logged (only hashes stored)
- Error stack traces not exposed to clients (only in server logs)

## 11. Security Architecture

### Input Validation

1. **URL Scheme Validation** (UrlValidator.java):
   - Whitelist: HTTP and HTTPS only
   - Reject: javascript:, data:, file:, ftp:, and all others
   - Method: java.net.URL.getProtocol() check

2. **URL Format Validation** (UrlValidator.java):
   - Parse with java.net.URL (validates RFC 3986 format)
   - Check length ≤ 2048 characters
   - Reject null/blank URLs

3. **Request Validation** (Bean Validation):
   - @NotBlank on original_url field
   - Spring validates before controller receives request
   - Returns HTTP 400 with field errors if invalid

### Data Protection

1. **IP Address Hashing** (IpHasher.java):
   - Extract client IP from request headers
   - One-way SHA-256 hash before storage
   - Deterministic (same IP always produces same hash)
   - Irreversible (cannot recover IP from hash)
   - Purpose: unique visitor counting without storing raw IPs

2. **No Server-Side URL Fetching:**
   - Service does NOT make HTTP requests to destination URLs
   - Prevents SSRF (Server-Side Request Forgery) attacks
   - Reduces dependencies and latency

### Current Security Risks

1. **HIGH RISK: Hardcoded Database Credentials**
   - Username/password visible in application.yml
   - Stored in source control (repository)
   - Mitigation: Use environment variables or secrets manager in production

2. **MEDIUM RISK: IP Header Spoofing**
   - ClientIpExtractor trusts X-Forwarded-For and X-Real-IP headers
   - Without proxy validation, headers can be spoofed
   - Mitigation: Configure Spring Security proxy configuration or firewall rules

3. **MEDIUM RISK: No Rate Limiting**
   - Service vulnerable to abuse (unlimited URL creation, redirect spam)
   - No throttling per IP or API key
   - Mitigation: Add rate limiting (future task)

4. **MEDIUM RISK: Public Endpoints (No Authentication)**
   - All endpoints publicly accessible
   - Anyone can create, read, or delete any URL
   - Mitigation: Add authentication/authorization (future task)

5. **LOW RISK: No HTTPS Enforcement**
   - Configuration allows HTTP or HTTPS
   - application.yml does not enforce HTTPS only
   - Mitigation: Configure reverse proxy for HTTPS termination

### Strengths

- ✅ Only HTTP/HTTPS URLs accepted (prevents malicious schemes)
- ✅ No raw IP addresses stored (only SHA-256 hashes)
- ✅ No exposure of internal exception details to clients
- ✅ Input validation at multiple layers (controller, service)
- ✅ Unique constraints prevent short-code hijacking

## 12. Scalability Considerations

### Current Bottlenecks

1. **Synchronous Click Recording**
   - Each redirect request must:
     a. Insert ClickEvent row
     b. Update click_count field
     c. Return HTTP 302
   - At high traffic (10,000+ redirects/sec), database writes become bottleneck
   - Solution (future): Async click recording via message queue (Kafka) or cache-then-flush

2. **In-Memory Analytics Calculation**
   - getAnalytics() loads ALL ClickEvent objects into memory
   - Streams to find MAX(clicked_at) timestamp
   - For URLs with millions of clicks, this is inefficient
   - Solution: Use native SQL query with MAX(clicked_at) aggregate

3. **Database Dependency**
   - All reads and writes go directly to PostgreSQL
   - No caching layer (Redis)
   - Database failure → entire service unavailable
   - Solution (future): Add read replica, implement circuit breaker

### Redirect Traffic Scaling

**Current Design Characteristics:**

- Reads (redirects) are simple indexed lookups: O(1) in short_url table
- Writes (clicks) are sequential: insert ClickEvent + update click_count
- No horizontal scaling built in (single service instance)

**Scaling to Higher Traffic:**

1. **Read Scaling (Redirects):**
   - Add read replicas for short_url queries
   - Implement Redis cache for frequently accessed URLs
   - Load balance across multiple service instances

2. **Write Scaling (Clicks):**
   - Use asynchronous click recording (decouple from redirect response)
   - Write to message queue, process asynchronously
   - Batch updates to click_count (reduce database writes)

3. **Database Scaling:**
   - Add connection pooling (HikariCP already used by Spring)
   - Implement read/write separation
   - Shard by short_code hash if needed (future)

### Analytics Growth

**Problem:** click_event table grows by number of redirects

**Current Approach:**
- All clicks stored indefinitely
- Queries load all rows for a URL
- No pagination or time-based filtering

**Scaling Options:**
- Archive old clicks to separate table/database
- Implement time-series database for analytics
- Pre-compute daily/hourly aggregates
- Paginate analytics endpoint (future)

### Concurrent Request Handling

- **Current:** Spring Boot with embedded Tomcat (thread pool, default ~200 threads)
- **Adequate for:** Hundreds of concurrent redirects
- **Scaling:** Increase thread pool size or add load balancer

### Database Connection Pool

- **Tool:** HikariCP (default in Spring Boot)
- **Configured:** Via spring.datasource properties in application.yml
- **Default:** 10 connections (adequate for prototype)
- **Scaling:** Increase pool size for higher concurrency

### Limitations of Current Design

**Does NOT scale horizontally to:**
- 1 million redirects per second (would need async, caching)
- Petabyte-scale analytics (would need data warehouse)
- Global geographic distribution (would need CDN + regional services)

**Appropriate for:**
- Thousands of redirects per second (single server)
- Millions of total URLs
- Interview prototype or small-scale deployment

## 13. Architectural Trade-offs

### Why Modular Monolith (Not Microservices)?

**Monolith Benefits:**
- Simpler to develop and test
- Single deployment unit
- Easier debugging (single JVM)
- No RPC latency or serialization overhead
- Transactions guarantee consistency (ACID)

**Monolith Trade-offs:**
- Cannot scale components independently
- Database becomes single point of failure
- Difficult to use different technologies per service
- Risk of tight coupling if not carefully designed

**For Interview Prototype:**
- Monolith appropriate; demonstrates good design principles
- If traffic grows, refactor to microservices later
- Currently not justified by prototype requirements

### Why No Redis?

**Would Add:**
- Complexity (another service to manage)
- Consistency challenges (cache invalidation, stale data)
- Cost and operational burden

**Current Approach:**
- Direct database queries
- Database handles caching implicitly (OS page cache, PostgreSQL internal cache)

**When to Add Redis:**
- If analytics queries become bottleneck (cache results)
- If redirects need sub-millisecond latency (cache short_url lookup)
- Requires cache invalidation strategy (when URL updated)

### Why No Kafka?

**Would Add:**
- Asynchronous click recording (decouple redirect from analytics)
- Improved redirect latency
- Ability to replay clicks
- Risk of losing clicks if service crashes before processing

**Current Approach:**
- Synchronous click recording
- Guarantees consistency (every click recorded immediately)
- Simpler code (less async complexity)

**When to Add Kafka:**
- If click recording slows down redirects (profiling needed)
- If need distributed tracing across services
- Requires message format and consumer implementation

### Why No Kubernetes?

**Would Add:**
- Container orchestration (health checks, auto-scaling)
- Service discovery (multiple instances)
- Configuration management (ConfigMaps, Secrets)
- Complex operational tooling

**Current Approach:**
- Deploy single JAR to server
- Simple Docker image if needed
- No orchestration complexity

**When to Add Kubernetes:**
- If deploying to cloud (AWS, GCP, Azure)
- If need auto-scaling based on load
- If manage multiple microservices
- Requires DevOps expertise and operational cost

### Why No GraphQL?

- REST API appropriate for simple CRUD operations
- No complex nested queries in requirements
- JSON responses simpler to cache/optimize
- GraphQL overhead not justified

### Why No Frontend?

- Assignment specifies API-only
- Frontend is separate concern (different repo)
- REST API can serve any client (web, mobile, CLI)
- Interview focuses on backend architecture

## 14. Future Architecture Improvements

The following improvements are **NOT implemented today** but are candidates for future work:

### Priority 1: Production Readiness

1. **Environment-Based Configuration**
   - Move database credentials to environment variables
   - Use Spring Cloud Config or similar for configuration management
   - Remove all secrets from application.yml

2. **Health Checks & Monitoring**
   - Add custom health indicator for database connectivity
   - Expose Prometheus metrics for monitoring
   - Add graceful shutdown hooks

3. **Logging & Observability**
   - Implement structured logging (JSON format)
   - Add distributed tracing (Sleuth, Jaeger)
   - Centralized log aggregation (ELK, Splunk)

4. **Rate Limiting**
   - Add rate limiting by IP address or API key
   - Prevent abuse (unlimited URL creation, redirect spam)
   - Return HTTP 429 (Too Many Requests)

5. **Authentication & Authorization**
   - Implement API key or OAuth2
   - Require authentication for DELETE endpoint
   - Support role-based access (admin vs user)

### Priority 2: Performance Optimization

1. **Analytics Query Optimization**
   - Replace in-memory MAX(clicked_at) with native SQL query
   - Reduce memory footprint for high-volume analytics

2. **Asynchronous Click Recording**
   - Decouple click recording from redirect response
   - Use message queue (Kafka) or async task queue
   - Improves redirect latency, enables retries

3. **Caching**
   - Redis cache for frequently accessed short URLs
   - Cache invalidation strategy (TTL, event-based)
   - Reduce database load

4. **Database Optimization**
   - Read replicas for analytics queries
   - Connection pooling tuning (HikariCP settings)
   - Query optimization (execution plans)

### Priority 3: Data Management

1. **Expired URL Cleanup Job**
   - Background job to hard-delete or archive expired URLs
   - Prevent database bloat
   - Optional: Move expired URLs to archive table

2. **Click Event Retention Policy**
   - Define retention period (e.g., keep 1 year of click data)
   - Archive old events to separate storage
   - Implement pagination for analytics

3. **Analytics Aggregation**
   - Pre-compute hourly/daily analytics
   - Time-series database for analytics (InfluxDB)
   - Reduce query latency for historical data

### Priority 4: Features

1. **Custom Short Codes**
   - Allow users to specify their own short codes
   - Validate uniqueness
   - Premium feature (user authentication required)

2. **QR Code Generation**
   - Generate QR code image for short URL
   - Return as PNG or SVG
   - Embed in dashboard (frontend)

3. **Analytics Filtering**
   - Filter clicks by date range
   - Group by User-Agent, Referer
   - Time-series analytics
   - Pagination support

4. **URL Preview**
   - Optional: Validate destination URL is accessible
   - Requires HTTP call (SSRF risk; needs validation)

5. **Bulk URL Creation**
   - Accept multiple URLs in single request
   - Return list of short codes
   - Improved user experience

### Priority 5: Scalability

1. **Microservices**
   - Separate URL creation/analytics service from redirect service
   - Allow independent scaling
   - Requires async communication

2. **Event-Driven Architecture**
   - Publish URL created/deleted events
   - Multiple subscribers (analytics, notifications)
   - Enables future features (webhooks, integrations)

3. **Global CDN**
   - Replicate redirect service globally
   - Reduce latency for international users
   - Requires synchronization strategy

4. **Data Warehouse**
   - Extract click analytics to data warehouse
   - Enable complex BI queries
   - Separate analytics from operational database

---

## Summary

The URL Shortener is built as a **modular monolith** with clear layered architecture:

- **Today:** Clean, maintainable Spring Boot application with REST API, JPA data access, and synchronized business logic. Appropriate for interview prototype.

- **Tomorrow:** As traffic grows, add async processing, caching, and eventually microservices. Current design provides foundation for scaling without major refactoring.

The architecture prioritizes simplicity, correctness, and maintainability over premature optimization or over-engineering. This approach demonstrates production-quality thinking while remaining appropriate for a one-day interview assignment.

