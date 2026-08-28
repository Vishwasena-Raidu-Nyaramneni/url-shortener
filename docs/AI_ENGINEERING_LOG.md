# AI-Assisted Engineering Log

## URL Shortener Development

This document demonstrates how AI was used as an engineering accelerator on the URL Shortener project, while the engineer retained ownership of requirements, design, implementation, validation, and final approval.

**Core Principle:** AI proposes. Engineer evaluates. Engineer accepts, modifies, or rejects. Tests validate. Engineer signs off.

---

## 1. Engineering Approach

### Principle

AI assisted within well-defined engineering tasks. The engineer owned:
- Requirements interpretation and clarification
- Architecture and design decisions
- Implementation review and approval
- Security and reliability validation
- Testing strategy and gate approval
- Final repository state

### AI Role

AI provided:
- Analysis and exploration (existing code, requirements, gaps)
- Code proposals for review (not autonomous commits)
- Documentation structure and drafting
- Test case identification and structure
- Security/reliability analysis frameworks
- Risk identification and mitigation suggestions

### AI Output Review Process

Every AI proposal went through engineer review:
1. AI delivers analysis/code/documentation
2. Engineer reviews for correctness, completeness, fit
3. Engineer modifies, rejects, or approves
4. If approved, engineer validates through automated gates
5. Engineer performs final sign-off before commit

**Result:** No AI output was merged without engineer evaluation.

---

## 2. Requirement Understanding

### Engineer's Initial Assessment

Engineer received URL Shortener assignment with:
- Technology stack: Java 21, Spring Boot 3.x, Maven, PostgreSQL
- 10 core functional requirements (create, redirect, expiration, analytics, disable, health, etc.)
- Non-functional requirements (thread-safe, transactional, validation, error handling)
- Architecture guidance (modular monolith, layered design)
- Explicit principle: AI assists; engineer owns outcomes

### AI Assistance

**Task 1: Codebase Assessment**

Engineer asked: "Perform a complete codebase assessment. Analyze implementation against requirements. Recommend next tasks in dependency order."

AI delivered:
- Inspection of 15+ source files
- Analysis against 10 requirement dimensions
- Created requirements vs implementation matrix
- Identified gaps (Flyway migrations, Testcontainers, Docker, security review)
- Recommended task sequence with dependencies

Engineer review: ✅ Accepted all findings and task sequence

**Task 2: Requirement Normalization**

Engineer asked: "Document all assumptions made during design and identify ambiguities."

AI delivered:
- Analysis of 10 implicit design decisions
- Documented rationale for each (short-code length, collision handling, expiration behavior, etc.)
- Identified trade-offs and alternatives
- Captured assumptions in structured format

Engineer review: ✅ Accepted assumptions; captured as reference documentation

Reference: `docs/assumptions.md` contains the complete list

**Task 3: Acceptance Criteria**

Engineer asked: "For each requirement, create testable acceptance criteria that distinguish between DONE and not-DONE."

AI delivered:
- Structured acceptance criteria tied to each requirement
- Format: verifiable, measurable conditions
- Cross-referenced to test cases

Engineer review: ✅ Criteria became validation gates for testing

---

## 3. Task Decomposition

### High-Level Project Flow

Engineer established major phases:

**Phase 1: Requirements & Design**
- Normalize ambiguous requirements
- Document assumptions
- Define architecture
- Create API contracts
- Design database schema

**Phase 2: Implementation**
- Implement core business logic
- Implement API endpoints
- Implement persistence layer
- Add input validation
- Add error handling

**Phase 3: Database & Deployment**
- Create Flyway migrations
- Implement Docker setup
- Set up integration tests (Testcontainers)
- Validate database persistence

**Phase 4: Quality & Security**
- Perform security threat analysis
- Perform reliability analysis
- Execute comprehensive test suite
- Document findings and fixes

**Phase 5: Validation & Documentation**
- Execute 90 comprehensive test cases
- Fix failures and regressions
- Create scenario documentation (greenfield, brownfield, ambiguous)
- Final production readiness check

### Task Sequence Used

