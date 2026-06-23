# Implementation Plan: AI UI Review Agent

## Overview

Implementation proceeds in four broad phases, each building on the previous:
1. **Project scaffolding** — Docker Compose, Spring Boot skeleton, React + Vite skeleton
2. **Backend core** — data model, validation, Claude integration, REST endpoints, rate limiting
3. **Frontend core** — upload zone, analysis panel, history panel, export
4. **Integration and wiring** — connect frontend to backend, E2E tests, coverage enforcement

Each phase ends with a checkpoint. Testing sub-tasks are marked `*` (optional for MVP pace); core implementation tasks are mandatory.

---

## Tasks

- [x] 1. Project scaffolding and infrastructure
  - [x] 1.1 Create Docker Compose and service configuration
    - Write `docker-compose.yml` defining `frontend` (port 3000), `backend` (port 8080), and `db` (MariaDB 11, port 3306) services
    - Accept `ANTHROPIC_API_KEY` and DB credentials exclusively via environment variables
    - Add a `.env.example` documenting all required variables
    - _Requirements: 16.1, 16.2_

  - [x] 1.2 Initialize Spring Boot 3 backend project
    - Use Spring Initializr with dependencies: Spring Web, Spring Data JPA, Spring Boot Actuator, Validation, MariaDB driver
    - Add Bucket4j, jqwik, JaCoCo plugin, and Testcontainers to `pom.xml` with pinned versions
    - Configure `application.properties` with DB URL, pool size, Anthropic timeout (default 12s), rate-limit threshold, and CORS allowed origin
    - Verify `/actuator/health` returns 200
    - _Requirements: 16.3, 16.4_

  - [x] 1.3 Initialize React 18 + Vite 5 frontend project
    - Scaffold with `npm create vite@latest` using React + TypeScript template
    - Install and configure: Tailwind CSS 3, Axios 1.x, Zustand 4, Lucide React, jsPDF + html2canvas, Vitest 1, Playwright 1
    - Configure `vite.config.ts` with API proxy to `http://localhost:8080`
    - Set up Vitest with jsdom and Testing Library
    - _Requirements: 16.5_

  - [x] 1.4 Configure startup validation for Anthropic API key
    - Implement a `@PostConstruct` bean in `AnthropicConfig.java` that reads `ANTHROPIC_API_KEY` from the environment
    - IF the key is absent or blank, throw an `IllegalStateException` with a descriptive message to halt startup
    - _Requirements: 11.4_

  - [ ]* 1.5 Smoke test: health endpoint and startup validation
    - Write a Spring Boot integration test asserting `/actuator/health` returns HTTP 200
    - Write a test asserting the application context fails to start when `ANTHROPIC_API_KEY` is missing
    - _Requirements: 16.3, 11.4_

- [x] 2. Database layer and persistence
  - [x] 2.1 Create AnalysisRecord JPA entity and repository
    - Implement `AnalysisRecord.java` with all fields from the schema: `id` (UUID v4), `sessionId`, `createdAt`, `overallScore`, `processingMs`, `imageHash`, `rawResponse`, `focusHint`, `ipAddress`
    - Annotate with `@Entity`, `@Table`, `@Index` on `session_id` and `image_hash`
    - Create `AnalysisRepository extends JpaRepository<AnalysisRecord, String>` with:
      - `findBySessionIdOrderByCreatedAtDesc(String sessionId, Pageable pageable)`
      - `findByImageHashAndCreatedAtAfter(String hash, LocalDateTime cutoff)`
    - _Requirements: 14.1, 14.2, 14.3_

  - [x] 2.2 Create DTO classes
    - Implement `AnalysisResultDto`, `CategoryDto`, `SuggestionDto`, `AnalysisSummaryDto` as Java records
    - Annotate with `@JsonProperty` for camelCase JSON serialization
    - Add `@NotNull`, `@Min`, `@Max` Bean Validation annotations where applicable
    - _Requirements: 3.8, 4.1, 4.2, 4.3_

  - [ ]* 2.3 Integration test: persistence round-trip
    - Using Testcontainers (MariaDB), write an integration test that inserts an `AnalysisRecord` with all fields populated and reads it back
    - Verify all fields survive the round-trip without truncation or type coercion
    - _Requirements: 14.1_

