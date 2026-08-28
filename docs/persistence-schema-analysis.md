# URL Shortener — Persistence Schema Analysis

## Executive Summary

**Current State:**
- JPA entities defined: `ShortUrl`, `ClickEvent`
- Repositories configured: `ShortUrlRepository`, `ClickEventRepository`
- Database migration exists: `V1__Initial_schema.sql`
- ORM Configuration: Hibernate with PostgreSQL dialect
- Migration tool: Flyway
- DDL Auto: `validate` (schema must exist, Hibernatne does not create)

**Key Finding:**
✅ **MATCH: JPA entities and Flyway migration are in sync**

The entity annotations and migration SQL define equivalent schemas. No significant mismatches detected.

---

## 1. Table Definitions

### 1.1 SHORT_URL Table

**Purpose:** Stores shortened URL mappings and metadata.

**Definition (from migration V1):**
```sql
CREATE TABLE short_url (
    id BIGSERIAL PRIMARY KEY,
    short_code VARCHAR(20) NOT NULL UNIQUE,
    original_url TEXT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE,
    click_count BIGINT NOT NULL DEFAULT 0
);
```

**JPA Entity Mapping (ShortUrl.java):**

| Entity Field | SQL Column | Type | Nullable | Default | Unique | Notes |
|--------------|-----------|------|----------|---------|--------|-------|
| id | id | BIGSERIAL | NO | (auto) | PK | @GeneratedValue(IDENTITY) |
| shortCode | short_code | VARCHAR(20) | NO | - | YES | @NotBlank, unique=true |
| originalUrl | original_url | TEXT | NO | - | NO | @NotBlank, columnDefinition=TEXT |
| status | status | VARCHAR(20) | NO | 'ACTIVE' | NO | @NotNull, length=20 |
| createdAt | created_at | TIMESTAMP WITH TIME ZONE | NO | - | NO | updatable=false, @PrePersist |
| updatedAt | updated_at | TIMESTAMP WITH TIME ZONE | NO | - | NO | @PreUpdate |
| expiresAt | expires_at | TIMESTAMP WITH TIME ZONE | YES | NULL | NO | nullable (soft expiration) |
| clickCount | click_count | BIGINT | NO | 0 | NO | default=0L |

**Analysis:**
✅ Match: Column types, nullability, defaults all align with JPA annotations.
✅ Timestamp strategy: OffsetDateTime (TIMESTAMP WITH TIME ZONE) is correct for timezone-aware storage.
⚠️ Note: DEFAULT 'ACTIVE' in SQL not enforced by JPA; application sets this in constructor.

---

### 1.2 CLICK_EVENT Table

**Purpose:** Records individual click events for analytics.

**Definition (from migration V1):**
```sql
CREATE TABLE click_event (
    id BIGSERIAL PRIMARY KEY,
    short_url_id BIGINT NOT NULL REFERENCES short_url(id) ON DELETE CASCADE,
    clicked_at TIMESTAMP WITH TIME ZONE NOT NULL,
    ip_hash VARCHAR(64),
    user_agent TEXT,
    referer TEXT
);
```

**JPA Entity Mapping (ClickEvent.java):**

| Entity Field | SQL Column | Type | Nullable | FK | Notes |
|--------------|-----------|------|----------|----|----|
| id | id | BIGSERIAL | NO | PK | @GeneratedValue(IDENTITY) |
| shortUrl | short_url_id | BIGINT | NO | FK short_url(id) | @ManyToOne, @JoinColumn, @ForeignKey |
| clickedAt | clicked_at | TIMESTAMP WITH TIME ZONE | NO | - | @NotNull, updatable=false, @PrePersist |
| ipHash | ip_hash | VARCHAR(64) | YES | - | nullable (hashed client IP) |
| userAgent | user_agent | TEXT | YES | - | nullable (User-Agent header) |
| referer | referer | TEXT | YES | - | nullable (Referer header) |

