package com.valuescreener.research.agent;

public class ResearchTimeoutException extends RuntimeException {

    public ResearchTimeoutException(String message, Throwable cause) {
        super(message, cause);
    }
}
