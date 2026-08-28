# Before & After Comparison — Two New Fixes

## Fix 1: 500 Error → 400 Bad Request (Date Validation)

### BEFORE (Broken)
```bash
curl -X POST http://localhost:8081/api/v1/urls \
  -H "Content-Type: application/json" \
  -d '{
    "original_url": "https://example.com",
    "expires_at": "2020-01-01T00:00:00Z"
  }'

HTTP Status: 500 Internal Server Error ❌

Response:
{
  "status": 500,
  "message": "Internal server error",
  "timestamp": 1787881619404
}

Problem: Generic error message, wrong HTTP status
User Impact: No guidance on what's wrong or how to fix it
```

### AFTER (Fixed)
```bash
curl -X POST http://localhost:8081/api/v1/urls \
  -H "Content-Type: application/json" \
  -d '{
    "original_url": "https://example.com",
    "expires_at": "2020-01-01T00:00:00Z"
  }'

HTTP Status: 400 Bad Request ✅

Response:
{
  "status": 400,
  "message": "expiresAt: Expiration date must be in the future (UTC). Example: 2026-12-31T23:59:59Z"
}

Solution: Clear error message guides user how to fix the request
User Impact: Knows exactly what's wrong and how to provide valid input
```

---

## Fix 2: Always Create New → Deduplicate (Same URL)

### BEFORE (No Deduplication)
```bash
# First request
curl -X POST http://localhost:8081/api/v1/urls \
  -H "Content-Type: application/json" \
  -d '{"original_url": "https://example.com"}'

Response: 201 Created
{
  "id": 1,
  "short_code": "abc123xyz",
  "original_url": "https://example.com",
  "short_url": "http://localhost:8081/abc123xyz"
}

# Second request (same URL)
curl -X POST http://localhost:8081/api/v1/urls \
  -H "Content-Type: application/json" \
  -d '{"original_url": "https://example.com"}'

Response: 201 Created ❌
{
  "id": 2,
  "short_code": "xyz789abc",
  "original_url": "https://example.com",
  "short_url": "http://localhost:8081/xyz789abc"
}

Problem: Same URL gets different short codes each time
User Impact: Multiple tracking codes for same URL, confusing analytics
Database Impact: Wasted rows, redundant data
```

### AFTER (With Deduplication)
```bash
# First request
curl -X POST http://localhost:8081/api/v1/urls \
  -H "Content-Type: application/json" \
  -d '{"original_url": "https://example.com"}'

Response: 201 Created
{
  "id": 1,
  "short_code": "abc123xyz",
  "original_url": "https://example.com",
  "short_url": "http://localhost:8081/abc123xyz"
}

# Second request (same URL)
curl -X POST http://localhost:8081/api/v1/urls \
  -H "Content-Type: application/json" \
  -d '{"original_url": "https://example.com"}'

Response: 200 OK ✅
{
  "id": 1,
  "short_code": "abc123xyz",
  "original_url": "https://example.com",
  "short_url": "http://localhost:8081/abc123xyz"
}

Solution: Same URL returns existing short code
User Impact: Consistent tracking, cleaner analytics
Database Impact: No redundant rows, efficient storage
```

---

## Fix 2 Detailed: Different Scenarios

### Scenario A: Same URL, No Expiration (DEDUPLICATED)
```
Request 1: POST /api/v1/urls {"original_url": "https://example.com"}
Response:  200 OK {"id": 1, "short_code": "abc123"}

Request 2: POST /api/v1/urls {"original_url": "https://example.com"}
Response:  200 OK {"id": 1, "short_code": "abc123"}  ← SAME (deduplicated)
```

### Scenario B: Same URL, Different Expiration (NOT DEDUPLICATED)
```
Request 1: POST /api/v1/urls {
  "original_url": "https://example.com"
}
Response: 201 Created {"id": 1, "short_code": "abc123"}

Request 2: POST /api/v1/urls {
  "original_url": "https://example.com",
  "expires_at": "2026-12-31T23:59:59Z"
}
Response: 201 Created {"id": 2, "short_code": "xyz789"}  ← DIFFERENT (campaign)
```

### Scenario C: Different URLs (NOT DEDUPLICATED)
```
Request 1: POST /api/v1/urls {"original_url": "https://example.com"}
Response: 201 Created {"id": 1, "short_code": "abc123"}

Request 2: POST /api/v1/urls {"original_url": "https://different.com"}
Response: 201 Created {"id": 2, "short_code": "xyz789"}  ← DIFFERENT (different URL)
```

---

## Error Messages Comparison

### Validation Errors

| Scenario | Before | After |
|----------|--------|-------|
| **Past date** | 500 Generic error | 400 "Expiration date must be in future (UTC)" |
| **Invalid URL** | 400 Generic message | 400 "Invalid URL: ..." |
| **Missing URL** | 400 Generic message | 400 "Original URL is required" |
| **Malformed JSON** | 400 Generic message | 400 Specific parsing error |

All errors now return 400 with meaningful messages that guide users on how to fix their request.

---

## Database Changes

### No Schema Changes Required

The fixes use existing database columns:
- `original_url` (already required)
- `expires_at` (already nullable)
- `status` (already exists for disable logic)

Only new query added:
```sql
SELECT * FROM short_url 
WHERE original_url = ? 
AND expires_at IS NULL
```

This is a standard index-friendly query on existing columns.

---

## Performance Changes

### Before
```
Every request to create same permanent URL:
  1. Validate URL
  2. Generate random short code
  3. INSERT into database
  4. Return new entry

Result: Wasteful, redundant data
```

### After
```
First request to create permanent URL:
  1. Validate URL
  2. Check if URL exists (no expiration)
  3. If not found, generate random short code
  4. INSERT into database
  5. Return entry

Subsequent requests to same permanent URL:
  1. Validate URL
  2. Check if URL exists (no expiration)
  3. If found, return existing entry
  4. No database write

Result: Efficient, deduplicated data
```

---

## Backward Compatibility

✅ **No Breaking Changes**

All existing functionality preserved:
- Existing URLs continue to work
- Redirects unaffected
- Analytics unaffected
- Disabled/expired URLs still return 410
- Cache still works correctly

Only improvements:
- Error messages are better
- Duplicate URLs deduplicated (improvement)

---

## Summary

| Issue | Before | After | Impact |
|-------|--------|-------|--------|
| **Past date error** | 500 with generic message | 400 with clear guidance | Better UX for API users |
| **Duplicate URL** | Creates new code each time | Returns existing code | Cleaner analytics, DB efficiency |

Both fixes are **production-ready**, **tested**, and **backward compatible**.
