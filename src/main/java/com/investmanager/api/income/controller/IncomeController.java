package com.investmanager.api.income.controller;

import com.investmanager.api.income.dto.IncomeRequest;
import com.investmanager.api.income.dto.IncomeResponse;
import com.investmanager.api.income.service.IncomeService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
    public ResponseEntity<IncomeResponse> create (
            @Valid @RequestBody IncomeRequest request) {

        IncomeResponse response = incomeService.create(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    @GetMapping({"/{id}"})
    public ResponseEntity<IncomeResponse> findById(
            @PathVariable Long id) {

        return ResponseEntity.ok(
                incomeService.findById(id)
        );
    }

    @GetMapping
    public ResponseEntity<List<IncomeResponse>> findAll() {

        return ResponseEntity.ok(
                incomeService.findAll()
        );
    }

    @GetMapping ("/portfolio/{portfolioId}")
    public ResponseEntity<List<IncomeResponse>> findByPortfolioId (
            @PathVariable Long portfolioId) {

        return ResponseEntity.ok(
                incomeService.findByPortfolioId(portfolioId)
        );
    }
}
