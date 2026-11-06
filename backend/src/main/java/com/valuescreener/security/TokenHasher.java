package com.valuescreener.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class TokenHasher {

    private TokenHasher() {
    }

    /** SHA-256 of the UTF-8 bytes as 64 lowercase hex characters. */
    public static String sha256Hex(String token) {
        try {
            java.util.Objects.requireNonNull(token, "token");
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    /** Constant-time comparison of the hash of the presented token against the expected hash. */
    public static boolean matches(String presentedToken, String expectedSha256Hex) {
        if (presentedToken == null || presentedToken.isBlank()
                || expectedSha256Hex == null || expectedSha256Hex.isBlank()) {
            return false;
        }
        byte[] presented = sha256Hex(presentedToken).getBytes(StandardCharsets.UTF_8);
        byte[] expected = expectedSha256Hex.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(presented, expected);
    }
}
