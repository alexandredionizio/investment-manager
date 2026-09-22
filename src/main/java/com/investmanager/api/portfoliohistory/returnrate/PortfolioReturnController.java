package com.investmanager.api.portfoliohistory.returnrate;

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

@RestController
@RequestMapping("/api/v1/portfolios/{portfolioId}/returns")
@Tag(
        name = "Portfolio Returns",
        description = "Endpoints for retrieving weighted portfolio returns"
)
public class PortfolioReturnController {

    private final PortfolioReturnService portfolioReturnService;

    public PortfolioReturnController(
            PortfolioReturnService portfolioReturnService) {

        this.portfolioReturnService =
                portfolioReturnService;
    }

    @Operation(
            summary = "Get portfolio returns",
            description = "Calculates weighted portfolio returns neutralizing purchases and sales while considering incomes"
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Portfolio returns retrieved successfully"
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid return period or dates"
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
    public ResponseEntity<PortfolioReturnResponse> findReturns(
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
                portfolioReturnService.getReturns(
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