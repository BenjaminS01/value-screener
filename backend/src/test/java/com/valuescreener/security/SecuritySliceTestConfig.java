package com.valuescreener.security;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.List;

/**
 * Security wiring for @WebMvcTest slices: the real chain plus a fixed account set.
 * admin / admin-password-123456789 (ADMIN), alice / alice-password-1234 (USER).
 */
@TestConfiguration
@Import({SecurityConfig.class, AccountRegistry.class, JsonSecurityErrorHandlers.class,
        LoginAttemptGuard.class, ClientIpResolver.class,
        LoginThrottleWebConfig.class})
public class SecuritySliceTestConfig {

    /** Controllable time for throttling tests; the guard in the cached context shares it. */
    @Bean
    @Primary
    MutableClock testClock() {
        return new MutableClock();
    }

    @Bean
    @Primary
    AccountsProperties testAccountsProperties() {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4);
        return new AccountsProperties(
                new AccountsProperties.Account("admin", encoder.encode("admin-password-123456789")),
                List.of(new AccountsProperties.Account("alice", encoder.encode("alice-password-1234"))));
    }
}
