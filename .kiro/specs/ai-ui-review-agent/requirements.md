# Requirements Document

## Introduction

The AI UI Review Agent is a full-stack web application that enables users to upload UI screenshots and receive immediate, structured design feedback powered by Anthropic's Claude Vision API. The system proxies all AI requests through a secure Spring Boot backend, stores analysis history in MariaDB, and presents results through a React 18 frontend. The application targets junior developers and solo founders who lack access to experienced designers for rapid UI/UX review cycles.

## Glossary

- **Analysis_Service**: The Spring Boot backend service responsible for orchestrating the full analysis pipeline
- **Anthropic_Client**: The internal service wrapper that communicates with the Anthropic Claude Vision API
- **Upload_Component**: The React frontend component that accepts image files and initiates analysis
- **Analysis_Panel**: The React frontend component that renders analysis results to the user
- **History_Panel**: The React frontend component that displays and manages recent analysis history
- **Rate_Limiter**: The Bucket4j-based component that enforces per-IP request throttling
- **Analysis_Record**: The JPA entity representing a persisted analysis result in MariaDB
- **Session**: A browser-scoped identifier used to group analyses by user without authentication
- **Overall_Score**: A numeric value from 0 to 100 representing the aggregate UI quality rating
- **Category_Score**: A numeric value from 0 to 100 representing quality within a single evaluation dimension
- **Suggestion**: A discrete piece of actionable feedback with a severity level, title, description, and recommendation
- **Focus_Hint**: An optional free-text field the user provides to direct the AI analysis toward specific concerns
- **Image_Hash**: A SHA-256 digest of the uploaded image binary used for cache deduplication
- **Analysis_Result_DTO**: The structured data transfer object containing the full parsed response from Claude
- **Export_Toolbar**: The UI component providing PDF export and markdown copy functionality

---

## Requirements

### Requirement 1: Image Upload

**User Story:** As a user, I want to upload a UI screenshot so that I can receive automated design feedback.

#### Acceptance Criteria

1. THE Upload_Component SHALL accept image files of type PNG, JPG, JPEG, and WebP.
2. THE Upload_Component SHALL reject any file whose size exceeds 10 megabytes.
3. THE Upload_Component SHALL accept image files via drag-and-drop interaction.
4. THE Upload_Component SHALL accept image files via a file picker dialog as a fallback to drag-and-drop.
5. WHEN a valid image file is selected, THE Upload_Component SHALL display an inline preview of the image using the FileReader API.
6. WHEN no valid file has been selected, THE Upload_Component SHALL render the Analyze button in a disabled state.
7. WHEN a valid file is selected, THE Upload_Component SHALL render the Analyze button in an enabled state.
8. WHEN a user drops a file of an unsupported MIME type, THE Upload_Component SHALL display an error message indicating the accepted file types.
9. WHEN a user selects a file exceeding 10 megabytes, THE Upload_Component SHALL display an error message indicating the size limit.
10. THE Upload_Component SHALL provide visible drag-over visual feedback while a file is being dragged over the drop area.

---

### Requirement 2: Focus Hint Input

**User Story:** As a user, I want to optionally provide a focus area for the review so that the AI directs its analysis toward my specific concerns.

#### Acceptance Criteria

1. THE Upload_Component SHALL include an optional text input field labeled as the Focus Hint.
2. WHEN the user submits an analysis, THE Analysis_Service SHALL forward the Focus_Hint value to the Anthropic_Client if one is provided.
3. WHEN no Focus_Hint is provided, THE Analysis_Service SHALL invoke the Anthropic_Client without a focus hint parameter.

---

### Requirement 3: Analysis Execution

**User Story:** As a user, I want the system to analyze my uploaded screenshot using AI so that I receive structured and detailed UI feedback.

#### Acceptance Criteria

