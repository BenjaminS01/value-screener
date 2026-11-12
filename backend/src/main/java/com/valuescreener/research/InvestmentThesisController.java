package com.valuescreener.research;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/research/theses")
public class InvestmentThesisController {

    private final InvestmentThesisService service;

    public InvestmentThesisController(InvestmentThesisService service) {
        this.service = service;
    }

    @GetMapping("/{isin}")
    public InvestmentThesisView get(@PathVariable String isin) {
        return service.get(isin);
    }

    @PutMapping("/{isin}")
    public InvestmentThesisView save(@PathVariable String isin, @RequestBody SaveThesisRequest request) {
        return service.save(isin, request);
    }
}
