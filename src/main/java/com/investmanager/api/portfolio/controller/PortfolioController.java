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
    private final com.investmanager.api.portfolio.service.PortfolioDeletionService deletionService;

    public PortfolioController(PortfolioService portfolioService,
                               com.investmanager.api.portfolio.service.PortfolioDeletionService deletionService) {
        this.portfolioService = portfolioService;
        this.deletionService = deletionService;
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

    @Operation(summary = "Rename portfolio", description = "Updates only the name of a portfolio owned by the authenticated user")
    @PutMapping("/{id}/name")
    public PortfolioResponse rename(
            @PathVariable Long id,
            @Valid @RequestBody com.investmanager.api.portfolio.dto.RenamePortfolioRequest request,
            Authentication authentication) {
        Long userId = Long.valueOf(authentication.getName());
        return portfolioService.rename(id, request, userId);
    }

    @Operation(summary = "Delete portfolio", description = "Permanently deletes an owned portfolio and all dependent records, including B3 import audit history")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id, Authentication authentication) {
        Long userId = Long.valueOf(authentication.getName());
        deletionService.delete(id, userId);
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