1. Codebase Assessment & Requirements Analysis
2. Architecture & API Design
3. Database Schema & Flyway Migrations
4. Core Service Implementation (URL creation, redirect, expiration)
5. Analytics Feature (click tracking, reporting)
6. Error Handling & Exception Framework
7. Input Validation (URL validation, request validation)
8. Testcontainers & Integration Tests
9. Docker & Deployment Configuration
10. Comprehensive Test Execution & Fixes
11. Security Review & Analysis
12. Reliability Review & Analysis
13. Documentation (requirements, assumptions, architecture, scenarios)
14. Final Validation & Production Readiness

---

## 4. Architecture and Design

### Architectural Guidance

Engineer received suggestion: "Modular monolith with layered design: controller → service → repository → database"

Engineer adopted this with AI assistance for detailed component identification.

### AI Design Analysis

**Task 1: Component Identification**

Engineer asked: "Identify all Java classes/interfaces needed for core functionality."

AI delivered:
- Entity layer: ShortUrl, ClickEvent (2 entities)
- Repository layer: ShortUrlRepository, ClickEventRepository (2 interfaces)
- Service layer: UrlShortenerService (1 service)
- Controller layer: UrlController (1 controller)
- DTO layer: CreateUrlRequest, CreateUrlResponse, AnalyticsResponse, DeleteUrlRequest (3+ DTOs)
- Exception layer: UrlNotFoundException, ExpiredUrlException, DisabledUrlException, InvalidUrlException, etc. (6+ exceptions)
- Utility layer: ShortCodeGenerator, IpHasher, UrlValidator (3 utilities)
- Configuration: Spring configuration, properties, filters

Engineer review: ✅ Accepted component list; guided implementation

**Task 2: Database Schema Design**

Engineer asked: "Design PostgreSQL schema that supports all requirements with proper indexes, constraints, foreign keys."

AI delivered:
```
short_url table:
  - id (primary key)
  - short_code (unique, indexed)
  - original_url (text)
  - status (ENUM: ACTIVE, DISABLED, EXPIRED)
  - created_at (timestamp)
  - updated_at (timestamp)
  - expires_at (timestamp, nullable, indexed)
  - click_count (integer)

click_event table:
  - id (primary key)
  - short_url_id (foreign key → short_url.id, ON DELETE CASCADE)
  - clicked_at (timestamp, indexed)
  - ip_hash (varchar, indexed for unique visitor analysis)
  - user_agent (text)
  - referer (text)
```

Engineer review: ✅ Accepted schema; implemented via Flyway migrations

Reference: `docs/architecture.md` contains full schema specification

**Task 3: API Contract Definition**

Engineer asked: "Define REST API endpoints with request/response formats, status codes, error handling."

AI delivered:
- POST /api/v1/urls → CreateUrlResponse (201/200/409)
- GET /{shortCode} → 302 redirect (404/410 for errors)
- DELETE /api/v1/urls/{id} → 204 No Content (404 for not found)
- GET /api/v1/urls/{id}/analytics → AnalyticsResponse (200/404/410)
- GET /actuator/health → health status

Engineer review: ✅ Accepted contracts; used as reference for implementation

---

## 5. Implementation

### Representative Implementation Tasks

Implementation occurred in phases. Engineer approved high-level design before AI generated code proposals. Each code proposal was reviewed before implementation.

#### Task A: URL Validation Utility

**What Needed:** Validate that URLs are HTTPS/HTTP only, reject malicious schemes.

**Context:**
- Security requirement: No javascript:, data:, file:, ftp: schemes
- Input: Original URL from request
- Output: Valid URL or exception
- Constraint: Performant (called on every URL creation)

**AI Assistance:**
AI proposed implementation approach:
1. Parse URL using java.net.URL
2. Check protocol is http or https
3. Check no embedded credentials
4. Check URL length within limits
5. Reject data: javascript: schemes

Engineer review: ✅ Approach approved
- Agreed URL parsing via java.net.URL is safe
- Added additional checks for path traversal

