# OpenAPI/Swagger Quick Reference Card

**Keep this handy while implementing**

---

## Annotation Imports

\\\java
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
\\\

---

## Controller-Level Annotation

\\\java
@RestController
@RequestMapping
@Tag(name = "URL Management", description = "Create, redirect, and manage short URLs")
public class UrlController {
    // endpoints...
}
\\\

---

## Endpoint Pattern

\\\java
@PostMapping("/api/v1/urls")
@Operation(
    summary = "Create a short URL",
    description = "Creates a new short URL for the provided original URL, or returns existing if already shortened",
    tags = {"URL Management"}
)
@ApiResponses(value = {
    @ApiResponse(responseCode = "201", description = "Short URL created"),
    @ApiResponse(responseCode = "200", description = "Short URL already exists"),
    @ApiResponse(responseCode = "400", description = "Invalid input (validation failed)"),
    @ApiResponse(responseCode = "409", description = "URL conflict")
})
public ResponseEntity<CreateUrlResponse> createShortUrl(@Valid @RequestBody CreateUrlRequest request) {
    // implementation...
}
\\\

---

## DTO Pattern

\\\java
@Schema(description = "Request to create a short URL")
public class CreateUrlRequest {
    
    @Schema(
        description = "Original URL to shorten",
        example = "https://example.com/very/long/path",
        minLength = 1,
        maxLength = 2048
    )
    @NotBlank(message = "Original URL is required")
    @Size(min = 1, max = 2048, message = "URL must be between 1 and 2048 characters")
    @JsonProperty("original_url")
    private String originalUrl;

    @Schema(
        description = "Expiration date in ISO-8601 format (e.g., 2026-12-31T23:59:59Z)",
        example = "2026-12-31T23:59:59Z"
    )
    @Future(message = "Expiration date must be in the future")
    @JsonProperty("expires_at")
    private OffsetDateTime expiresAt;

    // getters/setters...
}
\\\

---

## All 5 Endpoints

### 1. POST /api/v1/urls

\\\java
@ApiResponses(value = {
    @ApiResponse(responseCode = "201", description = "Short URL created"),
    @ApiResponse(responseCode = "200", description = "Short URL already exists"),
    @ApiResponse(responseCode = "400", description = "Invalid input"),
    @ApiResponse(responseCode = "409", description = "Conflict")
})
\\\

### 2. GET /{shortCode}

\\\java
@ApiResponses(value = {
    @ApiResponse(responseCode = "302", description = "Redirect to original URL"),
    @ApiResponse(responseCode = "404", description = "Short code not found"),
    @ApiResponse(responseCode = "410", description = "URL expired or disabled")
})
\\\

### 3. GET /api/v1/urls/{id}

\\\java
@ApiResponses(value = {
    @ApiResponse(responseCode = "200", description = "Short URL details"),
    @ApiResponse(responseCode = "404", description = "Short URL not found")
})
\\\

### 4. DELETE /api/v1/urls/{id}

\\\java
@ApiResponses(value = {
    @ApiResponse(responseCode = "204", description = "Short URL disabled"),
    @ApiResponse(responseCode = "404", description = "Short URL not found")
})
\\\

### 5. GET /api/v1/urls/{id}/analytics

\\\java
@ApiResponses(value = {
    @ApiResponse(responseCode = "200", description = "Analytics data"),
    @ApiResponse(responseCode = "404", description = "Short URL not found"),
    @ApiResponse(responseCode = "410", description = "URL expired or disabled")
})
\\\

---

## Response Schemas

### CreateUrlResponse
- id: Long (database ID)
- short_code: String (e.g., "abc123de")
- original_url: String (full URL)
- short_url: String (full short URL)

### AnalyticsResponse
- short_url_id: Long
- short_code: String
- total_clicks: Long
- unique_visitors: Long
- last_clicked_at: OffsetDateTime

