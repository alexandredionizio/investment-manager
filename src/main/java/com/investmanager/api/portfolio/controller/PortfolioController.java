package com.investmanager.api.portfolio.controller;

import com.investmanager.api.portfolio.dto.CreatePortfolioRequest;
import com.investmanager.api.portfolio.dto.PortfolioResponse;
import com.investmanager.api.portfolio.repository.PortfolioRepository;
import com.investmanager.api.portfolio.service.PortfolioService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

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
            @Valid @RequestBody CreatePortfolioRequest request) {

        return portfolioService.create(request);
    }

    @GetMapping("/{id}")
    public PortfolioResponse findById(@PathVariable Long id) {

        return portfolioService.findById(id);
    }

    @GetMapping
    public List<PortfolioResponse> findAll() {

        return portfolioService.findAll();
    }
}