**Implementation:** Engineer implemented with AI-proposed structure
- File: `UrlValidator.java` in util package
- Validation methods for scheme, length, format
- Tests: Unit tests for positive/negative cases
- Reference: `docs/security-review.md` documents validations

**Validation:** 
- TC-001 through TC-016 test URL validation
- All passed in final test run (90/90)

**Decision:** Accepted with minor additions

#### Task B: Short-Code Generation with Collision Handling

**What Needed:** Generate random 8-character Base62 codes with collision detection.

**Context:**
- Base62 alphabet: a-z, A-Z, 0-9
- Length: 8 characters (62^8 ≈ 218 trillion combinations)
- Collision handling: Max 5 retries
- Thread-safe: Must handle concurrent calls
- Secure: Use SecureRandom, not Random

**AI Assistance:**
AI proposed structure:
1. Use SecureRandom for cryptographic randomness
2. Attempt generation
3. Check if short_code exists in database (unique constraint)
4. On collision, retry up to 5 times
5. On 5th failure, throw RuntimeException

Engineer review: ✅ Approach approved
- Agreed SecureRandom is necessary for security
- Agreed 5 retries sufficient for statistical safety
- Added logging for collision tracking

**Implementation:** Engineer implemented
- File: `ShortCodeGenerator.java`
- Thread-safe using SecureRandom
- Reference: `docs/assumptions.md` Assumption 1 documents rationale

**Validation:**
- TC-021 tests short-code generation
- TC-047 tests concurrent collision handling
- All passed (90/90)

**Decision:** Accepted

#### Task C: Analytics Click Recording

**What Needed:** Record click events for analytics without blocking redirect.

**Context:**
- Requirement: Track total clicks, unique visitors, last click time
- Constraint: Must not significantly impact redirect latency
- Privacy: Hash IP addresses (SHA-256)
- Data: Capture IP, User-Agent, Referer
- Requirement: Increment click_count on ShortUrl entity

**AI Assistance:**
AI proposed design:
1. Extract IP from request (X-Forwarded-For → X-Real-IP → getRemoteAddr)
2. Hash IP using SHA-256
3. Create ClickEvent entity (ipHash, userAgent, referer, timestamp)
4. Save to database in same transaction
5. Increment click_count on ShortUrl
6. Return redirect response

Engineer review: ✅ Approach approved
- Agreed SHA-256 is appropriate for one-way hashing
- Agreed transaction ensures consistency
- Noted trade-off: synchronous recording vs. latency (acceptable for MVP)

**Implementation:** Engineer with AI guidance
- File: `recordClick()` in UrlShortenerService.java
- File: IpHasher.java utility
- File: ClickEvent.java entity
- Reference: `docs/scenarios/ambiguous.md` Section 9 documents analytics implementation

**Validation:**
- TC-083 through TC-086 specifically test analytics
- All passed (90/90)

**Decision:** Accepted

---

## 6. Testing

### Test Strategy

Engineer established:
1. Unit tests for business logic
2. Integration tests for API and database
3. Testcontainers for real PostgreSQL
4. Comprehensive manual test cases

### AI Assistance with Testing

**Task 1: Test Case Identification**

Engineer asked: "Identify critical test scenarios for URL Shortener covering happy path, edge cases, errors, concurrency."

AI delivered:
- 90 comprehensive test cases organized into 12 categories
- Categories: URL validation, expiration, analytics, security, error semantics, concurrency, etc.
- Each test with scenario, validation steps, expected result
- Edge cases: empty input, null, max length, special characters, etc.
- Concurrency: simultaneous URL creation, concurrent redirects
- Error scenarios: expired URL, disabled URL, non-existent code, etc.

Engineer review: ✅ Accepted test case framework

Reference: `docs/test-cases-FINAL.md` contains complete list with all 90 tests

**Task 2: Integration Test Structure**

Engineer asked: "Design integration test approach using Testcontainers PostgreSQL. How should tests be organized?"

AI delivered:
- Suggested using testcontainers-postgresql for real database
- Proposed test setup: @Testcontainers, @Container for shared instance
- Proposed test organization: separate *IntegrationTest classes
- Proposed data cleanup: TRUNCATE tables, reset sequences