- [x] 3. Backend file validation
  - [x] 3.1 Implement file validation logic in AnalysisService
    - Check MIME type is one of `image/png`, `image/jpeg`, `image/webp`; throw `InvalidFileTypeException` otherwise
    - Check file size is `> 0` and `< 10,485,760` bytes; throw `FileTooLargeException` if `size >= 10,485,760` or `== 0`
    - Check filename does not contain `../` or start with `/`; throw `PathTraversalException` if detected
    - Compute SHA-256 hash of the file bytes for cache lookup
    - _Requirements: 3.2, 3.3, 11.3_

  - [ ]* 3.2 Property test: MIME type validation boundary (Property 2)
    - **Property 2: MIME Type Validation Accepts Only Allowed Types**
    - **Validates: Requirements 3.2, 10.1**
    - Use jqwik `@Property` to generate arbitrary MIME type strings (including edge cases: empty string, uppercase, subtypes with parameters)
    - Assert only `image/png`, `image/jpeg`, `image/webp` pass; all others throw `InvalidFileTypeException`

  - [ ]* 3.3 Property test: file size validation boundary (Property 1)
    - **Property 1: File Size Validation Rejects Boundary and Oversized Files**
    - **Validates: Requirements 3.3**
    - Use jqwik `@Property` to generate file sizes: 0, 1, random in (1, 10MB), exactly 10MB, random above 10MB
    - Assert sizes `>0` and `<10,485,760` pass; sizes `>=10,485,760` or `==0` throw the appropriate exception

  - [ ]* 3.4 Property test: path traversal filename rejection (Property from 11.3)
    - Use jqwik `@Property` to generate filenames with and without `../` and absolute path prefixes
    - Assert traversal filenames are always rejected; safe filenames are always accepted

- [x] 4. Anthropic client integration
  - [x] 4.1 Implement AnthropicClientService
    - Use Spring's `RestClient` (or `WebClient`) configured with a 12-second connect + read timeout
    - Build the structured JSON prompt (see design — prompt structure section), injecting `focusHint` when present
    - Base64-encode the image bytes and include as a `vision` content block
    - Call `POST https://api.anthropic.com/v1/messages` with model `claude-sonnet-4-6`
    - Parse the response body string into `AnalysisResultDto` using Jackson
    - _Requirements: 3.4, 3.5, 3.6, 4.1, 4.2, 4.3_

  - [x] 4.2 Implement exponential backoff retry in AnthropicClientService
    - Retry on HTTP 429 (Anthropic rate limit), HTTP 5xx, `SocketTimeoutException`, `ConnectException`
    - Retry schedule: attempt 1 immediately, attempt 2 after 1s, attempt 3 after 2s, attempt 4 after 4s (max 3 retries)
    - After 3 retries exhausted, throw `UpstreamApiException`
    - Non-retryable errors (400, 401) propagate immediately as `AnalysisFailedException`
    - _Requirements: 3.7, 10.3, 10.4_

  - [ ]* 4.3 Unit test: prompt construction with and without focus hint
    - Verify that the assembled prompt string contains the JSON schema instruction
    - Verify that when `focusHint` is provided, the prompt contains the focus hint text
    - Verify that when `focusHint` is null/blank, no focus hint line appears in the prompt
    - _Requirements: 2.2, 2.3, 4.1, 4.2, 4.3_

  - [ ]* 4.4 Property test: JSON response round-trip parsing (Property 4)
    - **Property 4: JSON Response Round-Trip Consistency**
    - **Validates: Requirements 3.8, 4.1, 4.2, 4.3**
    - Use jqwik to generate arbitrary `AnalysisResultDto` instances (random scores, random suggestion texts, random severities from the allowed enum)
    - Serialize to JSON with Jackson, parse back into `AnalysisResultDto`, assert structural equivalence

  - [ ]* 4.5 Property test: suggestion severity membership (Property 8)
    - **Property 8: Suggestion Severity Membership**
    - **Validates: Requirements 4.3, 10.3**
    - Generate JSON responses with valid and invalid severity strings
    - Assert valid responses parse successfully; responses with invalid severity values throw `AnalysisFailedException`

