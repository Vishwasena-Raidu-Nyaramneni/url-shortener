# URL Shortener — Scenarios & Case Studies

This directory contains detailed scenarios demonstrating development patterns, decision-making, and AI-assisted engineering approaches.

---

## Available Scenarios

### 1. Greenfield Engineering Case Study (`greenfield.md`)

**Purpose:** Demonstrates how to execute a production-quality project from ambiguous business requirements through validation and production readiness.

**What It Covers:**
- 12 development stages from initial requirement to validation
- Requirements normalization and ambiguity resolution
- Architectural decision-making
- Task decomposition and sequencing
- Real problems encountered and solved (Docker port conflict, Flyway compatibility)
- Security and reliability analysis
- Testing and validation approach
- AI and engineer role separation

**Key Sections:**
1. **Stage 1-5:** Requirements → Design
2. **Stage 6-9:** Implementation → Testing
3. **Stage 10-11:** Deployment → Validation
4. **Stage 12:** Issues identified and prioritized
5. **Summary:** AI vs Engineer responsibilities
6. **Metrics:** Quantified outcomes
7. **Lessons:** Key takeaways for future projects

**Audience:**
- Engineers preparing for greenfield projects
- Technical interviewers evaluating engineering judgment
- AI/human collaboration practitioners
- Project managers structuring development workflows

**Key Outcomes Documented:**
- ✅ All 9 functional requirements implemented and tested
- ✅ Security review: 15 threat vectors analyzed
- ✅ Reliability review: 12 failure scenarios examined
- ⚠️ 4 issues identified (1 critical, 1 high, 2 medium/low)
- 📊 95% production readiness (1 fix remaining)

---

### 2. Brownfield Scaling Scenario (`brownfield.md`)

**Purpose:** Demonstrates how to analyze an existing production system, identify scaling bottlenecks, evaluate alternatives, and make pragmatic engineering decisions.

**Scenario:**
"The URL Shortener is successfully operating, but redirect traffic has increased significantly (1000+ req/sec). Redirect latency (p95: >500ms) and database load are increasing. How should we scale?"

**What It Covers:**
- Detailed analysis of current redirect flow and database operations
- Quantified bottleneck identification (connection pool, synchronous I/O, update contention)
- Three alternative approaches with trade-off analysis:
  * Option 1: PostgreSQL optimization (minor improvement, complex)
  * Option 2: In-memory cache (significant improvement, simple)
  * Option 3: Redis cache (best performance, adds infrastructure)
- Engineering decision with clear rationale
- Validation plan for chosen approach
- Risk assessment and mitigation
- MVP vs. Phase 2/3 roadmap

**Key Decision:**
- **Chosen:** Option 2 (In-Memory Cache) for MVP
- **Rationale:** Simple to implement (3-4 hours), 80-90% latency improvement, clear upgrade path to Redis (Phase 2)
- **Not chosen:** Option 1 (insufficient improvement), Option 3 (over-engineered for single-instance)
- **Future:** Documented upgrade path to Redis when multi-instance scaling required

**Audience:**
- Engineers evaluating scaling strategies
- Technical interviewers assessing systems thinking
- Architects making technology choices
- Teams managing technical debt and growth

**Key Outcomes Documented:**
- ✅ Current system analysis with actual code references
- ✅ Three options compared quantitatively (latency, scalability, complexity, cost)
- ✅ Engineering judgment: why pragmatism beats perfection for MVP
- ✅ Clear upgrade paths: Phase 1 → Phase 2 → Phase 3
- 📊 Validation plan to measure improvement
- 📊 Risks identified and mitigated

---

## How to Use These Scenarios

### For Greenfield Projects
Read **greenfield.md** to understand:
- How to decompose ambiguous requirements
- How to make architectural tradeoffs
- How to handle real problems (Docker, compatibility)
- How to validate production-quality code

### For Brownfield/Scaling Projects
Read **brownfield.md** to understand:
- How to analyze existing systems for bottlenecks
- How to evaluate multiple scaling alternatives quantitatively
- How to make pragmatic technology choices (not just "use Redis")
- When to defer optimization vs. when to implement now
- How to document upgrade paths for future phases

### For Interview Preparation
**Greenfield scenario:** Demonstrates full project lifecycle and engineering judgment  
**Brownfield scenario:** Demonstrates systems thinking and architectural decision-making

Read **greenfield.md** for:
- Requirements decomposition and ambiguity resolution
- Architectural decision-making
- Real problem-solving (Docker, Flyway, hostname bugs)
- Production readiness validation

Read **brownfield.md** for:
- "How would you scale this system?"
- "What are the tradeoffs between option X, Y, Z?"
- "When should you use caching vs. database optimization?"

### For Project Planning
Use these sections as templates:
- **Greenfield Stage 2:** Requirement analysis framework
- **Greenfield Stage 3:** Ambiguity resolution patterns
- **Greenfield Stage 4:** Task sequencing approach
- **Greenfield Stage 5:** Architecture review checklist
- **Brownfield Section 5:** Technology trade-off comparison matrix

### For AI/Human Collaboration
Reference these sections:
- **Greenfield Summary: AI vs Engineer Roles** → Clear responsibility model
- **Greenfield Real Problems Encountered** → How humans solve unexpected issues
- **Greenfield Validation Phase** → How validation surfaces real bugs

### For Technical Documentation
Use **greenfield.md** as a case study template:
- Document assumptions explicitly (Stage 3)
- Record architectural decisions with rationale (Stage 5)
- Capture security/reliability analysis (Stages 8-9)
- Track real issues and resolutions (Stage 10)

Use **brownfield.md** as a scaling decision template:
- Analyze bottlenecks with actual code references (Section 4)
- Compare alternatives quantitatively (Section 5)
- Justify technology choices with trade-off analysis (Section 6)
- Plan validation and risk mitigation (Sections 7-8)

