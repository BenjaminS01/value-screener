package com.valuescreener.security;

import jakarta.servlet.DispatcherType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRepository;

@Configuration
@EnableWebSecurity
@EnableConfigurationProperties(AccountsProperties.class)
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public UserDetailsService userDetailsService(AccountRegistry accountRegistry) {
        return accountRegistry.userDetailsService();
    }

    @Bean
    public CsrfTokenRepository csrfTokenRepository(
            @Value("${server.servlet.session.cookie.secure:true}") boolean secureCookie) {
        CookieCsrfTokenRepository repository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        // HttpOnly stays false: the SPA must read the cookie to echo it in the X-XSRF-TOKEN header.
        repository.setCookieCustomizer(c -> c.secure(secureCookie).sameSite("Strict"));
        return repository;
    }

    /** Browser session chain: login-only, reads need a session, everything else is ADMIN by default. */
    @Bean
    @Order(3)
    public SecurityFilterChain sessionSecurityFilterChain(HttpSecurity http,
                                                          CsrfTokenRepository csrfTokenRepository,
                                                          JsonSecurityErrorHandlers errorHandlers) throws Exception {
        http
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfTokenRepository)
                        .csrfTokenRequestHandler(new SpaCsrfTokenRequestHandler()))
                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable())
                .logout(logout -> logout.disable())
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(errorHandlers)
                        .accessDeniedHandler(errorHandlers))
                .authorizeHttpRequests(auth -> auth
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/auth/csrf").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/logout").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/**").authenticated()
                        // The only write a USER may do: trigger the deep-analysis demo (limited per account).
                        .requestMatchers(HttpMethod.POST, "/api/research/snapshots/*/deep-analysis").authenticated()
                        .anyRequest().hasRole("ADMIN"));
        return http.build();
    }
}
