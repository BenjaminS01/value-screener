package com.valuescreener.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LoginAuditTest {

    @Test
    void nullBecomesDash() {
        assertThat(LoginAudit.sanitize(null)).isEqualTo("-");
    }

    @Test
    void removesAllControlCharactersAndUnicodeLineSeparators() {
        assertThat(LoginAudit.sanitize("a\r\nb\tc\u0000d\u001be\u2028f\u2029g\u007fh")).isEqualTo("abcdefgh");
    }

    @Test
    void capsLengthAt64() {
        assertThat(LoginAudit.sanitize("x".repeat(100))).hasSize(64);
    }
}