**Analysis:**
✅ Match: FK relationship defined correctly in both SQL and JPA.
✅ Cascade delete configured: ON DELETE CASCADE (database constraint).
⚠️ Note: JPA @ManyToOne does NOT cascade on Hibernate delete (would need @OneToMany on ShortUrl side).
✅ Timestamp strategy: OffsetDateTime (TIMESTAMP WITH TIME ZONE).

---

## 2. Column Types and Mappings

| JPA Type | PostgreSQL Type | Rationale | Notes |
|----------|-----------------|-----------|-------|
| Long (id) | BIGSERIAL | 64-bit identity | Auto-increment via BIGSERIAL |
| String (short_code) | VARCHAR(20) | 8-char Base62 + margin | Matches max length annotation |
| String (original_url) | TEXT | Unbounded text | Supports URLs up to 2048 chars |
| String (status) | VARCHAR(20) | ACTIVE, DISABLED enum-style | Not strict enum type (flexibility) |
| OffsetDateTime | TIMESTAMP WITH TIME ZONE | Timezone-aware | Java 8+ java.time API |
| Long (click_count) | BIGINT | Counter | Supports very large numbers |
| String (ip_hash) | VARCHAR(64) | SHA-256 hex digest | 64 chars for hex-encoded 256 bits |
| String (user_agent) | TEXT | HTTP header value | Unbounded user agent strings |
| String (referer) | TEXT | HTTP header value | Unbounded referer URLs |

**Analysis:**
✅ All types are appropriate for the domain.
✅ No type mismatches between JPA and SQL.

---

## 3. Primary Keys

### short_url.id
- **Type:** BIGSERIAL (auto-increment)
- **JPA Strategy:** GenerationType.IDENTITY
- **SQL:** PRIMARY KEY
- **Rationale:** 
  - Sequential IDs for table identification (distinct from short codes, which are random)
  - BIGSERIAL provides 64-bit range (9.2 × 10^18 possible values)
  - Identity strategy matches Hibernate PostgreSQL dialect defaults

### click_event.id
- **Type:** BIGSERIAL (auto-increment)
- **JPA Strategy:** GenerationType.IDENTITY
- **SQL:** PRIMARY KEY
- **Rationale:** Same as above; click_event requires unique ID for each record

**Composite Keys:** None used. Single-column PKs appropriate for this schema.

---

## 4. Foreign Keys

### click_event.short_url_id → short_url.id

**SQL Definition:**
```sql
REFERENCES short_url(id) ON DELETE CASCADE
```

**JPA Definition:**
```java
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "short_url_id", nullable = false, foreignKey = @ForeignKey(name = "fk_short_url_id"))
private ShortUrl shortUrl;
```

**Referential Behavior:**
- **ON DELETE CASCADE:** ✅ Configured in SQL
- When ShortUrl is deleted, all associated ClickEvent rows are automatically deleted
- Database enforces referential integrity

**Cascade in Hibernate:**
- ⚠️ **Limitation:** @ManyToOne without CascadeType.REMOVE does not cascade deletes
- Hibernate deletes ClickEvent rows only if explicitly configured with @OneToMany(cascade = CascadeType.REMOVE) on ShortUrl side
- However, database-level ON DELETE CASCADE still prevents FK violations
- Application currently relies on database constraint (acceptable for MVP)

**Lazy Loading:**
- ✅ FetchType.LAZY configured (good for performance)
- ClickEvent queries won't eagerly fetch ShortUrl unless accessed

---

## 5. Unique Constraints

### short_url.short_code
- **Type:** Unique index/constraint
- **SQL:** `UNIQUE` on column definition + explicit index `idx_short_code`
- **JPA:** `unique = true` in @Column annotation + @Index
- **Enforcement:** Database enforces uniqueness
- **Rationale:** Ensure short codes are globally unique (no collisions)
- **Usage:** 
  - Collision detection triggers DataIntegrityViolationException
  - Service catches and retries on new short code

**No Other Unique Constraints:**
- created_at, updated_at, expires_at: Allow duplicates (okay for audit trail)
- ip_hash, user_agent, referer: Allow duplicates (expected; multiple clicks from same visitor)

---

## 6. Indexes