- [x] 5. Overall score computation
  - [x] 5.1 Implement weighted score computation in AnalysisService
    - Accept a list of `CategoryDto` (with names matching the 5 required categories)
    - Apply weights: Layout 0.20, Typography 0.20, Color & Contrast 0.20, Accessibility 0.25, Consistency 0.15
    - Compute `round(sum of weight * score)`, clamp result to [0, 100]
    - _Requirements: 4.4, 4.5_

  - [ ]* 5.2 Property test: weighted score invariant (Property 3)
    - **Property 3: Overall Score Derivation Invariant**
    - **Validates: Requirements 4.4, 4.5**
    - Use jqwik to generate random tuples of 5 scores in [0, 100]
    - Verify that the computed `overallScore` equals the weighted sum formula result
    - Verify that `overallScore` is always in [0, 100]

- [x] 6. Image caching layer
  - [x] 6.1 Implement cache lookup and storage in AnalysisService
    - After computing SHA-256 hash, query `AnalysisRepository.findByImageHashAndCreatedAtAfter(hash, now - 24h)`
    - If a matching record exists, deserialize `rawResponse` into `AnalysisResultDto`, set `cached = true`, and return without calling Claude
    - If no match, proceed with AI call, then persist the new `AnalysisRecord` with `imageHash` set
    - _Requirements: 3.10_

  - [ ]* 6.2 Property test: cache hit idempotence (Property 5)
    - **Property 5: Cache Hit Idempotence**
    - **Validates: Requirements 3.10**
    - Mock the `AnalysisRepository` to return a pre-existing record for a given hash within 24h
    - For any image hash, assert the second call returns `cached=true`, same `id`, same `overallScore`

- [x] 7. Rate limiting
  - [x] 7.1 Implement Bucket4j rate limiter
    - Configure a `Bucket` per client IP using `BucketConfiguration` with capacity 10 tokens, refill 10 per 60 seconds
    - Use a `ConcurrentHashMap<String, Bucket>` keyed by IP address
    - Read the threshold from `application.properties` (`rate-limit.requests-per-minute`, default 10)
    - Integrate as a Spring `HandlerInterceptor` on `POST /api/v1/analyze`
    - _Requirements: 8.1, 8.3_

  - [x] 7.2 Return HTTP 429 with Retry-After header
    - When the bucket is exhausted, throw `RateLimitExceededException`
    - In `GlobalExceptionHandler`, respond with HTTP 429, error code `RATE_LIMIT_EXCEEDED`, and `Retry-After` header (seconds until next refill)
    - _Requirements: 8.2, 8.4_

  - [ ]* 7.3 Property test: rate limit threshold invariant (Property 7)
    - **Property 7: Rate Limit Threshold Invariant**
    - **Validates: Requirements 8.1, 8.2, 8.4**
    - Use jqwik to generate request counts k in [1, 10]; verify all k requests succeed
    - For counts k in [11, 20]; verify the 11th and beyond return 429 with `Retry-After` header