### ErrorResponse
- status: int (HTTP status code)
- message: String (error description)
- timestamp: long (Unix milliseconds)

---

## Optional: application.yml Configuration

\\\yaml
springdoc:
  api-docs:
    path: /api/v3/api-docs
  swagger-ui:
    path: /swagger-ui.html
    tags-sorter: alpha
    operations-sorter: method
  show-actuator: false
\\\

---

## Testing Checklist

- [ ] Start application: \docker-compose up\
- [ ] Open http://localhost:8080/swagger-ui.html
- [ ] Verify all 5 endpoints appear
- [ ] Test POST /api/v1/urls with: \{"original_url": "https://example.com/path"}\
- [ ] See 201 CREATED response with short_code
- [ ] See request/response schemas in Swagger UI
- [ ] Click "Try it out" and test endpoints
- [ ] Verify error responses (400, 404, 410, 409)
- [ ] Run \mvn clean test\ → all 90 tests pass ✅

---

## Swagger UI Access

- **Interactive UI:** http://localhost:8080/swagger-ui.html
- **OpenAPI JSON:** http://localhost:8080/api/v3/api-docs
- **OpenAPI YAML:** http://localhost:8080/api/v3/api-docs.yaml

---

## Common Mistakes to Avoid

❌ DON'T:
- Add @Schema with wrong imports (use io.swagger.v3.oas.annotations.media.Schema)
- Forget @ApiResponse for all possible status codes
- Use old Swagger 2.0 annotations (@ApiModel, @ApiModelProperty)
- Add code beyond annotations (they're non-invasive)
- Change pom.xml (dependency already there)

✅ DO:
- Use consistent formatting
- Document all 10 status codes
- Add helpful descriptions
- Include example values
- Keep annotations focused and clear

---

## Status Codes Quick Reference

| Code | Meaning | When | Use In |
|------|---------|------|--------|
| 200 | OK | Successful GET | /api/v1/urls/{id}, /analytics |
| 201 | Created | New resource | POST /api/v1/urls |
| 204 | No Content | Successful delete | DELETE /api/v1/urls/{id} |
| 302 | Found (Redirect) | Follow link | GET /{shortCode} |
| 400 | Bad Request | Invalid input | Any endpoint (validation failed) |
| 404 | Not Found | Resource missing | GET /{shortCode}, GET /api/v1/urls/{id} |
| 405 | Method Not Allowed | Wrong HTTP method | Any endpoint (wrong method) |
| 409 | Conflict | Duplicate | POST /api/v1/urls (URL exists) |
| 410 | Gone | Expired/disabled | GET /{shortCode}, /analytics |
| 500 | Internal Error | Unexpected | Any endpoint (runtime error) |

---

## Time Saver: Copy-Paste Templates

### @Operation Template
\\\java
@Operation(
    summary = "SUMMARY HERE",
    description = "DESCRIPTION HERE",
    tags = {"URL Management"}
)
\\\

### @ApiResponses Template
\\\java
@ApiResponses(value = {
    @ApiResponse(responseCode = "200", description = "..."),
    @ApiResponse(responseCode = "400", description = "..."),
    @ApiResponse(responseCode = "404", description = "...")
})
\\\

### @Schema Template for DTO Field
\\\java
@Schema(description = "...", example = "...")
@JsonProperty("field_name")
private String fieldName;
\\\

---

## Files Checklist

- [ ] UrlController.java - Add @Tag, @Operation, @ApiResponses (10 min)
- [ ] CreateUrlRequest.java - Add @Schema (5 min)
- [ ] CreateUrlResponse.java - Add @Schema (5 min)
- [ ] AnalyticsResponse.java - Add @Schema (3 min)
- [ ] ErrorResponse.java - Add @Schema (3 min)
- [ ] ShortUrl.java - Add @Schema (4 min)
- [ ] application.yml - Optional config (5 min)

**Total: ~45 minutes**

---

**Print this card and keep it handy while implementing!**

