package com.uireview.exception;

public class PathTraversalException extends UIReviewException {

    public PathTraversalException(String message) {
        super(message);
    }

    public PathTraversalException(String message, Throwable cause) {
        super(message, cause);
    }
}
