package com.investmanager.api.transaction.controller;

import com.investmanager.api.portfolio.Portfolio;
import com.investmanager.api.transaction.dto.TransactionRequest;
import com.investmanager.api.transaction.dto.TransactionResponse;
import com.investmanager.api.transaction.repository.TransactionRepository;
import com.investmanager.api.transaction.service.TransactionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/transactions")
public class TransactionController {

    private final TransactionService transactionService;

    public TransactionController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @PostMapping
    public ResponseEntity<TransactionResponse> create(
            @Valid @RequestBody TransactionRequest request) {

        TransactionResponse response = transactionService.create(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<TransactionResponse> fundById(@PathVariable Long id) {

        TransactionResponse response = transactionService.findById(id);

        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<List<TransactionResponse>> findAll() {

        List<TransactionResponse> transactions =
                transactionService.findAll();

        return ResponseEntity.ok(transactions);
    }

    @GetMapping("/portfolio/{portfolioId}")
    public ResponseEntity<List<TransactionResponse>> listByPortfolio(
            @PathVariable Long portfolioId) {

        List<TransactionResponse> transactions =
                transactionService.findByPortfolioId(portfolioId);

        return ResponseEntity.ok(transactions);
    }
}
