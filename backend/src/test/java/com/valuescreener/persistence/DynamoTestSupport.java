package com.valuescreener.persistence;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

import java.net.URI;
import java.util.UUID;

/**
 * One DynamoDB Local container shared by all tests of the JVM. Spring tests extend this class;
 * plain tests use {@link #newClient()} and {@link #freshTableName()}.
 */
public abstract class DynamoTestSupport {

    private static final GenericContainer<?> DYNAMO =
            new GenericContainer<>(DockerImageName.parse("amazon/dynamodb-local:2.5.4"))
                    .withCommand("-jar", "DynamoDBLocal.jar", "-inMemory", "-sharedDb")
                    .withExposedPorts(8000);

    static {
        DYNAMO.start();
    }

    @DynamicPropertySource
    static void dynamoProperties(DynamicPropertyRegistry registry) {
        registry.add("app.dynamodb.endpoint", DynamoTestSupport::endpoint);
        registry.add("app.dynamodb.create-table", () -> "true");
    }

    public static String endpoint() {
        return "http://" + DYNAMO.getHost() + ":" + DYNAMO.getMappedPort(8000);
    }

    /** A unique table name, so test classes do not share data. */
    public static String freshTableName() {
        return "test-" + UUID.randomUUID();
    }

    public static DynamoDbClient newClient() {
        return DynamoDbClient.builder()
                .endpointOverride(URI.create(endpoint()))
                .region(Region.of("eu-central-1"))
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("local", "local")))
                .build();
    }
}
