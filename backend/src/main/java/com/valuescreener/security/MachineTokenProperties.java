package com.valuescreener.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.regex.Pattern;

@ConfigurationProperties("app")
public record MachineTokenProperties(Endpoint researchIngest, Endpoint researchSync) {

    private static final Pattern SHA256_HEX = Pattern.compile("[0-9a-f]{64}");

    public MachineTokenProperties {
        researchIngest = validated("app.research-ingest", researchIngest);
        researchSync = validated("app.research-sync", researchSync);
    }

    private static Endpoint validated(String name, Endpoint endpoint) {
        if (endpoint == null) {
            return new Endpoint(false, null);
        }
        if (endpoint.enabled()
                && (endpoint.tokenHash() == null || !SHA256_HEX.matcher(endpoint.tokenHash()).matches())) {
            throw new IllegalStateException(name + ".token-hash must be a SHA-256 hash as 64 lowercase hex "
                    + "characters when " + name + ".enabled=true (generate with MachineTokenGenerator)");
        }
        return endpoint;
    }

    public record Endpoint(boolean enabled, String tokenHash) {
    }
}
