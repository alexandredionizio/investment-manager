package com.investmanager.api.transaction.controller;

import com.investmanager.api.transaction.dto.TransactionRequest;
import com.investmanager.api.transaction.dto.TransactionResponse;
import com.investmanager.api.transaction.service.TransactionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/transactions")
public class TransactionController {

    private final TransactionService transactionService;

    public TransactionController(
            TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @PostMapping
    public ResponseEntity<TransactionResponse> create(
            @Valid @RequestBody TransactionRequest request,
            Authentication authentication) {

        Long userId =
                Long.valueOf(authentication.getName());

        TransactionResponse response =
                transactionService.create(request, userId);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<TransactionResponse> findById(
            @PathVariable Long id,
            Authentication authentication) {

        Long userId =
                Long.valueOf(authentication.getName());

        TransactionResponse response =
                transactionService.findById(id, userId);

        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<List<TransactionResponse>> findAll(
            Authentication authentication) {

        Long userId =
                Long.valueOf(authentication.getName());

        List<TransactionResponse> transactions =
                transactionService.findAll(userId);

        return ResponseEntity.ok(transactions);
    }

    @GetMapping("/portfolio/{portfolioId}")
    public ResponseEntity<List<TransactionResponse>> listByPortfolio(
            @PathVariable Long portfolioId,
            Authentication authentication) {

        Long userId =
                Long.valueOf(authentication.getName());

        List<TransactionResponse> transactions =
                transactionService.findByPortfolioId(
                        portfolioId,
                        userId
                );

        return ResponseEntity.ok(transactions);
    }
}