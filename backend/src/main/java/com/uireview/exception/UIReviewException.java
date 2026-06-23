package com.uireview.exception;

public class UIReviewException extends RuntimeException {

    public UIReviewException(String message) {
        super(message);
    }

    public UIReviewException(String message, Throwable cause) {
        super(message, cause);
    }
}
