package com.valuescreener.research;

import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Local-only write path for the research skill; does not exist unless app.research-ingest.enabled=true. */
@RestController
@RequestMapping("/api/research/ingest")
@ConditionalOnProperty(prefix = "app.research-ingest", name = "enabled", havingValue = "true")
public class ResearchIngestController {

    private final CompanySnapshotService service;
    private final StockGroupService groupService;

    public ResearchIngestController(CompanySnapshotService service, StockGroupService groupService) {
        this.service = service;
        this.groupService = groupService;
    }

    /** The group catalog the skill must choose from. */
    @GetMapping("/groups")
    public List<StockGroupView> groups() {
        return groupService.list();
    }

    /** The server always records the group as an AI proposal; groupSource in the body is ignored. */
    @PostMapping("/snapshots")
    public CompanySnapshotView upsert(@Valid @RequestBody UpsertCompanySnapshotRequest request) {
        return service.upsert(request, GroupSource.AI);
    }

    /** Reviewed snapshot in import format, for the host-side push script. */
    @GetMapping("/snapshots/{isin}/export")
    public UpsertCompanySnapshotRequest export(@PathVariable String isin) {
        return service.export(isin);
    }
}
