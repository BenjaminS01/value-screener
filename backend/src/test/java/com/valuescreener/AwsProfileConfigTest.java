package com.valuescreener;

import org.junit.jupiter.api.Test;
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
}
