package com.investmanager.api.quote.dto;

import java.math.BigDecimal;

public record QuoteResponse(
        String symbol,
        BigDecimal price
) {
}
