package com.investmanager.api.quote.client.dto;

import java.math.BigDecimal;

public record BrapiIndexHistoricalPoint(
        Long date,
        BigDecimal open,
        BigDecimal high,
        BigDecimal low,
        BigDecimal close,
        BigDecimal volume,
        BigDecimal adjustedClose
) {
}