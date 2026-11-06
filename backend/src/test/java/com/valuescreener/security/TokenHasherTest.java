package com.valuescreener.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TokenHasherTest {

    private static final String ABC_HASH = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad";

    @Test
    void sha256HexMatchesKnownVector() {
        assertThat(TokenHasher.sha256Hex("abc")).isEqualTo(ABC_HASH);
    }

    @Test
    void matchesIsTrueForCorrectToken() {
        assertThat(TokenHasher.matches("abc", ABC_HASH)).isTrue();
    }

    @Test
    void matchesIsFalseForNullBlankAndWrong() {
        assertThat(TokenHasher.matches(null, ABC_HASH)).isFalse();
        assertThat(TokenHasher.matches("", ABC_HASH)).isFalse();
        assertThat(TokenHasher.matches("   ", ABC_HASH)).isFalse();
        assertThat(TokenHasher.matches("abd", ABC_HASH)).isFalse();
        assertThat(TokenHasher.matches("abc", null)).isFalse();
        assertThat(TokenHasher.matches("abc", "")).isFalse();
    }
}
