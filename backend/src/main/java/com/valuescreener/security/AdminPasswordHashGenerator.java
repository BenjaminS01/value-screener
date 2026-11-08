package com.valuescreener.security;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.io.BufferedReader;
import java.io.Console;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Prints a bcrypt hash for a password read interactively (hidden) - never from the command line,
 * so the password does not end up in shell history or the process list.
 */
public class AdminPasswordHashGenerator {

    public static void main(String[] args) throws IOException {
        if (args.length != 0) {
            System.err.println("Refusing to run: the password must not be passed on the command line "
                    + "(shell history, process list). Run without arguments; it will be asked for.");
            System.exit(1);
            return;
        }
        char[] first;
        char[] second;
        Console console = System.console();
        if (console != null) {
            first = console.readPassword("Password: ");
            second = console.readPassword("Repeat password: ");
        } else {
            System.err.println("WARNING: no console available, reading ONE line from stdin; input is NOT hidden.");
            System.err.print("Password (input visible): ");
            System.err.flush();
            BufferedReader reader = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
            String line = reader.readLine();
            first = line == null ? null : line.toCharArray();
            second = first == null ? null : first.clone();
        }
        try {
            System.out.println(hash(first, second));
        } catch (IllegalArgumentException e) {
            System.err.println("Error: " + e.getMessage());
            System.exit(1);
        }
    }

    /** Verifies both entries are equal and non-empty, returns the bcrypt hash, and clears both arrays. */
    static String hash(char[] password, char[] repeat) {
        try {
            if (password == null || password.length == 0) {
                throw new IllegalArgumentException("password must not be empty");
            }
            if (repeat == null || !Arrays.equals(password, repeat)) {
                throw new IllegalArgumentException("passwords do not match");
            }
            return new BCryptPasswordEncoder().encode(CharBuffer.wrap(password));
        } finally {
            if (password != null) Arrays.fill(password, '\0');
            if (repeat != null) Arrays.fill(repeat, '\0');
        }
    }
}
