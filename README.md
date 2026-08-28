# URL Shortener

A production-oriented REST API service for creating and managing shortened URLs with click analytics, expiration, and disable controls.

## Features

- **Create shortened URLs** — Convert long URLs to compact 8-character Base62 identifiers
- **HTTP redirects** — 302 redirect to original URL with automatic click recording
- **Click analytics** — Track total clicks, unique visitors (by IP hash), and last click time
- **URL lifecycle management** — Support expiration dates and manual disabling
- **Input validation** — Restrict to HTTP/HTTPS schemes; reject malicious URLs (javascript:, data:, file:)
- **Production features** — Transactional consistency, input validation, error handling, logging

## Architecture

**Layered monolith:** Controller → Service → Repository → PostgreSQL

**Key Components:**
- `UrlController` — 5 REST endpoints (POST create, GET redirect, GET details, DELETE disable, GET analytics)
- `UrlShortenerService` — Business logic for creation, redirect, analytics
- `ShortCodeGenerator` — Cryptographically secure Base62 generation (62^8 ≈ 218 trillion combinations)
- `IpHasher` — SHA-256 hashing of client IPs (one-way, privacy-preserving)
- `GlobalExceptionHandler` — Consistent error responses with proper HTTP semantics

**Design highlights:**
- Collisions handled via database UNIQUE constraint + 5-retry mechanism
- Click recording in same transaction as redirect (consistency)
- No raw IP storage; all IP-derived data is hashed
- Expiration checked at redirect time (lazy evaluation)

See [docs/architecture.md](docs/architecture.md) for details.

## Technology Stack

| Component | Version |
|-----------|---------|
| Java | 21 |
| Spring Boot | 3.3.0 |
| PostgreSQL | 15 |
| JUnit 5 | Latest (via parent) |
| Testcontainers | 1.19.3 |
| Maven | 3.x |

## Project Structure

```
src/main/java/com/vishwasena/urlshortener/
├── controller/     UrlController (5 endpoints)
├── service/        UrlShortenerService (business logic)
├── repository/     JPA repositories
├── entity/         ShortUrl, ClickEvent
├── dto/            CreateUrlRequest, CreateUrlResponse, AnalyticsResponse
├── exception/      Custom exceptions + GlobalExceptionHandler
└── util/           ShortCodeGenerator, UrlValidator, IpHasher, ClientIpExtractor

src/main/resources/
├── application.yml (Spring config + Springdoc OpenAPI)
└── db/migration/   Flyway migrations (schema + initial data)
```

## API Overview

### Endpoints

| Method | Path | Purpose | Request | Response |
|--------|------|---------|---------|----------|
| POST | `/api/v1/urls` | Create short URL | `{original_url, expires_at?}` | `{id, short_code, original_url, short_url}` |
| GET | `/{shortCode}` | Redirect | — | 302 to original URL |
| GET | `/api/v1/urls/{id}` | Get details | — | `ShortUrl` (entity) |
| DELETE | `/api/v1/urls/{id}` | Disable URL | — | 204 No Content |
| GET | `/api/v1/urls/{id}/analytics` | Get analytics | — | `{total_clicks, unique_visitors, last_clicked_at}` |

### HTTP Status Codes

| Code | Scenario |
|------|----------|
| 201 | Short URL created |
| 200 | Short URL already exists / Get success |
| 204 | URL disabled |
| 302 | Redirect to original URL |
| 400 | Invalid input (validation failed) |
| 404 | Short code or URL not found |
| 405 | HTTP method not supported |
| 409 | URL conflict |
| 410 | URL expired or disabled |
| 500 | Internal server error |

### Interactive API Documentation

- **Swagger UI:** http://localhost:8080/swagger-ui.html
- **OpenAPI JSON:** http://localhost:8080/api/v3/api-docs
- **OpenAPI YAML:** http://localhost:8080/api/v3/api-docs.yaml

See [docs/architecture.md](docs/architecture.md) for full API spec.

## Database

**PostgreSQL 15** with Flyway migrations.

**Tables:**

```sql
short_url (id, short_code, original_url, status, created_at, updated_at, expires_at, click_count)
click_event (id, short_url_id, clicked_at, ip_hash, user_agent, referer)
```

**Indexes:** short_code (UNIQUE), status, expires_at, short_url_id, clicked_at

**Key properties:**
- `status`: ACTIVE | DISABLED
- `ip_hash`: SHA-256 one-way hash (no raw IP storage)
- `click_count`: Incremented on each redirect

See [docs/architecture.md](docs/architecture.md) for schema details.

## Local Setup

### Prerequisites

- Java 21
- Maven 3.x
- Docker and Docker Compose
- Port 8080 and 5432 available

### Steps

