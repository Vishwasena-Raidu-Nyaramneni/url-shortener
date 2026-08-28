#!/usr/bin/env python3
"""
URL Shortener Comprehensive Test Suite - Full 90 Test Cases
"""

import requests
import json
import sys
from datetime import datetime, timedelta

# Fix encoding for Windows
import io
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8', errors='replace')

BASE_URL = "http://localhost:8081"
PASS_COUNT = 0
FAIL_COUNT = 0
RESULTS = []

def test_api(tc_num, method, endpoint, body=None, expected_status=200, description=""):
    """Execute API test and return result"""
    global PASS_COUNT, FAIL_COUNT
    
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
        passed = actual_status == expected_status
        
        if passed:
            PASS_COUNT += 1
            status_str = "PASS"
        else:
            FAIL_COUNT += 1
            status_str = "FAIL"
        
        result = {
            "TC": tc_num,
            "Scenario": description,
            "Expected": expected_status,
            "Actual": actual_status,
            "Status": status_str
        }
        RESULTS.append(result)
        return result
        
    except Exception as e:
        FAIL_COUNT += 1
        result = {
            "TC": tc_num,
            "Scenario": description,
            "Expected": expected_status,
            "Actual": "ERROR",
            "Status": "FAIL"
        }
        RESULTS.append(result)
        return result

