package com.valuescreener.portfolio;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/portfolio")
public class PortfolioController {

    private final PortfolioService portfolioService;

    public PortfolioController(PortfolioService portfolioService) {
        this.portfolioService = portfolioService;
    }

    @GetMapping("/public")
    public List<PublicPortfolioPositionView> listPublicPositions() {
        return portfolioService.listPublicPositions();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public void buy(@Valid @RequestBody BuyPositionRequest request) {
        portfolioService.buy(request);
    }

    @PostMapping("/sell")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void sell(@Valid @RequestBody SellPositionRequest request) {
        portfolioService.sell(request);
    }
}