- [x] 8. REST controllers and exception handler
  - [x] 8.1 Implement AnalysisController
    - `POST /api/v1/analyze`: accept multipart/form-data (`file`, `focusHint`, `sessionId`), extract client IP, delegate to `AnalysisService`, return 200 with `AnalysisResultDto`
    - `GET /api/v1/history`: accept `sessionId` and `limit` query params, delegate to `AnalysisService.getHistory()`, return 200 with list of `AnalysisSummaryDto`
    - `GET /api/v1/analysis/{id}`: return 200 or 404
    - `DELETE /api/v1/analysis/{id}`: return 204 or 404
    - _Requirements: 3.1, 7.1, 7.5, 7.6_

  - [x] 8.2 Implement GlobalExceptionHandler
    - `@RestControllerAdvice` class mapping each domain exception to its HTTP status + error code + timestamp
    - Map: `InvalidFileTypeException` → 400, `FileTooLargeException` → 400, `PathTraversalException` → 400, `AnalysisFailedException` → 422, `RateLimitExceededException` → 429, `UpstreamApiException` → 500, `ServiceOverloadedException` → 503
    - _Requirements: 10.1–10.7_

  - [x] 8.3 Implement history limit enforcement in AnalysisService
    - `getHistory(sessionId, limit)`: query DB ordered by `created_at DESC` with `Pageable.ofSize(limit)` where limit defaults to 5 when null and 0 when explicitly provided as 0
    - _Requirements: 7.2, 7.3_

  - [ ]* 8.4 Integration tests: REST endpoints
    - Use `@SpringBootTest` + `MockMvc` + Testcontainers for MariaDB
    - Test cases: valid PNG upload → 200 with AnalysisResultDto; invalid MIME → 400 INVALID_FILE_TYPE; oversized file → 400 FILE_TOO_LARGE; mock Claude failure → 422 ANALYSIS_FAILED; 11th request → 429; GET history → correct array; DELETE → 204
    - _Requirements: 3.1–3.3, 7.1–7.6, 8.1–8.4, 10.1–10.5_

  - [ ]* 8.5 Property test: history limit enforcement (Property 6 + history API Property 9)
    - **Property 6: History Limit Enforcement** and **Property 9: History Session Isolation**
    - **Validates: Requirements 6.1, 7.1, 7.2, 7.3**
    - Generate random sequences of analysis inserts across two distinct session IDs
    - Verify each session's history contains only its own records and respects the limit

- [ ] 9. Checkpoint — Backend complete
  - Ensure all tests pass, JaCoCo line coverage is above 80%, and the backend Docker container starts cleanly with a valid API key environment variable.
  - Ask the user if any adjustments are needed before proceeding to the frontend.

- [x] 10. Frontend: Zustand store and API client
  - [x] 10.1 Implement Zustand store (useStore.ts)
    - Define and implement `AppState` with slices: `selectedFile`, `previewUrl`, `focusHint`, `analysisResult`, `isAnalyzing`, `analysisError`, `history`
    - Implement `setFile`, `setFocusHint`, `submitAnalysis`, `restoreFromHistory`, `clearHistory` actions
    - `submitAnalysis` calls the Axios API client, sets loading state, updates history on success
    - _Requirements: 1.5, 1.6, 1.7, 5.6, 6.1_

  - [x] 10.2 Implement Axios API client (api.ts)
    - Configure Axios instance with `baseURL: /api/v1` (proxied by Vite in dev, Nginx in production)
    - Add request interceptor to attach `sessionId` (generated once per session and stored in `sessionStorage`)
    - Add response interceptor to extract `errorCode` and set human-readable `analysisError` in the store
    - _Requirements: 3.1, 7.1_

  - [ ]* 10.3 Unit tests: store actions
    - Test `setFile` with valid PNG, invalid MIME, and oversized file — verify `analysisError` is set for invalid inputs
    - Test `submitAnalysis` happy path with mocked Axios — verify `history` grows and `isAnalyzing` transitions correctly
    - Test `clearHistory` — verify state and sessionStorage are both empty after the call
    - _Requirements: 1.6, 1.7, 6.1, 6.6_

