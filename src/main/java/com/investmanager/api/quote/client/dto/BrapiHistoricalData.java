package com.investmanager.api.quote.client.dto;

import java.util.List;

public record BrapiHistoricalData(
        String usedInterval,
        String usedRange,
        List<BrapiHistoricalPrice> historicalDataPrice
) {
}