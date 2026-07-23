package com.valuescreener.portfolio;

public class PositionNotFoundException extends RuntimeException {

    public PositionNotFoundException(String isin) {
        super("no portfolio position found for isin " + isin);
    }
}
