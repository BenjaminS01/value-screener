package com.valuescreener.research;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/research/groups")
public class StockGroupController {

    private final StockGroupService service;

    public StockGroupController(StockGroupService service) {
        this.service = service;
    }

    @GetMapping
    public List<StockGroupView> list() {
        return service.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public StockGroupView create(@Valid @RequestBody CreateStockGroupRequest request) {
        return service.create(request);
    }

    @PatchMapping("/{key}")
    public StockGroupView update(@PathVariable String key, @Valid @RequestBody UpdateStockGroupRequest request) {
        return service.update(key, request);
    }

    @DeleteMapping("/{key}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String key) {
        service.delete(key);
    }
}