1. WHEN the user activates the Analyze button, THE Analysis_Service SHALL accept a multipart/form-data POST request to `/api/v1/analyze` containing the image file, an optional Focus_Hint, and an optional Session identifier.
2. THE Analysis_Service SHALL validate that the submitted file's MIME type is one of `image/png`, `image/jpeg`, or `image/webp` before processing.
3. THE Analysis_Service SHALL validate that the submitted file size is greater than 0 bytes and strictly less than 10,485,760 bytes (10MB) before processing; files of exactly 10MB SHALL be rejected.
4. WHEN file validation passes, THE Analysis_Service SHALL convert the image binary to Base64 encoding and forward it to the Anthropic_Client.
5. THE Anthropic_Client SHALL invoke the Claude Vision API model `claude-sonnet-4-6` with a structured prompt that requests JSON output.
6. THE Anthropic_Client SHALL apply a 12-second timeout to each Claude Vision API request.
7. WHEN a Claude Vision API request fails with a transient error, THE Anthropic_Client SHALL retry using exponential backoff.
8. THE Analysis_Service SHALL parse the Claude Vision API response into an Analysis_Result_DTO containing an Overall_Score, a categories array, and a suggestions array.
9. WHEN analysis completes successfully, THE Analysis_Service SHALL persist an Analysis_Record to MariaDB and return the Analysis_Result_DTO to the caller.
10. WHEN two requests contain images with identical Image_Hash values, THE Analysis_Service SHALL return the cached Analysis_Record within 24 hours of initial analysis without re-invoking the Claude Vision API.

---

### Requirement 4: Structured Prompt Engineering

**User Story:** As a developer, I want the AI prompt to enforce a strict JSON schema so that the backend can reliably parse and structure the response.

#### Acceptance Criteria

1. THE Anthropic_Client SHALL include in every prompt an instruction for Claude to return a JSON object with an `overallScore` integer field (0–100).
2. THE Anthropic_Client SHALL include in every prompt an instruction for Claude to return a `categories` array where each element contains a `name` string, a `score` integer (0–100), and a `suggestions` array.
3. THE Anthropic_Client SHALL include in every prompt an instruction for Claude to return suggestions where each element contains `severity` (one of `Critical`, `Warning`, or `Suggestion`), `title`, `description`, and `recommendation` string fields.
4. THE Analysis_Service SHALL evaluate five named categories: Layout, Typography, Color & Contrast, Accessibility, and Consistency.
5. THE Analysis_Service SHALL apply the following score weights when computing the Overall_Score: Layout 20%, Typography 20%, Color & Contrast 20%, Accessibility 25%, Consistency 15%.

---

### Requirement 5: Analysis Results Display

**User Story:** As a user, I want to view the analysis results in a clear, structured format so that I can understand and act on the feedback.

#### Acceptance Criteria

1. WHEN an Analysis_Result_DTO is received, THE Analysis_Panel SHALL display the Overall_Score as a circular progress indicator.
2. THE Analysis_Panel SHALL display each category name alongside its Category_Score as a progress bar.
3. THE Analysis_Panel SHALL display each Suggestion as a card containing its severity badge, title, description, and recommendation.
4. THE Analysis_Panel SHALL render severity badges using distinct visual styling for each of the three severity levels: Critical, Warning, and Suggestion.
5. THE Analysis_Panel SHALL render category sections in a collapsible format allowing the user to expand or collapse each category.
6. WHEN an analysis is in progress, THE Analysis_Panel SHALL display a loading indicator to the user.

---

### Requirement 6: Analysis History

**User Story:** As a user, I want to view my recent analyses so that I can revisit or compare previous results.

#### Acceptance Criteria

1. THE History_Panel SHALL retain the last 5 analyses in React component state and in sessionStorage.
2. WHEN the browser session persists, THE History_Panel SHALL restore the history from sessionStorage on page load.
3. THE History_Panel SHALL display each history entry with a 128×128 pixel thumbnail, a formatted timestamp, the Overall_Score, and the analysis duration in milliseconds.
4. WHEN the user clicks a history entry, THE History_Panel SHALL restore that entry's Analysis_Result_DTO into the Analysis_Panel.
5. WHEN the user requests to clear the history, THE History_Panel SHALL display a confirmation modal before deleting all stored entries.
6. WHEN the user confirms clearing, THE History_Panel SHALL remove all entries from both React state and sessionStorage.
7. WHEN the History_Panel contains no entries, THE History_Panel SHALL render in a non-interactive state without producing runtime errors.

