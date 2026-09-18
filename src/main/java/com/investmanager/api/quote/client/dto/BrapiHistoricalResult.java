package com.investmanager.api.quote.client.dto;

public record BrapiHistoricalResult(
        String requestedSymbol,
        String symbol,
        boolean changed,
        BrapiHistoricalData data
) {
}