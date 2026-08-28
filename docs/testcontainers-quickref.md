# Testcontainers Integration — Quick Reference

## What Was Added

### 1. Test Infrastructure
- **AbstractPostgresIntegrationTest**: Base class with Testcontainers lifecycle
- **application-postgres-integration.yml**: PostgreSQL-specific configuration
- **Maven profiles**: `postgres-integration` and `all-tests`

### 2. PostgreSQL Integration Tests (19 new tests)
- **ShortUrlRepositoryPostgresIntegrationTest** (6 tests)
  - BIGSERIAL ID generation
  - Unique constraint enforcement
  - TIMESTAMP WITH TIME ZONE handling
  - CASCADE DELETE verification
  - Index performance
  - Click event ordering

- **UrlControllerPostgresIntegrationTest** (7 tests)
  - URL creation and persistence to PostgreSQL
  - Click recording transaction isolation
  - Expiration validation
  - Disabled URL handling
  - Analytics retrieval from real database
  - Transactional consistency
  - Indexed query optimization

- **FlywayMigrationPostgresIntegrationTest** (6 tests)
  - Flyway migration execution verification
  - Table structure validation
  - Column types and constraints
  - Foreign key cascade delete
  - Unique constraints
  - Index creation and availability

### 3. Repository Enhancements
- **ClickEventRepository**: Added 2 query methods
  - `countByShortUrlId()`: Count clicks by URL
  - `findFirstByShortUrlIdOrderByClickedAtDesc()`: Get most recent click

---

## Running Tests

### Default H2 Tests (RECOMMENDED for local development)
```bash
mvn clean test
```
- Result: **59 tests** pass
- Time: ~15-20 seconds
- No Docker required
- Includes: All unit tests + H2 integration tests

### PostgreSQL Tests Only
```bash
mvn clean test -Ppostgres-integration
```
- Result: **19 tests** (requires Docker)
- Time: ~30-40 seconds on first run (cached after)
- All tests validate against real PostgreSQL
- Flyway migrations run automatically

### All Tests (H2 + PostgreSQL)
```bash
mvn clean test -Pall-tests
```
- Result: **78 tests** total
- Time: ~60-80 seconds (requires Docker)
- Complete validation suite

---

## Test Breakdown by Suite

### H2 In-Memory Tests (Default, 59 tests)

| Test Class | Tests | Purpose |
|-----------|-------|---------|
| UrlControllerIntegrationTest | 20 | API endpoint validation |
| ShortUrlRepositoryTest | 5 | Repository queries with H2 |
| UrlShortenerServiceTest | 9 | Business logic (mocked) |
| UrlValidatorTest | 15 | URL validation rules |
| ShortCodeGeneratorTest | 5 | Short-code generation |
| IpHasherTest | 5 | IP hashing logic |
| **Total** | **59** | |

### PostgreSQL Integration Tests (Requires Docker, 19 tests)

| Test Class | Tests | Purpose |
|-----------|-------|---------|
| ShortUrlRepositoryPostgresIntegrationTest | 6 | Real PostgreSQL queries |
| UrlControllerPostgresIntegrationTest | 7 | Full stack with PostgreSQL |
| FlywayMigrationPostgresIntegrationTest | 6 | Schema and migration validation |
| **Total** | **19** | |

---

## Docker Requirements

### Check if Docker is Available

```bash
docker --version
docker ps
```

### For First Run
- Will pull `postgres:15-alpine` (~500MB)
- Takes ~30-40 seconds
- Subsequent runs use cached image (~5 seconds per test)

### Disable Testcontainers Cleanup
For faster local iteration (keeps container between runs):
```bash
export TESTCONTAINERS_REUSE_ENABLE=true
mvn test -Ppostgres-integration
```

---

## CI/CD Integration

### GitHub Actions
```yaml
- name: Run All Tests with PostgreSQL
  run: mvn clean test -Pall-tests
```

### Jenkins/GitLab
```bash
mvn clean test -Pall-tests --batch-mode
```

### Local Pre-Commit
```bash
# Fast check before push (H2 only)
mvn clean test

# If successful, can run PostgreSQL tests:
mvn clean test -Ppostgres-integration
```

---

## Key Features

✅ **Fully Isolated PostgreSQL Instances**
- Each test class gets fresh container
- No cross-test contamination
- Flyway migrations run fresh each time

✅ **No Manual Setup Required**
- No need to install PostgreSQL locally
- No need to create databases
- Container managed automatically

✅ **Production-Validated Schema**
- Flyway migrations run against real PostgreSQL
- Hibernate validates schema matches entities
- Constraints and indexes verified

✅ **Backward Compatible**
- Existing H2 tests unchanged
- Both test suites can coexist
- Choose what runs via Maven profiles

✅ **Developer-Friendly**
- Fast local iteration with H2 (default)
- Real DB validation available when needed
- Clear separation of concerns

---

## Test Execution Flow

### Default Flow (Developer Workstation)
```
Developer
    ↓
mvn clean test (H2 only)
    ↓
59 tests pass in 15-20s
    ↓
Ready to commit
```

### CI/CD Flow (After Merge)
```
GitHub Push
    ↓
CI/CD Triggers
    ↓
mvn clean test (H2) ← Fast validation
    ↓
mvn clean test -Pall-tests (H2 + PostgreSQL) ← Real DB validation
    ↓
78 tests pass in 60-80s
    ↓
Deploy to production
```

---

## Troubleshooting

### Error: "Could not find a valid Docker environment"
**Cause**: Docker not running
**Solution**: 
```bash
# Windows/Mac: Start Docker Desktop
# Linux: sudo systemctl start docker

# Then run H2 tests only (don't need Docker):
mvn clean test
```

### Error: "Connection refused to localhost:5432"
**Cause**: Testcontainers couldn't start container
**Solution**:
```bash
# Check Docker is running
docker ps

# Check available resources
docker system df

# Try running with single thread
mvn test -Ppostgres-integration -DthreadCount=1
```

### Tests Hang or Timeout
**Solution**:
```bash
# Increase timeout for slow systems
mvn test -Ppostgres-integration \
  -DargLine="-Dtestcontainers.containerWaitStrategy.timeout=120"
```

---

## Summary

| Aspect | H2 Tests | PostgreSQL Tests |
|--------|----------|------------------|
| **Run Command** | `mvn clean test` | `mvn test -Ppostgres-integration` |
| **Docker Required** | ❌ No | ✅ Yes |
| **Test Count** | 59 | 19 |
| **Execution Time** | ~15-20s | ~30-40s |
| **Use Case** | Daily development | CI/CD, production validation |
| **Coverage** | Business logic | Database constraints |
| **Parallelization** | Full support | Per-container |
| **Default Included** | ✅ Yes | ❌ No |

Both test suites are complementary. Use H2 for fast iteration, PostgreSQL for production confidence.