---

### Requirement 7: History API

**User Story:** As a developer, I want a history retrieval endpoint so that the frontend can query server-side stored analyses.

#### Acceptance Criteria

1. THE Analysis_Service SHALL expose a `GET /api/v1/history` endpoint that accepts `sessionId` and `limit` query parameters.
2. WHEN the `limit` parameter is provided, THE Analysis_Service SHALL return no more than the specified number of Analysis_Summary records.
3. WHEN `limit` is not provided, THE Analysis_Service SHALL default to returning a maximum of 5 Analysis_Summary records. WHEN `limit=0` is explicitly provided, THE Analysis_Service SHALL return zero records.
4. THE Analysis_Service SHALL return History API responses within 200 milliseconds under normal load.
5. THE Analysis_Service SHALL expose a `GET /api/v1/analysis/{id}` endpoint that returns a single Analysis_Record by its UUID identifier.
6. THE Analysis_Service SHALL expose a `DELETE /api/v1/analysis/{id}` endpoint that removes a single Analysis_Record from MariaDB.

---

### Requirement 8: Rate Limiting

**User Story:** As a system operator, I want per-IP rate limiting so that the service remains stable and protects against abuse.

#### Acceptance Criteria

1. THE Rate_Limiter SHALL enforce a maximum of 10 requests per minute per client IP address on the `/api/v1/analyze` endpoint.
2. WHEN a client exceeds the rate limit, THE Rate_Limiter SHALL respond with HTTP status 429 and include a `Retry-After` header indicating when the client may retry.
3. THE Rate_Limiter SHALL allow the rate limit threshold to be configured via `application.properties`.
4. WHEN a rate limit is exceeded, THE Analysis_Service SHALL return HTTP 429 with error code `RATE_LIMIT_EXCEEDED` in the response body.

---

### Requirement 9: Export and Share

**User Story:** As a user, I want to export my analysis results so that I can share or archive them.

#### Acceptance Criteria

1. WHEN the user activates the PDF export action, THE Export_Toolbar SHALL generate and download a PDF of the Analysis_Panel using html2canvas and jsPDF; IF PDF generation encounters a rendering or memory error, THE Export_Toolbar SHALL still attempt to download the partially generated output.
2. WHEN the user activates the Copy All action, THE Export_Toolbar SHALL copy the full analysis as a Markdown-formatted string to the clipboard.

---

### Requirement 10: Error Handling

**User Story:** As a user, I want clear error messages when something goes wrong so that I understand what happened and what to do next.

#### Acceptance Criteria

1. WHEN a file with an invalid MIME type is submitted to the backend, THE Analysis_Service SHALL return HTTP 400 with error code `INVALID_FILE_TYPE`.
2. WHEN a file exceeding 10MB is submitted to the backend, THE Analysis_Service SHALL return HTTP 400 with error code `FILE_TOO_LARGE`.
3. WHEN the Claude Vision API returns an error that cannot be resolved by retry, THE Analysis_Service SHALL return HTTP 422 with error code `ANALYSIS_FAILED`.
4. WHEN the Claude Vision API is unavailable, THE Analysis_Service SHALL return HTTP 500 with error code `UPSTREAM_API_ERROR`.
5. WHEN the service is overloaded beyond capacity, THE Analysis_Service SHALL return HTTP 503 with error code `SERVICE_OVERLOADED`.
6. WHEN an error response is received from the backend, THE Upload_Component SHALL display a user-readable error message describing the failure.
7. THE Analysis_Service SHALL handle all exceptions through a centralized `GlobalExceptionHandler` that maps domain exceptions to appropriate HTTP responses.

---

### Requirement 11: Security

**User Story:** As a system operator, I want the application to be secured against common vulnerabilities so that user data and API credentials are protected.

#### Acceptance Criteria