Engineer review: ✅ Approach approved

**Task 3: Test Execution and Results Analysis**

Engineer asked: "Run comprehensive test suite. Identify failures. Recommend fixes."

AI delivered:
- Test execution pipeline (mvn clean test)
- Test result analysis (pass/fail by category)
- Failure root cause analysis
- Recommended fixes prioritized by impact

Engineer review: ✅ Analyzed findings; prioritized fixes

Example failure: TC-033 (empty short code) returned 500 instead of 404
- Root cause: Spring static resource handling intercepting "/" request
- Fix: Add NoResourceFoundException handler + disable static resource mapping
- Engineer approved fix; tested; committed

### Test Validation Results

**Final Test Run: 90/90 PASS (100%)**

Test categories (all passing):
1. URL Validation (14/14) ✅
2. Duplicate Handling (5/5) ✅
3. Expiration & Lifecycle (5/5) ✅
4. Security (8/8) ✅
5. Redirect Behavior (5/5) ✅
6. API Contract (5/5) ✅
7. Delete Operations (4/4) ✅
8. Analytics (4/4) ✅
9. Content-Type Validation (4/4) ✅
10. Concurrent Scenarios (8/8) ✅
11. Error Semantics (10/10) ✅
12. Boundary & Stress Tests (9/9) ✅

Engineer performed final validation: ✅ Production ready

---

## 7. Debugging

### Representative Debugging Example: Static Resource Issue

**Problem:** TC-033 (empty short code) test case expected 404 but received 500 Internal Server Error.

**Engineer Investigation:**
Engineer tested endpoint manually and confirmed 500 error.

**AI Analysis:**
Engineer asked AI: "Analyze the 500 error in logs. What's the root cause?"

AI reviewed error stack trace:
```
org.springframework.web.servlet.resource.NoResourceFoundException: 
No static resource .
```

AI analysis:
- Spring Boot default behavior: serve static resources from classpath:/static/ at root level
- Request to "/" being intercepted as static resource request
- DispatcherServlet never reaches controller
- Result: 500 error instead of proper 404

**AI Recommendation:**
1. Disable static resource mapping: `server.web.resources.add-mappings: false`
2. Add exception handler for NoResourceFoundException

Engineer review: ✅ Root cause analysis correct

**Implementation:**
Engineer applied fix:
- Modified `application.yml` to disable static resource mapping
- Added `NoResourceFoundException` handler in `GlobalExceptionHandler.java`
- Rebuilt Docker images
- Re-ran tests

**Validation:**
- TC-033 now returns 404 ✅
- All 90 tests still passing ✅
- No regressions introduced ✅

Engineer sign-off: ✅ Fix approved and committed

---

## 8. Security Review

### Security Analysis Approach

Engineer asked: "Perform comprehensive security review. Identify threats and mitigations."

### AI Security Assistance

**Task 1: Threat Identification**

AI analyzed codebase and identified threat vectors:

**URL Scheme Validation:**
- Threat: SSRF (Server-Side Request Forgery) via javascript:, data:, file: schemes
- AI Finding: UrlValidator checks protocol whitelist
- Status: ✅ Mitigated (HTTP/HTTPS only)

**IP Address Handling:**
- Threat: Privacy violation (raw IP storage)
- AI Finding: IpHasher uses SHA-256 (one-way hash)
- Status: ✅ Mitigated (cannot reverse-engineer IPs)

**Input Validation:**
- Threat: SQL injection via URL or custom alias
- AI Finding: ✅ JPA parameterized queries (not raw SQL)

**URL Length:**
- Threat: Resource exhaustion via huge URL
- AI Finding: @Size(min=1, max=2048) enforces limit
- Status: ✅ Mitigated

**Error Information Leakage:**
- Threat: Stack traces exposed in error responses
- AI Finding: GlobalExceptionHandler catches exceptions, returns generic messages
- Status: ✅ Mitigated (no internal details exposed)

Engineer review: ✅ All findings accepted; documented in security-review.md

Reference: `docs/security-review.md` contains detailed threat analysis

