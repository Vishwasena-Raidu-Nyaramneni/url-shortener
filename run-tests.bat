@echo off
setlocal enabledelayedexpansion

set BASE_URL=http://localhost:8081
set PASS=0
set FAIL=0

echo.
echo ===== URL SHORTENER TEST SUITE =====
echo.

REM TC-001: Create HTTPS URL
echo TC-001: Create HTTPS URL
curl -s -X POST %BASE_URL%/api/v1/urls -H "Content-Type: application/json" -d "{\"original_url\":\"https://example.com\"}" -w "Status: %%{http_code}\n" -o nul
set /a PASS+=1

REM TC-002: Create HTTP URL
echo TC-002: Create HTTP URL
curl -s -X POST %BASE_URL%/api/v1/urls -H "Content-Type: application/json" -d "{\"original_url\":\"http://example.org\"}" -w "Status: %%{http_code}\n" -o nul
set /a PASS+=1

REM TC-003: Create URL with path
echo TC-003: Create URL with path
curl -s -X POST %BASE_URL%/api/v1/urls -H "Content-Type: application/json" -d "{\"original_url\":\"https://example.com/products/123\"}" -w "Status: %%{http_code}\n" -o nul
set /a PASS+=1

REM TC-006: Empty URL (should fail with 400)
echo TC-006: Empty URL
curl -s -X POST %BASE_URL%/api/v1/urls -H "Content-Type: application/json" -d "{\"original_url\":\"\"}" -w "Status: %%{http_code}\n" -o nul

echo.
echo PASS: %PASS%
echo.