1. THE Analysis_Service SHALL never expose the Anthropic API key in any HTTP response or client-accessible resource.
2. THE Analysis_Service SHALL use JPA parameterized queries for all database operations to prevent SQL injection.
3. WHEN processing uploaded files, THE Analysis_Service SHALL reject any filename containing path traversal sequences such as `../` or absolute path prefixes.
4. THE Analysis_Service SHALL store the Anthropic API key exclusively as a server-side environment variable or secret, never in source code. IF the Anthropic API key environment variable is not set at startup, THE Analysis_Service SHALL fail to start with a descriptive error.
5. THE Analysis_Service SHALL configure CORS to allow requests only from the designated frontend origin.

---

### Requirement 12: Performance

**User Story:** As a user, I want the application to be fast and responsive so that I receive feedback without long wait times.

#### Acceptance Criteria

1. THE Analysis_Service SHALL complete analysis requests at or below the 95th percentile latency of 8 seconds.
2. THE Upload_Component page SHALL achieve a Largest Contentful Paint of less than 2.5 seconds.
3. THE Analysis_Service SHALL respond to History API requests within 200 milliseconds at the 95th percentile.
4. THE Analysis_Service SHALL support 50 concurrent users without degradation below the stated latency thresholds.

---

### Requirement 13: Accessibility

**User Story:** As a user with accessibility needs, I want the application to meet accessibility standards so that I can use it with assistive technologies.

#### Acceptance Criteria

1. THE Upload_Component SHALL comply with WCAG 2.1 Level AA success criteria.
2. THE Analysis_Panel SHALL comply with WCAG 2.1 Level AA success criteria.
3. THE History_Panel SHALL comply with WCAG 2.1 Level AA success criteria.
4. THE Export_Toolbar SHALL comply with WCAG 2.1 Level AA success criteria.

---

### Requirement 14: Persistence and Data Model

**User Story:** As a developer, I want a well-defined database schema so that analysis records are stored reliably and queryable efficiently.

#### Acceptance Criteria

1. THE Analysis_Service SHALL persist each Analysis_Record with the following fields: `id` (UUID v4, primary key), `session_id` (VARCHAR 64, indexed, not null), `created_at` (DATETIME, not null, default NOW()), `overall_score` (TINYINT, 0–100, not null), `processing_ms` (INT, not null), `image_hash` (VARCHAR 64, indexed), `raw_response` (LONGTEXT, not null), `focus_hint` (VARCHAR 256, nullable), and `ip_address` (VARCHAR 45, not null).
2. THE Analysis_Service SHALL create an index on the `session_id` column to support efficient history queries.
3. THE Analysis_Service SHALL create an index on the `image_hash` column to support efficient cache lookups.

---

### Requirement 15: Testing and Quality

**User Story:** As a developer, I want sufficient automated test coverage so that the codebase remains maintainable and regressions are caught early.

#### Acceptance Criteria

1. THE Analysis_Service test suite SHALL achieve greater than 80% line coverage as measured by JaCoCo.
2. THE Upload_Component and associated frontend modules SHALL achieve greater than 70% line coverage as measured by Vitest.
3. THE Analysis_Service SHALL include unit tests for the rate limiting, file validation, prompt construction, and response parsing logic.
4. THE Analysis_Service SHALL include integration tests for the `/api/v1/analyze`, `/api/v1/history`, and `/api/v1/analysis/{id}` endpoints.

---

### Requirement 16: Infrastructure and Configuration

**User Story:** As a developer, I want a Docker Compose setup so that the full stack can be run locally with a single command.

#### Acceptance Criteria

1. THE system SHALL include a `docker-compose.yml` that defines services for the React frontend (port 3000), the Spring Boot backend (port 8080), and MariaDB 11 (port 3306).
2. THE system SHALL accept the Anthropic API key and database credentials exclusively through environment variables defined in the Docker Compose configuration.
3. THE system SHALL expose a `GET /actuator/health` endpoint from the Spring Boot service for container health checks.
4. THE Analysis_Service SHALL support a configurable Anthropic API timeout defaulting to 12 seconds via `application.properties`.
5. THE system SHALL support the following browser targets: Chrome 110 and above, Firefox 110 and above, and Safari 16 and above.
