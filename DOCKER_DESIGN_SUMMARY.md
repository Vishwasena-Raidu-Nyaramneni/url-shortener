# Docker Setup Design — Executive Summary

**Status:** ✅ DESIGN COMPLETE  
**Date:** 2025-08-27  
**Awaiting:** Engineer Approval Before Implementation

---

## 12-Point Design Summary

### 1. Dockerfile Strategy: Multi-Stage Build
**Decision:** Maven builder stage → eclipse-temurin runtime stage  
**Rationale:** Reduces size (500MB → 250MB), improves security, enables caching  
**Result:** ~250MB final image with all dependencies

### 2. Java Runtime Image: eclipse-temurin:21-jre-alpine
**Decision:** JRE only (not JDK), Alpine base  
**Rationale:** Stable, free, minimal attack surface, ~170MB base  
**Alternatives Considered:** openjdk (less maintained), zulu (commercial)  
**Result:** Production-ready runtime

### 3. Build Strategy: Maven in Docker, Skip Tests
**Decision:** Tests run in CI pipeline, not Docker build  
**Rationale:** Faster builds (saves 30-60s), separation of concerns  
**Performance:** First build 3-5 min, subsequent builds 1-2 min  
**Result:** Efficient Docker build process

### 4. PostgreSQL Version: postgres:15-alpine
**Decision:** LTS version (Oct 2022 - Oct 2027), Alpine base  
**Rationale:** Proven, feature-complete, aligns with Java ecosystem  
**Sizing:** ~170MB base image  
**Result:** Stable, long-supported database

### 5. Docker Compose Services: url-shortener + db
**Decision:** Two services (application + database) with network isolation  
**Rationale:** Simple, appropriate for interview project, health checks included  
**Orchestration:** Automatic startup sequence with `depends_on: service_healthy`  
**Result:** Clean, understandable architecture

### 6. Environment Variables: .env File (MVP) → Docker Secrets (Production)
**Decision:** .env file with .example template, path to Docker Secrets  
**Rationale:** Simple for MVP, secure with gitignore, scalable to production  
**Secrets:** DB_PASSWORD, JAVA_HEAP_SIZE, SPRING_PROFILES_ACTIVE  
**Result:** Flexible configuration management

### 7. Database Persistence: Named Volume (postgres_data)
**Decision:** Named Docker volume at /var/lib/postgresql/data  
**Rationale:** Data survives container restarts/removals, easy backup, standard Docker practice  
**Guarantee:** `docker-compose down` preserves data, `docker-compose up` restores  
**Result:** Reliable data persistence

### 8. Health Checks: Application + Database
**Application:** HTTP GET /actuator/health (30s interval, 5s timeout, 3 retries)  
**Database:** pg_isready (10s interval, 5s timeout, 5 retries)  
**Rationale:** Prevents race conditions, ensures readiness  
**Result:** Robust startup sequence

### 9. Database Readiness: depends_on + service_healthy
**Sequence:**  
1. Start PostgreSQL container  
2. Wait for pg_isready health check to pass (up to ~50 seconds)  
3. Start Spring Boot application  
4. Wait for /actuator/health to pass (up to ~25 seconds)  
5. Application connects to DB (guaranteed ready)  

**Fallback:** entrypoint.sh with retry logic (30 attempts, 1 second delays)  
**Result:** Zero race conditions, reliable startup

### 10. Non-Root Execution: appuser (system user, UID > 1000)
**Security:** Container runs as appuser, not root  
**Principle:** Least privilege - limits blast radius if compromised  
**Implementation:** Created in Dockerfile, owns JAR and logs  
**Verification:** `docker run url-shortener:latest whoami` → appuser  
**Result:** Production security best practice

### 11. Secret Handling: .env + .gitignore (MVP), Docker Secrets (Production)
**MVP Approach:**
- Committed: .env.example (template)
- Not Committed: .env (actual values)
- gitignore: Prevents accidental commits

**Production Approach:**
- Docker secrets or environment variables from secure source
- No hardcoded passwords in files
- Audit trail for secret changes

**Result:** Simple for interview, secure for production

### 12. Spring Profile Configuration: application-docker.yml
**Default:** application.yml (local development, localhost connections)  
**Docker:** application-docker.yml (container environment, network connections)  
**Activation:** SPRING_PROFILES_ACTIVE=docker (environment variable)  
**Substitution:** ${SPRING_DATASOURCE_URL} resolved from environment  
**Result:** Clean separation, easy debugging

---

## Files to Create (No Existing Code Changes)

```
NEW FILES:
├─ Dockerfile (40 lines, multi-stage)
├─ entrypoint.sh (20 lines, database retry logic)
├─ docker-compose.yml (70 lines, services + orchestration)
├─ .env.example (5 lines, template, committed)
├─ application-docker.yml (40 lines, docker config)
└─ docs/DOCKER_SETUP.md (50 lines, quick start guide)

UNCHANGED:
├─ pom.xml ✅ Already configured
├─ application.yml ✅ Already configured
├─ Source code ✅ No changes
└─ Tests ✅ No changes
```

---

## Quick Start (Post-Implementation)

```bash
# Setup
cp .env.example .env
docker build -t url-shortener:latest .

# Start (waits for DB, then app)
docker-compose up

# Verify
curl http://localhost:8080/actuator/health

# Create URL
curl -X POST http://localhost:8080/api/v1/urls \
  -H "Content-Type: application/json" \
  -d '{"originalUrl":"https://example.com"}'

# View logs
docker-compose logs -f url-shortener

# Stop (data persists)
docker-compose down

# Restart (same data)
docker-compose up
```

