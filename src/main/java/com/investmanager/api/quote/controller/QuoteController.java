package com.investmanager.api.quote.controller;

import com.investmanager.api.quote.dto.QuoteResponse;
import com.investmanager.api.quote.service.QuoteService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

@RestController
@RequestMapping("/api/v1/quotes")
public class QuoteController {

    private final QuoteService quoteService;

    public QuoteController(QuoteService quoteService) {
        this.quoteService = quoteService;
    }

    @GetMapping("/{symbol}")
    public QuoteResponse getQuote(@PathVariable String symbol) {

        BigDecimal price =
                quoteService.getCurrentPrice(symbol);

        return new QuoteResponse(
                symbol.toUpperCase(),
                price
        );
    }


}
