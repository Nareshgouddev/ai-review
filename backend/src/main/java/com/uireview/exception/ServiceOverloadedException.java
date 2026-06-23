package com.uireview.exception;

public class ServiceOverloadedException extends UIReviewException {

    public ServiceOverloadedException(String message) {
        super(message);
    }

    public ServiceOverloadedException(String message, Throwable cause) {
        super(message, cause);
    }
}
