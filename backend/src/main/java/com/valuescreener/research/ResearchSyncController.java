package com.valuescreener.research;

import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Live-side import of reviewed snapshots from the owner's machine; does not exist unless app.research-sync.enabled=true. */
@RestController
@RequestMapping("/api/research/sync")
@ConditionalOnProperty(prefix = "app.research-sync", name = "enabled", havingValue = "true")
public class ResearchSyncController {

    private static final Logger log = LoggerFactory.getLogger(ResearchSyncController.class);

    private final CompanySnapshotService service;

    public ResearchSyncController(CompanySnapshotService service) {
        this.service = service;
    }

    /** Honors groupSource from the body (the export carries a reviewed MANUAL assignment); missing means AI. */
    @PostMapping("/snapshots")
    public CompanySnapshotView sync(@Valid @RequestBody UpsertCompanySnapshotRequest request) {
        GroupSource source = request.groupSource() == null ? GroupSource.AI : request.groupSource();
        CompanySnapshotView view = service.upsert(request, source, HistoryOrigin.SYNC);
        SnapshotGroupView group = view.group();
        log.info("research import isin={} group={} source={} findings={} via=sync-token",
                view.isin(), group == null ? "-" : group.key(), group == null ? "-" : group.source(),
                view.findings().size());
        return view;
    }
}