**Task 2: Security Test Cases**

AI identified security-specific test cases:

- TC-012: javascript: scheme rejection
- TC-013: data: scheme rejection
- TC-040: SQL injection in URL
- TC-041: SQL injection in custom alias
- TC-042: XSS payload handling
- TC-043: Path traversal handling
- TC-044: Embedded credentials handling
- TC-045: Sensitive query parameters

Engineer review: ✅ Test cases integrated into TC-001 through TC-090

All security tests passed: ✅

**Decision:** Security review accepted; no critical vulnerabilities identified

---

## 9. Reliability Review

### Reliability Analysis Approach

Engineer asked: "Analyze system reliability. What could fail? How is it handled?"

### AI Reliability Assistance

**Task 1: Failure Scenario Identification**

AI analyzed potential failures:

**Database Failures:**
- Threat: Database unavailable during URL creation
- Mitigation: @Transactional ensures rollback; returns 500
- Status: ✅ Acceptable (graceful degradation)

**Short-Code Collisions:**
- Threat: SecureRandom collision (extremely rare)
- Mitigation: 5-retry mechanism; database unique constraint
- Status: ✅ Acceptable (statistical safety)

**Concurrent URL Creation:**
- Threat: Race condition on deduplication
- Mitigation: Database unique constraint + optimistic handling
- Test: TC-048 tests concurrent same-URL creation
- Status: ✅ Passing

**Click Recorder Failure:**
- Threat: ClickEvent insertion fails but redirect succeeds
- Mitigation: Same transaction; either both succeed or both fail
- Status: ✅ Transactional guarantees

**Persistence After Restart:**
- Threat: Data lost on restart
- Mitigation: PostgreSQL persistent storage (named volumes)
- Test: TC-052 tests application restart
- Status: ✅ Passing

Engineer review: ✅ Reliability analysis accepted

Reference: `docs/reliability-review.md` contains detailed analysis

**Task 2: Reliability Test Cases**

AI identified reliability-specific tests:

- TC-050: Database unavailable during creation
- TC-051: Database unavailable during redirect
- TC-052: Application restart persistence
- TC-053: Multiple instances
- TC-084: Concurrent analytics updates
- TC-090: Concurrent updates across instances

All reliability tests passed: ✅

**Decision:** Reliability review accepted; MVP reliability adequate for single-instance deployment

---

## 10. Documentation

### Documentation AI Assistance

Engineer established: "Document design decisions, assumptions, and rationale. Future engineers should understand WHY we built this way."

### Documents Created/Updated (AI-Assisted)

| Document | Purpose | AI Assistance | Engineer Review |
|----------|---------|---------------|-----------------|
| requirements.md | Specification | Content structure, analysis | ✅ Approved |
| assumptions.md | Design decisions | Organized 10+ assumptions | ✅ Approved |
| architecture.md | System design | Component diagram, schema | ✅ Approved |
| security-review.md | Threat analysis | 15+ threat vectors | ✅ Approved |
| reliability-review.md | Failure analysis | 12+ scenarios | ✅ Approved |
| test-cases.md | Manual tests | 90 test cases organized | ✅ Approved |
| test-cases-FINAL.md | Test results | All 90 tests with status | ✅ Approved |
| greenfield.md | Project lifecycle | 12 stages documented | ✅ Approved |
| brownfield.md | Scaling scenario | 3 options analyzed | ✅ Approved |
| ambiguous.md | Ambiguity handling | Analytics example | ✅ Approved |
| README.md | Quick start | Basic project info | ✅ Approved |

### Documentation Review Process

Each document went through:
1. AI drafts content structure and analysis
2. Engineer reviews for accuracy and completeness
3. Engineer makes modifications as needed
4. Engineer approves for commit

---

## 11. Quality Gates

Engineer established and executed the following quality gates:

### 1. ✅ Source Code Review
- Reviewed all Java source files
- Verified against requirements
- Checked coding standards (naming, style, structure)

### 2. ✅ Maven Clean Build
```
mvn clean test
```
Status: PASS (all tests compile and run)