- [ ] 11. Frontend: Upload Zone components
  - [ ] 11.1 Implement DropArea component
    - Handle `dragenter`, `dragover`, `dragleave`, `drop` events; apply `drag-over` CSS class during active drag
    - On drop, read `event.dataTransfer.files[0]`, validate MIME type and size client-side, call `store.setFile`
    - Render file input `<input type="file" accept=".png,.jpg,.jpeg,.webp">` as keyboard-accessible fallback
    - _Requirements: 1.1, 1.3, 1.4, 1.10_

  - [ ] 11.2 Implement ImagePreview component
    - Accept `file: File | null` prop
    - Use `FileReader.readAsDataURL` to produce a preview URL; render `<img>` with the data URL
    - Display nothing (or placeholder) when `file` is null
    - _Requirements: 1.5_

  - [ ] 11.3 Implement FocusHintInput component
    - Render labeled `<input type="text" maxLength={256}>` bound to `store.focusHint`
    - _Requirements: 2.1_

  - [ ] 11.4 Implement AnalyzeButton component
    - Render `<button disabled={!store.selectedFile || store.isAnalyzing}>`
    - Display spinner icon when `isAnalyzing` is true
    - On click, call `store.submitAnalysis()`
    - Render inline error message from `store.analysisError` below the button
    - _Requirements: 1.6, 1.7, 5.6, 10.6_

  - [ ]* 11.5 Unit tests: UploadZone
    - Test drag-over CSS class applied on `dragenter` and removed on `dragleave`
    - Test that dropping an invalid MIME file sets `analysisError` and does not set `selectedFile`
    - Test that dropping a valid file sets `selectedFile` and shows preview
    - Test button is disabled when no file selected, enabled when file selected
    - _Requirements: 1.1–1.10_

  - [ ]* 11.6 Property test: file acceptance in frontend matches backend rules (Property 2 reflection)
    - Generate arbitrary MIME type strings in the frontend validator
    - Verify the frontend accept-set exactly matches {image/png, image/jpeg, image/webp, image/jpg}
    - Verify the size check rejects files >= 10MB and accepts files < 10MB
    - _Requirements: 1.1, 1.2_

- [ ] 12. Frontend: Analysis Panel
  - [ ] 12.1 Implement ScoreRing component
    - Render an SVG circular progress indicator showing `overallScore` (0–100)
    - Display the numeric score in the center of the ring
    - _Requirements: 5.1_

  - [ ] 12.2 Implement CategoryScoreBar component
    - Accept `category: CategoryDto` prop
    - Render the category name, a horizontal `<progress>` or styled div bar reflecting `score`, and a collapse toggle button
    - Default to expanded; clicking toggle collapses/expands the suggestions list
    - _Requirements: 5.2, 5.5_

  - [ ] 12.3 Implement SuggestionCard component
    - Accept `suggestion: SuggestionDto` prop
    - Render severity badge with distinct Tailwind color classes: `Critical` = red, `Warning` = amber, `Suggestion` = blue
    - Render title, description, and recommendation fields
    - _Requirements: 5.3, 5.4_

  - [ ] 12.4 Implement AnalysisPanel component
    - Compose `ScoreRing`, list of `CategoryScoreBar` (each with its `SuggestionCard` children), and `ExportToolbar`
    - Show a spinner/skeleton when `store.isAnalyzing` is true
    - Render nothing (or an upload-prompt message) when `store.analysisResult` is null
    - _Requirements: 5.1–5.6_

  - [ ]* 12.5 Unit tests: AnalysisPanel rendering
    - Render with a complete mock `AnalysisResultDto`; assert score ring value, 5 category bars, suggestion cards
    - Assert Critical suggestion badge has red styling, Warning has amber, Suggestion has blue
    - Assert collapsing a category hides its suggestion cards
    - _Requirements: 5.1–5.5_

