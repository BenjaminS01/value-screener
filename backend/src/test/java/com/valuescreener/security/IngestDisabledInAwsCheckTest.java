package com.valuescreener.security;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IngestDisabledInAwsCheckTest {

    @Test
    void failsWhenIngestIsEnabled() {
        assertThatThrownBy(() -> new IngestDisabledInAwsCheck(true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("app.research-ingest.enabled");
    }

    @Test
    void passesWhenIngestIsDisabled() {
        new IngestDisabledInAwsCheck(false);
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(IngestDisabledInAwsCheck.class);

    @Test
    void awsProfileRefusesToStartWithIngestEnabled() {
        runner.withPropertyValues("spring.profiles.active=aws", "app.research-ingest.enabled=true")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void awsProfileStartsWithIngestDisabledOrSyncEnabled() {
        runner.withPropertyValues("spring.profiles.active=aws", "app.research-sync.enabled=true")
                .run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    void checkDoesNotExistOutsideAws() {
        runner.withPropertyValues("app.research-ingest.enabled=true")
                .run(context -> assertThat(context).hasNotFailed()
                        .doesNotHaveBean(IngestDisabledInAwsCheck.class));
    }
}
