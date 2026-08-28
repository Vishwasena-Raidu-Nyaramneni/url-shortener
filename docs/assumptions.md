# URL Shortener — Assumptions

This document captures assumptions made during the design and implementation of the URL Shortener prototype. These are decisions made when the requirements were ambiguous or silent on specific behavior.

## Assumption 1: Short-Code Length

### Context

The assignment specifies "Generate a unique short code" but does not specify length. Short-code length directly impacts:
- URL collision probability (longer = lower probability)
- User experience (shorter = more memorable)
- Storage efficiency
- DNS name limitations

### Decision

Implemented **8-character Base62 short codes**.

**Base62 Alphabet:** a-z, A-Z, 0-9 (62 characters)

**Example:** BcD1234, Xyz98765

### Rationale

- **8 characters provides ~218 trillion unique combinations** (62^8 ≈ 2.18 × 10^14)
- Sufficient for MVP scope (millions of short URLs)
- Balances memorability (not too long) vs uniqueness (not too short)
- Industry standard (bit.ly, TinyURL use similar lengths)
- Base62 is URL-safe (no special characters that need encoding)

### Alternatives Considered

| Length | Combinations | Use Case | Trade-offs |
|--------|-------------|----------|-----------|
| 4 chars | 1.5M | Very short URLs | High collision rate, impractical |
| 6 chars | 56B | Balance | Still acceptable but smaller margin |
| **8 chars** | **218T** | **MVP** | **Recommended: good balance** |
| 10 chars | 13.8Q | Ultra-reliable | Longer to type, overkill for MVP |

### Impact

- Collision probability is extremely low (max 5 retries handles all practical cases)
- URLs are reasonably short and memorable
- No special URL encoding needed
- Future enhancement: allow custom length via configuration

---

## Assumption 2: Short-Code Collision Handling

### Context

With random generation and large namespace, collisions are theoretically possible but extremely rare. The system must handle them gracefully without:
- Silently failing (losing data)
- Blocking indefinitely (retry forever)
- Exposing internal errors to users

### Decision

Implemented **max 5 retry attempts** on collision.

**Behavior:**
- Generate new short code using SecureRandom
- Attempt database insert
- If unique constraint violation: retry (up to 5 times)
- If 5th attempt also fails: return HTTP 500 with error message

### Rationale

- **5 retries provides extremely high success probability:**
  - Probability of 5 consecutive collisions: (1/218T)^5 ≈ essentially zero
  - Covers worst-case scenarios (bad random seed, etc.)
- **Fail-fast approach:**
  - If 5 retries fail, something is wrong (not just bad luck)
  - Better to alert operator than retry forever
- **Balances reliability with simplicity**
  - Simple logic (not a complex retry backoff)
  - Acceptable latency (max 5 DB round trips)

### Alternatives Considered

| Approach | Pros | Cons |
|----------|------|------|
| No retries (fail immediately) | Fast, deterministic | Unacceptable failure rate |
| Infinite retries | Eventually succeeds | Can hang indefinitely, denial of service |
| **5 retries** | **Balanced, practical** | **May fail with extremely rare bad luck** |
| Exponential backoff | Reduces lock contention | Adds complexity, latency |
| Sequential short codes | Deterministic, no collision | Predictable, security risk |

### Impact

- Users will see HTTP 500 only if collision rate is much worse than expected
- Acceptable for MVP (will never happen in practice)
- Production should monitor collision metrics
- Future: implement more sophisticated retry logic if metrics show collisions

---

## Assumption 3: URL Expiration is Soft (Checked at Access Time)

### Context

The system supports optional URL expiration, but the requirement does not specify:
- When expired URLs are deleted
- What happens to analytics of expired URLs
- Whether expired URLs can be re-enabled
- Storage implications of keeping expired URLs

### Decision

Implemented **soft expiration**: URLs remain in database indefinitely; expiration is checked only at redirect time.

**Behavior:**
- expires_at timestamp stored but never deleted
- On redirect attempt: check if expires_at < now()
- If expired: return HTTP 410 (Gone)
- No background job cleans up expired URLs

### Rationale

