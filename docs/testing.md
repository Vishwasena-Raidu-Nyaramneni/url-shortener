# URL Shortener — Testing Strategy

## Overview

The URL Shortener project uses a **two-tier testing approach**:

1. **Default Tests (H2 in-memory)**: Fast, local unit and integration tests
2. **PostgreSQL Integration Tests (Docker/Testcontainers)**: Real database validation in CI/CD

This strategy balances development velocity with production-readiness.

---

## Test Architecture

### Tier 1: H2 In-Memory Tests (Default)

**Profile**: `test` (active by default)

**Configuration**: `application-test.yml`

```yaml
datasource:
  url: jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE;MODE=PostgreSQL
  driver-class-name: org.h2.Driver
jpa:
  hibernate:
    ddl-auto: create-drop
  properties:
    hibernate:
      dialect: org.hibernate.dialect.H2Dialect
flyway:
  enabled: false
```

**Characteristics**:
- ✅ Fast execution (~15-20s full suite)
- ✅ No external dependencies (Docker not required)
- ✅ Complete schema auto-creation via Hibernate
- ✅ Deterministic and isolated per test class
- ✅ Suitable for local development and CI/CD

**Coverage**:
- Unit tests: Business logic, validation, utilities
- Integration tests: API endpoints, repository queries, transactions
- Service tests: Short-code generation, collision handling, click tracking

### Tier 2: PostgreSQL Integration Tests (Docker/Testcontainers)

**Profile**: `postgres-integration`

**Configuration**: `application-postgres-integration.yml`

```yaml
datasource:
  url: jdbc:postgresql://localhost:5432/url_shortener_test
  driver-class-name: org.postgresql.Driver
jpa:
  hibernate:
    ddl-auto: validate
    dialect: org.hibernate.dialect.PostgreSQLDialect
flyway:
  enabled: true
  locations: classpath:db/migration
```

**Base Class**: `AbstractPostgresIntegrationTest`

```java
@Testcontainers
@SpringBootTest
@ActiveProfiles("postgres-integration")
public abstract class AbstractPostgresIntegrationTest {
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");
    
    @DynamicPropertySource
    static void setProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }
}
```

**Characteristics**:
- ✅ Real PostgreSQL database (latest Alpine image)
- ✅ Flyway migrations run automatically
- ✅ Validates schema and constraints match JPA entities
- ✅ Container lifecycle managed automatically
- ✅ Slower (~30-40s per test class, once image is cached)
- ⚠️ Requires Docker to be installed and running

**PostgreSQL Test Classes**:
1. `ShortUrlRepositoryPostgresIntegrationTest` (6 tests)
   - BIGSERIAL ID generation
   - Unique constraint enforcement
   - TIMESTAMP WITH TIME ZONE handling
   - CASCADE DELETE verification
   - Index performance
   - Click event ordering

2. `UrlControllerPostgresIntegrationTest` (7 tests)
   - URL creation and persistence
   - Click recording transaction
   - Expiration validation
   - Disabled URL handling
   - Analytics retrieval
   - Transactional consistency
   - Indexed query optimization

3. `FlywayMigrationPostgresIntegrationTest` (6 tests)
   - Flyway migration execution
   - Table structure validation
   - Column types verification
   - Primary/foreign key constraints
   - Unique constraints
   - Index creation

---

## Running Tests

### Option 1: Default H2 Tests (Local Development)

```bash
# Fast, requires no Docker
mvn clean test

# Result: ~59 tests pass in ~15-20 seconds
```

### Option 2: PostgreSQL Integration Tests Only

```bash
# Requires Docker running
mvn clean test -Ppostgres-integration

# Result: ~19 PostgreSQL-specific tests run against real PostgreSQL
```

### Option 3: All Tests (H2 + PostgreSQL)

```bash
# Requires Docker running
mvn clean test -Pall-tests

# Result: ~78 total tests (59 H2 + 19 PostgreSQL)
```

### Option 4: Full Build (includes integration tests)

```bash
# With Maven verify
mvn clean verify

# Runs tests, then builds artifact
```

---

## Test Exclusion Rules (pom.xml)

**Default behavior**:
```xml
<excludes>
    <exclude>**/Abstract*.java</exclude>
    <exclude>**/*Postgres*.java</exclude>
</excludes>
```

This ensures PostgreSQL tests are excluded from the default test suite unless explicitly activated via Maven profile.

---

## CI/CD Integration

### GitHub Actions Example

```yaml
name: Tests
on: [push, pull_request]
jobs:
  test:
    runs-on: ubuntu-latest
    services:
      docker:
        image: docker:dind
    steps:
      - uses: actions/checkout@v3
      - uses: actions/setup-java@v3
        with:
          java-version: 21
      - name: Run H2 Tests (Fast)
        run: mvn clean test
      - name: Run PostgreSQL Tests (with Docker)
        run: mvn clean test -Ppostgres-integration
      - name: Upload Coverage
        uses: codecov/codecov-action@v3
```

