package com.investmanager.api.portfoliohistory.controller;

import com.investmanager.api.portfoliohistory.dto.PortfolioHistoryResponse;
import com.investmanager.api.portfoliohistory.model.PortfolioHistoryPeriod;
import com.investmanager.api.portfoliohistory.service.PortfolioHistoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/portfolios/{portfolioId}/history")
@Tag(
        name = "Portfolio History",
        description = "Endpoints for retrieving historical portfolio evolution"
)
public class PortfolioHistoryController {

    private final PortfolioHistoryService portfolioHistoryService;

    public PortfolioHistoryController(
            PortfolioHistoryService portfolioHistoryService) {

        this.portfolioHistoryService =
                portfolioHistoryService;
    }

    @Operation(
            summary = "Get portfolio history",
            description = "Calculates the historical portfolio value based on transactions and historical market prices"
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Portfolio history retrieved successfully"
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid history period or dates"
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
    @GetMapping
    public ResponseEntity<PortfolioHistoryResponse> findHistory(
            @PathVariable Long portfolioId,
            @RequestParam PortfolioHistoryPeriod period,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate startDate,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate endDate,
            Authentication authentication) {

        Long userId =
                Long.valueOf(
                        authentication.getName()
                );

        LocalDate referenceEndDate =
                period == PortfolioHistoryPeriod.CUSTOM
                        ? null
                        : LocalDate.now();

        return ResponseEntity.ok(
                portfolioHistoryService.getHistory(
                        portfolioId,
                        userId,
                        period,
                        startDate,
                        endDate,
                        referenceEndDate
                )
        );
    }
}