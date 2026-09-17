package com.investmanager.api.realizedresult.controller;

import com.investmanager.api.realizedresult.dto.RealizedResultResponse;
import com.investmanager.api.realizedresult.service.RealizedResultService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/portfolios/{portfolioId}/realized-results")
@Tag(
        name = "Realized Results",
        description = "Endpoints for retrieving realized portfolio results"
)
public class RealizedResultController {

    private final RealizedResultService realizedResultService;

    public RealizedResultController(
            RealizedResultService realizedResultService) {

        this.realizedResultService = realizedResultService;
    }

    @Operation(
            summary = "Get portfolio realized results",
            description = "Calculates realized profit and loss by asset based on the portfolio transaction history"
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Realized results retrieved successfully"
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
    public ResponseEntity<List<RealizedResultResponse>> findRealizedResults(
            @PathVariable Long portfolioId,
            Authentication authentication) {

        Long userId =
                Long.valueOf(authentication.getName());

        return ResponseEntity.ok(
                realizedResultService.calculateRealizedResults(
                        portfolioId,
                        userId
                )
        );
    }
}