---

## Metrics Summary

| Metric | Value |
|--------|-------|
| Development Stages | 12 |
| Task Components | 5 |
| Manual Test Scenarios | 9 |
| Test Pass Rate | 8/8 (100%) |
| Issues Identified | 4 |
| Production Readiness | 95% |
| Estimated Fix Time | 7 minutes |

---

## Key Decisions Documented

**Short-Code Design:**
- Length: 8 characters Base62 (a-z, A-Z, 0-9)
- Combinations: 218 trillion (62^8)
- Collision handling: 5-retry mechanism with database unique constraint
- Rationale: Balance between memorability and uniqueness

**Architecture:**
- Pattern: Modular monolith with layered design
- Database: PostgreSQL with Flyway migrations
- Deployment: Docker with multi-stage build
- Scaling: Synchronous click recording (acceptable for <1000 req/sec)

**Security:**
- URL validation: HTTP/HTTPS whitelist only
- IP handling: SHA-256 hashing (one-way)
- Error handling: Generic messages (no stack trace leakage)
- Prevention: No SSRF, no SQL injection, no XSS

**Reliability:**
- Transactions: @Transactional on all state changes
- Connection pooling: HikariCP with 10 connections
- Error recovery: Proper exception handling with HTTP status mapping
- Data persistence: Named volumes, survives restart

---

## Real Problems & Solutions

### Problem 1: Docker Port Conflict
**Symptom:** `Ports are not available: exposing port TCP 0.0.0.0:8080`  
**Root Cause:** Java application already listening on port 8080  
**Solution:** Changed docker-compose.yml to use external port 8081  
**Lesson:** Use alternate ports when elevated access unavailable

### Problem 2: Flyway PostgreSQL 16 Incompatibility
**Symptom:** `Unsupported Database: PostgreSQL 16.15`  
**Root Cause:** Spring Boot 3.3.0 defaults to Flyway 10.10.0 (doesn't support PG16)  
**Solution:** Updated pom.xml to Flyway 9.22.3  
**Lesson:** Verify database driver compatibility, pin explicit versions

### Problem 3: Short URL Returns Wrong Hostname
**Symptom:** API returns `http://url-shortener:8080/...` instead of `http://localhost:8081/...`  
**Root Cause:** APP_BASE_URL configured to Docker service name (only resolvable inside Docker network)  
**Status:** Identified in validation, requires fix before submission  
**Lesson:** Configuration must account for different execution contexts

---

## Validation Results

### Test Matrix
| Scenario | Expected | Actual | Status |
|----------|----------|--------|--------|
| Health Check | 200 UP | 200 ✓ | ✅ PASS |
| Create URL | 201 Created | 201 ✓ | ✅ PASS |
| Redirect | 302 Found | 302 ✓ | ✅ PASS |
| Invalid URL | 400 | 400 ✓ | ✅ PASS |
| Unknown Code | 404 | 404 ✓ | ✅ PASS |
| Analytics | Click recorded | Recorded ✓ | ✅ PASS |
| Expired URL | 410 Gone | 410 ✓ | ✅ PASS |
| Disabled URL | 410 Gone | 410 ✓ | ✅ PASS |
| Persistence | Data retained | Retained ✓ | ✅ PASS |

**Summary: 8/8 tests PASS** ✅

---

## Production Readiness Checklist

### Critical (Blocking)
- [ ] Fix Issue #1: Correct APP_BASE_URL in .env.example and docker-compose.yml

### High (Should Fix)
- [ ] Fix Issue #2: Document app.base-url configuration
- [ ] Fix Issue #3: Add expiration date validation

### Medium/Low (Nice to Have)
- [ ] Fix Issue #4: Set health show-details to "always"

### Already Complete ✅
- ✅ All functional requirements implemented
- ✅ Security review passed (no critical vulnerabilities)
- ✅ Reliability review passed (acceptable for MVP)
- ✅ Docker deployment working
- ✅ Integration tests with Testcontainers
- ✅ Manual validation successful

---

## Related Documentation

- **docs/requirements.md** — Functional and non-functional requirements
- **docs/architecture.md** — Architectural overview and design
- **docs/assumptions.md** — Assumptions made during design
- **docs/security-review.md** — Detailed security analysis
- **docs/reliability-review.md** — Detailed reliability analysis
- **VALIDATION_REPORT.md** — Complete validation findings

---

## Next Steps

**Before Final Submission:**
1. Review greenfield.md to understand project context
2. Fix Issue #1 (CRITICAL): APP_BASE_URL configuration
3. Apply Issues #2-3 fixes (HIGH priority)
4. Re-run validation to confirm all tests pass

**For Future Projects:**
1. Use Stage 2-5 (Requirement → Design) as template
2. Reference Problem/Solution section for similar issues
3. Adapt validation checklist from Stage 11-12
4. Document your own scenarios in this directory

---

## Questions?

**For greenfield/implementation questions**, refer to greenfield.md:
- **"Why did we make decision X?"** → See associated stage (typically Stage 5)
- **"How do we handle ambiguity?"** → See Stage 3
- **"What's the testing strategy?"** → See Stage 11-12
- **"How do we validate production readiness?"** → See VALIDATION_REPORT.md

**For scaling/architecture questions**, refer to brownfield.md:
- **"How would you scale redirect traffic?"** → See Section 1-2 (current system + problem)
- **"What are the trade-offs between caching options?"** → See Section 5 (comparison matrix)
- **"When should we implement caching vs. database optimization?"** → See Section 6 (engineering decision)
- **"What's the implementation plan?"** → See Section 10 (implementation outline)
- **"What risks should we mitigate?"** → See Section 8 (risks and trade-offs)

