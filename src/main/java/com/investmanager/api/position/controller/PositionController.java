package com.investmanager.api.position.controller;

import com.investmanager.api.position.dto.PositionMarketResponse;
import com.investmanager.api.position.dto.PositionResponse;
import com.investmanager.api.position.service.PositionService;
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
@RequestMapping("/api/v1/portfolios/{portfolioId}/positions")
@Tag(
        name = "Positions",
        description = "Endpoints for retrieving portfolio positions"
)
public class PositionController {

    private final PositionService positionService;

    public PositionController(PositionService positionService) {
        this.positionService = positionService;
    }

    @Operation(
            summary = "Get portfolio positions",
            description = "Calculates the accounting positions of a portfolio based on its transaction history"
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Positions retrieved successfully"
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
    public ResponseEntity<List<PositionResponse>> findPositions(
            @PathVariable Long portfolioId,
            Authentication authentication) {

        Long userId =
                Long.valueOf(authentication.getName());

        return ResponseEntity.ok(
                positionService.calculatePositions(
                        portfolioId,
                        userId
                )
        );
    }

    @Operation(
            summary = "Get portfolio market positions",
            description = "Calculates portfolio positions using current market prices"
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Market positions retrieved successfully"
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
    @GetMapping("/market")
    public ResponseEntity<List<PositionMarketResponse>> findMarketPositions(
            @PathVariable Long portfolioId,
            Authentication authentication) {

        Long userId =
                Long.valueOf(authentication.getName());

        return ResponseEntity.ok(
                positionService.calculateMarketPositions(
                        portfolioId,
                        userId
                )
        );
    }
}