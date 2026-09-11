package com.investmanager.api.income.controller;

import com.investmanager.api.income.dto.IncomeRequest;
import com.investmanager.api.income.dto.IncomeResponse;
import com.investmanager.api.income.service.IncomeService;
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
@RequestMapping("/api/v1/incomes")
@Tag(
        name = "Incomes",
        description = "Endpoints for managing investment incomes"
)
public class IncomeController {

    private final IncomeService incomeService;

    public IncomeController(IncomeService incomeService) {
        this.incomeService = incomeService;
    }

    @Operation(
            summary = "Create income",
            description = "Creates a new income for a portfolio belonging to the authenticated user"
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
                    description = "Income created successfully"
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid request data"
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "User not authenticated"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Portfolio or asset not found"
            )
    })
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

    @Operation(
            summary = "Find income by ID",
            description = "Returns an income belonging to the authenticated user"
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Income found successfully"
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "User not authenticated"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Income not found"
            )
    })
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

    @Operation(
            summary = "List incomes",
            description = "Returns all incomes belonging to the authenticated user"
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Incomes retrieved successfully"
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "User not authenticated"
            )
    })
    @GetMapping
    public ResponseEntity<List<IncomeResponse>> findAll(
            Authentication authentication) {

        Long userId =
                Long.valueOf(authentication.getName());

        return ResponseEntity.ok(
                incomeService.findAll(userId)
        );
    }

    @Operation(
            summary = "List incomes by portfolio",
            description = "Returns all incomes for a portfolio belonging to the authenticated user"
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Incomes retrieved successfully"
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