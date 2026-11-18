package com.valuescreener.research.group;

public class UnknownGroupException extends RuntimeException {

    public UnknownGroupException(String key) {
        super("Unknown group key: " + key);
    }
}
