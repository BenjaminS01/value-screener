package com.valuescreener.security;

/** Shared constants for the machine-token security tests. */
final class MachineTokenTestData {

    static final String TOKEN = "test-ingest-token";
    /** SHA-256 of "test-ingest-token". */
    static final String TOKEN_HASH = "12c9d8eb671641ca114db94b4bd7cea966626f0d9492a996808b1c6ee8ad6739";
    /** SHA-256 of "test-sync-token". */
    static final String SYNC_TOKEN_HASH = "a2f6fa2e108788e52648c1ff4d7c11c54e7f3909d4534b9b9063668f913f476b";
    static final String BODY = """
            {"ticker":"AAPL","isin":"US0378331005","companyName":"Apple Inc.",
             "country":"USA","groupKey":"SEMICONDUCTORS",
             "businessDescription":"Designs consumer electronics.","findings":[]}
            """;
    static final String UNAUTHENTICATED_JSON = "{\"error\":\"Authentication required\"}";

    private MachineTokenTestData() {
    }
}
