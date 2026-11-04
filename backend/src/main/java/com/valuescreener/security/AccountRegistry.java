package com.valuescreener.security;

import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Validates the configured accounts once at startup and exposes them as a UserDetailsService.
 * Error messages name the problem and the username, never the password hash.
 */
@Component
public class AccountRegistry {

    private static final Pattern BCRYPT = Pattern.compile("^\\$2[aby]\\$\\d{2}\\$[./A-Za-z0-9]{53}$");
    private static final String RESERVED_USERNAME = "*";

    private record Entry(String passwordHash, Role role) {
    }

    private final Map<String, Entry> accounts = new LinkedHashMap<>();

    public AccountRegistry(AccountsProperties properties) {
        if (properties.admin() == null) {
            throw new IllegalStateException("Admin account is not configured (app.admin)");
        }
        register(properties.admin(), Role.ADMIN);
        for (AccountsProperties.Account user : properties.users()) {
            register(user, Role.USER);
        }
    }

    private void register(AccountsProperties.Account account, Role role) {
        if (account == null || account.username() == null || account.username().isBlank()) {
            throw new IllegalStateException("Account with blank username configured (role " + role + ")");
        }
        String username = normalize(account.username());
        if (RESERVED_USERNAME.equals(username)) {
            throw new IllegalStateException("Username '" + username + "' is reserved");
        }
        if (account.passwordHash() == null || account.passwordHash().isBlank()) {
            throw new IllegalStateException("Account '" + username + "' has no password hash configured");
        }
        if (!BCRYPT.matcher(account.passwordHash()).matches()) {
            throw new IllegalStateException("Account '" + username + "' has a password hash that is not a valid bcrypt hash");
        }
        if (accounts.containsKey(username)) {
            throw new IllegalStateException("Duplicate username '" + username + "'");
        }
        accounts.put(username, new Entry(account.passwordHash(), role));
    }

    public static String normalize(String username) {
        return username.trim().toLowerCase(Locale.ROOT);
    }

    public boolean exists(String username) {
        return username != null && accounts.containsKey(normalize(username));
    }

    public Optional<Role> roleOf(String username) {
        if (username == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(accounts.get(normalize(username))).map(Entry::role);
    }

    public UserDetailsService userDetailsService() {
        List<UserDetails> users = accounts.entrySet().stream()
                .map(e -> (UserDetails) User.withUsername(e.getKey())
                        .password(e.getValue().passwordHash())
                        .roles(e.getValue().role().name())
                        .build())
                .toList();
        return new InMemoryUserDetailsManager(users);
    }
}
