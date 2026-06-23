package com.uireview.exception;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.NoSuchElementException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    record ErrorResponse(String errorCode, String message, String timestamp) {}

    private ErrorResponse errorResponse(String errorCode, String message) {
        return new ErrorResponse(errorCode, message, Instant.now().toString());
    }

    // ── 400 Bad Request ────────────────────────────────────────────────────────

    @ExceptionHandler(InvalidFileTypeException.class)
    public ResponseEntity<ErrorResponse> handleInvalidFileType(InvalidFileTypeException ex) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(errorResponse("INVALID_FILE_TYPE", ex.getMessage()));
    }

    @ExceptionHandler(FileTooLargeException.class)
    public ResponseEntity<ErrorResponse> handleFileTooLarge(FileTooLargeException ex) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(errorResponse("FILE_TOO_LARGE", ex.getMessage()));
    }

    @ExceptionHandler(PathTraversalException.class)
    public ResponseEntity<ErrorResponse> handlePathTraversal(PathTraversalException ex) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(errorResponse("INVALID_FILE_TYPE", ex.getMessage()));
    }

    // ── 422 Unprocessable Entity ───────────────────────────────────────────────

    @ExceptionHandler(AnalysisFailedException.class)
    public ResponseEntity<ErrorResponse> handleAnalysisFailed(AnalysisFailedException ex) {
        return ResponseEntity
                .status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(errorResponse("ANALYSIS_FAILED", ex.getMessage()));
    }

    // ── 429 Too Many Requests ──────────────────────────────────────────────────

    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<ErrorResponse> handleRateLimitExceeded(RateLimitExceededException ex) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.RETRY_AFTER, String.valueOf(ex.getRetryAfterSeconds()));
        return ResponseEntity
                .status(HttpStatus.TOO_MANY_REQUESTS)
                .headers(headers)
                .body(errorResponse("RATE_LIMIT_EXCEEDED", ex.getMessage()));
    }

    // ── 404 Not Found ─────────────────────────────────────────────────────────

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(NoSuchElementException ex) {
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(errorResponse("NOT_FOUND", ex.getMessage()));
    }

    // ── 500 Internal Server Error ──────────────────────────────────────────────

    @ExceptionHandler(UpstreamApiException.class)
    public ResponseEntity<ErrorResponse> handleUpstreamApi(UpstreamApiException ex) {
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(errorResponse("UPSTREAM_API_ERROR", ex.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(Exception ex) {
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(errorResponse("INTERNAL_ERROR", ex.getMessage()));
    }

    // ── 503 Service Unavailable ────────────────────────────────────────────────

    @ExceptionHandler(ServiceOverloadedException.class)
    public ResponseEntity<ErrorResponse> handleServiceOverloaded(ServiceOverloadedException ex) {
        return ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(errorResponse("SERVICE_OVERLOADED", ex.getMessage()));
    }
}