---

## Design Checkpoints

✅ **Application Layer**
- Multi-stage Dockerfile (efficient)
- Alpine Linux base (minimal, ~170MB)
- Non-root user (security)
- Health checks (readiness)
- Graceful shutdown (clean signals)

✅ **Database Layer**
- PostgreSQL 15-alpine (LTS, proven)
- Named volume persistence (survives restart)
- Health check (readiness detection)
- Network isolation (Docker bridge)
- Configurable parameters

✅ **Orchestration**
- docker-compose (simple, appropriate)
- Service dependencies (waits for DB)
- Environment variables (configurable)
- Network bridging (DNS service discovery)
- Restart policies (resilience)

✅ **Security**
- Non-root user execution
- Secret management (.env + gitignore)
- No hardcoded passwords
- Alpine images (minimal attack surface)
- Network isolation (internal bridge)

✅ **Operations**
- Health checks (automatic detection)
- Logging (container stdout/stderr)
- Graceful shutdown (signal handling)
- Data persistence (named volumes)
- Quick start/stop/restart

✅ **Documentation**
- Design rationale (in DOCKER_DESIGN.md)
- Command reference (in DOCKER_SETUP.md)
- Troubleshooting guide (in DOCKER_SETUP.md)
- Production path (in DOCKER_DESIGN.md)
- Secret handling (in DOCKER_DESIGN.md)

---

## Production Path

**This MVP design supports production deployment.**

**Immediate (days 1-2):** Use as-is for production
- Multi-stage Dockerfile ✅
- Named volumes ✅
- Health checks ✅
- Non-root user ✅

**Short term (weeks 2-3):** Upgrade for scale
- Docker Secrets instead of .env
- Image registry setup
- Reverse proxy with TLS
- Centralized logging

**Medium term (weeks 4-6):** Enterprise deployment
- Docker Swarm or Kubernetes
- Prometheus metrics
- Monitoring/alerting
- Blue-green deployment

**Long term (post-MVP):**
- Multi-region
- Load balancing
- Auto-scaling
- Disaster recovery

---

## Estimated Effort

**Implementation:** 2-3 hours
- Dockerfile: 30 min
- entrypoint.sh: 15 min
- docker-compose.yml: 30 min
- Profiles + .env: 20 min
- Testing: 20 min

**Total implementation + verification: ~2-3 hours**

---

## Design Principles Applied

1. **KISS (Keep It Simple):** Two services, straightforward configuration
2. **DRY (Don't Repeat Yourself):** Profiles override base config, no duplication
3. **Least Privilege:** Non-root user, minimal Alpine images
4. **Defense in Depth:** Health checks + retry logic + depends_on
5. **Production-Adjacent:** Path to production clear, no over-engineering for MVP
6. **Documentation-Driven:** Every decision rationale documented

---

## Comparison: Local vs Docker

| Aspect | Local Development | Docker |
|--------|---|---|
| **Database Setup** | Manual install PostgreSQL | Automatic via image |
| **Configuration** | application.yml | application-docker.yml |
| **Data Persistence** | File system | Named volume |
| **Dependencies** | Must have PostgreSQL installed | Docker provides everything |
| **Startup** | Manual (app + DB) | docker-compose up (automatic) |
| **Teardown** | Leave running or manual kill | docker-compose down |
| **Reproducibility** | Depends on local environment | Identical everywhere |
| **Onboarding** | Install PostgreSQL, configure, etc. | `docker-compose up` |

---

## Questions Addressed by Design

**Q: Why multi-stage?**  
A: Separates build (Maven, JDK) from runtime (JRE only). Final image ~250MB instead of ~500MB.

**Q: Why Alpine?**  
A: Minimal OS (~5MB) reduces attack surface and startup time.

**Q: Why not run as root?**  
A: Security best practice; limits blast radius if container compromised.

**Q: Why depends_on service_healthy?**  
A: Prevents application connecting to database before it's ready.

**Q: Why named volume instead of bind mount?**  
A: Named volumes are cleaner, avoid permission issues, survive container removal.

**Q: Why .env file?**  
A: Simple for MVP; gitignore prevents accidental commits; path to Docker Secrets for production.

**Q: Why health checks?**  
A: Detects when services are ready; enables automatic restart on failure.

**Q: Why application-docker.yml?**  
A: Clean separation of environments; easy to debug which config active.

---

## Conclusion

✅ **Docker design is complete, well-documented, and ready for implementation.**

This design:
- Follows industry best practices
- Appropriate for interview project
- Production-adjacent (clear upgrade path)
- Simple enough to explain in interview
- Comprehensive documentation included
- No over-engineering

**Awaiting engineer approval to proceed with implementation.**

Review **DOCKER_DESIGN.md** for:
- Complete code examples
- Detailed rationale for each decision
- Quick start commands
- Troubleshooting guide
- Production considerations
- Estimated files and effort

---

**Next Step:** Engineer approval to create 6 new files (Dockerfile, entrypoint.sh, docker-compose.yml, application-docker.yml, .env.example, docs/DOCKER_SETUP.md)

**Estimated Implementation Time:** 2-3 hours

**Test Plan:** Build image, start services, verify application health check, create test URL, verify redirect works, test data persistence
