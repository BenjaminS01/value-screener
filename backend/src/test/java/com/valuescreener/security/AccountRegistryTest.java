package com.valuescreener.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AccountRegistryTest {

    private static final String HASH = new BCryptPasswordEncoder().encode("x");

    private static AccountsProperties.Account account(String username, String hash) {
        return new AccountsProperties.Account(username, hash);
    }

    private static AccountsProperties props(String adminHash, List<AccountsProperties.Account> users) {
        return new AccountsProperties(account("admin", adminHash), users);
    }

    @Test
    void acceptsAdminAndUsers() {
        AccountRegistry registry = new AccountRegistry(props(HASH, List.of(account("alice", HASH))));

        assertThat(registry.roleOf("admin")).contains(Role.ADMIN);
        assertThat(registry.roleOf("alice")).contains(Role.USER);
    }

    @Test
    void matchesUsernamesCaseInsensitively() {
        AccountRegistry registry = new AccountRegistry(props(HASH, List.of(account("alice", HASH))));

        assertThat(registry.roleOf(" Alice ")).contains(Role.USER);
        assertThat(registry.userDetailsService().loadUserByUsername("ALICE").getUsername()).isEqualTo("alice");
    }

    @Test
    void rejectsMissingAdminHash() {
        assertThatThrownBy(() -> new AccountRegistry(props("", List.of())))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsDuplicateUsernamesAcrossAdminAndUsers() {
        assertThatThrownBy(() -> new AccountRegistry(props(HASH, List.of(account("Admin", HASH)))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("admin");
    }

    @Test
    void rejectsReservedUsernameStar() {
        assertThatThrownBy(() -> new AccountRegistry(props(HASH, List.of(account("*", HASH)))))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsBlankUsername() {
        assertThatThrownBy(() -> new AccountRegistry(props(HASH, List.of(account(" ", HASH)))))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsNonBcryptHash() {
        assertThatThrownBy(() -> new AccountRegistry(props(HASH, List.of(account("alice", "plaintext")))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageNotContaining("plaintext");
    }

    @Test
    void allowsEmptyUserList() {
        assertThatCode(() -> new AccountRegistry(props(HASH, null))).doesNotThrowAnyException();
        assertThat(new AccountRegistry(props(HASH, null)).exists("bob")).isFalse();
    }
}