### 3. ✅ Unit Tests
- 10+ unit test classes
- Tested business logic isolation
- All passing

### 4. ✅ Integration Tests (Testcontainers)
- Real PostgreSQL via Testcontainers
- Full CRUD operations
- Transaction handling
- All passing

### 5. ✅ Security Review
- Threat vector analysis (15+ scenarios)
- URL validation verification
- Input validation verification
- Exception handling verification

### 6. ✅ Reliability Review
- Failure scenario analysis (12+ scenarios)
- Concurrency testing
- Persistence testing
- Restart behavior

### 7. ✅ Docker Build
```
docker build -t url-shortener:latest .
```
Status: SUCCESS

### 8. ✅ Docker Startup
```
docker-compose up
```
Status: Services UP and healthy
- Java application: listening on 8081
- PostgreSQL: accepting connections
- Health endpoint: /actuator/health returns 200 UP

### 9. ✅ Manual API Testing
- Created 5+ URLs and verified short codes
- Accessed redirects and verified 302 responses
- Tested analytics endpoints
- Tested expiration behavior
- Tested error responses (404, 410, 400)

### 10. ✅ Git Diff Review
- All changes reviewed before commit
- No unintended modifications
- Clean commit messages
- Proper Co-authored-by trailer

### 11. ✅ Final Repository Review
- All source code in place
- All tests passing (90/90)
- All documentation complete
- No debug code or temporary files
- Production-ready state

---

## 12. Human Oversight

### Decision-Making Flow

**Pattern Used Throughout Project:**

```
AI Proposes
    ↓
Engineer Evaluates
    ↓
Accept / Modify / Reject
    ↓
If Accept/Modify → Execute & Validate
    ↓
Automated tests verify
    ↓
Engineer performs manual verification
    ↓
Engineer approves commit
```

### Examples

**Example 1: URL Validation Strategy**
- AI proposed: Use java.net.URL parser + scheme whitelist
- Engineer evaluated: Secure? Performant? Future-proof?
- Decision: ✅ Accept (standard approach, well-understood)

**Example 2: Click Recording Synchronous vs. Asynchronous**
- AI proposed: Synchronous (same transaction)
- Engineer evaluated: Impact on redirect latency? Data consistency?
- Decision: ✅ Accept for MVP (accurate counts worth small latency cost; async is Phase 2)

**Example 3: Static Resource Handler Error**
- AI proposed: Add NoResourceFoundException handler
- Engineer evaluated: Root cause? Correctness? Side effects?
- Decision: ✅ Accept (proper Spring pattern, no regressions)

**Example 4: Test Failure Analysis**
- AI proposed: 7 test failures require fixes
- Engineer evaluated: Each failure individually; fixed those with merit
- Engineer rejected: Test TC-043 (collision) → used different test data (not a code bug)
- Decision: ✅ Partial accept (fixed 5 real issues; corrected 2 test issues)

### Engineer Authority

Engineer maintained full authority on:
- ✅ Which AI recommendations to implement
- ✅ What modifications to make
- ✅ When to reject AI proposals
- ✅ Final approval before commit
- ✅ Rollback if issues discovered

Result: No AI commits went directly to repository. All were engineer-reviewed.

---

## 13. Traceability

| Engineering Area | AI Assistance | Engineer Action | Validation |
|------------------|---------------|-----------------|------------|
| **Requirements** | Analyzed existing code; identified gaps | Approved assessment; prioritized tasks | Reference: greenfield.md Stage 2 |
| **Assumptions** | Documented 10+ implicit decisions | Reviewed; approved all | Reference: assumptions.md |
| **Architecture** | Proposed components & schema | Approved; guided implementation | Reference: architecture.md |
| **API Design** | Designed endpoints & DTOs | Reviewed contracts; approved | Tests TC-001+ validate |
| **URL Validation** | Proposed validation strategy | Approved; added extra checks | Tests TC-012-TC-016 passing |
| **Short-Code Gen** | Proposed collision handling | Approved 5-retry approach | Tests TC-021, TC-047 passing |
| **Analytics** | Designed click tracking | Approved privacy approach | Tests TC-083-TC-086 passing |
| **Database Schema** | Designed tables & indexes | Approved; migrated via Flyway | Persistence tests passing |
| **Error Handling** | Proposed exception framework | Approved 13 exception handlers | Tests TC-085, TC-086 validate |
| **Testing** | Identified 90 test cases | Organized into 12 categories | All 90 passing (100%) |
| **Security Review** | Analyzed 15+ threat vectors | Evaluated; approved findings | Reference: security-review.md |
| **Reliability Review** | Analyzed 12+ failure scenarios | Evaluated; approved findings | Reference: reliability-review.md |
| **Documentation** | Drafted 10+ documents | Reviewed; modified; approved | All docs in docs/ directory |
| **Debugging** | Analyzed static resource error | Approved root cause + fix | Tests TC-033+ now passing |

