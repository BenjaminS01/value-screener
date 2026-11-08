package com.valuescreener.research;

public class StockGroupNotFoundException extends RuntimeException {

    public StockGroupNotFoundException(String key) {
        super("Group not found: " + key);
    }
}
