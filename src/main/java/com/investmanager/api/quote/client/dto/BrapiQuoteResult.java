package com.investmanager.api.quote.client.dto;

public record BrapiQuoteResult(

        String requestedSymbol,
        String symbol,
        BrapiQuoteData data
) {
}
