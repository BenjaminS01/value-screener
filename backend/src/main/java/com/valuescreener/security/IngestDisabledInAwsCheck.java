package com.valuescreener.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Anti-goal of the serverless design: the machine ingest endpoint must not exist in AWS. The profile "aws"
 * refuses to start when {@code app.research-ingest.enabled=true}. ({@code app.research-sync} is not checked:
 * it is enabled later on purpose.) The message names the property only, never a value.
 */
@Component
@Profile("aws")
public class IngestDisabledInAwsCheck {

    public IngestDisabledInAwsCheck(@Value("${app.research-ingest.enabled:false}") boolean ingestEnabled) {
        if (ingestEnabled) {
            throw new IllegalStateException("app.research-ingest.enabled must not be true in the aws profile: "
                    + "the ingest endpoint must not exist in AWS");
        }
    }
}