| Index Name | Table | Columns | Type | Rationale |
|------------|-------|---------|------|-----------|
| **idx_short_code** | short_url | short_code | UNIQUE | FK lookups, collision detection |
| **idx_status** | short_url | status | BTREE | Filter ACTIVE/DISABLED URLs |
| **idx_expires_at** | short_url | expires_at | BTREE | Find expired URLs (cleanup queries) |
| **idx_short_url_id** | click_event | short_url_id | BTREE | Join from short_url to clicks |
| **idx_clicked_at** | click_event | clicked_at | BTREE | Analytics queries (time range) |

**Query Patterns These Support:**

1. **idx_short_code:**
   ```sql
   SELECT * FROM short_url WHERE short_code = 'abc12345' -- Redirect lookup
   INSERT INTO short_url (...) -- Collision detection
   ```

2. **idx_status:**
   ```sql
   SELECT * FROM short_url WHERE status = 'ACTIVE' -- Status filtering
   ```

3. **idx_expires_at:**
   ```sql
   -- Future use: cleanup expired URLs
   SELECT * FROM short_url WHERE expires_at < NOW()
   ```

4. **idx_short_url_id:**
   ```sql
   SELECT * FROM click_event WHERE short_url_id = 123 -- Analytics retrieval
   COUNT(DISTINCT ip_hash) WHERE short_url_id = 123 -- Unique visitors
   ```

5. **idx_clicked_at:**
   ```sql
   -- Future use: time-range analytics
   SELECT * FROM click_event WHERE clicked_at BETWEEN ? AND ? WHERE short_url_id = 123
   ```

**Analysis:**
✅ All indexes defined in JPA entity annotations.
✅ All indexes present in SQL migration.
✅ No redundant indexes.
⚠️ Composite indexes not used (appropriate for current query patterns).

---

## 7. Referential Behavior

### Foreign Key: short_url_id → short_url.id

**Constraint Definition:**
```sql
REFERENCES short_url(id) ON DELETE CASCADE
```

**Behavior on DELETE:**
1. User deletes short URL: `DELETE FROM short_url WHERE id = 123`
2. Database cascade automatically executes: `DELETE FROM click_event WHERE short_url_id = 123`
3. All click events for that URL are removed
4. Referential integrity maintained

**Behavior on UPDATE:**
- No ON UPDATE CASCADE (not needed)
- short_url.id is IDENTITY (immutable)
- short_url_id FK cannot be updated

**Constraint Enforcement:**
- ✅ Database enforces: INSERT/UPDATE click_event with invalid short_url_id → ERROR
- ✅ Database enforces: DELETE short_url → CASCADE deletes click_event
- ⚠️ Hibernate does not cascade without explicit @OneToMany(cascade=DELETE) on inverse side
  - Workaround: Database constraint provides safety
  - Not ideal for ORM consistency (but acceptable for MVP)

**Recommendation for Production:**
Consider adding @OneToMany relationship to ShortUrl entity:
```java
@OneToMany(mappedBy = "shortUrl", cascade = CascadeType.REMOVE)
private List<ClickEvent> clickEvents;
```

This would:
- Allow Hibernate to cascade delete programmatically
- Maintain ORM consistency
- Not change database behavior (still relies on FK constraint)

---

## 8. Timestamp Strategy

### Timestamp Columns

| Column | Type | Auto-Set | Updatable | Rationale |
|--------|------|----------|-----------|-----------|
| created_at | TIMESTAMP WITH TIME ZONE | @PrePersist | NO | Immutable creation time |
| updated_at | TIMESTAMP WITH TIME ZONE | @PrePersist, @PreUpdate | YES | Modified on insert and update |
| clicked_at | TIMESTAMP WITH TIME ZONE | @PrePersist | NO | Immutable click time |

**Timezone Handling:**
- **Type:** TIMESTAMP WITH TIME ZONE (PostgreSQL)
- **Java Type:** OffsetDateTime (java.time.* API)
- **Serialization:** Jackson SNAKE_CASE with ISO-8601 format
- **Behavior:** 
  - Stored with UTC offset in database
  - OffsetDateTime preserves exact moment in time
  - No DST ambiguity (offset is immutable)

