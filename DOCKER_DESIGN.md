# URL Shortener — Docker Architecture Design

## Overview

This document proposes a Docker setup for the URL Shortener application suitable for one-day interview project deployment. The design prioritizes simplicity, clarity, and production-adjacent best practices without over-engineering.

---

## 1. Dockerfile Strategy

### Multi-Stage Build (Recommended)

**Rationale:**
- Separates build dependencies from runtime dependencies
- Reduces final image size (production images don't include Maven, compiler, sources)
- Improves security (no build tools in production image)
- Aligns with Spring Boot best practices

**Architecture:**

```
Stage 1: Maven Build Stage
├── Base: maven:3.9-eclipse-temurin-21-alpine
├── Purpose: Compile and build JAR
├── Input: Source code + pom.xml
└── Output: target/url-shortener-0.0.1-SNAPSHOT.jar

Stage 2: Application Runtime Stage
├── Base: eclipse-temurin:21-jre-alpine
├── Purpose: Run the application
├── Input: JAR from Stage 1
├── Output: Executable Docker image (slim, ~300MB)
└── Features: Non-root user, health check, proper signals
```

### Dockerfile Structure

```dockerfile
# Stage 1: Builder
FROM maven:3.9-eclipse-temurin-21-alpine AS builder
WORKDIR /build
COPY pom.xml .
COPY src src/
RUN mvn clean package -DskipTests

# Stage 2: Runtime
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
COPY --from=builder /build/target/url-shortener-*.jar app.jar
COPY --chown=appuser:appgroup entrypoint.sh .
RUN chmod +x entrypoint.sh
USER appuser
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
  CMD wget --quiet --tries=1 --spider http://localhost:8080/actuator/health || exit 1
ENTRYPOINT ["./entrypoint.sh"]
CMD ["java", "-jar", "app.jar"]
```

---

## 2. Java Runtime Image

### Selection: `eclipse-temurin:21-jre-alpine`

**Rationale:**

| Choice | Reason |
|--------|--------|
| **eclipse-temurin** | Officially supported JDK by Eclipse Foundation; reliable, free, no licensing concerns |
| **21-jre** (not jdk) | Production JRE only (no compiler/build tools); reduces image size from ~500MB to ~300MB |
| **alpine** | Minimal base image (~5MB); reduces attack surface; standard for containerized Java |

**Alternatives Considered:**

| Alternative | Pros | Cons | Decision |
|---|---|---|---|
| openjdk:21-jre | Well-known | Less maintained, larger | ❌ Reject |
| azul/zulu:21-jre-alpine | Good support | Commercial backing (less needed for MVP) | ❌ Reject |
| gcr.io/distroless/java21 | Minimal, secure | Learning curve, opaque internals | ⚠️ Future |

### Image Sizing

```
Base image:      eclipse-temurin:21-jre-alpine  ~170 MB
+ Application JAR:                              ~50 MB (with dependencies)
+ Entry script:                                 <1 KB
─────────────────────────────────────────────────────────
= Final image size:                           ~220-250 MB
```

### Alpine Considerations

**Advantages:**
- Minimal attack surface
- Small image size
- Fast container startup

**Gotchas (handled in design):**
- ✅ No `bash` (use `ash` or avoid shell scripts) → Use `entrypoint.sh` with `#!/bin/sh`
- ✅ Limited packages (no `wget`, `curl`) → Add in Dockerfile if needed: `RUN apk add --no-cache curl`
- ✅ glibc vs musl libc → eclipse-temurin handles compatibility

---

## 3. Build Strategy

### Maven Layering in Docker (Cache Optimization)

**Problem:** Maven build in Docker re-downloads dependencies on every build, even if pom.xml unchanged.

**Solution:** Docker layer caching with separate `pom.xml` and `src/` copy steps.

```dockerfile
COPY pom.xml .
RUN mvn dependency:resolve  # Layer cached if pom.xml unchanged
COPY src src/
RUN mvn package
```

**Build Performance:**
- First build: ~3-5 minutes (depends on network, ~600MB downloads)
- Subsequent builds (code change only): ~30-60 seconds (dependencies cached)
- Full rebuild (pom.xml change): ~2-3 minutes (re-downloads)

### Build Optimization Options

**Option 1: Skip Tests (Default for Docker Build)**
```bash
RUN mvn clean package -DskipTests
```
✅ Faster (saves 30-60 seconds)  
✅ Tests run in CI/CD pipeline, not Docker build  
✓ Recommended for MVP

**Option 2: Include Tests (Safety)**
```bash
RUN mvn clean package
```
❌ Slower (adds 30-60 seconds)  
✅ Tests run in build (catches issues early)  
⚠️ Consider for production

**Decision for MVP:** Skip tests. Tests should run in CI pipeline (GitHub Actions, Jenkins).

### Build Command (For Engineer)

```bash
# Development (local laptop)
docker build -t url-shortener:latest .

# With BuildKit (faster, better caching)
DOCKER_BUILDKIT=1 docker build -t url-shortener:latest .

# Production (with test execution)
docker build --build-arg SKIP_TESTS=false -t url-shortener:1.0.0 .
```

---

## 4. PostgreSQL Version

### Selection: `postgres:15-alpine`

**Rationale:**

| Version | Status | Notes |
|---------|--------|-------|
| **postgres:15** | LTS, stable | Released Oct 2022, supported until Oct 2027 ✅ |
| postgres:16 | Latest | Released Oct 2023 (newer, but 15 proven) |
| postgres:14 | End of life | Oct 2026 (still supported, but aging) |

**Decision:** `postgres:15-alpine` balances stability, features, and freshness.

### Database Configuration

```yaml
PostgreSQL 15:
├── Image: postgres:15-alpine (~170 MB)
├── Port: 5432 (exposed to Docker network, not host)
├── Database: url_shortener (created on startup)
├── User: postgres (default, will be replaced with app user)
├── Password: Configurable via environment (default: postgres for MVP)
├── Locale: C.UTF-8 (alpine standard)
├── Encoding: UTF-8
└── Volume: /var/lib/postgresql/data (persistent storage)
```

### Alternatives Considered

| Database | Rationale | Decision |
|----------|-----------|----------|
| **PostgreSQL 15** | Proven, LTS, feature-complete | ✅ SELECTED |
| MySQL 8 | Popular but overkill for MVP | ⚠️ Future option |
| MariaDB 11 | Good, but PostgreSQL more common in Spring | ⚠️ Future option |
| SQLite | No, requires file persistence; limits concurrency | ❌ Reject |

---

## 5. Docker Compose Services

### Service Architecture

```
┌─────────────────────────────────────┐
│  docker-compose.yml                 │
├─────────────────────────────────────┤
│ Service: url-shortener              │
│  ├─ Image: url-shortener:latest     │
│  ├─ Port: 8080:8080                 │
│  ├─ Depends on: db                  │
│  ├─ Env: DATABASE_URL, etc.         │
│  └─ Health check: /actuator/health  │
│                                     │
│ Service: db (PostgreSQL)            │
│  ├─ Image: postgres:15-alpine       │
│  ├─ Port: 5432:5432                 │
│  ├─ Env: POSTGRES_DB, USER, PASS    │
│  ├─ Volume: postgres_data (named)   │
│  └─ Health check: pg_isready        │
│                                     │
│ Volume: postgres_data               │
│  └─ Persists /var/lib/postgresql    │
└─────────────────────────────────────┘
```

### docker-compose.yml Structure

**Services:**

```yaml
services:
  url-shortener:
    build: .                           # Build from Dockerfile in current dir
    image: url-shortener:latest
    container_name: url-shortener-app
    ports:
      - "8080:8080"                    # Host:Container
    environment:
      SPRING_DATASOURCE_URL: jdbc:postgresql://db:5432/url_shortener
      SPRING_DATASOURCE_USERNAME: postgres
      SPRING_DATASOURCE_PASSWORD: ${DB_PASSWORD:-postgres}
      SPRING_PROFILES_ACTIVE: docker
      JAVA_TOOL_OPTIONS: -Xmx512m      # JVM heap size
    depends_on:
      db:
        condition: service_healthy     # Wait for DB health check
    networks:
      - url-shortener-network
    restart: unless-stopped            # Auto-restart on failure (not on explicit stop)

  db:
    image: postgres:15-alpine
    container_name: url-shortener-db
    environment:
      POSTGRES_DB: url_shortener
      POSTGRES_USER: postgres
      POSTGRES_PASSWORD: ${DB_PASSWORD:-postgres}
      POSTGRES_INITDB_ARGS: "-c shared_buffers=128MB -c max_connections=50"
    volumes:
      - postgres_data:/var/lib/postgresql/data
    ports:
      - "5432:5432"                    # Expose for local psql debugging
    networks:
      - url-shortener-network
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U postgres"]
      interval: 10s
      timeout: 5s
      retries: 5
      start_period: 10s
    restart: unless-stopped

networks:
  url-shortener-network:
    driver: bridge

volumes:
  postgres_data:
    driver: local
```

---

## 6. Environment Variables

### Mapping: Spring Boot ← Docker Compose ← Environment

```
├─ application.yml (default, development)
│  └─ datasource.url: jdbc:postgresql://localhost:5432/url_shortener
│  └─ datasource.username: postgres
│
├─ application-docker.yml (Docker override)
│  └─ datasource.url: ${SPRING_DATASOURCE_URL}
│  └─ datasource.username: ${SPRING_DATASOURCE_USERNAME}
│
└─ docker-compose.yml (environment injection)
   ├─ SPRING_DATASOURCE_URL: jdbc:postgresql://db:5432/url_shortener
   ├─ SPRING_DATASOURCE_USERNAME: postgres
   ├─ SPRING_PROFILES_ACTIVE: docker
   └─ JAVA_TOOL_OPTIONS: -Xmx512m
```

### Environment Variables (Recommended)

```yaml
SPRING_PROFILES_ACTIVE: docker              # Activate docker profile
SPRING_DATASOURCE_URL: jdbc:postgresql://db:5432/url_shortener
SPRING_DATASOURCE_USERNAME: postgres        # Non-root user (future)
SPRING_DATASOURCE_PASSWORD: ${DB_PASSWORD}  # From .env or command
SPRING_JPA_HIBERNATE_DDL_AUTO: validate     # Never auto-create in production
SPRING_FLYWAY_ENABLED: true                 # Migrations always enabled
APP_BASE_URL: http://url-shortener:8080     # For redirect URLs (internal)
JAVA_TOOL_OPTIONS: -Xmx512m                 # JVM settings
LOGGING_LEVEL_ROOT: INFO                    # Production logging
```

### Secret Handling

**MVP Approach (Interview):**
```bash
# Use .env file for local development
# .env (NOT committed to git)
DB_PASSWORD=secure-password-here
```

```bash
# Run with .env
docker-compose --env-file .env up
```

**Production Approach:**
```bash
# Use Docker secrets (Swarm) or .env from secure source
# Never hardcode passwords in compose file
docker secret create db_password -  # Read from stdin
```

**Template .env.example (committed to git):**
```
# Copy this to .env and fill in actual values
DB_PASSWORD=changeme
JAVA_HEAP_SIZE=-Xmx512m
SPRING_PROFILES_ACTIVE=docker
```

---

## 7. Database Persistence

### Volume Strategy

**Named Volume (Recommended for MVP):**
```yaml
volumes:
  postgres_data:
    driver: local
```

**Advantages:**
- ✅ Data survives container restart: `docker-compose down` → `docker-compose up`
- ✅ Data survives container removal: `docker rm <container>` (volume remains)
- ✅ Located in Docker's data directory (usually `/var/lib/docker/volumes/`)
- ✅ Easy to backup: `docker run --rm -v postgres_data:/data -v $(pwd):/backup alpine tar czf /backup/pg.tar.gz /data`
- ✅ Portable across hosts (with caveats)

**Bind Mount (Not Recommended for Database):**
```yaml
volumes:
  - /path/on/host:/var/lib/postgresql/data  # ❌ Don't use for DB
```
Problems:
- ❌ Permission issues (Docker UID/GID vs host)
- ❌ fsync latency on some systems
- ❌ Less portable between systems

### Database Persistence Guarantees

```
┌─────────────────────────────────────────────┐
│ docker-compose up                           │
│ ├─ DB starts, mounts postgres_data volume  │
│ ├─ Flyway migrations run                    │
│ ├─ Application starts                       │
│ └─ Data written to postgres_data/           │
│                                             │
│ docker-compose down (stops containers)      │
│ └─ postgres_data/ PERSISTS ✅              │
│                                             │
│ docker-compose up (restart)                 │
│ └─ DB mounts existing postgres_data/       │
│    (no data loss) ✅                        │
│                                             │
│ docker volume rm postgres_data              │
│ └─ ⚠️ DESTRUCTIVE (only if deliberate)     │
└─────────────────────────────────────────────┘
```

### Backup/Restore Strategy

**Backup (MVP):**
```bash
docker-compose exec db pg_dump -U postgres url_shortener > backup.sql
```

**Restore:**
```bash
cat backup.sql | docker-compose exec -T db psql -U postgres url_shortener
```

---

## 8. Health Checks

### Application Health Check

**Strategy:** HTTP GET `/actuator/health`

```dockerfile
HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
  CMD wget --quiet --tries=1 --spider http://localhost:8080/actuator/health || exit 1
```

**Parameters:**
- `interval=30s`: Check every 30 seconds
- `timeout=5s`: Fail if response doesn't arrive in 5 seconds
- `start_period=10s`: Don't fail during first 10 seconds (JVM startup)
- `retries=3`: Consider unhealthy after 3 consecutive failures

**Endpoint Details:**
```
GET http://localhost:8080/actuator/health
Response (when healthy):
{
  "status": "UP"
}
Status codes:
├─ 200 OK: Healthy
└─ 503 Service Unavailable: Unhealthy
```

**Alternative: TCP Check (Simpler)**
```dockerfile
HEALTHCHECK --interval=10s --timeout=3s --start-period=5s --retries=3 \
  CMD nc -z localhost 8080 || exit 1
```
Less reliable (port open ≠ application healthy)

### Database Health Check

```yaml
healthcheck:
  test: ["CMD-SHELL", "pg_isready -U postgres"]
  interval: 10s
  timeout: 5s
  retries: 5
  start_period: 10s
```

**What it does:**
```
pg_isready -U postgres
├─ Connects to PostgreSQL
├─ Checks if ready to accept connections
├─ Returns: 0 (ready) or non-zero (not ready)
└─ Very fast (~100ms)
```

### Orchestration: Wait for Dependencies

**docker-compose.yml:**
```yaml
depends_on:
  db:
    condition: service_healthy    # Wait for DB health check to pass
```

**Startup Sequence:**
```
1. docker-compose up
2. Start db service (PostgreSQL container)
3. Wait for pg_isready health check to pass (up to 10 + 5*5 = 35 seconds)
4. Start url-shortener service (Spring app)
5. Wait for /actuator/health to pass (up to 10 + 5*3 = 25 seconds)
6. docker-compose up completes
Total: ~60 seconds typical, up to 90 seconds if slow
```

---

## 9. Database Readiness

### Problem: Race Condition

**Scenario:**
```
1. PostgreSQL container starts
2. docker-compose runs URL Shortener container
3. Spring tries to connect to DB (not ready yet)
4. Connection fails → Application crashes
5. docker-compose restarts URL Shortener
6. Eventually DB is ready → Application succeeds
```

**Result:** Multiple restarts, delayed startup.

### Solution: Health Check + Depends On

**In docker-compose.yml:**
```yaml
url-shortener:
  depends_on:
    db:
      condition: service_healthy    # ✅ Wait for health check
```

**How it works:**
1. docker-compose starts db service
2. docker-compose **waits** for db health check to pass
3. docker-compose starts url-shortener service
4. Application connects to DB (guaranteed ready)

### Fallback: Entrypoint Script with Retry

**entrypoint.sh:**
```bash
#!/bin/sh
# Wait for PostgreSQL to be ready
max_attempts=30
attempt=0

while [ $attempt -lt $max_attempts ]; do
  if nc -z db 5432 2>/dev/null; then
    echo "PostgreSQL is ready!"
    exec java -jar app.jar "$@"
  fi
  attempt=$((attempt + 1))
  echo "Waiting for PostgreSQL... (attempt $attempt/$max_attempts)"
  sleep 1
done

echo "PostgreSQL did not become ready in time"
exit 1
```

**Advantage:** Works even without Docker health checks (resilient).

---

## 10. Non-Root Application Execution

### Security: Why Not Run as Root?

**Risk:** Container runs as root (UID 0)
```bash
docker run alpine whoami
# Output: root
```

**Vulnerability:**
- If application is compromised, attacker has root access
- Can modify container, escape sandbox
- Can access mounted volumes as root

### Solution: Create Dedicated User

**In Dockerfile:**
```dockerfile
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
COPY --chown=appuser:appgroup app.jar .
USER appuser
```

**What this does:**
```
addgroup -S appgroup           # Create system group (no login)
adduser -S appuser             # Create system user (no login, no shell)
                   -G appgroup # Add user to appgroup
COPY --chown=appuser:appgroup  # Change JAR ownership
USER appuser                   # Switch to this user for RUN/CMD
```

**Verification:**
```bash
docker run url-shortener:latest whoami
# Output: appuser (not root)
```

**Principle of Least Privilege:**
- ✅ Application can read/write JAR and logs
- ✅ Application cannot modify system files
- ✅ Application cannot install packages
- ✅ Reduces blast radius if compromised

---

## 11. Secret Handling

### MVP: Environment Variables + .env File

**Recommended for Interview/Dev:**

```yaml
# docker-compose.yml
services:
  db:
    environment:
      POSTGRES_PASSWORD: ${DB_PASSWORD:-postgres}
```

```bash
# .env (NOT committed)
DB_PASSWORD=my-secure-password
```

```bash
# .gitignore
.env
.env.local
*.env
```

**Usage:**
```bash
docker-compose up       # Reads .env automatically
docker-compose up --env-file custom.env  # Custom file
```

### Production: Secrets Best Practices (Not for MVP, But Document)

**Option 1: Docker Secrets (Swarm)**
```bash
docker secret create db_password -
# Type password, press Ctrl-D

# Access in service
POSTGRES_PASSWORD_FILE: /run/secrets/db_password
```

**Option 2: Environment Variable from File**
```bash
docker run --env-file /secure/location/.env url-shortener
# File path: /secure/location/.env (only readable by authorized users)
```

**Option 3: HashiCorp Vault (Overkill for MVP)**
- External secret management
- Not recommended for one-day interview project

### Secret Files Template

**File Structure:**
```
project/
├─ docker-compose.yml       (committed)
├─ .env.example              (committed, template)
├─ .env                      (NOT committed, actual secrets)
├─ .gitignore               (contains .env)
└─ README.md                (instructions to copy .env.example)
```

**.env.example (Template, Committed):**
```
# Copy to .env and fill in real values
DB_PASSWORD=changeme
JAVA_HEAP_SIZE=-Xmx512m
SPRING_PROFILES_ACTIVE=docker
```

**.env (Actual, Not Committed):**
```
DB_PASSWORD=actual-secure-password-123
JAVA_HEAP_SIZE=-Xmx1024m
SPRING_PROFILES_ACTIVE=docker
```

---

## 12. Spring Profile / Configuration

### Two-Profile Strategy

**Profile 1: `application.yml` (Default, Local Development)**

```yaml
# application.yml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/url_shortener
    username: postgres
    password: postgres
  jpa:
    hibernate:
      ddl-auto: validate
  flyway:
    enabled: true

app:
  base-url: http://localhost:8080

logging:
  level:
    root: INFO
    com.vishwasena.urlshortener: DEBUG
```

**Profile 2: `application-docker.yml` (Docker, Production-Adjacent)**

```yaml
# application-docker.yml
spring:
  datasource:
    url: ${SPRING_DATASOURCE_URL}
    username: ${SPRING_DATASOURCE_USERNAME}
    password: ${SPRING_DATASOURCE_PASSWORD}
    hikari:
      maximum-pool-size: 10
      minimum-idle: 2
      connection-timeout: 10000
  jpa:
    hibernate:
      ddl-auto: validate
  flyway:
    enabled: true

app:
  base-url: ${APP_BASE_URL:http://url-shortener:8080}

logging:
  level:
    root: INFO
    com.vishwasena.urlshortener: INFO          # Less verbose in production

server:
  shutdown: graceful                           # Graceful shutdown
  servlet:
    shutdown-wait-time: 30s
```

### Activation in docker-compose.yml

```yaml
environment:
  SPRING_PROFILES_ACTIVE: docker
```

**Spring Boot Resolution:**
```
1. Load application.yml (base configuration)
2. Load application-docker.yml (docker-specific overrides)
3. Apply environment variable substitution (${SPRING_DATASOURCE_URL})
4. Result: Merged configuration with Docker values
```

### Startup Output Example

```
2025-08-27 17:45:23.123  INFO [url-shortener,,] 
  Started UrlShortenerApplication in 8.234 seconds (process running for 8.456s)
  
2025-08-27 17:45:23.456  INFO [url-shortener,,] 
  Spring profiles active: docker
  
2025-08-27 17:45:23.789  INFO [url-shortener,,] 
  Connected to PostgreSQL at jdbc:postgresql://db:5432/url_shortener
  
2025-08-27 17:45:24.123  INFO [url-shortener,,] 
  Flyway migrations completed: 1 migration executed
  
2025-08-27 17:45:24.456  INFO [url-shortener,,] 
  Application is ready to receive requests
```

---

## Summary: Complete Docker Setup Design

| Component | Choice | Rationale |
|-----------|--------|-----------|
| **Dockerfile Strategy** | Multi-stage build | Reduces image size, improves security |
| **Java Runtime** | eclipse-temurin:21-jre-alpine | Small, reliable, Alpine-based |
| **Build Process** | Maven in Docker, skip tests | Fast, tests in CI pipeline |
| **PostgreSQL** | postgres:15-alpine | LTS, proven, Alpine-based |
| **Compose Services** | url-shortener + db | Simple, docker-compose manages orchestration |
| **Environment** | .env file + variables | Easy for MVP, secure with proper gitignore |
| **Database Persistence** | Named volume postgres_data | Survives restarts, easy backup |
| **Health Checks** | HTTP /actuator/health + pg_isready | Ensures readiness before depending services |
| **Database Readiness** | depends_on + condition: service_healthy | Prevents connection race condition |
| **Non-Root User** | appuser (UID >1000) | Principle of least privilege |
| **Secrets** | .env file (MVP), Docker secrets (future) | Simple for interview, scalable path |
| **Spring Profile** | application-docker.yml | Environment-specific configuration |

---

## Estimated Files to Create

1. **Dockerfile** (40 lines)
   - Multi-stage build
   - Non-root user
   - Health check
   - Entrypoint script

2. **entrypoint.sh** (20 lines)
   - Database readiness retry logic
   - Graceful startup

3. **docker-compose.yml** (70 lines)
   - Two services (app + database)
   - Health checks
   - Volume persistence
   - Environment configuration

4. **application-docker.yml** (40 lines)
   - Docker-specific Spring configuration
   - Environment variable substitution
   - Production-appropriate logging

5. **.env.example** (5 lines, committed)
   - Template for .env

6. **docs/DOCKER_SETUP.md** (Documentation, 50 lines)
   - Quick start guide
   - Common commands
   - Troubleshooting

---

## Quick Start Commands (Post-Implementation)

```bash
# Build image
docker build -t url-shortener:latest .

# Start services (with .env file)
docker-compose up

# Stop services (preserves data)
docker-compose down

# View logs
docker-compose logs -f url-shortener

# Access database
docker-compose exec db psql -U postgres url_shortener

# Restart application
docker-compose restart url-shortener

# Full clean start (removes data)
docker-compose down -v  # -v removes volumes

# List containers and volumes
docker-compose ps
docker volume ls
```

---

## Production Considerations (Future Enhancement)

This design is interview-ready. For production deployment:

1. **Secrets Management**: Replace .env with Docker Secrets or HashiCorp Vault
2. **Image Registry**: Push images to Docker Hub or private registry
3. **Logging**: Centralize logs (ELK stack, Datadog, etc.)
4. **Monitoring**: Add Prometheus metrics export
5. **Networking**: Configure reverse proxy (nginx) for TLS
6. **Scaling**: Move to Docker Swarm or Kubernetes if needed
7. **CI/CD**: Automate image building in GitHub Actions

---

## Design Status

✅ **READY FOR IMPLEMENTATION**

This design:
- ✅ Follows Docker best practices
- ✅ Appropriate for one-day interview project
- ✅ Production-adjacent (path to production clear)
- ✅ Simple enough to explain in interview
- ✅ No over-engineering
- ✅ Comprehensive documentation

**Awaiting engineer approval to proceed with implementation.**
