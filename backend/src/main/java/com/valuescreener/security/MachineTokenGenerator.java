package com.valuescreener.security;

import java.security.SecureRandom;
import java.util.Base64;

/** Prints a fresh random machine token and its SHA-256 hash. Only the hash goes into .env. */
public class MachineTokenGenerator {
    public static void main(String[] args) {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        System.out.println("token=" + token);
        System.out.println("sha256=" + TokenHasher.sha256Hex(token));
    }
}
