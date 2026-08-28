# Quick Test Guide — Verify Both Fixes

## Deploy & Test

### 1. Build & Deploy
```bash
cd E:\url-shortener
mvn clean package -DskipTests
docker build -t url-shortener:latest .
docker-compose down
docker-compose up -d

# Wait 10 seconds for containers to start
sleep 10

# Verify health
curl http://localhost:8081/actuator/health
# Expected: {"status":"UP"}
```

---

## Test Fix #1: Error Message for Past Dates

### Test: Send Past Date
```bash
curl -X POST http://localhost:8081/api/v1/urls \
  -H "Content-Type: application/json" \
  -d '{
    "original_url": "https://example.com",
    "expires_at": "2020-01-01T00:00:00Z"
  }'
```

### Expected Result
```
HTTP Status: 400 Bad Request ✅

Response:
{
  "status": 400,
  "message": "expiresAt: Expiration date must be in the future (UTC). Example: 2026-12-31T23:59:59Z"
}
```

### What This Proves
- ✅ Returns 400 (not 500)
- ✅ Error message is clear
- ✅ Message guides user on UTC format

---

## Test Fix #2a: Deduplication (No Expiration)

### Step 1: Create Permanent Link
```bash
curl -X POST http://localhost:8081/api/v1/urls \
  -H "Content-Type: application/json" \
  -d '{"original_url": "https://example.com"}'
```

Save the `short_code` and `id` from response (e.g., "abc123", id: 1)

### Step 2: Request Same URL Again
```bash
curl -X POST http://localhost:8081/api/v1/urls \
  -H "Content-Type: application/json" \
  -d '{"original_url": "https://example.com"}'
```

### Expected Result
```
Same response as Step 1:
{
  "id": 1,
  "short_code": "abc123",
  "original_url": "https://example.com",
  "short_url": "http://localhost:8081/abc123"
}
```

### What This Proves
- ✅ Same ID returned (not created new)
- ✅ Same short_code returned (deduplicated)
- ✅ HTTP 200 (not 201, since not newly created)

---

## Test Fix #2b: No Deduplication (With Expiration)

### Step 1: Create Permanent Link (No Expiration)
```bash
curl -X POST http://localhost:8081/api/v1/urls \
  -H "Content-Type: application/json" \
  -d '{"original_url": "https://example.com"}'

# Response: id: 1, short_code: "abc123"
```

### Step 2: Request Same URL with Expiration
```bash
curl -X POST http://localhost:8081/api/v1/urls \
  -H "Content-Type: application/json" \
  -d '{
    "original_url": "https://example.com",
    "expires_at": "2026-12-31T23:59:59Z"
  }'
```

### Expected Result
```
Different response:
{
  "id": 2,
  "short_code": "xyz789",
  "original_url": "https://example.com",
  "short_url": "http://localhost:8081/xyz789"
}
```

### What This Proves
- ✅ Different ID created (campaign needs separate link)
- ✅ Different short_code (campaign tracking separate)
- ✅ HTTP 201 (newly created)
- ✅ Expiration campaign link separate from permanent link

---

## Test Fix #2c: Different URLs

### Step 1: Create First URL
```bash
curl -X POST http://localhost:8081/api/v1/urls \
  -H "Content-Type: application/json" \
  -d '{"original_url": "https://example.com"}'

# Response: id: 1, short_code: "abc123"
```

### Step 2: Create Different URL
```bash
curl -X POST http://localhost:8081/api/v1/urls \
  -H "Content-Type: application/json" \
  -d '{"original_url": "https://different.com"}'
```

### Expected Result
```
Different response:
{
  "id": 2,
  "short_code": "xyz789",
  "original_url": "https://different.com",
  "short_url": "http://localhost:8081/xyz789"
}
```

### What This Proves
- ✅ Different IDs for different URLs
- ✅ Different short codes
- ✅ Standard behavior preserved

---

## Bonus Tests

### Test: Access the Redirect
```bash
# Should redirect to original URL
curl -L http://localhost:8081/abc123
# Expected: Redirects to https://example.com
```

### Test: Get Analytics
```bash
curl http://localhost:8081/api/v1/urls/1/analytics
# Expected: Shows click data
```

### Test: Disable URL
```bash
curl -X DELETE http://localhost:8081/api/v1/urls/1
# Expected: 204 No Content

# Then access again:
curl http://localhost:8081/abc123
# Expected: 410 Gone
```

---

## Summary

| Test | Fix | Result | Evidence |
|------|-----|--------|----------|
| Past date | #1 | 400 with error message | HTTP 400, meaningful message |
| Same URL duplicate | #2a | Returns existing code | Same ID, same short_code |
| Same URL with expiration | #2b | Creates new campaign link | Different ID, different code |
| Different URLs | #2c | Creates new codes | Different IDs, different codes |

All tests should PASS ✅

---

## Rollback (If Needed)

```bash
# Stop containers
docker-compose down

# Revert code changes (if necessary)
git checkout -- src/

# Rebuild old version
mvn clean package -DskipTests
docker build -t url-shortener:latest .
docker-compose up -d
```

---

## Troubleshooting

| Issue | Cause | Solution |
|-------|-------|----------|
| 500 error still on past date | Old image cached | Rebuild with `docker build --no-cache` |
| Duplicate URLs not deduplicated | Database has old data | Clear and restart (test environment) |
| Containers won't start | Port 8081 in use | Change port in docker-compose.yml |
| Build fails | Compilation error | Check Java version (needs 21+) |

---

## Success Criteria

All 4 main tests (past date, 2a duplicate, 2b expiration, 2c different URL) should pass ✅

When all pass, the fixes are working correctly and ready for production deployment.