1. Clone repository:
   ```bash
   git clone <repo>
   cd url-shortener
   ```

2. Build:
   ```bash
   mvn clean install
   ```

3. Start services:
   ```bash
   docker-compose up --build
   ```

4. Application is ready at:
   - API: http://localhost:8080
   - Health: http://localhost:8080/actuator/health
   - Swagger UI: http://localhost:8080/swagger-ui.html

5. Stop services:
   ```bash
   docker-compose down
   ```

## Docker Setup

**docker-compose.yml** includes:
- Java application (port 8080)
- PostgreSQL 15 (port 5432)
- Named volume for persistent data

**Quick commands:**
```bash
docker-compose up -d                    # Start in background
docker-compose logs -f url-shortener    # Follow logs
docker-compose exec url-shortener bash  # Shell into app container
docker-compose down                     # Stop and remove containers
```

See root `docker-compose.yml` for details.

## Testing

### Test Coverage

- **90+ automated tests** (100% pass rate)
- Unit tests for business logic
- Integration tests with Testcontainers (real PostgreSQL)
- End-to-end API tests
- Concurrency tests
- Error scenario tests

### Run Tests

```bash
mvn clean test                  # Run all tests
mvn -Pall-tests clean test     # Include PostgreSQL integration tests (requires Docker)
```

For detailed testing strategy, see [docs/testing.md](docs/testing.md).

## Documentation

| Document | Purpose |
|----------|---------|
| [docs/requirements.md](docs/requirements.md) | Functional/non-functional requirements, acceptance criteria |
| [docs/architecture.md](docs/architecture.md) | System design, components, API, database, trade-offs |
| [docs/testing.md](docs/testing.md) | Testing strategy, test categories, critical scenarios |
| [docs/security.md](docs/security.md) | URL validation, SSRF prevention, input validation, error handling |
| [docs/AI_ENGINEERING_LOG.md](docs/AI_ENGINEERING_LOG.md) | How AI assisted engineering while engineer retained ownership |
| [docs/scenarios/greenfield.md](docs/scenarios/greenfield.md) | MVP implementation lifecycle and decisions |
| [docs/scenarios/brownfield.md](docs/scenarios/brownfield.md) | Scaling scenario analysis |
| [docs/scenarios/ambiguous.md](docs/scenarios/ambiguous.md) | How ambiguous requirements were clarified (analytics example) |

## Known Limitations

1. **Single-instance deployment** — No distributed caching or replication
2. **Synchronous click recording** — Analytics writes are in-band with redirects (acceptable for MVP)
3. **No authentication** — Public API (suitable for MVP)
4. **IP-based unique visitor counting** — Not fully accurate behind proxies
5. **Fixed short-code length** — 8 characters; no configuration option
6. **No custom short codes** — Only system-generated codes

## Future Improvements

- Asynchronous click recording (separate queue)
- Distributed caching layer (Redis)
- Custom short-code option
- Bulk URL creation
- User authentication and per-user URL management
- Advanced analytics (geographic, device type, referrer analysis)
- Configurable short-code length and alphabet
- Rate limiting and API quotas
- URL preview endpoint (safe browsing check)

## Quality Assurance

| Gate | Status |
|------|--------|
| Maven compilation | ✅ PASS |
| All 90 tests | ✅ PASS |
| Docker build | ✅ PASS |
| Security review | ✅ PASS (no critical issues) |
| Reliability analysis | ✅ PASS (acceptable for MVP) |

See [docs/testing.md](docs/testing.md) and [docs/security.md](docs/security.md) for details.

## Interview Demonstration

1. **Show Swagger UI** — http://localhost:8080/swagger-ui.html (interactive API docs)
2. **Create short URL** — POST /api/v1/urls with example
3. **View redirect** — GET /{shortCode}
4. **Check analytics** — GET /api/v1/urls/{id}/analytics
5. **Error handling** — Test 404, 410, 400 responses
6. **Code review** — Discuss architecture, design decisions, trade-offs

See [docs/scenarios/greenfield.md](docs/scenarios/greenfield.md) for complete implementation walkthrough.

## Assessment Evidence

| Requirement | Documentation |
|-------------|-----------------|
| AI-assisted engineering | [docs/AI_ENGINEERING_LOG.md](docs/AI_ENGINEERING_LOG.md) |
| Requirements clarification | [docs/requirements.md](docs/requirements.md), [docs/scenarios/ambiguous.md](docs/scenarios/ambiguous.md) |
| Architecture decisions | [docs/architecture.md](docs/architecture.md) |
| Security | [docs/security.md](docs/security.md) |
| Testing | [docs/testing.md](docs/testing.md) |
| Implementation | Source code + passing tests |

## License

MIT
