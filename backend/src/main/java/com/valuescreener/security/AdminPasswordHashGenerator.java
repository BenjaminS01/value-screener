package com.valuescreener.security;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public class AdminPasswordHashGenerator {
    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Usage: AdminPasswordHashGenerator <plain-text-password>");
            System.exit(1);
        }
        System.out.println(new BCryptPasswordEncoder().encode(args[0]));
    }
}
