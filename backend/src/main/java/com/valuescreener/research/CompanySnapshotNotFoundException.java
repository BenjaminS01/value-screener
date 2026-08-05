package com.valuescreener.research;

public class CompanySnapshotNotFoundException extends RuntimeException {

    public CompanySnapshotNotFoundException(String isin) {
        super("no company snapshot found for isin " + isin);
    }
}
