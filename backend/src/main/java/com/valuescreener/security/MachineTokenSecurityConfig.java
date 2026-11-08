package com.valuescreener.security;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Machine-to-machine chains. Enabled: stateless bearer token with exactly one authority.
 * Disabled: permitAll, but no controller exists for the path, so the answer is 404 (not 401 from the session chain).
 */
@Configuration
@EnableConfigurationProperties(MachineTokenProperties.class)
public class MachineTokenSecurityConfig {

    public static final String INGEST_AUTHORITY = "RESEARCH_INGEST";
    public static final String SYNC_AUTHORITY = "RESEARCH_SYNC";

    @Bean
    @Order(1)
    public SecurityFilterChain researchIngestChain(HttpSecurity http, MachineTokenProperties properties,
                                                   JsonSecurityErrorHandlers errorHandlers) throws Exception {
        return machineChain(http.securityMatcher("/api/research/ingest/**"),
                properties.researchIngest(), INGEST_AUTHORITY, errorHandlers);
    }

    @Bean
    @Order(2)
    public SecurityFilterChain researchSyncChain(HttpSecurity http, MachineTokenProperties properties,
                                                 JsonSecurityErrorHandlers errorHandlers) throws Exception {
        return machineChain(http.securityMatcher("/api/research/sync/**"),
                properties.researchSync(), SYNC_AUTHORITY, errorHandlers);
    }

    private SecurityFilterChain machineChain(HttpSecurity http, MachineTokenProperties.Endpoint endpoint,
                                             String authority, JsonSecurityErrorHandlers errorHandlers)
            throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(cache -> cache.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
        if (endpoint.enabled()) {
            http
                    .addFilterBefore(new MachineTokenFilter(endpoint.tokenHash(), authority),
                            UsernamePasswordAuthenticationFilter.class)
                    .exceptionHandling(ex -> ex
                            .authenticationEntryPoint(errorHandlers)
                            .accessDeniedHandler(errorHandlers))
                    .authorizeHttpRequests(auth -> auth.anyRequest().hasAuthority(authority));
        } else {
            http.authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        }
        return http.build();
    }
}
