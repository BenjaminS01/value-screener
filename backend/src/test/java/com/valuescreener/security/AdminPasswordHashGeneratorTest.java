package com.valuescreener.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AdminPasswordHashGeneratorTest {

    @Test
    void hashMatchesPasswordAndClearsInput() {
        char[] password = "correct-horse-battery-staple".toCharArray();
        String hash = AdminPasswordHashGenerator.hash(password, password.clone());
        assertThat(new BCryptPasswordEncoder().matches("correct-horse-battery-staple", hash)).isTrue();
        assertThat(new String(password)).isEqualTo("\0".repeat(28));
    }

    @Test
    void emptyPasswordIsRejected() {
        assertThatThrownBy(() -> AdminPasswordHashGenerator.hash(new char[0], new char[0]))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("empty");
    }

    @Test
    void nullPasswordIsRejected() {
        assertThatThrownBy(() -> AdminPasswordHashGenerator.hash(null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void mismatchIsRejected() {
        assertThatThrownBy(() -> AdminPasswordHashGenerator.hash("abc".toCharArray(), "abd".toCharArray()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("do not match");
    }
}
