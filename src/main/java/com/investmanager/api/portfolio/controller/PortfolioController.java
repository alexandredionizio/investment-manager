package com.investmanager.api.portfolio.controller;

import com.investmanager.api.portfolio.dto.CreatePortfolioRequest;
import com.investmanager.api.portfolio.dto.PortfolioResponse;
import com.investmanager.api.portfolio.service.PortfolioService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;
import java.util.List;

@RestController
@RequestMapping("/api/v1/portfolios")
public class PortfolioController {

    private final PortfolioService portfolioService;

    public PortfolioController(PortfolioService portfolioService) {
        this.portfolioService = portfolioService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PortfolioResponse create(
            @Valid @RequestBody CreatePortfolioRequest request,
            Authentication authentication) {

        Long userId = Long.valueOf(authentication.getName());

        return portfolioService.create(request, userId);
    }

    @GetMapping("/{id}")
    public PortfolioResponse findById(
            @PathVariable Long id,
            Authentication authentication) {

        Long userId = Long.valueOf(authentication.getName());

        return portfolioService.findById(id, userId);
    }

    @GetMapping
    public List<PortfolioResponse> findAll(
            Authentication authentication) {

        Long userId = Long.valueOf(authentication.getName());

        return portfolioService.findAll(userId);
    }
}
