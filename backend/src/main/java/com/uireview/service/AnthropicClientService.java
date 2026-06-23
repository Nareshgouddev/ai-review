package com.uireview.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uireview.exception.AnalysisFailedException;
import com.uireview.exception.UpstreamApiException;
import com.uireview.model.dto.AnalysisResultDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * Service that communicates with the Anthropic Claude Vision API.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Builds the structured JSON prompt with an optional focus hint.</li>
 *   <li>Base64-encodes the image bytes and packages them as a vision content block.</li>
 *   <li>POSTs to {@code POST https://api.anthropic.com/v1/messages}.</li>
 *   <li>Parses the returned JSON string into an {@link AnalysisResultDto}.</li>
 *   <li>Retries transient failures (HTTP 429, 5xx) with exponential backoff.</li>
 * </ul>
 *
 * <p>Requirements: 3.4, 3.5, 3.6, 3.7, 4.1, 4.2, 4.3, 10.3, 10.4
 */
@Service
public class AnthropicClientService {

    private static final Logger log = LoggerFactory.getLogger(AnthropicClientService.class);

    /** Anthropic Messages endpoint path (relative to base URL). */
    private static final String MESSAGES_PATH = "/v1/messages";

    /**
     * Retry schedule — delays in milliseconds before each successive attempt.
     * Attempt 1 runs immediately; attempts 2–4 wait 1 s, 2 s, 4 s respectively.
     * That gives a maximum of 3 retries (4 total attempts).
     */
    private static final long[] BACKOFF_DELAYS_MS = {0L, 1_000L, 2_000L, 4_000L};

    // -----------------------------------------------------------------------
    // Structured prompt components
    // -----------------------------------------------------------------------

    private static final String PROMPT_SCHEMA_INSTRUCTION =
            "You are an expert UI/UX designer and accessibility consultant. " +
            "Analyze the provided UI screenshot and return ONLY valid JSON matching this exact schema:\n" +
            "{\"overallScore\": <integer 0-100>, " +
            "\"categories\": [{" +
            "\"name\": \"<Layout|Typography|Color & Contrast|Accessibility|Consistency>\", " +
            "\"score\": <integer 0-100>, " +
            "\"suggestions\": [{" +
            "\"severity\": \"<Critical|Warning|Suggestion>\", " +
            "\"title\": \"<concise title>\", " +
            "\"description\": \"<detailed description>\", " +
            "\"recommendation\": \"<specific actionable recommendation>\"" +
            "}]}]}\n" +
            "Evaluate exactly these five categories in order: " +
            "Layout (weight 20%), Typography (weight 20%), Color & Contrast (weight 20%), " +
            "Accessibility (weight 25%), Consistency (weight 15%).\n";

    private static final String PROMPT_SUFFIX =
            "Return ONLY the JSON object. No markdown fences, no prose.";

    // -----------------------------------------------------------------------
    // Instance state
    // -----------------------------------------------------------------------

    private final String model;
    private final int maxTokens;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    // -----------------------------------------------------------------------
    // Constructor
    // -----------------------------------------------------------------------

    public AnthropicClientService(
            @Qualifier("anthropicApiKey")  String apiKey,
            @Qualifier("anthropicModel")   String model,
            @Qualifier("anthropicMaxTokens") int maxTokens,
            @Qualifier("anthropicTimeoutMs") long timeoutMs,
            ObjectMapper objectMapper) {

        this.model        = model;
        this.maxTokens    = maxTokens;
        this.objectMapper = objectMapper;

        // Configure connect + read timeouts via SimpleClientHttpRequestFactory
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        int timeoutMsInt = (int) Math.min(timeoutMs, Integer.MAX_VALUE);
        requestFactory.setConnectTimeout(timeoutMsInt);
        requestFactory.setReadTimeout(timeoutMsInt);

        this.restClient = RestClient.builder()
                .baseUrl("https://api.anthropic.com")
                .requestFactory(requestFactory)
                .defaultHeader("x-api-key",          apiKey)
                .defaultHeader("anthropic-version",   "2023-06-01")
                .defaultHeader("Content-Type",         "application/json")
                .build();
    }

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    /**
     * Analyzes the given image using the Anthropic Claude Vision API.
     *
     * @param imageBytes the raw image bytes (PNG / JPEG / WebP)
     * @param mimeType   the MIME type of the image, e.g. {@code "image/png"}
     * @param focusHint  optional hint directing the analysis; may be {@code null} or blank
     * @return parsed {@link AnalysisResultDto} — note: {@code id}, {@code sessionId},
     *         {@code processingMs}, and {@code cached} are filled in by the caller
     *         (AnalysisService); they are {@code null}/default here
     * @throws AnalysisFailedException if a non-retryable error occurs (HTTP 400 or 401)
     * @throws UpstreamApiException    if all retry attempts are exhausted
     */
    public AnalysisResultDto analyze(byte[] imageBytes, String mimeType, String focusHint) {
        String base64Image = Base64.getEncoder().encodeToString(imageBytes);
        String promptText  = buildPrompt(focusHint);
        Map<String, Object> payload = buildPayload(base64Image, mimeType, promptText);

        return executeWithRetry(payload);
    }

    // -----------------------------------------------------------------------
    // Prompt construction
    // -----------------------------------------------------------------------

    /**
     * Assembles the full text prompt, appending the focus hint line when present.
     */
    String buildPrompt(String focusHint) {
        StringBuilder sb = new StringBuilder(PROMPT_SCHEMA_INSTRUCTION);
        if (focusHint != null && !focusHint.isBlank()) {
            sb.append("Focus particularly on: ").append(focusHint.strip()).append("\n");
        }
        sb.append(PROMPT_SUFFIX);
        return sb.toString();
    }

    // -----------------------------------------------------------------------
    // Request payload builder
    // -----------------------------------------------------------------------

    /**
     * Builds the Anthropic Messages API request body as a plain {@code Map}.
     *
     * <pre>
     * {
     *   "model": "...",
     *   "max_tokens": ...,
     *   "messages": [{
     *     "role": "user",
     *     "content": [
     *       { "type": "image", "source": { "type": "base64", "media_type": "...", "data": "..." } },
     *       { "type": "text",  "text": "..." }
     *     ]
     *   }]
     * }
     * </pre>
     */
    private Map<String, Object> buildPayload(String base64Image, String mimeType, String promptText) {
        Map<String, Object> imageSource = Map.of(
                "type",       "base64",
                "media_type", mimeType,
                "data",       base64Image
        );

        Map<String, Object> imageBlock = Map.of(
                "type",   "image",
                "source", imageSource
        );

        Map<String, Object> textBlock = Map.of(
                "type", "text",
                "text", promptText
        );

        Map<String, Object> message = Map.of(
                "role",    "user",
                "content", List.of(imageBlock, textBlock)
        );

        return Map.of(
                "model",      model,
                "max_tokens", maxTokens,
                "messages",   List.of(message)
        );
    }

    // -----------------------------------------------------------------------
    // HTTP call with exponential backoff retry (Task 4.2)
    // -----------------------------------------------------------------------

    /**
     * Executes the POST request with up to 3 retries using exponential backoff.
     *
     * <p>Retry schedule (Requirements 3.7):
     * <ul>
     *   <li>Attempt 1 — immediate</li>
     *   <li>Attempt 2 — after 1 s</li>
     *   <li>Attempt 3 — after 2 s</li>
     *   <li>Attempt 4 — after 4 s</li>
     * </ul>
     *
     * <p>Retryable conditions: HTTP 429, HTTP 5xx, network-level {@link RestClientException}
     * (covers {@code SocketTimeoutException} and {@code ConnectException}).<br>
     * Non-retryable conditions: HTTP 400, HTTP 401 — thrown immediately as
     * {@link AnalysisFailedException}.
     *
     * @throws AnalysisFailedException immediately on HTTP 400/401
     * @throws UpstreamApiException    after exhausting all retry attempts
     */
    private AnalysisResultDto executeWithRetry(Map<String, Object> payload) {
        RestClientException lastException = null;

        for (int attempt = 0; attempt < BACKOFF_DELAYS_MS.length; attempt++) {
            sleepIfNeeded(BACKOFF_DELAYS_MS[attempt]);

            try {
                String rawResponseBody = postToAnthropic(payload);
                return parseResponse(rawResponseBody);

            } catch (HttpClientErrorException ex) {
                int statusCode = ex.getStatusCode().value();

                // Non-retryable client errors — fail immediately
                if (statusCode == 400 || statusCode == 401) {
                    log.error("Non-retryable Anthropic API error (HTTP {}): {}", statusCode, ex.getMessage());
                    throw new AnalysisFailedException(
                            "Anthropic API rejected the request with HTTP " + statusCode
                            + ". Check the API key and request format.", ex);
                }

                // HTTP 429 — retryable
                if (statusCode == 429) {
                    log.warn("Anthropic rate limit (HTTP 429) on attempt {}. Will retry.", attempt + 1);
                    lastException = ex;
                } else {
                    // Any other 4xx — treat as non-retryable
                    log.error("Unexpected client error from Anthropic API (HTTP {})", statusCode, ex);
                    throw new AnalysisFailedException(
                            "Unexpected Anthropic API client error: HTTP " + statusCode, ex);
                }

            } catch (HttpServerErrorException ex) {
                // HTTP 5xx — retryable
                log.warn("Anthropic server error (HTTP {}) on attempt {}. Will retry.",
                        ex.getStatusCode().value(), attempt + 1);
                lastException = ex;

            } catch (RestClientException ex) {
                // Network-level error (timeout, connection refused, etc.) — retryable
                log.warn("Network-level error calling Anthropic API on attempt {}: {}",
                        attempt + 1, ex.getMessage());
                lastException = ex;
            }
        }

        // All attempts exhausted
        log.error("Exhausted all {} retry attempts calling Anthropic API.", BACKOFF_DELAYS_MS.length);
        throw new UpstreamApiException(
                "Anthropic API is unavailable after " + BACKOFF_DELAYS_MS.length
                + " attempts. Last error: " + (lastException != null ? lastException.getMessage() : "unknown"),
                lastException);
    }

    // -----------------------------------------------------------------------
    // HTTP call
    // -----------------------------------------------------------------------

    /**
     * Issues the actual HTTP POST and returns the raw response body string.
     *
     * @throws RestClientException on any HTTP or network error (caller handles retries)
     */
    private String postToAnthropic(Map<String, Object> payload) {
        return restClient.post()
                .uri(MESSAGES_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .body(payload)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> {
                    // Let Spring throw the appropriate HttpStatusCodeException
                    // so executeWithRetry can inspect the status code.
                    // Default behaviour already does this; this block is here for clarity.
                })
                .body(String.class);
    }

    // -----------------------------------------------------------------------
    // Response parsing
    // -----------------------------------------------------------------------

    /**
     * Parses the Anthropic API response envelope and extracts the JSON string
     * from {@code content[0].text}, then deserializes it into an
     * {@link AnalysisResultDto}.
     *
     * <p>Anthropic response shape:
     * <pre>
     * {
     *   "id": "...",
     *   "type": "message",
     *   "content": [{"type": "text", "text": "<json string>"}],
     *   ...
     * }
     * </pre>
     *
     * @throws AnalysisFailedException when the response cannot be parsed
     */
    AnalysisResultDto parseResponse(String rawResponseBody) {
        try {
            // Parse the outer Anthropic envelope
            Map<String, Object> envelope = objectMapper.readValue(
                    rawResponseBody,
                    new TypeReference<Map<String, Object>>() {});

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> contentBlocks =
                    (List<Map<String, Object>>) envelope.get("content");

            if (contentBlocks == null || contentBlocks.isEmpty()) {
                throw new AnalysisFailedException(
                        "Anthropic response contained no content blocks.");
            }

            Object textValue = contentBlocks.get(0).get("text");
            if (textValue == null) {
                throw new AnalysisFailedException(
                        "Anthropic response content[0] has no 'text' field.");
            }

            String innerJson = textValue.toString().strip();

            // Parse the inner JSON (the model's output) into AnalysisResultDto
            return objectMapper.readValue(innerJson, AnalysisResultDto.class);

        } catch (AnalysisFailedException ex) {
            throw ex; // propagate domain exceptions as-is
        } catch (Exception ex) {
            log.error("Failed to parse Anthropic API response: {}", ex.getMessage(), ex);
            throw new AnalysisFailedException(
                    "Failed to parse Anthropic API response into AnalysisResultDto: " + ex.getMessage(), ex);
        }
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    /**
     * Sleeps for {@code delayMs} milliseconds. No-op when {@code delayMs == 0}.
     * Restores the interrupt flag if the sleep is interrupted.
     */
    private static void sleepIfNeeded(long delayMs) {
        if (delayMs <= 0) {
            return;
        }
        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            log.warn("Backoff sleep interrupted.");
        }
    }
}