**Example:**
```java
// Java code
OffsetDateTime now = OffsetDateTime.now(); // 2025-01-15T14:30:00-05:00 (EST)

// Database storage (converted to UTC)
// 2025-01-15 19:30:00+00:00

// On retrieval
OffsetDateTime retrieved = ...; // Reconstructed with original offset
```

**Accuracy:**
- ✅ All timestamps are server-generated (via @PrePersist/@PreUpdate)
- ✅ No client-provided timestamps (prevents clock skew)
- ✅ Immutable fields (created_at, clicked_at) prevent accidental updates
- ✅ Timezone-aware comparison works correctly across regions

**Analysis:**
✅ Strategy is sound for MVP.
✅ No timestamp drift issues.
⚠️ Assumes server clock is synchronized (would need NTP in production).

---

## 9. Entity Relationships

### One-to-Many: ShortUrl ↔ ClickEvent

**Direction:** One ShortUrl → Many ClickEvent

**JPA Configuration:**

**ClickEvent Side (Foreign Key):**
```java
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "short_url_id", nullable = false, foreignKey = @ForeignKey(name = "fk_short_url_id"))
private ShortUrl shortUrl;
```

**ShortUrl Side (Inverse - NOT currently defined):**
```java
// Currently missing:
// @OneToMany(mappedBy = "shortUrl", cascade = CascadeType.REMOVE)
// private List<ClickEvent> clickEvents;
```

**Query Support:**

1. **Find clicks for a URL:**
   ```java
   List<ClickEvent> clicks = clickEventRepository.findByShortUrlId(shortUrlId);
   ```
   Uses: `SELECT * FROM click_event WHERE short_url_id = ?` (backed by idx_short_url_id)

2. **Count unique visitors:**
   ```java
   long uniqueVisitors = clickEventRepository.countUniqueVisitors(shortUrlId);
   ```
   Uses: `SELECT COUNT(DISTINCT ip_hash) FROM click_event WHERE short_url_id = ?`

3. **No need to fetch ShortUrl from ClickEvent in current code:**
   - FetchType.LAZY prevents unnecessary queries
   - If needed, explicit `fetch()` can override

**Missing Inverse Relationship:**
- ⚠️ No @OneToMany on ShortUrl side
- Impact: Hibernate doesn't know about the collection when loading ShortUrl
- Workaround: Use repository queries instead of navigating object graph
- Production enhancement: Consider adding @OneToMany for ORM completeness

---

## 10. Data Type Details

### Numeric Types

**BIGINT (click_count):**
- Range: -9,223,372,036,854,775,808 to 9,223,372,036,854,775,807
- Sufficient for: 10 billion clicks per URL (exabyte-scale before overflow)
- Appropriate for: Click counter (no need for BigInteger)

**BIGSERIAL (id):**
- Equivalent to: BIGINT with auto-increment sequence
- Creates implicit sequence: short_url_id_seq, click_event_id_seq
- Guaranteed unique for: 9.2 × 10^18 rows

### Text Types

**VARCHAR(20) for short_code:**
- 8-character Base62 code + 12 characters margin
- Supports future longer codes without migration
- Unique constraint ensures no duplicates

**VARCHAR(64) for ip_hash:**
- SHA-256 hash = 256 bits = 64 hex characters
- Fixed length, appropriate for deterministic hashing
- Supports future hash algorithm upgrades (if needed)

**TEXT for original_url, user_agent, referer:**
- Unbounded length (up to 1 GB per row in PostgreSQL)
- Supports long URLs (original_url up to 2048 chars)
- Supports complex User-Agent strings (often 100+ chars)
- Supports full Referer URLs

### Timestamp Types

**TIMESTAMP WITH TIME ZONE:**
- Stores: Date, time, and UTC offset
- Range: 4713 BC to 5874897 AD
- Precision: Microsecond (6 decimal places in PostgreSQL)
- Appropriate for: Global system requiring timezone awareness

---

## 11. Constraints Summary

### NOT NULL Constraints

