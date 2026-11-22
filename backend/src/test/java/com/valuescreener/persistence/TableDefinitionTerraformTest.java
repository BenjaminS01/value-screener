package com.valuescreener.persistence;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The table exists twice: TableDefinition (DynamoDB Local, tests) and Terraform (AWS). This test keeps them equal
 * (design 6.3: no drift). Maven runs tests in backend/, so the Terraform file is one level up.
 */
class TableDefinitionTerraformTest {

    private static String terraform;

    @BeforeAll
    static void readTerraform() throws IOException {
        terraform = Files.readString(Path.of("../infra/data/dynamodb.tf"));
    }

    /** The value of the first {@code key = value} assignment in the file, without quotes. */
    private static String assignment(String key) {
        Matcher m = Pattern.compile("(?m)^\\s*" + key + "\\s*=\\s*\"?([^\"\\s]+)\"?\\s*$").matcher(terraform);
        assertThat(m.find()).as("assignment %s in dynamodb.tf", key).isTrue();
        return m.group(1);
    }

    @Test
    void tableNameIsTheBackendDefault() {
        String defaultName = new Binder(new MapConfigurationPropertySource())
                .bindOrCreate("app.dynamodb", DynamoProperties.class).tableName();
        assertThat(assignment("name")).isEqualTo(defaultName);
    }

    @Test
    void keysAreTheSame() {
        assertThat(assignment("hash_key")).isEqualTo(TableDefinition.PK);
        assertThat(assignment("range_key")).isEqualTo(TableDefinition.SK);
    }

    @Test
    void capacityIsTheSame() {
        assertThat(Long.parseLong(assignment("read_capacity"))).isEqualTo(TableDefinition.READ_CAPACITY);
        assertThat(Long.parseLong(assignment("write_capacity"))).isEqualTo(TableDefinition.WRITE_CAPACITY);
    }

    @Test
    void ttlAttributeIsTheSame() {
        assertThat(assignment("attribute_name")).as("first attribute_name is in the ttl block")
                .isEqualTo(TableDefinition.TTL);
    }
}
