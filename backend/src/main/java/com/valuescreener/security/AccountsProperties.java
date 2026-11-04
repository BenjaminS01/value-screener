package com.valuescreener.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties("app")
public record AccountsProperties(Account admin, List<Account> users) {

    public AccountsProperties {
        users = users == null ? List.of() : List.copyOf(users);
    }

    public record Account(String username, String passwordHash) {
    }
}
