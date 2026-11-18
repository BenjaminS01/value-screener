package com.valuescreener.research.group;

public class StockGroupNotFoundException extends RuntimeException {

    public StockGroupNotFoundException(String key) {
        super("Group not found: " + key);
    }
}