| Column | Table | Nullable | Rationale |
|--------|-------|----------|-----------|
| id | short_url | NO | Primary key requirement |
| short_code | short_url | NO | Core business logic (must have code) |
| original_url | short_url | NO | Core business logic (must have destination) |
| status | short_url | NO | Must know if ACTIVE or DISABLED |
| created_at | short_url | NO | Audit trail requirement |
| updated_at | short_url | NO | Audit trail requirement |
| click_count | short_url | NO | Counter must have value (defaults to 0) |
| expires_at | short_url | **YES** | Optional expiration (NULL = never expires) |
| id | click_event | NO | Primary key requirement |
| short_url_id | click_event | NO | Every click must reference a URL |
| clicked_at | click_event | NO | Every click must have timestamp |
| ip_hash | click_event | **YES** | May be NULL if IP extraction fails |
| user_agent | click_event | **YES** | May be NULL if header missing |
| referer | click_event | **YES** | May be NULL if header missing |

**Analysis:**
✅ Nullability constraints match JPA annotations.
✅ Reasonable defaults (e.g., status='ACTIVE', click_count=0).
✅ Optional fields allow graceful degradation (missing headers don't fail inserts).

---

## 12. Comparison: Entity Annotations vs. Migration SQL

### ShortUrl Entity vs. short_url Table

| Aspect | JPA Annotation | SQL Migration | Match |
|--------|----------------|----------------|-------|
| Table name | @Table("short_url") | CREATE TABLE short_url | ✅ |
| id field | @Id @GeneratedValue(IDENTITY) | BIGSERIAL PRIMARY KEY | ✅ |
| short_code field | @Column(unique=true, length=20) | VARCHAR(20) UNIQUE | ✅ |
| original_url field | @Column(columnDefinition="TEXT") | TEXT | ✅ |
| status field | @Column(length=20) | VARCHAR(20) | ✅ |
| created_at field | @Column(updatable=false) | TIMESTAMP WITH TIME ZONE | ✅ |
| updated_at field | @Column | TIMESTAMP WITH TIME ZONE | ✅ |
| expires_at field | @Column | TIMESTAMP WITH TIME ZONE (nullable) | ✅ |
| click_count field | @Column | BIGINT DEFAULT 0 | ✅ |
| Indexes | @Index (3 indexes) | CREATE INDEX (3 indexes) | ✅ |

### ClickEvent Entity vs. click_event Table

| Aspect | JPA Annotation | SQL Migration | Match |
|--------|----------------|----------------|-------|
| Table name | @Table("click_event") | CREATE TABLE click_event | ✅ |
| id field | @Id @GeneratedValue(IDENTITY) | BIGSERIAL PRIMARY KEY | ✅ |
| shortUrl FK | @ManyToOne @JoinColumn | BIGINT REFERENCES | ✅ |
| FK name | @ForeignKey("fk_short_url_id") | REFERENCES... | ✅ |
| FK behavior | (no cascade in JPA) | ON DELETE CASCADE (SQL) | ⚠️ Partial* |
| clicked_at field | @Column(updatable=false) | TIMESTAMP WITH TIME ZONE | ✅ |
| ip_hash field | @Column(length=64) | VARCHAR(64) | ✅ |
| user_agent field | @Column(columnDefinition="TEXT") | TEXT | ✅ |
| referer field | @Column(columnDefinition="TEXT") | TEXT | ✅ |
| Indexes | @Index (2 indexes) | CREATE INDEX (2 indexes) | ✅ |

**\* FK Cascade Note:**
- SQL has ON DELETE CASCADE (database-level constraint)
- JPA does not cascade (no @OneToMany or CascadeType.REMOVE)
- Result: Database prevents FK violations; Hibernate doesn't auto-delete in-memory
- Acceptable for MVP (database constraint provides safety)

---

## 13. Identified Issues and Mismatches

### Issue 1: DEFAULT Status Value ⚠️ Minor

**Description:**
- SQL: `status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'`
- JPA: Constructor sets status in Java, not via SQL default

**Impact:**
- If SQL insert bypasses JPA, status defaults to ACTIVE (correct)
- If JPA insert, status set in constructor (correct)
- No functional impact, but maintenance concern

**Recommendation:**
- ✅ Current approach is fine for this application (JPA always manages inserts)
- Alternative: Remove SQL DEFAULT if only JPA will insert

---

### Issue 2: Cascade Delete (Hibernate vs. Database) ⚠️ Minor

**Description:**
- SQL: `ON DELETE CASCADE` configured
- JPA: @ManyToOne without CascadeType.REMOVE

**Impact:**
- Database prevents referential integrity violations (safe)
- Hibernate doesn't cascade deletes in-memory (orphan ClickEvents if object deleted in session)
- In current application, rarely a problem (deletes via repository)

**Recommendation:**
- ✅ Current implementation is acceptable for MVP
- Production enhancement: Add @OneToMany(cascade=CascadeType.REMOVE) to ShortUrl

---

### Issue 3: No Explicit Composite Index for Analytics ℹ️ Informational

**Description:**
- Query: `SELECT COUNT(DISTINCT ip_hash) FROM click_event WHERE short_url_id = ? AND clicked_at > ?`
- Current indexes: idx_short_url_id, idx_clicked_at (separate)
- No composite index: (short_url_id, clicked_at)

**Impact:**
- Query planner may use either index separately
- Performance acceptable for MVP scale
- May benefit from composite index at higher scale

**Recommendation:**
- ✅ Current indexes sufficient for MVP
- Performance consideration: Benchmark before adding composite indexes

---

### Issue 4: No Application-Level Constraints ℹ️ Informational

**Description:**
- Database enforces: UNIQUE short_code, FK relationships
- Application could enforce: Max URL length, status values
- Currently done via validation (UrlValidator, @NotBlank, @NotNull)

**Impact:**
- Validation layer appropriate for REST API
- Database constraints provide defense-in-depth

**Recommendation:**
- ✅ Current approach follows layered security best practice

---

## 14. Schema Completeness Checklist

### Required Elements ✅

- [x] Primary keys defined (both tables)
- [x] Foreign keys with referential integrity (short_url_id)
- [x] Unique constraints (short_code)
- [x] NOT NULL constraints on required fields
- [x] Appropriate data types (TEXT, BIGINT, TIMESTAMP WITH TIME ZONE, VARCHAR)
- [x] Indexes on frequently queried columns
- [x] Timestamp columns for audit trail
- [x] Cascade delete behavior (database-level)
- [x] Consistent naming convention (snake_case)

### Optional (Not Needed for MVP) ℹ️

- [ ] Check constraints (e.g., status IN ('ACTIVE', 'DISABLED'))
- [ ] Computed/generated columns (e.g., click_count from COUNT aggregation)
- [ ] Partitioning (for >100M rows)
- [ ] Full-text search indexes (not needed for this app)
- [ ] JSON columns (not needed currently)
- [ ] Materialized views (not needed for MVP scale)

---

## 15. Production-Readiness Assessment

### Database Design

| Aspect | Status | Notes |
|--------|--------|-------|
| Normalization | ✅ Good | Tables properly normalized (1 FK relationship) |
| Scalability | ⚠️ Adequate | Monolithic design; single PostgreSQL sufficient for MVP |
| Backup/Recovery | ⚠️ Needs Config | Flyway handles schema; DB backup strategy TBD |
| Query Performance | ✅ Good | Indexes on most-queried columns |
| Data Integrity | ✅ Strong | FK constraints, unique constraints, NOT NULL constraints |
| Audit Trail | ✅ Good | created_at, updated_at, click_event timestamps |
| Timezone Handling | ✅ Correct | TIMESTAMP WITH TIME ZONE prevents ambiguity |

### Potential Enhancements (Not MVP)

1. **Inverse Relationship:** @OneToMany on ShortUrl for ORM completeness
2. **Composite Index:** (short_url_id, clicked_at) for time-range analytics
3. **Enum Constraint:** CHECK (status IN ('ACTIVE', 'DISABLED'))
4. **Partitioning:** If click_event exceeds 100M rows
5. **Archive Strategy:** Soft-delete or archive expired URLs

---

## 16. Migration Strategy

### Current Approach (Flyway)

**Configuration (application.yml):**
```yaml
spring:
  flyway:
    enabled: true
    locations: classpath:db/migration
```

**Execution:**
- Application startup runs: `mvn spring-boot:run`
- Flyway automatically: Validates, applies pending migrations
- Schema version tracked in: `flyway_schema_history` table

**Migration File:** `src/main/resources/db/migration/V1__Initial_schema.sql`

**Behavior:**
- ✅ First run: Creates tables, indexes, FK constraints
- ✅ Subsequent runs: Validates schema exists, skips if already applied
- ✅ Error on: Mismatch between migration history and current schema

### Validation Strategy (Production)

```yaml
spring:
  jpa:
    hibernate:
      ddl-auto: validate  # Fail if schema doesn't match entities
```

**Effect:**
- ✅ Prevents accidental schema changes
- ✅ Fails fast if migration wasn't applied
- ⚠️ Does not auto-create schema (Flyway must run first)

---

## 17. Schema Evolution (Future Migrations)

**If you add a new feature, steps would be:**

1. Update JPA entity (e.g., add new @Column)
2. Create new migration file:
   ```
   src/main/resources/db/migration/V2__Add_new_column.sql
   ```
3. Migration auto-applies on startup

**Example (hypothetical - not implemented):**
```sql
-- V2__Add_analytics_tracking.sql
ALTER TABLE click_event ADD COLUMN device_type VARCHAR(50);
ALTER TABLE click_event ADD COLUMN is_bot BOOLEAN DEFAULT false;
```

---

## 18. Schema Diagram

```
┌──────────────────────────────────────────┐
│           short_url                       │
├──────────────────────────────────────────┤
│ id (BIGSERIAL, PK)                       │
│ short_code (VARCHAR(20), UNIQUE)         │
│ original_url (TEXT)                      │
│ status (VARCHAR(20))                     │
│ created_at (TIMESTAMP WITH TZ)           │
│ updated_at (TIMESTAMP WITH TZ)           │
│ expires_at (TIMESTAMP WITH TZ, NULL)     │
│ click_count (BIGINT)                     │
└──────────────────────────────────────────┘
           ▲ 1
           │
           │ (One-to-Many)
           │ Foreign Key: ON DELETE CASCADE
           │
           │ *
┌──────────────────────────────────────────┐
│           click_event                     │
├──────────────────────────────────────────┤
│ id (BIGSERIAL, PK)                       │
│ short_url_id (BIGINT, FK)                │
│ clicked_at (TIMESTAMP WITH TZ)           │
│ ip_hash (VARCHAR(64), NULL)              │
│ user_agent (TEXT, NULL)                  │
│ referer (TEXT, NULL)                     │
└──────────────────────────────────────────┘

Indexes:
  short_url: idx_short_code (UNIQUE), idx_status, idx_expires_at
  click_event: idx_short_url_id, idx_clicked_at
```

---

## Conclusion

### Summary

✅ **SCHEMA IS PRODUCTION-READY FOR MVP**

The JPA entities and Flyway migration are properly aligned. All required constraints, indexes, and relationships are correctly defined for:
- Unique short code generation and collision detection
- Click tracking with referential integrity
- Analytics queries (unique visitor counts, time-based filtering)
- Soft URL expiration
- Audit trail (created/updated timestamps)

### Verification Checklist

- [x] All entities have corresponding table definitions
- [x] All columns have matching types and nullability
- [x] Primary keys defined correctly
- [x] Foreign keys with cascade delete
- [x] Unique constraints on short_code
- [x] Indexes on query paths
- [x] Timestamp strategy is timezone-aware
- [x] Migration file (V1) defines complete schema
- [x] Flyway configured for automatic migration
- [x] Hibernate validation mode (ddl-auto: validate)

### Minor Enhancements (Not Required for MVP)

- Add @OneToMany(cascade=CascadeType.REMOVE) to ShortUrl for Hibernate consistency
- Add CHECK constraint for status enum values
- Consider composite index (short_url_id, clicked_at) for time-range analytics

### No Blockers Identified ✅

The application is ready to connect to a PostgreSQL database with this schema deployed.

