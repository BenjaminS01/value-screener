package com.valuescreener.research;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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

    @PostMapping
    public CompanySnapshotView upsert(@Valid @RequestBody UpsertCompanySnapshotRequest request) {
        return service.upsert(request);
    }

    @GetMapping
    public List<CompanySnapshotView> listAll() {
        return service.listAll();
    }

    @GetMapping("/{isin}")
    public CompanySnapshotView getByIsin(@PathVariable String isin) {
        return service.getByIsin(isin);
    }
}
