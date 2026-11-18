package com.valuescreener.research.model;

public record SourceReference(String url, String claim) {

    public SourceReference {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("url must not be blank");
        }
        if (claim == null || claim.isBlank()) {
            throw new IllegalArgumentException("claim must not be blank");
        }
    }
}