- [ ] 13. Frontend: History Panel
  - [ ] 13.1 Implement HistoryItem component
    - Accept `entry: HistoryEntry` prop
    - Render 128×128 `<img src={entry.thumbnailDataUrl}>`, formatted timestamp, overall score badge, duration in ms
    - On click, call `store.restoreFromHistory(entry)`
    - _Requirements: 6.3, 6.4_

  - [ ] 13.2 Implement ClearHistoryButton with confirmation modal
    - Render a "Clear History" button
    - On click, open a confirmation `<dialog>` or modal component asking the user to confirm
    - On confirm, call `store.clearHistory()` which removes all entries from state and sessionStorage
    - _Requirements: 6.5, 6.6_

  - [ ] 13.3 Implement HistoryPanel component
    - On mount, load history from sessionStorage into `store.history` if present
    - Render list of `HistoryItem` components (max 5)
    - Render `ClearHistoryButton`
    - When history is empty, render a non-interactive placeholder message
    - _Requirements: 6.1, 6.2, 6.7_

  - [ ]* 13.4 Unit tests: HistoryPanel
    - Test sessionStorage round-trip: add items, simulate unmount/remount, verify items restored
    - Test max-5 enforcement: add 6 items, verify only 5 most recent remain
    - Test clear with confirmation: open modal, cancel → items remain; confirm → items cleared
    - Test empty state renders without errors
    - _Requirements: 6.1, 6.2, 6.5, 6.6, 6.7_

  - [ ]* 13.5 Property test: history capped at 5 entries (Property 6)
    - **Property 6: History Limit Enforcement**
    - **Validates: Requirements 6.1**
    - Generate sequences of 0–10 `HistoryEntry` additions using Vitest's fast-check (fc)
    - After each addition, assert `history.length === Math.min(totalAdded, 5)`

  - [ ]* 13.6 Property test: sessionStorage round-trip (Property — from 6.2)
    - Generate random arrays of up to 5 `HistoryEntry` objects
    - Write to sessionStorage via the store serializer, read back via the store deserializer
    - Assert the deserialized entries are deeply equal to the originals

- [ ] 14. Frontend: Export Toolbar
  - [ ] 14.1 Implement exportUtils.ts
    - `generateMarkdown(result: AnalysisResultDto): string` — produce a Markdown string containing overall score, all 5 category names + scores, and all suggestion titles, descriptions, and recommendations
    - `exportPdf(elementId: string): Promise<void>` — use `html2canvas` to capture the element, pass canvas to `jsPDF`, download as `ui-review-{id}.pdf`; catch errors and still attempt download of whatever was produced
    - _Requirements: 9.1, 9.2_

  - [ ] 14.2 Implement ExportToolbar component
    - Render "Export PDF" button calling `exportUtils.exportPdf('analysis-panel')`
    - Render "Copy as Markdown" button calling `navigator.clipboard.writeText(generateMarkdown(result))`
    - Disable both buttons when `analysisResult` is null
    - _Requirements: 9.1, 9.2_

  - [ ]* 14.3 Property test: markdown completeness (Property 10)
    - **Property 10: Export Markdown Completeness**
    - **Validates: Requirements 9.2**
    - Use fast-check to generate arbitrary `AnalysisResultDto` objects (random scores, random suggestion texts)
    - Call `generateMarkdown(result)` and assert the output string contains: the overall score, all 5 category names, all category scores, and every suggestion title

  - [ ]* 14.4 Unit test: PDF export error resilience
    - Mock `html2canvas` to throw an error
    - Assert `exportPdf` still calls `jsPDF.save()` (attempts download of partial output)
    - _Requirements: 9.1_

- [ ] 15. Checkpoint — Frontend core complete
  - Ensure all tests pass, Vitest line coverage is above 70%, and the full React application renders correctly at http://localhost:3000.
  - Ask the user if any adjustments are needed before wiring integration.

