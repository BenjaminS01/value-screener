package com.valuescreener.persistence;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * @param endpoint empty means real AWS; set it to point at DynamoDB Local
 */
@ConfigurationProperties("app.dynamodb")
public record DynamoProperties(
        @DefaultValue("value-screener") String tableName,
        @DefaultValue("") String endpoint,
        @DefaultValue("eu-central-1") String region,
        @DefaultValue("false") boolean createTable) {
}
