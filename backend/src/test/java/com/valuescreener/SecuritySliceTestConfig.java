package com.valuescreener;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.List;
import com.valuescreener.security.auth.AccountRegistry;
import com.valuescreener.security.auth.AccountsProperties;
import com.valuescreener.security.auth.JsonSecurityErrorHandlers;
import com.valuescreener.security.auth.SecurityConfig;
import com.valuescreener.security.edge.ClientIpResolver;
import com.valuescreener.security.machinetoken.MachineTokenSecurityConfig;
import com.valuescreener.security.throttle.InMemoryLoginAttemptStore;
import com.valuescreener.security.throttle.LoginAttemptGuard;
import com.valuescreener.security.throttle.LoginThrottleWebConfig;

/**
 * Security wiring for @WebMvcTest slices: the real chain plus a fixed account set.
 * admin / admin-password-123456789 (ADMIN), alice / alice-password-1234 (USER).
 */
@TestConfiguration
@Import({SecurityConfig.class, MachineTokenSecurityConfig.class, AccountRegistry.class, JsonSecurityErrorHandlers.class,
        LoginAttemptGuard.class, ClientIpResolver.class,
        LoginThrottleWebConfig.class})
public class SecuritySliceTestConfig {

    /** In-memory throttle state; the guard in the cached context shares it. */
    @Bean
    InMemoryLoginAttemptStore testLoginAttemptStore() {
        return new InMemoryLoginAttemptStore();
    }

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
