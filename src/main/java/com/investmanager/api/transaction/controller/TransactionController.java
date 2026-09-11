package com.investmanager.api.transaction.controller;

import com.investmanager.api.transaction.dto.TransactionRequest;
import com.investmanager.api.transaction.dto.TransactionResponse;
import com.investmanager.api.transaction.service.TransactionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/transactions")
@Tag(
        name = "Transactions",
        description = "Endpoints for managing investment transactions"
)
public class TransactionController {

    private final TransactionService transactionService;

    public TransactionController(
            TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @Operation(
            summary = "Create transaction",
            description = "Creates a new BUY or SELL transaction for a portfolio belonging to the authenticated user"
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
                    description = "Transaction created successfully"
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid request data or business rule violation"
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "User not authenticated"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Portfolio, asset or broker not found"
            )
    })
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

    @Operation(
            summary = "Find transaction by ID",
            description = "Returns a transaction belonging to the authenticated user"
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Transaction found successfully"
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "User not authenticated"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Transaction not found"
            )
    })
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

    @Operation(
            summary = "List transactions",
            description = "Returns all transactions belonging to the authenticated user"
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Transactions retrieved successfully"
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "User not authenticated"
            )
    })
    @GetMapping
    public ResponseEntity<List<TransactionResponse>> findAll(
            Authentication authentication) {

        Long userId =
                Long.valueOf(authentication.getName());

        List<TransactionResponse> transactions =
                transactionService.findAll(userId);

        return ResponseEntity.ok(transactions);
    }

    @Operation(
            summary = "List transactions by portfolio",
            description = "Returns all transactions for a portfolio belonging to the authenticated user"
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Transactions retrieved successfully"
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "User not authenticated"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Portfolio not found"
            )
    })
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