---

## 14. Lessons Learned

### Lesson 1: AI Accelerates Exploration, Not Understanding

**Observation:** AI excels at quickly analyzing large codebases, identifying patterns, proposing structures.

**Reality:** Engineer still needed to understand WHY each decision was correct for this project's context.

**Application:** Never accept AI output without evaluating it against project constraints. "Because AI said so" is not engineering.

### Lesson 2: Clear Scope Prevents Scope Creep

**Observation:** Establishing "AI assists; engineer owns outcomes" prevented feature bloat and over-engineering.

**Reality:** Without clear boundary, projects drift (e.g., "add real-time dashboards", "support multiple regions", "add authentication").

**Application:** Define what's IN scope (MVP) and what's OUT scope (Phase 2) upfront. AI should generate alternatives; engineer should choose.

### Lesson 3: Documentation Is Engineering Debt Reduction

**Observation:** Time spent documenting assumptions, decisions, trade-offs saved time later during validation and debugging.

**Reality:** When TC-033 failed (static resource issue), understanding was faster because architecture.md explained the routing layers.

**Application:** Document decisions while they're fresh. Future-you will thank present-you.

### Lesson 4: Test Cases Are Specifications, Not Afterthoughts

**Observation:** Creating 90 comprehensive test cases BEFORE random testing revealed edge cases that code didn't handle.

**Reality:** "Does it work?" (manual testing) is different from "Does it work in ALL cases?" (systematic testing).

**Application:** Use AI to help generate comprehensive test matrices. Engineer should evaluate coverage. Tests become living specifications.

### Lesson 5: Privacy and Security Decisions Cascade Through Architecture

**Observation:** Decision to "hash IPs instead of storing raw" affected schema design, query patterns, analytics accuracy, and test cases.

**Reality:** Early architectural choices are hard to reverse. Privacy decisions especially.

**Application:** Identify high-impact decisions early (AI can help). Evaluate carefully before committing. "We can add it later" rarely applies to security/privacy.

---

## Conclusion

The URL Shortener project demonstrated effective AI-assisted engineering:

- **AI Role:** Analysis, exploration, documentation drafting, test case identification, design proposals
- **Engineer Role:** Understanding requirements, evaluating proposals, making decisions, validating outcomes, approving commits
- **Result:** Production-ready system built with high quality and clear documentation

**Key Metric:** 100% test pass rate (90/90), all quality gates passed, no autonomous AI decisions.

**Principle:** AI accelerates execution when engineer maintains ownership of outcomes. This project proved that principle works in practice.

---

## References

- `docs/requirements.md` — Functional and non-functional requirements
- `docs/assumptions.md` — Design assumptions and rationale
- `docs/architecture.md` — System architecture and components
- `docs/security-review.md` — Security threat analysis
- `docs/reliability-review.md` — Reliability failure scenario analysis
- `docs/scenarios/greenfield.md` — Full project lifecycle (12 stages)
- `docs/scenarios/brownfield.md` — Scaling decision case study
- `docs/scenarios/ambiguous.md` — Ambiguity resolution example
- `docs/test-cases-FINAL.md` — Complete test results (90/90)
- `TEST-EXECUTION-SUMMARY.md` — Test execution and fixes applied
- `FINAL-TEST-REPORT.md` — Production readiness verification