- **Simpler implementation** (no scheduled jobs, no cron complexity)
- **Preserves audit trail** (can query when URL expired, how many clicks before expiration)
- **Consistent with soft-delete pattern** (disable also uses soft update)
- **Lower operational complexity** for MVP
- **Allows future features** (re-enable, analytics history)

### Alternatives Considered

| Approach | Pros | Cons |
|----------|------|------|
| **Soft expiration (current)** | **Simple, preserves history** | **Database grows; no cleanup** |
| Hard deletion | Reduces storage | Loses analytics, audit trail |
| Archive to separate table | Balances both | Adds complexity, need two queries |
| Scheduled cleanup job | Manages storage | Operational burden, potential data loss |

### Impact

- Database will accumulate expired URLs indefinitely
- Not a problem for MVP (thousands of URLs)
- Future: implement retention policy and cleanup job
- **Requires future product decision:** Should old URLs be archived or deleted?

---

## Assumption 4: Redirect Uses HTTP 302 (Temporary Redirect)

### Context

Assignment specifies "Use HTTP 302 for the MVP" but does not explain the rationale. HTTP 302 (Found) is temporary; HTTP 301 (Moved Permanently) is permanent.

### Decision

Implemented **HTTP 302 (Found)** for all redirects.

### Rationale

- **Allows URL updates without browser cache issues**
  - HTTP 301 causes browsers to cache permanently (bypass service)
  - HTTP 302 allows service to change destination URL without user cache invalidation
- **Safer default** (temporary is more flexible than permanent)
- **Matches service semantics** (short URL → mutable mapping to destination)
- **Assignment requirement**

### Alternatives Considered

| Code | Name | Behavior | Use Case |
|------|------|----------|----------|
| 301 | Moved Permanently | Browser caches result | When URL destination will never change |
| **302** | **Found (temporary)** | **Browser re-queries each time** | **Mutable mappings (our case)** |
| 303 | See Other | POST → GET redirect | For form submissions (not applicable) |
| 307 | Temporary Redirect | Preserves HTTP method | For method-sensitive redirects (not applicable) |

### Impact

- Users must accept that service can update where short URLs point
- Slightly higher latency (browser always queries service, no cache)
- Prevents accidental cache issues
- If future requirement is "never change URLs," can change to 301

---

## Assumption 5: Unique Visitors = Distinct IP Hashes

### Context

Analytics requirement specifies "unique visitors based on IP hash" but does not define "unique":
- Same person, different IP → counted as different visitor?
- Same IP, different person (shared network) → counted as one visitor?
- Should time window matter (same IP, different day)?

### Decision

Implemented **unique visitor = COUNT(DISTINCT ip_hash)** regardless of time.

**Behavior:**
- Hash client IP to SHA-256
- Count distinct hashes for a URL
- Result: "X unique visitors" (based on distinct IPs)

### Rationale

