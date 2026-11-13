package com.valuescreener.deepanalysis;

/** Generic failure; the message must never carry the function ARN or other AWS details. */
public class DeepAnalysisUnavailableException extends RuntimeException {

    public DeepAnalysisUnavailableException(String message) {
        super(message);
    }

    public DeepAnalysisUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
