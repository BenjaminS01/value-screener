package com.valuescreener;

import com.valuescreener.security.auth.AccountsProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

/** Reads application-aws.yml as plain YAML: no Spring context, no AWS call. */
class AwsProfileConfigTest {

    private static Properties load(String resource) {
        YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new ClassPathResource(resource));
        return yaml.getObject();
    }

    @Test
    void awsProfileImportsParameterStoreAndSetsLambdaDefaults() {
        Properties p = load("application-aws.yml");

        assertThat(p.getProperty("spring.config.import")).isEqualTo("aws-parameterstore:/value-screener/");
        assertThat(p.getProperty("spring.cloud.aws.parameterstore.enabled")).isEqualTo("true");
        assertThat(p.getProperty("app.dynamodb.create-table")).isEqualTo("false");
        assertThat(p.getProperty("app.dynamodb.seed-groups")).isEqualTo("true");
        assertThat(p.getProperty("app.deep-analysis.qualifier")).isEqualTo("live");
        assertThat(p.getProperty("app.security.client-ip-header")).isEqualTo("CloudFront-Viewer-Address");
        assertThat(p.getProperty("app.security.origin-verify.required")).isEqualTo("true");
        assertThat(p.getProperty("app.security.trust-forwarded-for")).isEqualTo("false");
    }

    @Test
    void defaultProfileKeepsSpringCloudAwsParameterStoreOff() {
        Properties p = load("application.yml");

        assertThat(p.getProperty("spring.cloud.aws.parameterstore.enabled")).isEqualTo("false");
        assertThat(p.getProperty("spring.config.import")).isNull();
    }

    /** SSM names allow only a-zA-Z0-9_.-/ : indexed users must be addressed as app.users.<n>.<field>. */
    @Test
    void ssmStyleUserPropertiesBindIntoTheUsersList() {
        MapConfigurationPropertySource source = new MapConfigurationPropertySource(java.util.Map.of(
                "app.users.0.username", "alice", "app.users.0.password-hash", "hash-a",
                "app.users.1.username", "bob", "app.users.1.password-hash", "hash-b"));

        AccountsProperties accounts = new Binder(source).bind("app", AccountsProperties.class).get();

        assertThat(accounts.users()).containsExactly(
                new AccountsProperties.Account("alice", "hash-a"), new AccountsProperties.Account("bob", "hash-b"));
    }

    @Test
    void awsProfileDocumentsOnlyValidSsmParameterNames() throws Exception {
        String yaml = new String(new ClassPathResource("application-aws.yml").getInputStream().readAllBytes());

        assertThat(yaml).doesNotContain("[<n>]").contains("/value-screener/app.users.<n>.username")
                .contains("/value-screener/app.users.<n>.password-hash");
    }
}