- [x] 16. Security hardening and CORS
  - [x] 16.1 Configure CORS in Spring Boot
    - Create `CorsConfig.java` with `@Configuration` and `WebMvcConfigurer`
    - Allow origin only from `FRONTEND_ORIGIN` environment variable (e.g., `http://localhost:3000`)
    - Allow methods: GET, POST, DELETE; allow headers: Content-Type, X-Session-ID
    - _Requirements: 11.5_

  - [ ]* 16.2 Unit test: API key never in responses (Property 11 — security invariant)
    - **Property: API key not exposed in any HTTP response**
    - **Validates: Requirements 11.1**
    - For each endpoint (analyze, history, get-by-id, delete, health), call the endpoint and assert the `ANTHROPIC_API_KEY` value does not appear anywhere in the response body or headers

- [ ] 17. End-to-end integration and Playwright tests
  - [ ]* 17.1 Happy path E2E test
    - Upload a valid PNG screenshot via the drag-and-drop zone
    - Assert loading indicator appears
    - Assert Analysis Panel renders with score ring, 5 categories, at least one suggestion card
    - Assert the newly added history entry appears in the History Panel
    - _Requirements: 3.1–3.9, 5.1–5.6, 6.1–6.4_

  - [ ]* 17.2 Export E2E tests
    - Trigger "Export PDF" and assert a file download occurs
    - Trigger "Copy as Markdown" and assert clipboard contains the analysis content
    - _Requirements: 9.1, 9.2_

  - [ ]* 17.3 Error path E2E test
    - Attempt to drop an invalid file type and assert the error message appears
    - Mock the backend to return 429 and assert the rate-limit error message is displayed
    - _Requirements: 1.8, 8.2, 10.6_

- [ ] 18. Final checkpoint — Full integration
  - Ensure all tests pass (backend JaCoCo > 80%, frontend Vitest > 70%)
  - Verify `docker-compose up` starts all three services and the application is reachable at http://localhost:3000
  - Verify `/actuator/health` returns 200
  - Ask the user if any adjustments are needed before declaring the spec complete.

---

## Task Dependency Graph

```json
{
  "waves": [
    {
      "wave": 1,
      "tasks": ["1"],
      "description": "Project scaffolding and infrastructure"
    },
    {
      "wave": 2,
      "tasks": ["2", "3", "7", "10"],
      "description": "Database layer, file validation, rate limiting, frontend store — all depend only on wave 1"
    },
    {
      "wave": 3,
      "tasks": ["4", "11", "13"],
      "description": "Anthropic client (depends on 2), Upload Zone (depends on 10), History Panel (depends on 10)"
    },
    {
      "wave": 4,
      "tasks": ["5", "6", "12"],
      "description": "Score computation (depends on 4), caching (depends on 2+4), Analysis Panel (depends on 10)"
    },
    {
      "wave": 5,
      "tasks": ["8", "14"],
      "description": "REST controllers (depends on 2,3,5,6,7), Export Toolbar (depends on 10,12)"
    },
    {
      "wave": 6,
      "tasks": ["9", "15"],
      "description": "Backend checkpoint (depends on 8), Frontend checkpoint (depends on 11,12,13,14)"
    },
    {
      "wave": 7,
      "tasks": ["16"],
      "description": "Security hardening and CORS (depends on 8)"
    },
    {
      "wave": 8,
      "tasks": ["17"],
      "description": "E2E tests (depends on 15, 16)"
    },
    {
      "wave": 9,
      "tasks": ["18"],
      "description": "Final checkpoint (depends on 9, 17)"
    }
  ]
}
```

---

## Notes

- Tasks marked with `*` are optional and can be skipped for faster MVP delivery
- Each property-based test maps to a named property in `design.md` — use the annotation format: `// Feature: ai-ui-review-agent, Property N: <title>`
- jqwik is used for backend property-based tests; fast-check (`fc`) is used for frontend property-based tests (via Vitest)
- Testcontainers is used for all backend integration tests requiring a real MariaDB instance
- The Anthropic API is always mocked in unit and property tests; only the E2E tests may call the real API (controlled by an environment flag)
- All four backend test categories (rate limiting, file validation, prompt construction, response parsing) must exist before the build passes