---

## Test Coverage Summary

### Current Coverage

| Test Suite | Test Count | Time (s) | Database | Flyway |
|------------|-----------|---------|----------|--------|
| H2 Unit/Integration | 59 | 15-20 | H2 in-memory | Disabled |
| PostgreSQL Integration | 19 | 30-40 | Real PostgreSQL | Enabled |
| **Total** | **78** | **60-80** | Both | Conditional |

### Coverage by Feature

#### URL Creation & Short-Code Generation
- ✅ Valid URL creation
- ✅ Short-code uniqueness (H2 + PostgreSQL)
- ✅ Collision retry logic with mocks
- ✅ BIGSERIAL ID generation (PostgreSQL)
- ✅ H2 BIGINT fallback

#### Redirect
- ✅ Valid short code redirect
- ✅ Unknown short code (404)
- ✅ Expired URL (410)
- ✅ Disabled URL (410)
- ✅ Correct Location header
- ✅ X-Forwarded-For IP extraction

#### Click Analytics
- ✅ Click count increment
- ✅ Unique visitor counting
- ✅ Last click timestamp
- ✅ Click events with IP hash, user agent, referer
- ✅ Zero-clicks edge case

#### Database Integrity
- ✅ Foreign key constraints (PostgreSQL CASCADE DELETE)
- ✅ Unique constraint on short_code
- ✅ Indexes created and available
- ✅ Timestamp with timezone handling

#### Error Handling
- ✅ Validation failures (400)
- ✅ Not found (404)
- ✅ Expired (410)
- ✅ Unexpected server errors (5xx)

---

## When to Use Each Test Type

### H2 Tests (Use for Local Development)
- Quick feedback loop
- No Docker installation required
- Adequate for business logic validation
- Good for TDD and rapid iteration
- Run before commit

### PostgreSQL Tests (Use in CI/CD)
- Production-readiness verification
- Real database behavior validation
- Flyway migration validation
- Index and constraint verification
- Run after merge or in nightly builds

---

## Known Limitations & Trade-offs

### H2 Mode: PostgreSQL
- H2 is configured with `MODE=PostgreSQL` for better compatibility
- Not all PostgreSQL-specific features are emulated (e.g., BIGSERIAL is just BIGINT)
- Some SQL dialects may behave differently

### PostgreSQL Tests
- Require Docker installation and running daemon
- Slower per-test execution (~30-40s per test class initially, ~5s after image cached)
- Pull alpine image on first run (~500MB)
- Not suitable for every local test run (use H2 for daily development)

### Testcontainers Configuration
- Container is **not reused** (`withReuse(false)`) for test isolation
- Each test class gets fresh container
- Can be configured with `withReuse(true)` for faster local iteration if needed

---

## Troubleshooting

### Docker Not Found Error

**Error**: `Could not find a valid Docker environment`

**Solution**:
```bash
# Ensure Docker Desktop is running (Windows/Mac)
# On Linux, ensure Docker daemon is running:
sudo systemctl start docker

# Run only H2 tests if Docker unavailable:
mvn clean test
```

### PostgreSQL Container Won't Start

**Possible causes**:
- Docker daemon not running
- Insufficient disk space
- Port conflict (unlikely with Testcontainers)
- Image pull failure

**Solution**:
```bash
# Check Docker status
docker ps

# Try pulling image manually
docker pull postgres:15-alpine

# Run with verbose logging
mvn test -Ppostgres-integration -X
```

### Test Timeout

**If PostgreSQL test times out**:
- First run is slower (pulling image): 30-40s normal
- Increase timeout: `mvn test -Ppostgres-integration -DargLine="-Dtestcontainers.ryuk.disabled=true"`
- Check Docker resources (CPU/memory limits)

---

## Future Enhancements

1. **Testcontainers Network for Service Integration**
   - Redis for caching (future)
   - Multiple service containers

2. **Test Profiles for Different Scenarios**
   - High-load testing
   - Failure scenario testing
   - Performance benchmarking

3. **Code Coverage Metrics**
   - JaCoCo plugin integration
   - Coverage reports in CI/CD

4. **Contract Testing**
   - Pact tests for API contracts
   - Schema validation against OpenAPI spec

---

## Summary

The two-tier testing approach provides:
- **Fast feedback** during development (H2 tests)
- **Production confidence** before deployment (PostgreSQL tests)
- **Flexibility** to choose based on context and environment
- **Maintainability** through separated test profiles

This balances pragmatism with rigor appropriate for a one-day interview prototype.
