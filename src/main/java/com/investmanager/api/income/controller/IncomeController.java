package com.investmanager.api.income.controller;

import com.investmanager.api.income.dto.IncomeRequest;
import com.investmanager.api.income.dto.IncomeResponse;
import com.investmanager.api.income.service.IncomeService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/incomes")
public class IncomeController {

    private final IncomeService incomeService;

    public IncomeController(IncomeService incomeService) {
        this.incomeService = incomeService;
    }

    @PostMapping
    public ResponseEntity<IncomeResponse> create(
            @Valid @RequestBody IncomeRequest request,
            Authentication authentication) {

        Long userId =
                Long.valueOf(authentication.getName());

        IncomeResponse response =
                incomeService.create(request, userId);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<IncomeResponse> findById(
            @PathVariable Long id,
            Authentication authentication) {

        Long userId =
                Long.valueOf(authentication.getName());

        return ResponseEntity.ok(
                incomeService.findById(id, userId)
        );
    }

    @GetMapping
    public ResponseEntity<List<IncomeResponse>> findAll(
            Authentication authentication) {

        Long userId =
                Long.valueOf(authentication.getName());

        return ResponseEntity.ok(
                incomeService.findAll(userId)
        );
    }

    @GetMapping("/portfolio/{portfolioId}")
    public ResponseEntity<List<IncomeResponse>> findByPortfolioId(
            @PathVariable Long portfolioId,
            Authentication authentication) {

        Long userId =
                Long.valueOf(authentication.getName());

        return ResponseEntity.ok(
                incomeService.findByPortfolioId(
                        portfolioId,
                        userId
                )
        );
    }
}