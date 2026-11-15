package com.valuescreener.security;

import com.valuescreener.persistence.DynamoProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.session.config.annotation.web.http.EnableSpringHttpSession;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

import java.time.Clock;
import java.time.Duration;

/**
 * HTTP sessions in DynamoDB instead of the servlet container's memory. The cookie (name, HttpOnly,
 * Secure, SameSite) is configured by Spring Boot from {@code server.servlet.session.cookie.*}.
 */
@Configuration
@EnableSpringHttpSession
public class SessionConfig {

    static final Duration REFRESH_INTERVAL = Duration.ofMinutes(10);

    @Bean
    public DynamoSessionRepository sessionRepository(DynamoDbClient dynamo, DynamoProperties props, Clock clock,
                                                     @Value("${server.servlet.session.timeout:2h}") Duration timeout) {
        return new DynamoSessionRepository(dynamo, props, clock, timeout, REFRESH_INTERVAL);
    }
}
