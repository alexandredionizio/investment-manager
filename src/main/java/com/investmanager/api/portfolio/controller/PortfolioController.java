package com.investmanager.api.portfolio.controller;

import com.investmanager.api.portfolio.dto.CreatePortfolioRequest;
import com.investmanager.api.portfolio.dto.PortfolioResponse;
import com.investmanager.api.portfolio.service.PortfolioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/portfolios")
@Tag(
        name = "Portfolios",
        description = "Endpoints for managing user portfolios"
)
public class PortfolioController {

    private final PortfolioService portfolioService;

    public PortfolioController(PortfolioService portfolioService) {
        this.portfolioService = portfolioService;
    }

    @Operation(
            summary = "Create portfolio",
            description = "Creates a new portfolio for the authenticated user"
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
                    description = "Portfolio created successfully"
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid request data"
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "User not authenticated"
            )
    })
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PortfolioResponse create(
            @Valid @RequestBody CreatePortfolioRequest request,
            Authentication authentication) {

        Long userId = Long.valueOf(authentication.getName());

        return portfolioService.create(request, userId);
    }

    @Operation(
            summary = "Find portfolio by ID",
            description = "Returns a portfolio that belongs to the authenticated user"
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Portfolio found successfully"
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
    @GetMapping("/{id}")
    public PortfolioResponse findById(
            @PathVariable Long id,
            Authentication authentication) {

        Long userId = Long.valueOf(authentication.getName());

        return portfolioService.findById(id, userId);
    }

    @Operation(
            summary = "List portfolios",
            description = "Returns all portfolios belonging to the authenticated user"
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Portfolios retrieved successfully"
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "User not authenticated"
            )
    })
    @GetMapping
    public List<PortfolioResponse> findAll(
            Authentication authentication) {

        Long userId = Long.valueOf(authentication.getName());

        return portfolioService.findAll(userId);
    }
}