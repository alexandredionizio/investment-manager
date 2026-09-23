package com.investmanager.api.quote.client.dto;

import java.util.List;

public record BrapiIndexHistoricalResult(
        String symbol,
        List<BrapiIndexHistoricalPoint> historicalDataPrice
) {
}