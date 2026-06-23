package com.uireview.exception;

public class RateLimitExceededException extends UIReviewException {

    private final long retryAfterSeconds;

    public RateLimitExceededException(String message, long retryAfterSeconds) {
        super(message);
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public RateLimitExceededException(String message, Throwable cause) {
        super(message, cause);
        this.retryAfterSeconds = 60L;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