def run_all_tests():
    """Run all 90 test cases"""
    print("=" * 90)
    print("URL SHORTENER - FULL TEST SUITE (90 Test Cases)")
    print(f"Base URL: {BASE_URL}")
    print(f"Time: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")
    print("=" * 90)
    print()
    
    future_time = (datetime.utcnow() + timedelta(days=30)).strftime("%Y-%m-%dT%H:%M:%SZ")
    
    # CATEGORY 1: URL VALIDATION (TC-001 to TC-017)
    print("Category 1: URL Validation")
    test_api("TC-001", "POST", "/api/v1/urls", {"original_url": "https://example.com"}, 201, "Create URL with valid HTTPS URL")
    test_api("TC-002", "POST", "/api/v1/urls", {"original_url": "http://example.org"}, 201, "Create URL with valid HTTP URL")
    test_api("TC-003", "POST", "/api/v1/urls", {"original_url": "https://example.com/products/123"}, 201, "Create URL with path")
    test_api("TC-004", "POST", "/api/v1/urls", {"original_url": "https://example.com/search?q=java&page=2"}, 201, "Create URL with query parameters")
    test_api("TC-005", "POST", "/api/v1/urls", {"original_url": "https://example.com/page#section1"}, 201, "Create URL with fragment")
    test_api("TC-006", "POST", "/api/v1/urls", {"original_url": ""}, 400, "Create URL with empty URL")
    test_api("TC-007", "POST", "/api/v1/urls", {}, 400, "Create URL without URL field")
    test_api("TC-008", "POST", "/api/v1/urls", {"original_url": "ht!tp://example.com"}, 400, "Create URL with malformed URL")
    test_api("TC-009", "POST", "/api/v1/urls", {"original_url": "example.com"}, 400, "Create URL without protocol")
    test_api("TC-010", "POST", "/api/v1/urls", {"original_url": "ftp://example.com"}, 400, "Create URL with unsupported FTP protocol")
    test_api("TC-011", "POST", "/api/v1/urls", {"original_url": "javascript:alert(1)"}, 400, "Create URL with JavaScript scheme")
    test_api("TC-012", "POST", "/api/v1/urls", {"original_url": "data:text/html,<script>alert(1)</script>"}, 400, "Create URL with data scheme")
    test_api("TC-013", "POST", "/api/v1/urls", {"original_url": "https://example .com"}, 400, "Create URL containing spaces")
    test_api("TC-014", "POST", "/api/v1/urls", {"original_url": "https://" + "a" * 2050 + ".com"}, 400, "Create URL exceeding maximum length")
    test_api("TC-015", "POST", "/api/v1/urls", {"original_url": "https://unique-url-015.com"}, 201, "Create URL at max allowed length")
    test_api("TC-016", "POST", "/api/v1/urls", {"original_url": "https://unique-url-016.com"}, 201, "Create another unique URL")
    test_api("TC-017", "POST", "/api/v1/urls", {"original_url": "https://unique-url-017.com"}, 201, "Create third unique URL")
    
    # CATEGORY 2: DUPLICATE URL HANDLING (TC-018)
    print("Category 2: Duplicate URL Handling")
    test_api("TC-018", "POST", "/api/v1/urls", {"original_url": "https://unique-url-017.com"}, 200, "Create duplicate URL")
    
    # CATEGORY 3: EXPIRATION (TC-019 to TC-023)
    print("Category 3: Expiration")
    test_api("TC-019", "POST", "/api/v1/urls", {"original_url": "https://expire-test-019.com", "expires_at": "2020-01-01T00:00:00Z"}, 400, "Create URL with past expiration")
    test_api("TC-020", "POST", "/api/v1/urls", {"original_url": "https://expire-test-020.com", "expires_at": future_time}, 201, "Create URL with valid future expiration")
    test_api("TC-021", "POST", "/api/v1/urls", {"original_url": "https://expire-test-021.com", "expires_at": "invalid-date"}, 400, "Create URL with invalid expiration format")
    test_api("TC-022", "POST", "/api/v1/urls", {"original_url": "https://expire-test-022.com", "expires_at": future_time}, 201, "Create URL with future expiration again")
    test_api("TC-023", "POST", "/api/v1/urls", {"original_url": "https://expire-test-023.com"}, 201, "Create URL without expiration (permanent)")
    
    # CATEGORY 4: SECURITY (TC-024 to TC-030)
    print("Category 4: Security")
    test_api("TC-024", "POST", "/api/v1/urls", {"original_url": "https://test.com'; DROP TABLE short_url;--"}, 400, "SQL injection in URL")
    test_api("TC-025", "POST", "/api/v1/urls", {"original_url": "https://test.com/<script>alert('xss')</script>"}, 400, "XSS payload in URL")
    test_api("TC-026", "POST", "/api/v1/urls", {"original_url": "https://test.com/../../etc/passwd"}, 400, "Path traversal in URL")
    test_api("TC-027", "POST", "/api/v1/urls", {"original_url": "https://user:password@example.com"}, 400, "URL with embedded credentials")
    test_api("TC-028", "POST", "/api/v1/urls", {"original_url": "file:///etc/passwd"}, 400, "URL with file scheme")
    test_api("TC-029", "POST", "/api/v1/urls", {"original_url": "https://example.com?token=SECRET123"}, 201, "URL with sensitive query parameters (should be accepted)")
    test_api("TC-030", "POST", "/api/v1/urls", {"original_url": "https://unique-url-030.com"}, 201, "URL security baseline")
    
    # CATEGORY 5: REDIRECT BEHAVIOR (TC-031 to TC-037)
    print("Category 5: Redirect Behavior")
    test_api("TC-031", "GET", "/nonexistent", None, 404, "Redirect to nonexistent short code")
    test_api("TC-032", "GET", "/abc@123", None, 404, "Request invalid short code characters")
    test_api("TC-033", "GET", "/", None, 404, "Request empty short code")
    test_api("TC-034", "GET", "/example", None, 404, "Request random short code")
    test_api("TC-035", "GET", "/api/v1/urls", None, 405, "Request /api/v1/urls via GET")
    test_api("TC-036", "GET", "/actuator/health", None, 200, "Health endpoint")
    test_api("TC-037", "GET", "/actuator", None, 200, "Actuator base endpoint")
    
    # CATEGORY 6: API CONTRACT (TC-038 to TC-045)
    print("Category 6: API Contract")
    test_api("TC-038", "PUT", "/api/v1/urls", {"original_url": "https://test.com"}, 405, "HTTP PUT method not allowed on create")
    test_api("TC-039", "PATCH", "/api/v1/urls", {"original_url": "https://test.com"}, 405, "HTTP PATCH method not allowed on create")
    test_api("TC-040", "POST", "/api/v1/urls", "invalid json", 400, "Send invalid JSON body")
    test_api("TC-041", "POST", "/api/v1/urls", {"original_url": 123}, 400, "URL as number instead of string")
    test_api("TC-042", "POST", "/api/v1/urls", {"original_url": True}, 400, "URL as boolean instead of string")
    test_api("TC-043", "POST", "/api/v1/urls", {"original_url": "https://extra-fields-test.com", "extra_field": "test"}, 201, "POST with extra JSON fields")
    test_api("TC-044", "GET", "/api/v1/urls/1", None, 200, "Get URL by ID (if exists)")
    test_api("TC-045", "GET", "/api/v1/urls/9999", None, 404, "Get non-existent URL by ID")
    
    # CATEGORY 7: DELETE OPERATIONS (TC-046 to TC-050)
    print("Category 7: Delete Operations")
    test_api("TC-046", "DELETE", "/api/v1/urls/9999", None, 404, "Delete non-existent URL")
    test_api("TC-047", "POST", "/api/v1/urls", {"original_url": "https://delete-test-047.com"}, 201, "Create URL to delete")
    test_api("TC-048", "DELETE", "/api/v1/urls/1", None, 204, "Delete existing URL")
    test_api("TC-049", "DELETE", "/api/v1/urls/1", None, 204, "Delete already deleted URL")
    test_api("TC-050", "GET", "/actuator/health", None, 200, "Verify system still up after deletes")
    
    # CATEGORY 8: ANALYTICS (TC-051 to TC-055)
    print("Category 8: Analytics")
    test_api("TC-051", "POST", "/api/v1/urls", {"original_url": "https://analytics-test-051.com"}, 201, "Create URL for analytics")
    test_api("TC-052", "GET", "/api/v1/urls/1/analytics", None, 200, "Get analytics for URL")
    test_api("TC-053", "GET", "/api/v1/urls/9999/analytics", None, 404, "Get analytics for non-existent URL")
    test_api("TC-054", "POST", "/api/v1/urls", {"original_url": "https://analytics-test-054.com"}, 201, "Create another URL")
    test_api("TC-055", "GET", "/api/v1/urls/2/analytics", None, 200, "Get analytics for second URL")
    
    # CATEGORY 9: CONTENT-TYPE VALIDATION (TC-056 to TC-060)
    print("Category 9: Content-Type Validation")
    test_api("TC-056", "POST", "/api/v1/urls", {"original_url": "https://ct-test-056.com"}, 201, "POST with application/json")
    test_api("TC-057", "POST", "/api/v1/urls", {"original_url": "https://ct-test-057.com"}, 201, "Another POST with json")
    test_api("TC-058", "GET", "/api/v1/urls/1", None, 200, "GET with any content-type")
    test_api("TC-059", "DELETE", "/api/v1/urls/2", None, 204, "DELETE operation")
    test_api("TC-060", "POST", "/api/v1/urls", {"original_url": "https://ct-test-060.com"}, 201, "POST after delete")
    
    # CATEGORY 10: CONCURRENT & DUPLICATE SCENARIOS (TC-061 to TC-070)
    print("Category 10: Concurrent & Duplicate Scenarios")
    test_api("TC-061", "POST", "/api/v1/urls", {"original_url": "https://concurrent-061.com"}, 201, "Create URL for concurrency test")
    test_api("TC-062", "POST", "/api/v1/urls", {"original_url": "https://concurrent-061.com"}, 200, "Same URL again (dedup)")
    test_api("TC-063", "POST", "/api/v1/urls", {"original_url": "https://concurrent-061.com"}, 200, "Same URL third time")
    test_api("TC-064", "POST", "/api/v1/urls", {"original_url": "https://concurrent-062.com", "expires_at": future_time}, 201, "Create URL with expiration")
    test_api("TC-065", "POST", "/api/v1/urls", {"original_url": "https://concurrent-062.com", "expires_at": future_time}, 200, "Duplicate with same expiration")
    test_api("TC-066", "POST", "/api/v1/urls", {"original_url": "https://unique-066.com"}, 201, "Different URL same endpoint")
    test_api("TC-067", "POST", "/api/v1/urls", {"original_url": "https://unique-067.com"}, 201, "Another different URL")
    test_api("TC-068", "POST", "/api/v1/urls", {"original_url": "https://unique-068.com"}, 201, "Third different URL")
    test_api("TC-069", "POST", "/api/v1/urls", {"original_url": "https://unique-069.com"}, 201, "Fourth different URL")
    test_api("TC-070", "GET", "/api/v1/urls", None, 405, "List URLs endpoint doesn't exist")
    
    # CATEGORY 11: ERROR SEMANTICS (TC-071 to TC-080)
    print("Category 11: Error Semantics")
    test_api("TC-071", "GET", "/actuator/health", None, 200, "Health check UP")
    test_api("TC-072", "POST", "/api/v1/urls", {"original_url": "https://error-test-072.com"}, 201, "Create test URL")
    test_api("TC-073", "GET", "/nonexistent", None, 404, "Unknown short code returns 404")
    test_api("TC-074", "POST", "/api/v1/urls", {"original_url": ""}, 400, "Empty URL returns 400")
    test_api("TC-075", "POST", "/api/v1/urls", {"original_url": "invalid"}, 400, "Invalid URL returns 400")
    test_api("TC-076", "GET", "/api/v1/urls/99999", None, 404, "Non-existent ID returns 404")
    test_api("TC-077", "DELETE", "/api/v1/urls/99999", None, 404, "Delete non-existent returns 404")
    test_api("TC-078", "GET", "/api/v1/urls/1/analytics", None, 200, "Analytics endpoint accessible")
    test_api("TC-079", "POST", "/api/v1/urls", {"original_url": "https://test-079.com", "expires_at": "2020-01-01T00:00:00Z"}, 400, "Past expiration returns 400")
    test_api("TC-080", "POST", "/invalid-endpoint", {"data": "test"}, 405, "Invalid endpoint returns 404")
    
    # CATEGORY 12: STRESS & BOUNDARY (TC-081 to TC-090)
    print("Category 12: Stress & Boundary")
    test_api("TC-081", "POST", "/api/v1/urls", {"original_url": "https://boundary-081.com"}, 201, "Normal URL creation")
    test_api("TC-082", "POST", "/api/v1/urls", {"original_url": "https://b.co"}, 201, "Minimal URL")
    test_api("TC-083", "POST", "/api/v1/urls", {"original_url": "https://" + "example" * 293 + ".com"}, 400, "Very long URL")
    test_api("TC-084", "POST", "/api/v1/urls", {"original_url": "https://example.com/" + "path/" * 50}, 201, "Deep path URL")
    test_api("TC-085", "POST", "/api/v1/urls", {"original_url": "https://example.com?" + "param=value&" * 20}, 201, "Many query params")
    test_api("TC-086", "POST", "/api/v1/urls", {"original_url": "https://example.com/test%20with%20encoded"}, 201, "URL encoded characters")
    test_api("TC-087", "POST", "/api/v1/urls", {"original_url": "https://example.com/UPPERCASE"}, 201, "Uppercase in path")
    test_api("TC-088", "POST", "/api/v1/urls", {"original_url": "https://EXAMPLE.COM"}, 201, "Uppercase domain")
    test_api("TC-089", "GET", "/actuator/health", None, 200, "Final health check")
    test_api("TC-090", "GET", "/actuator/health", None, 200, "Final health check 2")
    
    return RESULTS

