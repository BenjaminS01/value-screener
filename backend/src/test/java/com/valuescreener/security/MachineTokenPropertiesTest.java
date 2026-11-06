package com.valuescreener.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MachineTokenPropertiesTest {

    private static final String HASH = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad";

    @Test
    void enabledWithoutHashFails() {
        assertThatThrownBy(() -> new MachineTokenProperties(new MachineTokenProperties.Endpoint(true, null), null))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new MachineTokenProperties(null, new MachineTokenProperties.Endpoint(true, " ")))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void enabledWithNonHexHashFails() {
        assertThatThrownBy(() -> new MachineTokenProperties(
                new MachineTokenProperties.Endpoint(true, "not-a-hash"), null))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new MachineTokenProperties(
                new MachineTokenProperties.Endpoint(true, HASH.toUpperCase()), null))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void disabledWithoutHashIsFine() {
        MachineTokenProperties props = new MachineTokenProperties(null, null);
        assertThat(props.researchIngest().enabled()).isFalse();
        assertThat(props.researchSync().enabled()).isFalse();
    }

    @Test
    void enabledWithValidHashIsFine() {
        MachineTokenProperties props = new MachineTokenProperties(
                new MachineTokenProperties.Endpoint(true, HASH), null);
        assertThat(props.researchIngest().tokenHash()).isEqualTo(HASH);
    }
}
