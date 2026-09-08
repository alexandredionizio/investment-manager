package com.investmanager.api.quote.client.dto;

import java.math.BigDecimal;

public record BrapiQuoteData(

        String shortName,
        String currency,
        BigDecimal regularMarketPrice,
        BigDecimal regularMarketChangePercent
) {
}
