package com.valuescreener.research;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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
    public List<CompanySnapshotView> list(@RequestParam(required = false) String group) {
        return service.list(group);
    }

    @GetMapping("/{isin}")
    public CompanySnapshotView getByIsin(@PathVariable String isin) {
        return service.getByIsin(isin);
    }

    @PutMapping("/{isin}/group")
    public CompanySnapshotView assignGroup(@PathVariable String isin, @Valid @RequestBody AssignGroupRequest request) {
        return service.assignGroupManually(isin, request.groupKey());
    }
}
