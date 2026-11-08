package com.valuescreener.portfolio;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
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

    @GetMapping
    public List<?> list(Authentication authentication) {
        boolean admin = authentication != null && authentication.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
        return admin ? portfolioService.listForAdmin() : portfolioService.listForUser();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public void buy(@Valid @RequestBody BuyPositionRequest request) {
        portfolioService.buy(request);
    }

    @PutMapping("/{isin}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void correct(@PathVariable String isin, @Valid @RequestBody CorrectPositionRequest request) {
        portfolioService.correct(isin, request);
    }

    @DeleteMapping("/{isin}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String isin) {
        portfolioService.delete(isin);
    }

    @PostMapping("/sell")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void sell(@Valid @RequestBody SellPositionRequest request) {
        portfolioService.sell(request);
    }
}
