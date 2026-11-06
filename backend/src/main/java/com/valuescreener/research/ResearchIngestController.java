package com.valuescreener.research;

import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Local-only write path for the research skill; does not exist unless app.research-ingest.enabled=true. */
@RestController
@RequestMapping("/api/research/ingest")
@ConditionalOnProperty(prefix = "app.research-ingest", name = "enabled", havingValue = "true")
public class ResearchIngestController {

    private final CompanySnapshotService service;

    public ResearchIngestController(CompanySnapshotService service) {
        this.service = service;
    }

    @PostMapping("/snapshots")
    public CompanySnapshotView upsert(@Valid @RequestBody UpsertCompanySnapshotRequest request) {
        return service.upsert(request);
    }
}
