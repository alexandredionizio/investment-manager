package com.investmanager.api.quote.controller;

import com.investmanager.api.quote.dto.QuoteResponse;
import com.investmanager.api.quote.service.QuoteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

@RestController
@RequestMapping("/api/v1/quotes")
@Tag(
        name = "Quotes",
        description = "Endpoints for retrieving current market quotes"
)
public class QuoteController {

    private final QuoteService quoteService;

    public QuoteController(QuoteService quoteService) {
        this.quoteService = quoteService;
    }

    @Operation(
            summary = "Get current quote",
            description = "Returns the current market price for the specified asset symbol"
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Quote retrieved successfully"
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "User not authenticated"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Quote not found"
            )
    })
    @GetMapping("/{symbol}")
    public QuoteResponse getQuote(
            @PathVariable String symbol) {

        BigDecimal price =
                quoteService.getCurrentPrice(symbol);

        return new QuoteResponse(
                symbol.toUpperCase(),
                price
        );
    }
}