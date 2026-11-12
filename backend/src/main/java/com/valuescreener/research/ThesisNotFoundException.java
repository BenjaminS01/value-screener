package com.valuescreener.research;

public class ThesisNotFoundException extends RuntimeException {

    public ThesisNotFoundException(String isin) {
        super("Thesis not found: " + isin);
    }
}
