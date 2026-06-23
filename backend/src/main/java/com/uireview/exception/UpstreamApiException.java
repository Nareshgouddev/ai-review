package com.uireview.exception;

/**
 * Thrown when the Anthropic Claude Vision API is unreachable or returns an
 * unrecoverable error after all retry attempts are exhausted.
 *
 * <p>Maps to HTTP 500 with error code {@code UPSTREAM_API_ERROR} via
 * {@code GlobalExceptionHandler}.
 *
 * <p>Requirement: 10.4 — WHEN the Claude Vision API is unavailable,
 * the Analysis_Service SHALL return HTTP 500 with error code UPSTREAM_API_ERROR.
 */
public class UpstreamApiException extends UIReviewException {

    public UpstreamApiException(String message) {
        super(message);
    }

    public UpstreamApiException(String message, Throwable cause) {
        super(message, cause);
    }
}
