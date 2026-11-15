package com.valuescreener.persistence;

/** A write lost against concurrent writers of the same item and may be retried by the caller. */
public class ConcurrentUpdateException extends RuntimeException {

    public ConcurrentUpdateException(String message, Throwable cause) {
        super(message, cause);
    }
}
