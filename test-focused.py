#!/usr/bin/env python3
"""Test the remaining 14 failing test cases"""

import requests
import json
from datetime import datetime, timedelta
import io
import sys

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8', errors='replace')

BASE_URL = "http://localhost:8081"
RESULTS = []

def test_api(tc_num, method, endpoint, body=None, expected_status=200, description=""):
    """Execute API test and return result"""
    url = f"{BASE_URL}{endpoint}"
    headers = {"Content-Type": "application/json"}
    
    try:
        if method == "POST":
            response = requests.post(url, json=body, headers=headers, timeout=5)
        elif method == "GET":
            response = requests.get(url, headers=headers, timeout=5)
        elif method == "DELETE":
            response = requests.delete(url, headers=headers, timeout=5)
        else:
            response = requests.request(method, url, json=body, headers=headers, timeout=5)
        
        actual_status = response.status_code
    except:
        actual_status = "ERROR"
    
    status = "PASS" if actual_status == expected_status else "FAIL"
    
    result = {
        "tc": tc_num,
        "expected": expected_status,
        "actual": actual_status,
        "status": status,
        "description": description
    }
    RESULTS.append(result)
    
    print(f"{tc_num}: {description}")
    print(f"   Expected: {expected_status}, Actual: {actual_status} - {status}")
    
    return result

print("=" * 90)
print("FOCUSED TEST - Remaining 14 Failed Test Cases")
print("=" * 90)
print()

# First, create some test URLs to use for GET/DELETE/Analytics tests
print("SETUP: Creating test URLs...")
r1 = requests.post(
    f"{BASE_URL}/api/v1/urls",
    json={"original_url": "https://test-setup-1.com"},
    headers={"Content-Type": "application/json"}
)
id_1 = r1.json().get("id") if r1.status_code == 201 else None
print(f"Created URL 1 with ID: {id_1}")

r2 = requests.post(
    f"{BASE_URL}/api/v1/urls",
    json={"original_url": "https://test-setup-2.com"},
    headers={"Content-Type": "application/json"}
)
id_2 = r2.json().get("id") if r2.status_code == 201 else None
print(f"Created URL 2 with ID: {id_2}")

print()
print("=" * 90)
print("RUNNING TESTS")
print("=" * 90)
print()

# TC-014: Very long URL (should be rejected by @Size)
test_api("TC-014", "POST", "/api/v1/urls", 
         {"original_url": "https://" + "a" * 2100 + ".com"}, 400, 
         "Very long URL (2100+ chars)")

# TC-033: Empty short code
test_api("TC-033", "GET", "/", None, 404, 
         "Empty short code")

# TC-035: GET /api/v1/urls (should be 404, but API returns 405 for unsupported method)
test_api("TC-035", "GET", "/api/v1/urls", None, 404, 
         "GET /api/v1/urls")

# TC-043: POST with extra JSON fields (should cause different hash if not handled)
test_api("TC-043", "POST", "/api/v1/urls", 
         {"original_url": "https://extra-fields-test.com", "extra_field": "value"}, 201, 
         "POST with extra JSON fields (first time)")

# TC-044: Get URL by ID
if id_1:
    test_api("TC-044", "GET", f"/api/v1/urls/{id_1}", None, 200, 
             f"Get URL by ID ({id_1})")
else:
    print("TC-044: SKIPPED (no test URL created)")

# TC-048: Delete existing URL
if id_1:
    test_api("TC-048", "DELETE", f"/api/v1/urls/{id_1}", None, 204, 
             f"Delete existing URL ({id_1})")
else:
    print("TC-048: SKIPPED (no test URL to delete)")

# TC-052: Get analytics for URL
if id_2:
    test_api("TC-052", "GET", f"/api/v1/urls/{id_2}/analytics", None, 200, 
             f"Get analytics for URL ({id_2})")
else:
    print("TC-052: SKIPPED (no test URL for analytics)")

# TC-055: Get analytics for second URL (would need id_2, but it's used above in 052)
if id_2:
    test_api("TC-055", "GET", f"/api/v1/urls/{id_2}/analytics", None, 200, 
             f"Get analytics for second URL ({id_2})")
else:
    print("TC-055: SKIPPED")

# TC-058: GET /api/v1/urls/{id}
if id_2:
    test_api("TC-058", "GET", f"/api/v1/urls/{id_2}", None, 200, 
             f"GET /api/v1/urls/{{id}} for existing URL ({id_2})")
else:
    print("TC-058: SKIPPED")

# TC-059: DELETE /api/v1/urls/{id}
if id_2:
    test_api("TC-059", "DELETE", f"/api/v1/urls/{id_2}", None, 204, 
             f"DELETE /api/v1/urls/{{id}} ({id_2})")
else:
    print("TC-059: SKIPPED")

# TC-070: List URLs endpoint (should be 404, but we're sending 405 for GET)
test_api("TC-070", "GET", "/api/v1/urls", None, 404, 
         "GET /api/v1/urls (list endpoint)")

# TC-078: Analytics endpoint accessibility
test_api("TC-078", "GET", "/api/v1/urls/1/analytics", None, 200, 
         "Analytics endpoint for first URL")

# TC-080: Invalid endpoint
test_api("TC-080", "GET", "/invalid-endpoint", None, 404, 
         "Invalid endpoint")

# TC-083: Very long URL (same as TC-014, testing again)
test_api("TC-083", "POST", "/api/v1/urls", 
         {"original_url": "https://" + "b" * 2000 + ".com"}, 400, 
         "Very long URL boundary test")

print()
print("=" * 90)
print("RESULTS SUMMARY")
print("=" * 90)
pass_count = sum(1 for r in RESULTS if r["status"] == "PASS")
fail_count = sum(1 for r in RESULTS if r["status"] == "FAIL")
print(f"PASSED: {pass_count}")
print(f"FAILED: {fail_count}")
print()

# Print detailed results
for r in RESULTS:
    status_icon = "✅" if r["status"] == "PASS" else "❌"
    print(f"{status_icon} {r['tc']}: {r['status']} (Expected: {r['expected']}, Actual: {r['actual']})")
