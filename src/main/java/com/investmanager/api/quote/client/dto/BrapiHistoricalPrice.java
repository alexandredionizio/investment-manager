package com.investmanager.api.quote.client.dto;

import java.math.BigDecimal;

public record BrapiHistoricalPrice(
        Long date,
        BigDecimal open,
        BigDecimal high,
        BigDecimal low,
        BigDecimal close,
        Long volume,
        BigDecimal adjustedClose
) {
}