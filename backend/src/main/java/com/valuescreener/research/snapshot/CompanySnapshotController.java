package com.valuescreener.research.snapshot;

import com.valuescreener.research.history.SnapshotHistoryView;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/research/snapshots")
public class CompanySnapshotController {

    private final CompanySnapshotService service;

    public CompanySnapshotController(CompanySnapshotService service) {
        this.service = service;
    }

    @GetMapping
    public List<SnapshotSummaryView> list() {
        return service.list();
    }

    @GetMapping("/{isin}")
    public CompanySnapshotView getByIsin(@PathVariable String isin) {
        return service.getByIsin(isin);
    }

    @GetMapping("/{isin}/history")
    public SnapshotHistoryView history(@PathVariable String isin) {
        return service.history(isin);
    }

    @PutMapping("/{isin}/group")
    public CompanySnapshotView assignGroup(@PathVariable String isin, @Valid @RequestBody AssignGroupRequest request) {
        return service.assignGroupManually(isin, request.groupKey());
    }
}