- **Simple calculation** (single SQL COUNT(DISTINCT))
- **Deterministic** (same IP always produces same hash)
- **Privacy-friendly** (no device tracking, cookies, or user accounts)
- **Appropriate for MVP** (good enough for analytics without complexity)
- **Time-independent** (visitor count doesn't degrade over time)

### Alternatives Considered

| Definition | Pros | Cons | Complexity |
|-----------|------|------|-----------|
| Distinct IP (current) | Simple, GDPR-friendly | Shared networks look like one user | ★☆☆ |
| Time-windowed (daily, weekly) | More granular | Need aggregation jobs | ★★★ |
| Cookies or sessions | True visitor identification | Privacy concerns, requires frontend | ★★★★ |
| User accounts | Accurate, trackable | Requires auth, not MVP scope | ★★★★★ |
| User-Agent + IP | Better differentiation | More complex, still imperfect | ★★☆ |

### Impact

- Shared networks (office WiFi, school) count as single visitor
- VPN users with same exit IP show as one visitor
- Analytics are approximate, not precise
- **Requires future product decision:** Should unique visitors be time-windowed or per-session?

---

## Assumption 6: IP Address Hashing is Deterministic (Not Per-Session)

### Context

IP hashing is used to preserve privacy while enabling unique visitor counts. But hash strategy affects behavior:
- Deterministic hash: same IP always produces same hash (across time, requests)
- Random salt: each hash is different (can't track repeating visitors)

### Decision

Implemented **deterministic SHA-256 hashing** (no salt).

**Behavior:**
`java
ipHash = SHA256(ipAddress) // Same IP → same hash every time
`

### Rationale

- **Enables unique visitor counting** (same IP over time = same hash = recognized as repeat visitor)
- **Reproducible** (easier to debug, test, verify)
- **No salt needed** for MVP (salt adds complexity without clear benefit)
- **One-way hashing** preserves privacy (cannot recover IP from hash)

### Alternatives Considered

| Approach | Pros | Cons |
|----------|------|------|
| **Deterministic (current)** | **Simple, enables repeat visitor detection** | **Less privacy if hash leaked** |
| Random salt | Better privacy | Cannot detect repeat visitors, hash doesn't persist |
| Per-session salt | Time-based uniqueness | Complex, needs session table |
| Salted with URL ID | Prevents cross-URL tracking | Still complex, minimal privacy gain |

### Impact

- Same IP hashing to same value across all requests/time
- Able to count "repeat visitors" (same IP, multiple clicks)
- If IP hash is compromised, attacker can identify users
- **Requires future product decision:** Should hash include salt for additional privacy?

---

## Assumption 7: Client IP Extracted from Headers (Trusts Proxy)

### Context

Client IP can come from multiple sources:
- Direct connection: equest.getRemoteAddr()
- Behind proxy/load balancer: X-Forwarded-For or X-Real-IP headers
- Headers can be spoofed if not validated

### Decision

Implemented **header-based extraction with fallback**:

`java
1. Try header "X-Forwarded-For" (take first IP if multiple)
2. Try header "X-Real-IP"
3. Fallback to request.getRemoteAddr()
`

### Rationale

- **Supports proxied deployments** (load balancers, CDNs)
- **Fallback ensures robustness** (works even without proxy headers)
- **Standard pattern** (most frameworks do this)
- **Appropriate for MVP** (assumes trusted proxy environment)

### Alternatives Considered

| Approach | Pros | Cons | Trust Required |
|----------|------|------|-----------------|
| Always use request.getRemoteAddr() | Simple, no spoofing | Fails behind proxies | Only direct conn |
| **Header-based with fallback (current)** | **Flexible, works with proxies** | **Can be spoofed if untrusted** | **Trusted proxy env** |
| Validate proxy config | Most secure | Complex Spring Security setup | Strict validation |
| Ignore IP entirely | Zero spoofing risk | Can't track visitors | No tracking |

### Impact

- Works in proxied deployments (cloud, load balancers)
- Assumes environment is configured to trust proxy headers
- **SECURITY RISK (MEDIUM):** Headers can be spoofed if not behind trusted proxy
- Production deployment should validate proxy configuration
- **Requires future decision:** Add proxy validation or use stricter IP extraction?

---

## Assumption 8: Timestamps Use OffsetDateTime (Timezone-Aware)

### Context

Timestamps must store both date/time and timezone information for:
- Expiration checks across time zones
- Analytics queries spanning day boundaries
- Audit trail clarity

### Decision

Implemented **OffsetDateTime** (timezone-aware, with offset).

**Behavior:**
`java
OffsetDateTime.now()  // Includes timezone offset
// Example: 2025-01-15T14:30:00+00:00 (UTC)
// Example: 2025-01-15T14:30:00-05:00 (EST)
`

**Database Storage:** TIMESTAMP WITH TIME ZONE (PostgreSQL)

### Rationale

- **Timezone-aware** (not ambiguous across regions)
- **Correct for expiration checks** (prevents off-by-one hour errors)
- **Immutable timezone offset** (doesn't adjust for DST)
- **Standard in Java** (java.time API recommended)

### Alternatives Considered

| Type | Pros | Cons |
|------|------|------|
| LocalDateTime | Simple, human-readable | Ambiguous without timezone |
| Instant | Universal, immutable | Requires conversion to user timezone |
| **OffsetDateTime (current)** | **Clear, timezone-aware, immutable** | **Slightly more verbose** |
| UNIX timestamp (long) | Storage efficient | Loses readability, requires conversion |

### Impact

- Expiration checks are correct across time zones
- Stored with full timezone offset (no DST confusion)
- API accepts ISO 8601 format: 2025-12-31T23:59:59+00:00
- Clients must handle timezone offset in requests
- No assumption needed: clearly specified in implementation

---

## Assumption 9: Error Responses Include Status Code and Message

### Context

Error handling must be consistent and informative without exposing sensitive details:
- Clients need enough info to handle errors
- Internal stack traces should not be exposed
- Error response format is not specified

### Decision

Implemented **consistent JSON error response**:

`json
{
  "status": 400,
  "message": "Invalid URL: javascript:alert('xss')",
  "timestamp": 1705329600000
}
`

- **status:** HTTP status code (400, 404, 410, 500)
- **message:** Human-readable error description
- **timestamp:** Millisecond timestamp for debugging

### Rationale

- **Standard format** (consistent across all errors)
- **Sufficient for debugging** (timestamp helps find logs)
- **Security** (no stack traces exposed to clients)
- **Simplicity** (no nested error objects)

### Alternatives Considered

| Format | Pros | Cons |
|--------|------|------|
| Just HTTP status | Minimal, fast | No context for client |
| {error: "message"} | Simple | No timestamp, no status code |
| **{status, message, timestamp} (current)** | **Informative, consistent** | **More verbose than minimal** |
| RFC 7807 Problem Details | Standard format | More complex, not all clients support |

### Impact

- All errors have same structure (easier for clients)
- Timestamp helps correlate with server logs
- Message may expose some implementation details (mitigation: generic messages for 500)
- Future: Consider RFC 7807 if API grows complex

---

## Assumption 10: URL Scheme Whitelist (Only HTTP/HTTPS)

### Context

Users can provide any URL as input. Allowing arbitrary schemes risks:
- javascript: → XSS (if URL echoed in HTML)
- data: → Data URI exploitation
- ile: → Local file access
- tp: → Unintended protocol

### Decision

Implemented **scheme whitelist: only HTTP and HTTPS allowed**.

**Behavior:**
`java
// Accepted
createShortUrl("https://example.com/path") → ✅ OK
createShortUrl("http://example.com/path")  → ✅ OK

// Rejected
createShortUrl("javascript:alert('xss')") → ❌ HTTP 400
createShortUrl("data:text/html,<h1>Hi</h1>") → ❌ HTTP 400
createShortUrl("file:///etc/passwd") → ❌ HTTP 400
`

### Rationale

- **Security** (prevents malicious URL schemes)
- **Clarity** (service focused on web URLs, not arbitrary URIs)
- **Simplicity** (one validation rule)
- **Matches user expectation** (URL shortener ≈ web URLs)

### Alternatives Considered

| Approach | Pros | Cons |
|----------|------|------|
| Allow any scheme | Flexible | Major security risk |
| **Whitelist HTTP/HTTPS (current)** | **Secure, clear, simple** | **Cannot shorten other protocols** |
| Whitelist HTTP/HTTPS + FTP | More protocols | FTP may not be needed |
| Dynamic whitelist (config) | Flexible | Over-engineering for MVP |

### Impact

- Cannot shorten FTP, mailto, telnet, or other URLs
- Protects users from XSS if they paste URLs in comments/emails
- Clear boundary on supported URL types
- Future: add HTTPS enforcement (disable HTTP)

---

## Assumption 11: Database Choice is PostgreSQL

### Context

The assignment specifies "PostgreSQL" but doesn't explain why. Alternative databases have different trade-offs:
- PostgreSQL: Feature-rich, ACID, JSON support
- MySQL: Popular, simpler
- SQLite: No setup, good for dev
- NoSQL: Different consistency model

### Decision

Implemented **PostgreSQL as specified**.

**Version:** Latest available (via Maven driver)

**Dialect:** org.hibernate.dialect.PostgreSQLDialect

### Rationale

- **Assignment requirement** (specified in tech stack)
- **ACID guarantees** (transactions are reliable)
- **Strong type safety** (schema validation)
- **Scalability** (handles millions of rows efficiently)
- **JSON support** (could store analytics in future)
- **Widely used** (production databases use this)

### Alternatives Considered

| Database | Pros | Cons | Why Not |
|----------|------|------|---------|
| PostgreSQL (current) | Feature-rich, ACID | Setup required | ✅ Chosen |
| MySQL | Popular, simpler setup | Less feature-rich | Not specified |
| SQLite | Zero setup, file-based | Not suitable for scale | Dev only |
| MongoDB | Flexible schema | No ACID transactions | Different model |

### Impact

- Requires PostgreSQL running (docker-compose provided)
- Flyway migrations manage schema
- ACID transactions guarantee consistency
- Not assumed: DB is implementation choice, not assumption

---

## Assumption 12: No Authentication Required for MVP

### Context

The assignment specifies "no authentication unless specifically requested." But APIs typically need auth:
- Who can create URLs?
- Who can delete URLs?
- Who can view analytics?

### Decision

Implemented **no authentication**. All endpoints are publicly accessible.

**Behavior:**
- Anyone can POST to /api/v1/urls (create)
- Anyone can GET /{shortCode} (redirect)
- Anyone can GET /api/v1/urls/{id} (retrieve)
- Anyone can DELETE /api/v1/urls/{id} (disable)
- Anyone can GET /api/v1/urls/{id}/analytics (analytics)

### Rationale

- **Assignment requirement** ("no authentication unless requested")
- **Simplifies MVP** (no identity management, JWT, OAuth)
- **Matches demo scenario** (interview prototype doesn't need security)
- **Clear decision point** (easy to add auth later)

### Alternatives Considered

| Approach | Pros | Cons |
|----------|------|------|
| No auth (current) | Simple, fast to implement | Anyone can delete any URL |
| API key | Simple auth, no sessions | Still need key distribution |
| OAuth2 | Industry standard, flexible | Significant complexity |
| Basic Auth | Simple | Credentials in every request |

### Impact

- Public API (anyone can use)
- No user accounts or ownership
- Anyone can delete any short URL
- Analytics visible to everyone
- **SECURITY RISK (MEDIUM):** Requires future authentication
- **Requires future product decision:** What auth model? (API key, OAuth, SSO)

---

## Assumption 13: No Rate Limiting for MVP

### Context

Without rate limiting, service is vulnerable to abuse:
- Unlimited URL creation (storage exhaustion)
- Unlimited redirects (DOS attack)
- Unlimited analytics queries (CPU exhaustion)

### Decision

Implemented **no rate limiting**. No request throttling or abuse prevention.

### Rationale

- **Assignment specifies MVP scope** (focuses on core functionality)
- **Simpler codebase** (reduces complexity)
- **Interview setting** (trusted environment, not production)
- **Clear future task** (rate limiting is distinct feature)

### Alternatives Considered

| Approach | Pros | Cons |
|----------|------|------|
| No rate limiting (current) | Simple, clear MVP scope | Vulnerable to abuse |
| IP-based rate limiting | Prevents basic DOS | Easy to bypass with proxy |
| API key + quota | Fair, trackable | Requires auth and key distribution |
| Global rate limiting | Simple threshold | Affects all users equally |

### Impact

- Service can accept unlimited requests from single client
- No protection against DOS attacks
- Storage not protected from URL creation spam
- **SECURITY RISK (MEDIUM):** Requires future rate limiting
- **Requires future product decision:** Rate limiting strategy? (IP-based, API key, adaptive)

---

## Assumption 14: Synchronous Click Recording (Not Asynchronous)

### Context

Click recording adds latency to redirect response:
- Insert ClickEvent row
- Update click_count
- Increment timestamps

Synchronous recording means redirect waits for all database writes.

### Decision

Implemented **synchronous click recording** in same HTTP request.

**Behavior:**
1. Receive redirect request
2. Look up short URL
3. Record click (insert event + update count) ← in same transaction
4. Return HTTP 302 redirect

### Rationale

- **Consistency guarantee** (click always recorded if redirect returns)
- **Simpler code** (no async complexity, no message queue)
- **ACID transaction** (all-or-nothing: either fully recorded or fails)
- **Appropriate for MVP** (modest traffic doesn't require async)

### Alternatives Considered

| Approach | Pros | Cons |
|----------|------|------|
| **Synchronous (current)** | **Consistent, simple, ACID** | **Latency impact, scales poorly** |
| Async with message queue | Low latency, scales | Complex, eventual consistency |
| Cache-then-flush | Batches updates | Data loss risk, approximate counts |
| Asynchronous job | Decouples latency | Delayed analytics, potential data loss |

### Impact

- Redirect latency includes database write time
- Every click is guaranteed to be recorded
- At high traffic (1000+ req/sec), database writes become bottleneck
- Analytics always accurate (no eventual consistency issues)
- **Requires future decision:** Should click recording be async at scale?

---

## Assumption 15: No Caching Layer (Direct Database Queries)

### Context

Short URLs are read frequently (on every redirect). Caching could reduce database load:
- Redis cache for URL lookups
- In-memory cache for hot URLs
- Database query cache

But caching adds complexity:
- Cache invalidation (when URL is updated/disabled)
- Cache consistency (what if multiple servers)
- Operational overhead (another service)

### Decision

Implemented **no caching**. All queries go directly to database.

**Behavior:**
- Every redirect queries database for short_url
- Every analytics request queries for click_event
- No in-memory or Redis cache

### Rationale

- **Simplicity** (one less thing to configure/debug)
- **Consistency** (no cache invalidation issues)
- **Correctness** (every read gets current data)
- **MVP appropriate** (thousands of requests/sec don't require cache)
- **Database handles caching** (PostgreSQL has query cache, OS has page cache)

### Alternatives Considered

| Approach | Pros | Cons |
|----------|------|------|
| No caching (current) | Simple, consistent | Higher database load |
| Redis cache | Fast reads, scalable | Cache invalidation, operational overhead |
| Guava/Caffeine cache | In-process, simple | Single-server only, memory usage |
| Database query cache | Free, implicit | Limited control, database-dependent |

### Impact

- Database is primary bottleneck for high traffic
- Consistent reads (no stale cache)
- Simpler architecture (fewer moving parts)
- **Requires future decision:** When to add caching? (performance profiling needed)

---

## Assumption 16: Monolithic Deployment (Not Microservices)

### Context

Service could be split into microservices:
- URL creation service
- Redirect service (high traffic)
- Analytics service (queries)
- Admin service (delete/disable)

But microservices add complexity:
- Inter-service communication (latency, failures)
- Distributed transactions (eventual consistency)
- Operational complexity (multiple deployments)
- Data consistency challenges

### Decision

Implemented **single monolithic Spring Boot application**.

**Behavior:**
- All features in one JAR
- Single deployment unit
- Shared PostgreSQL database

### Rationale

- **Simplicity** (one service to deploy, monitor, debug)
- **Consistency** (ACID transactions across features)
- **Interview appropriate** (demonstrates clean architecture, not premature optimization)
- **Clear separation of concerns** (layers: controller, service, repo) without microservices overhead

### Alternatives Considered

| Architecture | Pros | Cons |
|--------------|------|------|
| **Monolith (current)** | **Simple, consistent, deployable** | **Cannot scale independently** |
| Microservices | Scale features separately | Operational complexity, distributed transactions |
| Serverless (Lambda) | Auto-scaling | Vendor lock-in, cold starts, limited languages |

### Impact

- All features scale together (cannot isolate high-traffic redirects)
- Consistent ACID transactions possible
- Single failure point (whole service goes down)
- Future: Refactor to microservices if needed
- **Not an assumption: clear design decision**

---

## Assumption 17: No Kubernetes/Container Orchestration

### Context

Service could be deployed to Kubernetes for:
- Auto-scaling based on load
- Rolling updates
- Service discovery
- Health checks and recovery

But Kubernetes adds operational complexity.

### Decision

Implemented **single JAR deployment** without Kubernetes.

**Behavior:**
- Maven builds JAR
- Docker image provided (manual)
- Deploy to single server or simple orchestration

### Rationale

- **MVP scope** (focus on code, not infrastructure)
- **Interview setting** (infrastructure not primary concern)
- **Simpler operations** (one server easier than K8s cluster)

### Alternatives Considered

| Approach | Pros | Cons |
|----------|------|------|
| **Single JAR (current)** | **Simple, fast deployment** | **Manual scaling, limited HA** |
| Docker + Docker Compose | Simple orchestration | Single-machine, no cloud scaling |
| Kubernetes | Auto-scaling, cloud-native | Operational complexity |
| Serverless (Lambda) | Auto-scaling, no ops | Vendor lock-in, different model |

### Impact

- Manual scaling (add more servers, load balance)
- No auto-recovery from crashes
- Simpler to understand and deploy
- Production should add health checks and monitoring
- **Requires future decision:** Deploy to Kubernetes if scale requires it

---

## Assumption 18: Flyway Handles Database Migrations

### Context

Database schema must evolve as features are added. Migration approaches:
- Manual SQL scripts (error-prone)
- ORM auto-schema generation (risky in production)
- Migration tool (versioned, reproducible)

### Decision

Implemented **Flyway migrations** for database schema versioning.

**Configuration:**
- lyway.enabled: true in application.yml
- Migrations in src/main/resources/db/migration/
- Naming: V1__Initial_schema.sql
- Auto-runs on startup

### Rationale

- **Reproducible** (version control for schema)
- **Rollback possible** (understand schema changes)
- **Audit trail** (who changed what, when)
- **Works in CI/CD** (automatic on deployment)
- **Production standard** (widely used)

### Alternatives Considered

| Approach | Pros | Cons |
|----------|------|------|
| Manual SQL | Full control | Drift risk, no versioning |
| Hibernate ddl-auto: create-drop | Automatic | Destroys data, not production-safe |
| **Flyway (current)** | **Versioned, reversible, audit** | **More setup** |
| Liquibase | Similar to Flyway | More complex, XML-heavy |

### Impact

- Schema versioning in source control
- Migrations run automatically on startup
- Safe for production deployments
- Must coordinate schema and code changes
- **Not an assumption: clear implementation choice**

---

## Summary of Key Assumptions

| Assumption | Decision | Requires Future Clarification |
|-----------|----------|------------------------------|
| 1. Short-Code Length | 8 characters Base62 | ✅ No (sufficient for MVP) |
| 2. Collision Handling | Max 5 retries | ✅ No (extremely rare) |
| 3. Expiration Strategy | Soft (checked at access) | ⚠️ Yes (cleanup policy) |
| 4. Redirect Status | HTTP 302 (temporary) | ✅ No (appropriate for MVP) |
| 5. Unique Visitors | Distinct IP hashes | ⚠️ Yes (time-windowing) |
| 6. IP Hashing | Deterministic (no salt) | ⚠️ Yes (privacy level) |
| 7. IP Extraction | Headers + fallback | ⚠️ Yes (trust proxy config) |
| 8. Timestamps | OffsetDateTime | ✅ No (clear choice) |
| 9. Error Response | JSON with status/message | ✅ No (adequate) |
| 10. URL Schemes | HTTP/HTTPS whitelist | ✅ No (secure) |
| 11. Database | PostgreSQL | ✅ No (specified) |
| 12. Authentication | None (public API) | ⚠️ Yes (add auth) |
| 13. Rate Limiting | None | ⚠️ Yes (add limiting) |
| 14. Click Recording | Synchronous | ⚠️ Yes (async at scale) |
| 15. Caching | None (direct queries) | ⚠️ Yes (add at scale) |
| 16. Architecture | Monolith | ⚠️ Yes (split if scale) |
| 17. Orchestration | Single JAR (no K8s) | ⚠️ Yes (add if cloud) |
| 18. Migrations | Flyway | ✅ No (clear choice) |

**Key:**
- ✅ Assumption appropriate for MVP (no clarification needed)
- ⚠️ Assumption should be clarified before production

---

## Next Steps

Before production deployment, clarify these assumptions with stakeholders:

1. **Expiration Cleanup:** Should expired URLs be archived, deleted, or kept indefinitely?
2. **Analytics Precision:** Should unique visitors be time-windowed (daily, weekly) or cumulative?
3. **IP Privacy:** Should IP hash use salt for additional privacy?
4. **Proxy Configuration:** Will service run behind trusted proxy? How to validate headers?
5. **Authentication:** What auth model? (API key, OAuth, SSO)
6. **Rate Limiting:** Limit by IP, API key, or hybrid? What are rate limits?
7. **Click Recording:** Should redirect latency be independent of analytics recording (async)?
8. **Caching:** At what traffic level should caching be added?
9. **Scalability:** Is monolith acceptable for expected scale, or split to microservices?
10. **Deployment:** Cloud (Kubernetes) or traditional server?

