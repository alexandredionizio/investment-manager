package com.investmanager.api.benchmark.controller;

import com.investmanager.api.benchmark.dto.BenchmarkComparisonPointResponse;
import com.investmanager.api.benchmark.service.BenchmarkComparisonService;
import com.investmanager.api.portfoliohistory.model.PortfolioHistoryPeriod;
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
import java.util.List;

@RestController
@RequestMapping(
        "/api/v1/portfolios/{portfolioId}/benchmark-comparison"
)
@Tag(
        name = "Benchmark Comparison",
        description = "Endpoints for comparing portfolio returns with market benchmarks"
)
public class BenchmarkComparisonController {

    private final BenchmarkComparisonService benchmarkComparisonService;

    public BenchmarkComparisonController(
            BenchmarkComparisonService benchmarkComparisonService) {

        this.benchmarkComparisonService =
                benchmarkComparisonService;
    }

    @Operation(
            summary = "Compare portfolio returns with benchmarks",
            description = "Compares weighted portfolio returns with CDI and IBOV accumulated returns"
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Benchmark comparison retrieved successfully"
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid comparison period or dates"
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
    public ResponseEntity<List<BenchmarkComparisonPointResponse>>
    findComparison(
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
                benchmarkComparisonService.getComparison(
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