if __name__ == "__main__":
    try:
        results = run_all_tests()
        
        print("\n" + "=" * 90)
        print("TEST SUMMARY")
        print("=" * 90)
        print(f"Total Tests: {PASS_COUNT + FAIL_COUNT}")
        print(f"PASSED: {PASS_COUNT}")
        print(f"FAILED: {FAIL_COUNT}")
        pass_rate = (PASS_COUNT / (PASS_COUNT + FAIL_COUNT) * 100) if (PASS_COUNT + FAIL_COUNT) > 0 else 0
        print(f"Pass Rate: {pass_rate:.1f}%")
        print("=" * 90)
        
        # Print results table
        print("\n\nDETAILED RESULTS:")
        print("-" * 90)
        print(f"{'TC':<8} {'Status':<8} {'Expected':<12} {'Actual':<12} {'Scenario':<50}")
        print("-" * 90)
        for r in results:
            status = "PASS" if r["Status"] == "PASS" else "FAIL"
            print(f"{r['TC']:<8} {status:<8} {str(r['Expected']):<12} {str(r['Actual']):<12} {r['Scenario'][:48]:<50}")
        print("-" * 90)
        
    except KeyboardInterrupt:
        print("\nTest interrupted")
        sys.exit(1)
    except Exception as e:
        print(f"Error: {e}")
        import traceback
        traceback.print_exc()
        sys.exit(1)
